package com.javargasp.tiominutermvi.domain.session

import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.testutil.ActiveSessionTestKit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Active time on screen (fake monotonic time) and how it is saved (fake wall clock). */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutSessionTest {

    private val kit = ActiveSessionTestKit()

    private fun TestScope.monotonic() = MonotonicTimeSource { testScheduler.currentTime }

    private fun TestScope.session() = ActiveWorkoutSession(
        backgroundScope, kit.tracker, ActiveTimeMeter(monotonic()), KEY
    )

    /** Moves both clocks: the monotonic (virtual) time and the wall clock. */
    private fun TestScope.pass(millis: Long) {
        kit.clock.now += millis
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `an explicit pause (timer stopped) is not counted`() = runTest {
        val meter = ActiveTimeMeter(monotonic())
        meter.setTimerRunning(true)
        advanceTimeBy(60_000)
        meter.setTimerRunning(false)
        advanceTimeBy(5 * 60_000)
        meter.setTimerRunning(true)
        advanceTimeBy(30_000)

        assertEquals(90, meter.seconds)
    }

    @Test
    fun `a running timer counts in the background and with the screen off`() = runTest {
        val meter = ActiveTimeMeter(monotonic())
        meter.setTimerRunning(true)
        advanceTimeBy(60_000)
        meter.setVisible(false)
        advanceTimeBy(10 * 60_000)
        meter.setVisible(true)
        advanceTimeBy(60_000)

        assertEquals(720, meter.seconds)
    }

    @Test
    fun `an untimed step (reps) counts only while visible, a stopped timer in the background not at all`() = runTest {
        val meter = ActiveTimeMeter(monotonic())
        meter.setActiveOnScreen(true)
        advanceTimeBy(60_000)
        meter.setVisible(false)
        advanceTimeBy(10 * 60_000)
        meter.setVisible(true)
        advanceTimeBy(30_000)
        // Waiting for input: neither a timer nor an untimed step
        meter.setActiveOnScreen(false)
        advanceTimeBy(5 * 60_000)

        assertEquals(90, meter.seconds)
    }

    @Test
    fun `nothing is stored before the first action, then the active time so far`() = runTest {
        val session = session()
        session.setTimerRunning(true)
        pass(20_000)
        assertNull(kit.store.session)

        session.start()

        assertEquals(20, kit.store.session?.activeSeconds)
    }

    @Test
    fun `the active time is saved every few seconds while counting, and on every change`() = runTest {
        val session = session()
        session.setTimerRunning(true)
        session.start()
        val savesAfterStart = kit.store.saves.size

        pass(ActiveWorkoutSession.CHECKPOINT_MILLIS * 3)
        assertEquals(savesAfterStart + 3, kit.store.saves.size)
        assertEquals(15, kit.store.session?.activeSeconds)

        session.setVisible(false) // to the background: the timer keeps running, so do the saves
        pass(ActiveWorkoutSession.CHECKPOINT_MILLIS * 2)
        assertEquals(25, kit.store.session?.activeSeconds)

        session.setTimerRunning(false) // paused: saved at once, then no more saves
        runCurrent()
        val savesWhenPaused = kit.store.saves.size
        pass(10 * 60_000)

        assertEquals(savesWhenPaused, kit.store.saves.size)
        assertEquals(25, kit.store.session?.activeSeconds)
    }

    @Test
    fun `a process death keeps the time saved last, recorded after 2 hours`() = runTest {
        val session = session()
        session.setTimerRunning(true)
        session.start()
        pass(12 * 60_000)
        // The process dies: nothing else runs; a later check closes the stored session
        session.close()

        kit.clock.now += SESSION_INACTIVITY_TIMEOUT + 1
        kit.tracker.closeInactive()

        assertEquals(listOf(12 * 60), kit.store.abandoned.map { it.activeSeconds })
    }

    @Test
    fun `back after more than 2 hours stopped - the old session is recorded and counting restarts`() = runTest {
        val session = session()
        session.setTimerRunning(true)
        session.start()
        pass(10 * 60_000)
        session.setTimerRunning(false)
        runCurrent()
        pass(3 * 60 * 60_000)

        session.setTimerRunning(true)
        runCurrent()
        pass(60_000)

        assertEquals(listOf(10 * 60), kit.store.abandoned.map { it.activeSeconds })
        assertEquals(60, session.activeSeconds)
        session.setTimerRunning(false)
    }

    @Test
    fun `finishing ends the session and returns the active time, once`() = runTest {
        val session = session()
        session.setTimerRunning(true)
        session.start()
        pass(45_000)

        assertEquals(45, session.finish())
        assertNull(kit.store.session)

        // Nothing is saved or closed afterwards
        session.onUserAction()
        runCurrent()
        kit.clock.now += 5 * 60 * 60_000L
        kit.tracker.closeInactive()
        assertNull(kit.store.session)
        assertTrue(kit.store.abandoned.isEmpty())
    }

    @Test
    fun `starting records an older unfinished session and returns it to announce`() = runTest {
        kit.clock.now = 1_000L
        kit.tracker.start("daily:9")
        kit.tracker.update("daily:9", 420)

        val closed = session().start()

        assertEquals(listOf(ClosedSession(420)), closed)
        assertEquals(KEY, kit.store.session?.key)
    }

    private companion object {
        const val KEY = "daily:4"
    }
}
