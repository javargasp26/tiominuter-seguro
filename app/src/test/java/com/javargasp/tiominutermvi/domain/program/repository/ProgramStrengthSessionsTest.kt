package com.javargasp.tiominutermvi.domain.program.repository

import com.javargasp.tiominutermvi.domain.goal.countsTowardGoal
import com.javargasp.tiominutermvi.domain.session.ClosedSession
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.testutil.FakeWallClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Program strength sessions: the gym's cases with a fake clock (the workout stays resumable). */
class ProgramStrengthSessionsTest {

    private val clock = FakeWallClock()
    private val store = InMemoryStrengthSessionStore()
    private val sessions = ProgramStrengthSessions(store, clock)
    private val t0 = 1_760_990_400_000L

    private fun at(minutes: Int) = t0 + minutes * MINUTE

    private suspend fun setAt(minutes: Int, dayIndex: Int = 1) {
        clock.now = at(minutes)
        sessions.onSetCompleted(PROGRAM, WEEK, dayIndex)
    }

    private suspend fun checkAt(minutes: Int) {
        clock.now = at(minutes)
        sessions.closeInactive()
    }

    @Test
    fun `abandoned after sets at 0, 10 and 40 min - one entry of 40 min dated on the start`() = runTest {
        setAt(0)
        setAt(10)
        setAt(40)

        checkAt(2 * 60 + 41)

        assertEquals(listOf(StrengthSession(PROGRAM, WEEK, 1, at(0), at(40))), store.recorded)
        assertEquals(40 * 60, store.recorded.single().durationSeconds)
        assertNull(store.sessions[Triple(PROGRAM, WEEK, 1)])
        assertEquals(listOf(ClosedSession(40 * 60)), sessions.takeClosedSessions())
    }

    @Test
    fun `a check at 2 h 39 min closes nothing`() = runTest {
        setAt(0)
        setAt(40)

        checkAt(2 * 60 + 39)

        assertTrue(store.recorded.isEmpty())
        assertEquals(StrengthSession(PROGRAM, WEEK, 1, at(0), at(40)), store.sessions[Triple(PROGRAM, WEEK, 1)])
    }

    @Test
    fun `a long continuous session of 2 h 30 is one entry`() = runTest {
        for (minute in 0..140 step 20) setAt(minute)
        setAt(150)

        checkAt(150 + 121)

        assertEquals(listOf(150 * 60), store.recorded.map { it.durationSeconds })
    }

    @Test
    fun `a set 3 h after the last one closes the old session and starts a new one`() = runTest {
        setAt(0)
        setAt(25)

        setAt(25 + 180)

        assertEquals(listOf(25 * 60), store.recorded.map { it.durationSeconds })
        assertEquals(StrengthSession(PROGRAM, WEEK, 1, at(205), at(205)), store.sessions[Triple(PROGRAM, WEEK, 1)])
    }

    @Test
    fun `a single set then abandoned records 0 s, which doesn't count for the goal`() = runTest {
        setAt(0)

        checkAt(3 * 60)

        assertEquals(0, store.recorded.single().durationSeconds)
        assertFalse(countsTowardGoal(entry(0)))
    }

    @Test
    fun `the clock moved back - nothing closes and the duration can't go negative`() = runTest {
        setAt(60)

        checkAt(-5 * 60)
        assertTrue(store.recorded.isEmpty())

        setAt(-5 * 60)
        val session = store.sessions.getValue(Triple(PROGRAM, WEEK, 1))
        assertEquals(StrengthSession(PROGRAM, WEEK, 1, at(-5 * 60), at(-5 * 60)), session)
        assertEquals(0, StrengthSession(PROGRAM, WEEK, 1, startedAt = at(10), lastActivityAt = at(0)).durationSeconds)
    }

    @Test
    fun `a double or concurrent check records one entry`() = runTest {
        setAt(0)
        setAt(30)
        store.yieldInTransaction = true
        clock.now = at(4 * 60)

        List(5) { async { sessions.closeInactive() } }.awaitAll()
        sessions.closeInactive()

        assertEquals(1, store.recorded.size)
    }

    @Test
    fun `each workout has its own session`() = runTest {
        setAt(0, dayIndex = 0)
        setAt(100, dayIndex = 1)

        checkAt(2 * 60 + 30)

        assertEquals(listOf(0), store.recorded.map { it.dayIndex })
        assertEquals(setOf(Triple(PROGRAM, WEEK, 1)), store.sessions.keys)
    }

    @Test
    fun `a recording failure rolls the close back and the next check retries`() = runTest {
        setAt(0)
        setAt(45)
        store.recordError = IllegalStateException("disk full")

        checkAt(4 * 60)
        assertEquals(StrengthSession(PROGRAM, WEEK, 1, at(0), at(45)), store.sessions[Triple(PROGRAM, WEEK, 1)])

        store.recordError = null
        checkAt(4 * 60)
        assertEquals(1, store.recorded.size)
    }

    private fun entry(durationSeconds: Int) = TrainingLogEntry(
        id = 1, uuid = "u", dateLocal = "2026-10-20", completedAt = t0, source = TrainingSource.PROGRAM,
        refId = "program:7:2:1", title = "Full body A", durationSeconds = durationSeconds, createdAt = t0, updatedAt = t0
    )

    /** In-memory store with Room-like rollback; [yieldInTransaction] interleaves concurrent callers. */
    private class InMemoryStrengthSessionStore : StrengthSessionStore {
        val sessions = mutableMapOf<Triple<Long, Int, Int>, StrengthSession>()
        val recorded = mutableListOf<StrengthSession>()
        var yieldInTransaction = false
        var recordError: Exception? = null

        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            val sessionsBefore = sessions.toMap()
            val recordedBefore = recorded.toList()
            return try {
                if (yieldInTransaction) yield()
                block()
            } catch (e: Throwable) {
                sessions.clear()
                sessions.putAll(sessionsBefore)
                recorded.clear()
                recorded.addAll(recordedBefore)
                throw e
            }
        }

        override suspend fun get(programId: Long, week: Int, dayIndex: Int) = sessions[Triple(programId, week, dayIndex)]
        override suspend fun getAll() = sessions.values.toList()
        override suspend fun save(session: StrengthSession) {
            sessions[Triple(session.programId, session.week, session.dayIndex)] = session
        }
        override suspend fun delete(programId: Long, week: Int, dayIndex: Int) {
            sessions.remove(Triple(programId, week, dayIndex))
        }
        override suspend fun recordAbandoned(session: StrengthSession) {
            recordError?.let { throw it }
            recorded += session
        }
    }

    private companion object {
        const val PROGRAM = 7L
        const val WEEK = 2
        const val MINUTE = 60_000L
    }
}
