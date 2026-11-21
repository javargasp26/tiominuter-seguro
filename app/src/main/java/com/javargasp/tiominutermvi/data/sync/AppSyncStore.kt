package com.javargasp.tiominutermvi.data.sync

import androidx.room.withTransaction
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.daily.DailyProgressSync
import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.sync.DocKey
import com.javargasp.tiominutermvi.domain.sync.IncomingDoc
import com.javargasp.tiominutermvi.domain.sync.LocalSyncStore
import com.javargasp.tiominutermvi.domain.sync.OutgoingDoc
import com.javargasp.tiominutermvi.domain.sync.PendingChange
import com.javargasp.tiominutermvi.domain.sync.ProgramRefIds
import com.javargasp.tiominutermvi.domain.sync.ProgramRefMapper
import com.javargasp.tiominutermvi.domain.sync.PullQuery
import com.javargasp.tiominutermvi.domain.sync.SyncCollection
import com.javargasp.tiominutermvi.domain.sync.SyncConflicts
import com.javargasp.tiominutermvi.domain.time.WallClock
import kotlinx.coroutines.flow.Flow

/**
 * [LocalSyncStore] of the app database: the training log, the weekly goals and the daily progress.
 * Pulled documents are written with [AppSyncDao] directly, never through the repositories, so
 * nothing is enqueued, except the daily progress when the device is ahead ([DailyProgressSync]).
 */
class AppSyncStore(
    private val database: TioMinuterDatabase,
    private val programs: ProgramRefMapper,
    private val clock: WallClock
) : LocalSyncStore {

    private val outbox get() = database.syncOutboxDao()
    private val dao get() = database.appSyncDao()

    override suspend fun pending(limit: Int): List<OutgoingDoc> =
        outbox.oldest(limit).mapNotNull { row ->
            val change = row.toPendingChange() ?: return@mapNotNull null
            val fields = if (change.delete) null else fieldsOf(change.key)
            // The daily progress is never tombstoned: a change without its row has nothing to say
            if (fields == null && !change.key.collection.canTombstone) {
                outbox.drop(change.key)
                return@mapNotNull null
            }
            OutgoingDoc(change, fields)
        }

    /** The current row's document; a row that is gone becomes a tombstone. */
    private suspend fun fieldsOf(key: DocKey): Map<String, Any?>? = when (key.collection) {
        SyncCollection.TRAINING_LOG -> dao.getEntry(key.id)?.let {
            TrainingLogDocMapper.toFields(it, ProgramRefIds.toRemote(it.refId, programs))
        }
        SyncCollection.WEEKLY_GOAL -> dao.getGoal(key.id)?.let(WeeklyGoalDocMapper::toFields)
        SyncCollection.DAILY_PROGRESS -> dao.getDailyProgress()?.let(DailyProgressDocMapper::toFields)
        else -> null
    }

    override suspend fun pendingCount(): Int = outbox.count()

    override fun observePendingCount(): Flow<Int> = outbox.observeCount()

    override suspend fun acknowledge(changes: List<PendingChange>) {
        database.withTransaction {
            changes.forEach {
                outbox.deleteIfVersion(it.key.collection.name, it.key.parentId.orEmpty(), it.key.id, it.version)
            }
        }
    }

    /**
     * The daily progress last, after the training log (its first state may come from the history),
     * and always in full: one document, so the store also learns that the server has none.
     */
    override suspend fun pullPlan(): List<PullQuery> = listOf(
        PullQuery(SyncCollection.WEEKLY_GOAL),
        PullQuery(SyncCollection.TRAINING_LOG),
        PullQuery(SyncCollection.DAILY_PROGRESS, fullFetch = true)
    )

    override suspend fun apply(query: PullQuery, docs: List<IncomingDoc>): List<PullQuery> {
        database.withTransaction {
            if (query.collection == SyncCollection.DAILY_PROGRESS) {
                applyDailyProgress(docs.firstOrNull { it.key.id == SyncCollection.DAILY_PROGRESS_DOC_ID && !it.deleted })
            } else {
                docs.forEach { applyOne(it) }
            }
        }
        return emptyList()
    }

    /** Merged, never last-write-wins: the more advanced state stays ([DailyProgressSync]). */
    private suspend fun applyDailyProgress(doc: IncomingDoc?) {
        val key = DocKey(SyncCollection.DAILY_PROGRESS, SyncCollection.DAILY_PROGRESS_DOC_ID)
        val remote = doc?.let { DailyProgressDocMapper.toDomain(it.fields) ?: return logUnreadable(key) }
        val history = DailyUnlockRules.fromHistory(dao.dailyRefIds().map { DailyUnlockRules.HistoryEntry(TrainingSource.DAILY, it) })
        val row = dao.getDailyProgress()
        val local = row?.let { DailyProgress(it.lastCompletedDay, it.cycle) } ?: history
        when (val outcome = DailyProgressSync.reconcile(local, remote, history)) {
            is DailyProgressSync.Outcome.ApplyRemote -> {
                dao.upsertDailyProgress(outcome.progress.toEntity(doc!!.updatedAt.millis))
                outbox.drop(key)
            }
            is DailyProgressSync.Outcome.PushLocal -> {
                val now = clock.nowMillis()
                if (row == null || outcome.progress != local) dao.upsertDailyProgress(outcome.progress.toEntity(now))
                outbox.enqueue(key, delete = false, now = now)
            }
            DailyProgressSync.Outcome.InSync -> if (row == null) {
                dao.upsertDailyProgress(local.toEntity(doc!!.updatedAt.millis))
            } else {
                outbox.drop(key)
            }
        }
    }

    private fun DailyProgress.toEntity(updatedAt: Long) =
        DailyProgressEntity(lastCompletedDay = lastCompletedDay, cycle = cycle, updatedAt = updatedAt)

    private suspend fun applyOne(doc: IncomingDoc) {
        val key = doc.key
        val pending = outbox.isPending(key)
        if (SyncConflicts.decide(doc, pending) == SyncConflicts.Decision.KEEP_LOCAL) return
        if (pending) outbox.drop(key)
        when (key.collection) {
            SyncCollection.TRAINING_LOG ->
                if (doc.deleted) {
                    dao.deleteEntry(key.id)
                } else {
                    val existing = dao.getEntry(key.id)
                    val refId = ProgramRefIds.toLocal(doc.fields.string("refId"), programs)
                    val entity = TrainingLogDocMapper.toEntity(key.id, doc.fields, existing, refId, doc.updatedAt.millis)
                        ?: return logUnreadable(key)
                    if (existing == null) dao.insertEntry(entity) else dao.updateEntry(entity)
                }
            SyncCollection.WEEKLY_GOAL ->
                if (doc.deleted) {
                    dao.deleteGoal(key.id)
                } else {
                    val goal = WeeklyGoalDocMapper.toEntity(key.id, doc.fields, doc.updatedAt.millis) ?: return logUnreadable(key)
                    dao.upsertGoal(goal)
                }
            else -> Unit
        }
    }

    override suspend fun enqueueAll() {
        database.withTransaction {
            val now = clock.nowMillis()
            dao.entryUuids().forEach { outbox.enqueue(DocKey(SyncCollection.TRAINING_LOG, it), delete = false, now = now) }
            dao.goalWeeks().forEach { outbox.enqueue(DocKey(SyncCollection.WEEKLY_GOAL, it), delete = false, now = now) }
            if (dao.getDailyProgress() != null) {
                outbox.enqueue(DocKey(SyncCollection.DAILY_PROGRESS, SyncCollection.DAILY_PROGRESS_DOC_ID), delete = false, now = now)
            }
        }
    }

    private fun logUnreadable(key: DocKey) {
        // The collection only: ids and values are personal data
        println("Sync: skipped an unreadable ${key.collection.path} document")
    }
}
