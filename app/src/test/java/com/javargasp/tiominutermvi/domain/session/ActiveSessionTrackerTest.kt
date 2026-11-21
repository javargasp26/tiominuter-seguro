package com.javargasp.tiominutermvi.domain.session

import com.javargasp.tiominutermvi.testutil.ActiveSessionTestKit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The persisted active-time session (daily, program cardio) with a hand-moved wall clock. */
class ActiveSessionTrackerTest {

    private val kit = ActiveSessionTestKit()
    private val tracker = kit.tracker
    private val t0 = 1_760_990_400_000L

    private fun at(minutes: Int) = t0 + minutes * MINUTE

    private suspend fun startAt(minutes: Int, key: String = KEY) {
        kit.clock.now = at(minutes)
        tracker.start(key)
    }

    private suspend fun updateAt(minutes: Int, activeSeconds: Int, key: String = KEY): Boolean {
        kit.clock.now = at(minutes)
        return tracker.update(key, activeSeconds)
    }

    private suspend fun checkAt(minutes: Int) {
        kit.clock.now = at(minutes)
        tracker.closeInactive()
    }

    @Test
    fun `the active time is saved with the last activity`() = runTest {
        startAt(0)
        assertTrue(updateAt(10, activeSeconds = 540))

        assertEquals(ActiveSession(KEY, startedAt = at(0), activeSeconds = 540, lastActivityAt = at(10)), kit.store.session)
    }

    @Test
    fun `left for 2 hours, it is recorded with the active time saved so far, dated on its start`() = runTest {
        startAt(0)
        updateAt(25, activeSeconds = 1_200)

        checkAt(25 + 119)
        assertTrue(kit.store.abandoned.isEmpty())
        checkAt(25 + 121)

        assertEquals(listOf(ActiveSession(KEY, at(0), 1_200, at(25))), kit.store.abandoned)
        assertNull(kit.store.session)
        assertEquals(listOf(ClosedSession(1_200)), tracker.takeClosedSessions())
        assertTrue(tracker.takeClosedSessions().isEmpty())
    }

    @Test
    fun `after a process death the stored session is recovered and closed with its active time`() = runTest {
        startAt(0)
        updateAt(30, activeSeconds = 1_500)

        // A new process: a new tracker over the same storage
        val restarted = ActiveSessionTracker(kit.store, kit.clock, "Restarted")
        kit.clock.now = at(30 + 121)
        restarted.closeInactive()

        assertEquals(listOf(1_500), kit.store.abandoned.map { it.activeSeconds })
        assertEquals(at(0), kit.store.abandoned.single().startedAt)
    }

    @Test
    fun `starting a new session records the open one first`() = runTest {
        startAt(0, key = "daily:1")
        updateAt(5, activeSeconds = 300, key = "daily:1")

        startAt(20, key = "daily:2")

        assertEquals(listOf(ActiveSession("daily:1", at(0), 300, at(5))), kit.store.abandoned)
        assertEquals(ActiveSession("daily:2", at(20), 0, at(20)), kit.store.session)
        assertEquals(listOf(ClosedSession(300)), tracker.takeClosedSessions())
    }

    @Test
    fun `a normal finish ends the session without recording it, so it never closes later`() = runTest {
        startAt(0)
        updateAt(30, activeSeconds = 1_800)

        tracker.finish(KEY)
        checkAt(5 * 60)

        assertNull(kit.store.session)
        assertTrue(kit.store.abandoned.isEmpty())
    }

    @Test
    fun `an update after 2 hours of inactivity records the old session and starts a new one`() = runTest {
        startAt(0)
        updateAt(10, activeSeconds = 600)

        assertFalse(updateAt(10 + 180, activeSeconds = 650))

        assertEquals(listOf(600), kit.store.abandoned.map { it.activeSeconds })
        assertEquals(ActiveSession(KEY, at(190), 0, at(190)), kit.store.session)
    }

    @Test
    fun `an update of a session closed meanwhile starts a new one instead of reviving it`() = runTest {
        startAt(0)
        updateAt(10, activeSeconds = 600)
        checkAt(10 + 121)

        assertFalse(updateAt(10 + 122, activeSeconds = 610))

        assertEquals(1, kit.store.abandoned.size)
        assertEquals(0, kit.store.session?.activeSeconds)
    }

    @Test
    fun `the clock moved back - nothing closes`() = runTest {
        startAt(60)
        updateAt(70, activeSeconds = 600)

        checkAt(-5 * 60)
        assertTrue(updateAt(-5 * 60, activeSeconds = 620))

        assertTrue(kit.store.abandoned.isEmpty())
        assertEquals(620, kit.store.session?.activeSeconds)
    }

    @Test
    fun `checking twice, or concurrently, records once`() = runTest {
        startAt(0)
        updateAt(10, activeSeconds = 600)
        kit.store.yieldInTransaction = true
        kit.clock.now = at(4 * 60)

        List(5) { async { tracker.closeInactive() } }.awaitAll()
        tracker.closeInactive()

        assertEquals(1, kit.store.abandoned.size)
    }

    @Test
    fun `a recording failure keeps the session for the next check`() = runTest {
        startAt(0)
        updateAt(10, activeSeconds = 600)
        kit.store.recordError = IllegalStateException("disk full")

        checkAt(4 * 60)
        assertEquals(600, kit.store.session?.activeSeconds)
        assertTrue(tracker.takeClosedSessions().isEmpty())

        kit.store.recordError = null
        checkAt(4 * 60)
        assertEquals(1, kit.store.abandoned.size)
    }

    @Test
    fun `negative active time is stored as 0`() = runTest {
        startAt(0)
        updateAt(1, activeSeconds = -5)

        assertEquals(0, kit.store.session?.activeSeconds)
    }

    // --- Shared rules ---

    @Test
    fun `the shared rules - timeout, clamp, latest run`() {
        assertEquals(2 * 60 * MINUTE, SESSION_INACTIVITY_TIMEOUT)
        assertFalse(SessionRules.isInactive(lastActivityAt = at(0), now = at(120)))
        assertTrue(SessionRules.isInactive(lastActivityAt = at(0), now = at(120) + 1))
        assertFalse(SessionRules.isInactive(lastActivityAt = at(500), now = at(0)))
        assertEquals(40 * 60, SessionRules.durationSeconds(at(0), at(40)))
        assertEquals(0, SessionRules.durationSeconds(at(40), at(0)))
        assertNull(SessionRules.latestRun(emptyList()))
        assertEquals(at(240) to at(250), SessionRules.latestRun(listOf(at(0), at(250), at(240))))
        assertEquals(at(0) to at(240), SessionRules.latestRun(listOf(at(240), at(0), at(120))))
    }

    private companion object {
        const val KEY = "daily:4"
        const val MINUTE = 60_000L
    }
}
