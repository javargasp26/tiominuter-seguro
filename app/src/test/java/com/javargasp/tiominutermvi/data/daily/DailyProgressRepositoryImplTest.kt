package com.javargasp.tiominutermvi.data.daily

import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.sync.ChangeTracker
import com.javargasp.tiominutermvi.domain.sync.DocKey
import com.javargasp.tiominutermvi.domain.sync.SyncCollection
import com.javargasp.tiominutermvi.testutil.FakeDailyProgressDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyProgressRepositoryImplTest {

    private val dao = FakeDailyProgressDao()
    private val tracker = RecordingChangeTracker()
    private var now = 1_000L
    private val repository = DailyProgressRepositoryImpl(dao, { now }, tracker)
    private val key = DocKey(SyncCollection.DAILY_PROGRESS, "state")

    @Test
    fun `a new person starts at Day 1, stored and enqueued once`() = runTest {
        assertEquals(DailyProgress(), repository.observeProgress().first())
        assertEquals(DailyProgress(), repository.observeProgress().first())

        assertEquals(DailyProgressEntity(lastCompletedDay = 0, cycle = 0, updatedAt = 1_000L), dao.row.value)
        assertEquals(listOf(key), tracker.enqueued)
        assertEquals("enqueued in the row's transaction", 1, tracker.enqueuedInTransaction)
    }

    @Test
    fun `an existing person starts from the highest day of their history, once`() = runTest {
        dao.historyRefIds = listOf("daily:3", "daily:11", null, "daily:x")

        assertEquals(DailyProgress(lastCompletedDay = 11), repository.observeProgress().first())

        // Later history (a session closed for inactivity, a repeat) never moves it again
        dao.historyRefIds = listOf("daily:25")
        assertEquals(DailyProgress(lastCompletedDay = 11), repository.observeProgress().first())
        assertEquals(1, tracker.enqueued.size)
    }

    @Test
    fun `finishing the next day unlocks the following one, a repeat writes nothing`() = runTest {
        repository.onDayFinished(1)
        now = 2_000L
        repository.onDayFinished(2)
        val enqueuedBefore = tracker.enqueued.size

        repository.onDayFinished(1)
        repository.onDayFinished(9)

        assertEquals(DailyProgressEntity(lastCompletedDay = 2, cycle = 0, updatedAt = 2_000L), dao.row.value)
        assertEquals(enqueuedBefore, tracker.enqueued.size)
    }

    @Test
    fun `restart starts a new cycle at Day 1 and is synced`() = runTest {
        dao.row.value = DailyProgressEntity(lastCompletedDay = 30, cycle = 0, updatedAt = 1L)

        repository.restart()

        assertEquals(DailyProgress(lastCompletedDay = 0, cycle = 1), repository.observeProgress().first())
        assertEquals(listOf(key), tracker.enqueued)
    }

    @Test
    fun `after a local wipe the next read starts again from the (empty) history`() = runTest {
        dao.row.value = DailyProgressEntity(lastCompletedDay = 8, cycle = 2, updatedAt = 1L)
        dao.deleteAll()

        assertEquals(DailyProgress(), repository.observeProgress().first())
        assertTrue(dao.row.value != null)
    }

    /** Records what is enqueued and checks it happens inside a transaction. */
    private class RecordingChangeTracker : ChangeTracker {
        val enqueued = mutableListOf<DocKey>()
        var transactions = 0
        var enqueuedInTransaction = 0
        private var depth = 0

        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            depth++
            transactions++
            try {
                return block()
            } finally {
                depth--
            }
        }

        override suspend fun enqueue(key: DocKey, delete: Boolean) {
            enqueued += key
            if (depth > 0) enqueuedInTransaction++
        }
    }
}
