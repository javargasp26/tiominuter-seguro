package com.javargasp.tiominutermvi.domain.gymsession

import com.javargasp.tiominutermvi.domain.goal.countsTowardGoal
import com.javargasp.tiominutermvi.domain.session.SESSION_INACTIVITY_TIMEOUT
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.testutil.GymSessionTestKit
import com.javargasp.tiominutermvi.testutil.RecordingSessionEntryRecorder.Entry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gym session with a fake clock: sets, the 2-hour inactivity close and normal completion. */
class GymSessionTrackerTest {

    private val kit = GymSessionTestKit(titles = mapOf(64 to "Pull Day"))
    private val tracker = kit.tracker
    private val routine = 64

    private val t0 = 1_760_990_400_000L // any wall-clock origin

    private fun at(minutes: Int) = t0 + minutes * MINUTE

    private suspend fun setAt(minutes: Int) {
        kit.clock.now = at(minutes)
        tracker.onSetCompleted(routine)
    }

    private suspend fun checkAt(minutes: Int): List<ClosedGymSession> {
        kit.clock.now = at(minutes)
        return tracker.closeInactiveSessions()
    }

    @Test
    fun `sets at 0, 10 and 40 min then abandoned - the check at 2 h 41 min records 40 min and resets`() = runTest {
        setAt(0)
        setAt(10)
        setAt(40)

        val closed = checkAt(2 * 60 + 41)

        assertEquals(listOf(ClosedGymSession(routine, 40 * 60)), closed)
        assertEquals(
            listOf(Entry(TrainingSource.GYM, "gym:64", "Pull Day", 40 * 60, completedAt = at(40), dateOfMillis = at(0))),
            kit.recorder.entries
        )
        assertEquals(listOf(routine), kit.store.clearedProgress)
        assertNull(kit.store.sessions[routine])
    }

    @Test
    fun `a check at 2 h 39 min closes nothing`() = runTest {
        setAt(0)
        setAt(10)
        setAt(40)

        assertTrue(checkAt(2 * 60 + 39).isEmpty())
        assertTrue(kit.recorder.entries.isEmpty())
        assertTrue(kit.store.clearedProgress.isEmpty())
        assertEquals(GymSession(routine, at(0), at(40)), kit.store.sessions[routine])
    }

    @Test
    fun `exactly 2 hours after the last set is not more than the timeout`() = runTest {
        setAt(0)
        assertTrue(checkAt(120).isEmpty())
        assertEquals(1, checkAt(121).size)
    }

    @Test
    fun `a continuous session of 2 h 30 with sets every 20 min records 2 h 30`() = runTest {
        for (minute in 0..140 step 20) setAt(minute)
        setAt(150)
        assertTrue(checkAt(150 + 119).isEmpty())

        checkAt(150 + 121)

        assertEquals(listOf(150 * 60), kit.recorder.entries.map { it.durationSeconds })
    }

    @Test
    fun `completing the routine records first to last set and ends the session`() = runTest {
        for (minute in 0..140 step 20) setAt(minute)
        setAt(150)
        kit.clock.now = at(151)

        val entryId = tracker.completeSession(routine, "Pull Day")

        assertEquals(1L, entryId)
        assertEquals(
            listOf(Entry(TrainingSource.GYM, "gym:64", "Pull Day", 150 * 60, completedAt = at(151), dateOfMillis = at(151))),
            kit.recorder.entries
        )
        // The progress stays for the completion dialog; nothing left to close later
        assertTrue(kit.store.clearedProgress.isEmpty())
        assertTrue(checkAt(24 * 60).isEmpty())
        assertEquals(1, kit.recorder.entries.size)
    }

    @Test
    fun `completing a routine without a session records nothing (already recorded)`() = runTest {
        setAt(0)
        tracker.completeSession(routine, "Pull Day")

        assertNull(tracker.completeSession(routine, "Pull Day"))
        assertEquals(1, kit.recorder.entries.size)
    }

    @Test
    fun `a set 3 h after the last one closes the old session and starts a new one`() = runTest {
        setAt(0)
        setAt(25)

        setAt(25 + 180)

        assertEquals(listOf(25 * 60), kit.recorder.entries.map { it.durationSeconds })
        assertEquals(listOf(routine), kit.store.clearedProgress)
        assertEquals(GymSession(routine, at(205), at(205)), kit.store.sessions[routine])
        assertEquals(listOf(ClosedGymSession(routine, 25 * 60)), tracker.takeClosedSessions())
    }

    @Test
    fun `a single set then abandoned records 0 s, which doesn't count for the goal`() = runTest {
        setAt(0)

        checkAt(3 * 60)

        val entry = kit.recorder.entries.single()
        assertEquals(0, entry.durationSeconds)
        assertFalse(countsTowardGoal(logEntry(entry.durationSeconds)))
    }

    @Test
    fun `the clock moved back - nothing closes and the duration can't go negative`() = runTest {
        setAt(60)

        // Hours before the last set: never inactive
        assertTrue(checkAt(-5 * 60).isEmpty())
        assertTrue(kit.recorder.entries.isEmpty())

        // A set at that earlier time keeps the session open with a non-negative duration
        setAt(-5 * 60)
        val session = kit.store.sessions.getValue(routine)
        assertEquals(GymSession(routine, at(-5 * 60), at(-5 * 60)), session)
        assertEquals(0, session.durationSeconds)
        assertEquals(0, GymSession(routine, sessionStartedAt = at(10), lastSetAt = at(0)).durationSeconds)
    }

    @Test
    fun `checking twice records one entry`() = runTest {
        setAt(0)
        setAt(30)

        checkAt(4 * 60)
        checkAt(4 * 60)

        assertEquals(1, kit.recorder.entries.size)
    }

    @Test
    fun `concurrent checks record one entry`() = runTest {
        setAt(0)
        setAt(30)
        kit.store.yieldInTransaction = true
        kit.clock.now = at(4 * 60)

        val results = List(5) { async { tracker.closeInactiveSessions() } }.awaitAll()

        assertEquals(1, kit.recorder.entries.size)
        assertEquals(1, results.sumOf { it.size })
    }

    @Test
    fun `a check concurrent with a late set records the old session once`() = runTest {
        setAt(0)
        setAt(30)
        kit.store.yieldInTransaction = true
        kit.clock.now = at(4 * 60)

        listOf(async { tracker.closeInactiveSessions() }, async { tracker.onSetCompleted(routine) }).awaitAll()

        assertEquals(1, kit.recorder.entries.size)
        assertEquals(GymSession(routine, at(240), at(240)), kit.store.sessions[routine])
    }

    @Test
    fun `closed sessions are announced once`() = runTest {
        setAt(0)
        checkAt(3 * 60)

        assertEquals(1, tracker.takeClosedSessions().size)
        assertTrue(tracker.takeClosedSessions().isEmpty())
    }

    @Test
    fun `every routine's session closes on its own`() = runTest {
        setAt(0)
        kit.clock.now = at(100)
        tracker.onSetCompleted(65)

        assertEquals(listOf(routine), checkAt(2 * 60 + 30).map { it.routineId })
        assertEquals(listOf(65), checkAt(100 + 121).map { it.routineId })
    }

    @Test
    fun `a discarded session (reset by hand) is never recorded`() = runTest {
        setAt(0)
        setAt(20)

        tracker.discardSession(routine)

        assertTrue(checkAt(5 * 60).isEmpty())
        assertTrue(kit.recorder.entries.isEmpty())
    }

    @Test
    fun `a recording failure rolls the close back and keeps the session for the next check`() = runTest {
        setAt(0)
        setAt(45)
        kit.recorder.error = IllegalStateException("disk full")

        assertTrue(checkAt(4 * 60).isEmpty())
        assertEquals(GymSession(routine, at(0), at(45)), kit.store.sessions[routine])
        assertTrue(kit.store.clearedProgress.isEmpty())
        assertTrue(tracker.takeClosedSessions().isEmpty())

        kit.recorder.error = null
        assertEquals(listOf(ClosedGymSession(routine, 45 * 60)), checkAt(4 * 60))
    }

    @Test
    fun `a failure while noting a set doesn't throw`() = runTest {
        setAt(0)
        kit.recorder.error = IllegalStateException("disk full")

        setAt(4 * 60)

        assertEquals(GymSession(routine, at(0), at(0)), kit.store.sessions[routine])
    }

    // --- Rules ---

    @Test
    fun `sessions from old progress use the latest run of completions`() {
        assertNull(GymSessionRules.fromCompletionTimes(routine, emptyList()))
        assertEquals(
            GymSession(routine, at(0), at(0)),
            GymSessionRules.fromCompletionTimes(routine, listOf(at(0)))
        )
        // In any order; gaps of up to 2 hours stay in the same session
        assertEquals(
            GymSession(routine, at(0), at(240)),
            GymSessionRules.fromCompletionTimes(routine, listOf(at(240), at(0), at(120)))
        )
        // An exercise done days earlier is not part of it
        assertEquals(
            GymSession(routine, at(3 * 24 * 60), at(3 * 24 * 60 + 50)),
            GymSessionRules.fromCompletionTimes(routine, listOf(at(0), at(3 * 24 * 60 + 50), at(3 * 24 * 60)))
        )
    }

    @Test
    fun `the timeout is 2 hours`() {
        assertEquals(2 * 60 * MINUTE, SESSION_INACTIVITY_TIMEOUT)
        assertFalse(GymSessionRules.isInactive(lastSetAt = at(0), now = at(120)))
        assertTrue(GymSessionRules.isInactive(lastSetAt = at(0), now = at(120) + 1))
        assertFalse(GymSessionRules.isInactive(lastSetAt = at(500), now = at(0)))
    }

    private fun logEntry(durationSeconds: Int) = TrainingLogEntry(
        id = 1, uuid = "u", dateLocal = "2026-10-20", completedAt = t0, source = TrainingSource.GYM,
        refId = "gym:64", title = "Pull Day", durationSeconds = durationSeconds, createdAt = t0, updatedAt = t0
    )

    private companion object {
        const val MINUTE = 60_000L
    }
}
