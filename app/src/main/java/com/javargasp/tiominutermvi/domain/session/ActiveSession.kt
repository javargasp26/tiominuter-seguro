package com.javargasp.tiominutermvi.domain.session

import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.domain.time.WallClock

/**
 * A workout measured in active time (daily, program cardio): [key] is its training-log refId
 * ("daily:3", "program:12:2:1"), [startedAt] and [lastActivityAt] are wall-clock millis, and
 * [activeSeconds] is the active time saved so far. At most one per kind.
 */
data class ActiveSession(
    val key: String,
    val startedAt: Long,
    val activeSeconds: Int,
    val lastActivityAt: Long
)

/** Storage of one kind's active session and how an abandoned one is recorded. */
interface ActiveSessionStore : SessionTransactions {
    suspend fun get(): ActiveSession?
    suspend fun save(session: ActiveSession)
    suspend fun delete()

    /** One entry with the session's active time, completed at its last activity, dated on its start. */
    suspend fun recordAbandoned(session: ActiveSession)
}

/**
 * The persisted active session of one kind. "Activity" is the workout running or any user
 * action in it: the ViewModel saves the active time every few seconds while running and on
 * every pause, leave or action ([update]). After [SESSION_INACTIVITY_TIMEOUT] without activity
 * the session is closed lazily ([closeInactive], or the next [update]) with the active time
 * saved so far. No background work.
 */
class ActiveSessionTracker(
    private val store: ActiveSessionStore,
    private val clock: WallClock,
    name: String
) : InactiveSessionCloser {

    private val lock = SessionLock(name)
    private val notices = ClosedSessionNotices<ClosedSession>()

    /**
     * A workout of this kind starts: an open session (of any workout of this kind) is recorded
     * first, then a new one starts now with no active time. False when it could not be stored.
     */
    suspend fun start(key: String): Boolean = lock.run(store, "start $key", default = false) {
        store.get()?.let { close(it) }
        val now = clock.nowMillis()
        store.save(ActiveSession(key, startedAt = now, activeSeconds = 0, lastActivityAt = now))
        true
    }

    /**
     * Saves [activeSeconds] of [key] as of now. Returns false when that session no longer goes
     * on: it was closed (recorded) meanwhile, belongs to another workout, or was inactive for
     * too long (it is recorded now). Then a new session starts now with no active time, and the
     * caller restarts its count from 0.
     */
    suspend fun update(key: String, activeSeconds: Int): Boolean = lock.run(store, "update $key", default = true) {
        val now = clock.nowMillis()
        val current = store.get()
        if (current != null && current.key == key && !SessionRules.isInactive(current.lastActivityAt, now)) {
            store.save(current.copy(activeSeconds = activeSeconds.coerceAtLeast(0), lastActivityAt = now))
            true
        } else {
            current?.let { close(it) }
            store.save(ActiveSession(key, startedAt = now, activeSeconds = 0, lastActivityAt = now))
            false
        }
    }

    /**
     * The workout finished normally: its session ends without being recorded here (the caller
     * records the finished workout once, with its own final active time).
     */
    suspend fun finish(key: String) {
        lock.run(store, "finish $key", default = Unit) {
            if (store.get()?.key == key) store.delete()
        }
    }

    /** Records the session if it has been inactive for more than the timeout. */
    override suspend fun closeInactive() {
        lock.run(store, "close an inactive session", default = Unit) {
            val current = store.get() ?: return@run
            if (SessionRules.isInactive(current.lastActivityAt, clock.nowMillis())) close(current)
        }
    }

    /** Sessions closed for inactivity (or replaced) not announced yet; each one only once. */
    fun takeClosedSessions(): List<ClosedSession> = notices.take()

    /**
     * Removes the session, then records it: when the entry lives in another database (program
     * cardio), a failed entry still rolls the removal back, and the lock keeps a second close out.
     */
    private suspend fun close(session: ActiveSession) {
        store.delete()
        store.recordAbandoned(session)
        notices.add(ClosedSession(session.activeSeconds))
    }
}

/**
 * Active time of a workout, measured with [time] (monotonic), never the wall clock. It counts:
 * - while the workout's **timer is running** ([setTimerRunning]: a countdown, a stopwatch), in
 *   the foreground or the background, screen on or off, as the timer itself keeps going;
 * - while an **untimed exercise step** is shown and the screen is visible
 *   ([setActiveOnScreen] + [setVisible]: a REPS exercise, done before tapping "Done").
 * Not counted: explicit pauses, steps waiting for the person with no timer (a TIME exercise
 * before "Get Ready"), and an untimed step while the screen is not visible.
 */
class ActiveTimeMeter(private val time: MonotonicTimeSource) {
    private var accumulatedMillis = 0L
    private var countingSince: Long? = null
    private var timerRunning = false
    private var activeOnScreen = false
    private var visible = true

    val isCounting: Boolean get() = countingSince != null

    val seconds: Int
        get() = ((accumulatedMillis + (countingSince?.let { time.nowMillis() - it } ?: 0L)) / 1000L).toInt()

    fun setTimerRunning(running: Boolean) {
        timerRunning = running
        refresh()
    }

    fun setActiveOnScreen(active: Boolean) {
        activeOnScreen = active
        refresh()
    }

    fun setVisible(visible: Boolean) {
        this.visible = visible
        refresh()
    }

    /** Back to 0 (a new session started); keeps counting if it was. */
    fun reset() {
        accumulatedMillis = 0L
        if (countingSince != null) countingSince = time.nowMillis()
    }

    private fun refresh() {
        val shouldCount = timerRunning || (activeOnScreen && visible)
        val since = countingSince
        if (shouldCount && since == null) {
            countingSince = time.nowMillis()
        } else if (!shouldCount && since != null) {
            accumulatedMillis += time.nowMillis() - since
            countingSince = null
        }
    }
}
