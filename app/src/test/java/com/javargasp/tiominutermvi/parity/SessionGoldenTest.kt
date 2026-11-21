package com.javargasp.tiominutermvi.parity

import com.javargasp.tiominutermvi.domain.gymsession.GymSession
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionStore
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionTracker
import com.javargasp.tiominutermvi.domain.program.repository.ProgramStrengthSessions
import com.javargasp.tiominutermvi.domain.program.repository.StrengthSession
import com.javargasp.tiominutermvi.domain.program.repository.StrengthSessionStore
import com.javargasp.tiominutermvi.domain.session.ActiveSession
import com.javargasp.tiominutermvi.domain.session.ActiveSessionTracker
import com.javargasp.tiominutermvi.domain.session.ActiveTimeMeter
import com.javargasp.tiominutermvi.domain.session.ActiveWorkoutSession
import com.javargasp.tiominutermvi.domain.session.SESSION_INACTIVITY_TIMEOUT
import com.javargasp.tiominutermvi.domain.session.SessionRules
import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.testutil.InMemoryActiveSessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Test

/**
 * shared/golden/sessions.json: the session rules (Session.kt, ActiveSession.kt,
 * ActiveWorkoutSession.kt, ProgramStrengthSessions.kt, GymSessionTracker.kt) as event sequences → what is stored after
 * each event, what is recorded (an abandoned session: its active or set-to-set seconds, completed
 * at its last activity, dated on its start) and the active seconds a finished workout records.
 *
 * Time: `advance` moves the monotonic and the wall clock together (virtual time of the test
 * scheduler, so the 5-s checkpoints run as in the app); `clock` moves only the wall clock (a
 * clock changed by hand, possibly back). `processDeath` drops the ViewModel's meter and session
 * (the store survives) and a new one opens the same workout, as after a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionGoldenTest {

    private val baseWall = 1_761_048_000_000L // 2025-10-21T12:00:00Z

    // ------------------------------------------------------------------
    // Active time (daily, program cardio)
    // ------------------------------------------------------------------

    private class Ev(val type: String, val value: Long? = null, val flag: Boolean? = null, val key: String? = null)

    private fun advance(ms: Long) = Ev("advance", value = ms)
    private fun clock(ms: Long) = Ev("clock", value = ms)
    private fun timer(on: Boolean) = Ev("timer", flag = on)
    private fun onScreen(on: Boolean) = Ev("onScreen", flag = on)
    private fun visible(on: Boolean) = Ev("visible", flag = on)
    private val start = Ev("start")
    private val action = Ev("action")
    private val finish = Ev("finish")
    private val processDeath = Ev("processDeath")
    private val closeInactive = Ev("closeInactive")
    private fun open(key: String) = Ev("open", key = key)

    private val twoHours = SESSION_INACTIVITY_TIMEOUT

    private val activeScenarios: List<Pair<String, List<Ev>>> = listOf(
        "daily: a timed exercise, then a REPS one shown, hidden and shown again" to listOf(
            start, timer(true), advance(60_000), timer(false), onScreen(true), advance(30_000), visible(false),
            advance(20_000), visible(true), advance(10_000), onScreen(false), action, finish
        ),
        "a running timer counts in the background" to listOf(
            start, timer(true), visible(false), advance(60_000), visible(true), advance(5_000), timer(false), finish
        ),
        "an untimed step doesn't count in the background" to listOf(
            start, onScreen(true), visible(false), advance(60_000), visible(true), advance(12_500), finish
        ),
        "an explicit pause is not counted" to listOf(
            start, timer(true), advance(10_000), timer(false), advance(30_000), action, timer(true), advance(10_000), finish
        ),
        "nothing counts before the first action; opening and leaving records nothing" to listOf(
            timer(true), advance(15_000), timer(false), advance(1_000), closeInactive
        ),
        "counting before start is kept by the first save" to listOf(
            timer(true), advance(7_000), start, advance(6_000), timer(false), finish
        ),
        "process death mid-workout, reopened within 2 h: the old session is recorded with the last saved time" to listOf(
            start, timer(true), advance(42_500), processDeath, advance(60_000), start, timer(true), advance(20_000), finish
        ),
        "process death, then the app starts exactly 2 h later: still open" to listOf(
            start, timer(true), advance(30_000), timer(false), processDeath, advance(twoHours), closeInactive
        ),
        "process death, then the app starts 2 h + 1 ms later: recorded" to listOf(
            start, timer(true), advance(30_000), timer(false), processDeath, advance(twoHours + 1), closeInactive
        ),
        "back after more than 2 h on the same screen: the old session is recorded and the count restarts" to listOf(
            start, timer(true), advance(10_000), timer(false), advance(twoHours + 1_000), action, timer(true),
            advance(20_000), timer(false), finish
        ),
        "back after exactly 2 h: the same session goes on" to listOf(
            start, timer(true), advance(10_000), timer(false), advance(twoHours), action, timer(true), advance(5_000), finish
        ),
        "the clock moved back: never inactive" to listOf(
            start, timer(true), advance(10_000), timer(false), clock(-3_600_000), advance(3 * 3_600_000), closeInactive,
            action, finish
        ),
        "the clock moved forward over 2 h: closed lazily" to listOf(
            start, timer(true), advance(10_000), timer(false), clock(3 * 3_600_000), closeInactive
        ),
        "a second start and a second finish do nothing" to listOf(
            start, timer(true), advance(8_000), start, timer(false), finish, finish
        ),
        "another workout of the same kind is open: starting records it first" to listOf(
            open("daily:1"), advance(5_000), start, timer(true), advance(5_000), timer(false), finish
        ),
        "a long workout: many checkpoints" to listOf(
            start, timer(true), advance(300_000), visible(false), advance(300_000), visible(true), timer(false),
            onScreen(true), advance(61_000), onScreen(false), finish
        )
    )

    private fun runActive(events: List<Ev>): JsonElement {
        val key = "daily:3"
        lateinit var steps: List<JsonElement>
        lateinit var finale: JsonObject
        runTest {
            var wallOffset = 0L
            val wall = WallClock { baseWall + testScheduler.currentTime + wallOffset }
            val mono = MonotonicTimeSource { testScheduler.currentTime }
            val store = InMemoryActiveSessionStore()
            val tracker = ActiveSessionTracker(store, wall, "GoldenSession")
            fun newSession(scope: TestScope) = ActiveTimeMeter(mono).let { meter ->
                meter to ActiveWorkoutSession(scope.backgroundScope, tracker, meter, key)
            }
            var (meter, session) = newSession(this)
            val finished = mutableListOf<JsonElement>()
            val announced = mutableListOf<JsonElement>()
            val out = mutableListOf<JsonElement>()
            for (event in events) {
                when (event.type) {
                    "advance" -> advanceTimeBy(event.value!!)
                    "clock" -> wallOffset += event.value!!
                    "start" -> session.start().forEach { announced += num(it.durationSeconds) }
                    "timer" -> session.setTimerRunning(event.flag!!)
                    "onScreen" -> session.setActiveOnScreen(event.flag!!)
                    "visible" -> session.setVisible(event.flag!!)
                    "action" -> session.onUserAction()
                    "finish" -> finished += num(session.finish())
                    "processDeath" -> {
                        session.close()
                        val next = newSession(this)
                        meter = next.first
                        session = next.second
                    }
                    "closeInactive" -> tracker.closeInactive()
                    "open" -> tracker.start(event.key!!)
                }
                runCurrent()
                out += obj(
                    "event" to str(event.type),
                    "meterSeconds" to num(meter.seconds),
                    "stored" to activeJson(store.session)
                )
            }
            steps = out
            finale = obj(
                "recorded" to arr(store.abandoned.map(::activeJson)),
                "finishedSeconds" to arr(finished),
                "announcedSeconds" to arr(announced),
                "saves" to num(store.saves.size)
            )
            events.lastOrNull()?.let { session.close() }
        }
        return obj(
            "events" to arr(events.map(::eventJson)),
            "steps" to arr(steps),
            "result" to finale
        )
    }

    private fun eventJson(event: Ev): JsonElement = obj(
        *listOfNotNull(
            "type" to str(event.type),
            event.value?.let { "ms" to num(it) },
            event.flag?.let { "on" to bool(it) },
            event.key?.let { "key" to str(it) }
        ).toTypedArray()
    )

    private fun activeJson(session: ActiveSession?): JsonElement = session?.let {
        obj(
            "key" to str(it.key),
            "startedAt" to num(it.startedAt),
            "activeSeconds" to num(it.activeSeconds),
            "lastActivityAt" to num(it.lastActivityAt)
        )
    } ?: str(null)

    // ------------------------------------------------------------------
    // Set to set (program strength; the gym's rule)
    // ------------------------------------------------------------------

    private class MemoryStrengthStore : StrengthSessionStore {
        val sessions = linkedMapOf<Triple<Long, Int, Int>, StrengthSession>()
        val abandoned = mutableListOf<StrengthSession>()
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
        override suspend fun get(programId: Long, week: Int, dayIndex: Int) = sessions[Triple(programId, week, dayIndex)]
        override suspend fun getAll() = sessions.values.toList()
        override suspend fun save(session: StrengthSession) {
            sessions[Triple(session.programId, session.week, session.dayIndex)] = session
        }
        override suspend fun delete(programId: Long, week: Int, dayIndex: Int) {
            sessions.remove(Triple(programId, week, dayIndex))
        }
        override suspend fun recordAbandoned(session: StrengthSession) {
            abandoned += session
        }
    }

    private fun set(day: Long = 0) = Ev("set", value = day)

    private val strengthScenarios: List<Pair<String, List<Ev>>> = listOf(
        "three sets: first → last" to listOf(set(), advance(90_000), set(), advance(120_500), set()),
        "a set exactly 2 h after the last one extends the session" to listOf(set(), advance(60_000), set(), advance(twoHours), set()),
        "a set 2 h + 1 ms after the last one records the old session and starts a new one" to listOf(
            set(), advance(60_000), set(), advance(twoHours + 1), set(), advance(30_000), set()
        ),
        "closeInactive after 2 h + 1 s records it; at 2 h it stays" to listOf(
            set(), advance(45_000), set(), advance(twoHours), closeInactive, advance(1_000), closeInactive
        ),
        "the clock moved back: the start moves to now, never a negative duration" to listOf(
            set(), advance(30_000), clock(-600_000), set(), advance(10_000), set()
        ),
        "two workouts' sessions are independent" to listOf(
            set(0), advance(10_000), set(1), advance(twoHours - 5_000), set(0), advance(10_000), closeInactive
        ),
        "a single set: duration 0" to listOf(set(), advance(twoHours + 1), closeInactive)
    )

    private fun runStrength(events: List<Ev>): JsonElement {
        lateinit var steps: List<JsonElement>
        lateinit var recorded: List<JsonElement>
        lateinit var announced: List<JsonElement>
        runTest {
            var wallOffset = 0L
            val wall = WallClock { baseWall + testScheduler.currentTime + wallOffset }
            val store = MemoryStrengthStore()
            val sessions = ProgramStrengthSessions(store, wall)
            val out = mutableListOf<JsonElement>()
            for (event in events) {
                when (event.type) {
                    "advance" -> advanceTimeBy(event.value!!)
                    "clock" -> wallOffset += event.value!!
                    "set" -> sessions.onSetCompleted(programId = 7, week = 1, dayIndex = event.value!!.toInt())
                    "closeInactive" -> sessions.closeInactive()
                }
                runCurrent()
                out += obj("event" to str(event.type), "stored" to arr(store.sessions.values.map(::strengthJson)))
            }
            steps = out
            recorded = store.abandoned.map(::strengthJson)
            announced = sessions.takeClosedSessions().map { num(it.durationSeconds) }
        }
        return obj(
            "events" to arr(events.map(::eventJson)),
            "steps" to arr(steps),
            "result" to obj("recorded" to arr(recorded), "announcedSeconds" to arr(announced))
        )
    }

    private fun strengthJson(session: StrengthSession): JsonElement = obj(
        "dayIndex" to num(session.dayIndex),
        "startedAt" to num(session.startedAt),
        "lastActivityAt" to num(session.lastActivityAt),
        "durationSeconds" to num(session.durationSeconds)
    )

    // ------------------------------------------------------------------
    // Gym (GymSessionTracker: the same set-to-set rule; a close also resets the routine's progress,
    // a completion is dated now; the web reuses its strength sessions for it)
    // ------------------------------------------------------------------

    private class MemoryGymStore : GymSessionStore {
        val sessions = linkedMapOf<Int, GymSession>()
        val cleared = mutableListOf<Int>()
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
        override suspend fun get(routineId: Int) = sessions[routineId]
        override suspend fun getAll() = sessions.values.toList()
        override suspend fun save(session: GymSession) {
            sessions[session.routineId] = session
        }
        override suspend fun delete(routineId: Int) {
            sessions.remove(routineId)
        }
        override suspend fun clearProgress(routineId: Int) {
            cleared += routineId
        }
        override suspend fun routineTitle(routineId: Int) = "Routine $routineId"
    }

    private fun gymSet(routineId: Long = 64) = Ev("set", value = routineId)
    private fun complete(routineId: Long = 64) = Ev("complete", value = routineId)
    private fun discard(routineId: Long = 64) = Ev("discard", value = routineId)

    private val gymScenarios: List<Pair<String, List<Ev>>> = listOf(
        "sets then the routine completed: one entry dated now, first → last set" to listOf(
            gymSet(), advance(90_000), gymSet(), advance(600_000), gymSet(), advance(5_000), complete()
        ),
        "a completion shown again (a reload) records nothing more" to listOf(gymSet(), advance(60_000), gymSet(), complete(), complete()),
        "1 h 59 min without a set: still open" to listOf(gymSet(), advance(60_000), gymSet(), advance(7_140_000), closeInactive),
        "exactly 2 h without a set: still open" to listOf(gymSet(), advance(60_000), gymSet(), advance(twoHours), closeInactive),
        "2 h 1 min without a set: recorded (dated on its start, at its last set) and the progress reset" to listOf(
            gymSet(), advance(60_000), gymSet(), advance(twoHours + 60_000), closeInactive
        ),
        "a late set after 2 h + 1 ms records the old session first, then starts a new one" to listOf(
            gymSet(), advance(30_000), gymSet(), advance(twoHours + 1), gymSet(), advance(45_000), gymSet(), complete()
        ),
        "a manual reset discards the session without recording it" to listOf(gymSet(), advance(120_000), gymSet(), discard(), closeInactive, complete()),
        "the clock moved back: never closed, never negative" to listOf(
            gymSet(), advance(60_000), clock(-3 * 3_600_000), advance(3 * 3_600_000), closeInactive, gymSet(), complete()
        ),
        "two routines are independent" to listOf(
            gymSet(64), advance(10_000), gymSet(65), advance(twoHours - 5_000), gymSet(64), advance(10_000), closeInactive, complete(64)
        )
    )

    private fun runGym(events: List<Ev>): JsonElement {
        lateinit var steps: List<JsonElement>
        lateinit var finale: JsonElement
        runTest {
            var wallOffset = 0L
            val wall = WallClock { baseWall + testScheduler.currentTime + wallOffset }
            val store = MemoryGymStore()
            val recorded = mutableListOf<JsonElement>()
            val tracker = GymSessionTracker(
                store,
                { source, refId, title, duration, completedAt, dateOfMillis ->
                    recorded += obj(
                        "source" to str(source.name), "refId" to str(refId), "title" to str(title),
                        "durationSeconds" to num(duration), "completedAt" to num(completedAt), "dateOfMillis" to num(dateOfMillis)
                    )
                    recorded.size.toLong()
                },
                wall
            )
            val completions = mutableListOf<JsonElement>()
            val out = mutableListOf<JsonElement>()
            for (event in events) {
                when (event.type) {
                    "advance" -> advanceTimeBy(event.value!!)
                    "clock" -> wallOffset += event.value!!
                    "set" -> tracker.onSetCompleted(event.value!!.toInt())
                    "complete" -> completions += bool(tracker.completeSession(event.value!!.toInt(), "Routine ${event.value}") != null)
                    "discard" -> tracker.discardSession(event.value!!.toInt())
                    "closeInactive" -> tracker.closeInactiveSessions()
                }
                runCurrent()
                out += obj(
                    "event" to str(event.type),
                    "stored" to arr(store.sessions.values.map {
                        obj("routineId" to num(it.routineId), "startedAt" to num(it.sessionStartedAt), "lastSetAt" to num(it.lastSetAt))
                    })
                )
            }
            steps = out
            finale = obj(
                "recorded" to arr(recorded),
                "completed" to arr(completions),
                "progressCleared" to arr(store.cleared.map(::num)),
                "announcedSeconds" to arr(tracker.takeClosedSessions().map { num(it.durationSeconds) })
            )
        }
        return obj("events" to arr(events.map(::eventJson)), "steps" to arr(steps), "result" to finale)
    }

    // ------------------------------------------------------------------

    @Test
    fun `sessions golden`() {
        val rules = obj(
            "inactivityTimeoutMillis" to num(SESSION_INACTIVITY_TIMEOUT),
            "checkpointMillis" to num(ActiveWorkoutSession.CHECKPOINT_MILLIS),
            "isInactive" to arr(
                listOf(0L to 0L, 0L to twoHours, 0L to twoHours + 1, 5_000L to 0L, twoHours to 0L, 0L to Long.MAX_VALUE / 2)
                    .map { (last, now) -> obj("lastActivityAt" to num(last), "now" to num(now), "inactive" to bool(SessionRules.isInactive(last, now))) }
            ),
            "durationSeconds" to arr(
                listOf(0L to 0L, 0L to 999L, 0L to 1_000L, 0L to 59_999L, 10_000L to 0L, 0L to 7_200_000L)
                    .map { (s, e) -> obj("startedAt" to num(s), "endedAt" to num(e), "seconds" to num(SessionRules.durationSeconds(s, e))) }
            ),
            "latestRun" to arr(
                listOf(
                    emptyList(), listOf(5L), listOf(30L, 10L, 20L), listOf(0L, twoHours, twoHours + 1),
                    listOf(0L, twoHours + 1, twoHours + 2), listOf(10L, 10L + twoHours + 1, 20L + twoHours + 1)
                ).map { times ->
                    val run = SessionRules.latestRun(times)
                    obj("times" to arr(times.map(::num)), "run" to (run?.let { arr(listOf(num(it.first), num(it.second))) } ?: str(null)))
                }
            )
        )
        Golden.verify(
            "sessions",
            Golden.document(
                domain = "sessions",
                generatedBy = "app/src/test/java/com/javargasp/tiominutermvi/parity/SessionGoldenTest.kt",
                description = "SessionRules; active time (ActiveWorkoutSession + ActiveSessionTracker + ActiveTimeMeter, " +
                    "key daily:3) and set to set (ProgramStrengthSessions; GymSessionTracker with its progress reset and completion) as event sequences; wall clock starts at baseWall",
                extra = mapOf("rules" to rules, "baseWall" to num(baseWall)),
                cases = activeScenarios.map { (id, events) -> obj("id" to str(id), "kind" to str("active"), "run" to runActive(events)) } +
                    strengthScenarios.map { (id, events) -> obj("id" to str(id), "kind" to str("strength"), "run" to runStrength(events)) } +
                    gymScenarios.map { (id, events) -> obj("id" to str(id), "kind" to str("gym"), "run" to runGym(events)) }
            )
        )
    }
}
