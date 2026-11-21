package com.javargasp.tiominutermvi.data.traininglog

import com.javargasp.tiominutermvi.data.local.dao.FeedShareDao
import com.javargasp.tiominutermvi.data.local.entity.FeedShareEntity
import com.javargasp.tiominutermvi.domain.goal.countsTowardGoal
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.RecordedEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutShare
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutShareQueue
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutShareStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The group feed's queue in Room (`feed_share`, in the training log's database so a row is written
 * in its entry's transaction). Only counted workouts recorded while signed in ([currentUid]) get a
 * row: a guest's workouts are never posted, also after signing in later.
 */
class RoomWorkoutShareStore(
    private val dao: FeedShareDao,
    private val clock: WallClock,
    private val currentUid: () -> String?
) : WorkoutShareQueue, WorkoutShareStore {

    private val latest = MutableStateFlow<Map<TrainingSource, RecordedEntry>>(emptyMap())

    override suspend fun onRecorded(entry: TrainingLogEntry) {
        val now = clock.nowMillis()
        if (!countsTowardGoal(entry)) return
        val uid = currentUid() ?: return
        dao.put(
            FeedShareEntity(
                entryUuid = entry.uuid,
                ownerUid = uid,
                share = true,
                entryDeleted = false,
                targetGroupIds = null,
                postedGroupIds = "",
                refusedGroupIds = "",
                postedPhotoPath = null,
                createdAt = now,
                version = 1,
                dirty = true
            )
        )
    }

    // Only once committed: a screen reading the row right away must find it
    override fun onRecordCommitted(entry: TrainingLogEntry) {
        latest.value = latest.value + (entry.source to RecordedEntry(entry.id, entry.source, clock.nowMillis()))
    }

    override suspend fun onDeleted(uuid: String) {
        dao.markDeleted(uuid)
    }

    override suspend fun onPhotoChanged(uuid: String) {
        dao.touch(uuid)
    }

    override fun observeDirtyCount(): Flow<Int> = dao.observeDirtyCount()

    override suspend fun dirty(): List<WorkoutShare> = dao.dirty().map { it.toDomain() }

    override suspend fun get(uuid: String): WorkoutShare? = dao.get(uuid)?.toDomain()

    override suspend fun setShare(uuid: String, share: Boolean) {
        dao.setShare(uuid, share)
    }

    override suspend fun saveProgress(share: WorkoutShare, stillDirty: Boolean) {
        dao.saveProgress(
            uuid = share.entryUuid,
            targetGroupIds = share.targetGroupIds?.let(::join),
            postedGroupIds = join(share.postedGroupIds),
            refusedGroupIds = join(share.refusedGroupIds),
            postedPhotoPath = share.postedPhotoPath,
            version = share.version,
            stillDirty = stillDirty
        )
    }

    override suspend fun remove(uuid: String) = dao.delete(uuid)

    override suspend fun removeDoneBefore(millis: Long) = dao.deleteDoneBefore(millis)

    override fun latestRecorded(source: TrainingSource): Flow<RecordedEntry?> = latest.map { it[source] }

    private companion object {
        fun join(ids: List<String>): String = ids.joinToString(",")

        fun split(value: String): List<String> = value.split(',').filter { it.isNotEmpty() }

        fun FeedShareEntity.toDomain() = WorkoutShare(
            entryUuid = entryUuid,
            ownerUid = ownerUid,
            share = share,
            entryDeleted = entryDeleted,
            targetGroupIds = targetGroupIds?.let(::split),
            postedGroupIds = split(postedGroupIds),
            refusedGroupIds = split(refusedGroupIds),
            postedPhotoPath = postedPhotoPath,
            createdAt = createdAt,
            version = version,
            dirty = dirty
        )
    }
}
