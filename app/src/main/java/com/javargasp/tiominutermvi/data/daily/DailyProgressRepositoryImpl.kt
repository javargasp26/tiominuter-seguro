package com.javargasp.tiominutermvi.data.daily

import com.javargasp.tiominutermvi.data.local.dao.DailyProgressDao
import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.daily.DailyProgressRepository
import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules
import com.javargasp.tiominutermvi.domain.sync.ChangeTracker
import com.javargasp.tiominutermvi.domain.sync.DocKey
import com.javargasp.tiominutermvi.domain.sync.SyncCollection
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Room-backed daily progress (one row). The row is created on the first read from the history
 * ([DailyUnlockRules.fromHistory]: the highest day among the DAILY entries), once: later reads find
 * it. A local wipe removes it, and the next read creates it again from the (now empty) history.
 * Every write enqueues the document in the same transaction, as every synced write.
 */
class DailyProgressRepositoryImpl(
    private val dao: DailyProgressDao,
    private val clock: WallClock,
    private val changes: ChangeTracker = ChangeTracker.None
) : DailyProgressRepository {

    private val mutex = Mutex()

    override fun observeProgress(): Flow<DailyProgress> =
        dao.observe()
            .onStart { ensureInitialized() }
            // Gone (a local wipe while shown): created again from the history
            .onEach { if (it == null) ensureInitialized() }
            .filterNotNull()
            .map { DailyProgress(it.lastCompletedDay, it.cycle) }

    override suspend fun onDayFinished(routineId: Int) = update { DailyUnlockRules.afterFinishing(it, routineId) }

    override suspend fun restart() = update(DailyUnlockRules::restart)

    /** Writes [transform] of the current state when it changes it; never lost to a cleared screen. */
    private suspend fun update(transform: (DailyProgress) -> DailyProgress) = withContext(NonCancellable) {
        mutex.withLock {
            changes.inTransaction {
                val current = currentOrInitial()
                val next = transform(current)
                if (next != current) write(next)
            }
        }
    }

    private suspend fun ensureInitialized() {
        mutex.withLock {
            changes.inTransaction {
                if (dao.get() == null) write(initial())
            }
        }
    }

    private suspend fun currentOrInitial(): DailyProgress {
        val row = dao.get() ?: return initial().also { write(it) }
        return DailyProgress(row.lastCompletedDay, row.cycle)
    }

    private suspend fun initial(): DailyProgress =
        DailyUnlockRules.fromHistory(dao.dailyRefIds().map { DailyUnlockRules.HistoryEntry(TrainingSource.DAILY, it) })

    private suspend fun write(progress: DailyProgress) {
        dao.upsert(DailyProgressEntity(lastCompletedDay = progress.lastCompletedDay, cycle = progress.cycle, updatedAt = clock.nowMillis()))
        changes.enqueue(DAILY_PROGRESS_KEY)
    }

    companion object {
        /** The one document: `users/{uid}/dailyProgress/state`. */
        val DAILY_PROGRESS_KEY = DocKey(SyncCollection.DAILY_PROGRESS, SyncCollection.DAILY_PROGRESS_DOC_ID)
    }
}
