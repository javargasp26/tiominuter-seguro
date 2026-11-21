package com.javargasp.tiominutermvi.data.traininglog

import com.javargasp.tiominutermvi.data.local.dao.TrainingLogDao
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryRules
import com.javargasp.tiominutermvi.domain.traininglog.InvalidActivityEntryException
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutShareQueue
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoFiles
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import com.javargasp.tiominutermvi.domain.sync.SyncCollection
import com.javargasp.tiominutermvi.domain.sync.DocKey
import com.javargasp.tiominutermvi.domain.sync.ChangeTracker

/**
 * Room-backed training history; also the app's [TrainingLogRecorder].
 * [newUuid] is injectable so tests can predict it. [photoFiles] deletes an entry's photo
 * files together with the entry. [shares] marks what the group feed posts, in the entry's own
 * transaction (a counted workout recorded, an entry deleted, a photo changed).
 */
class TrainingLogRepositoryImpl(
    private val dao: TrainingLogDao,
    private val clock: WallClock,
    private val localDateProvider: LocalDateProvider,
    private val photoFiles: TrainingPhotoFiles,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val changes: ChangeTracker = ChangeTracker.None,
    private val shares: WorkoutShareQueue = WorkoutShareQueue.None
) : TrainingLogRepository, TrainingLogRecorder, SessionEntryRecorder {

    override fun observeEntries(): Flow<List<TrainingLogEntry>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override fun observeEntriesBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntry>> =
        dao.observeBetween(fromDate, toDate).map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun getEntry(id: Long): TrainingLogEntry? = dao.getById(id)?.toDomainOrNull()

    override suspend fun getEntryByUuid(uuid: String): TrainingLogEntry? = dao.getByUuid(uuid)?.toDomainOrNull()

    override suspend fun addActivityEntry(input: ActivityEntryInput): Long {
        val now = clock.nowMillis()
        validate(input, now, ActivityEntryRules.windowStart(localDateProvider))
        val entry = TrainingLogEntry(
            id = 0,
            uuid = newUuid(),
            dateLocal = localDateProvider.dateOf(input.completedAt),
            completedAt = input.completedAt,
            source = TrainingSource.ACTIVITY,
            refId = null,
            title = ActivityEntryRules.titleFor(input),
            durationSeconds = input.durationMinutes!! * SECONDS_PER_MINUTE,
            activityType = input.type!!.code,
            intensity = input.intensity!!.code,
            note = ActivityEntryRules.noteFor(input),
            photoPath = null,
            createdAt = now,
            updatedAt = now
        )
        return withContext(NonCancellable) { insertTracked(entry) }
    }

    override suspend fun updateActivityEntry(id: Long, input: ActivityEntryInput) {
        val existing = dao.getById(id)?.toDomainOrNull()
            ?: throw IllegalStateException("Training log entry $id does not exist")
        check(existing.source == TrainingSource.ACTIVITY) { "Entry $id is ${existing.source}, not ACTIVITY" }
        val now = clock.nowMillis()
        val earliest = minOf(
            ActivityEntryRules.windowStart(localDateProvider),
            localDateProvider.startOfDay(existing.dateLocal)
        )
        validate(input, now, earliest)
        val updated = existing.copy(
            dateLocal = localDateProvider.dateOf(input.completedAt),
            completedAt = input.completedAt,
            title = ActivityEntryRules.titleFor(input),
            durationSeconds = input.durationMinutes!! * SECONDS_PER_MINUTE,
            activityType = input.type!!.code,
            intensity = input.intensity!!.code,
            note = ActivityEntryRules.noteFor(input),
            updatedAt = now
        )
        withContext(NonCancellable) {
            changes.inTransaction {
                dao.update(updated.toEntity())
                changes.enqueue(DocKey(SyncCollection.TRAINING_LOG, updated.uuid))
            }
        }
    }

    private fun validate(input: ActivityEntryInput, now: Long, earliest: Long) {
        val errors = ActivityEntryRules.validate(input, now, earliest)
        if (errors.isNotEmpty()) throw InvalidActivityEntryException(errors)
    }

    /** The files go after the row: a failure to delete them leaves orphans for the cleanup. */
    override suspend fun deleteEntry(id: Long) {
        val existing = dao.getById(id)
        val photoPath = existing?.photoPath
        withContext(NonCancellable) {
            changes.inTransaction {
                dao.delete(id)
                // The other devices learn about it from the tombstone
                if (existing != null) changes.enqueue(DocKey(SyncCollection.TRAINING_LOG, existing.uuid), delete = true)
                // Its posts in my groups go too
                if (existing != null) shares.onDeleted(existing.uuid)
            }
        }
        if (photoPath != null) {
            try {
                photoFiles.deletePhoto(photoPath)
            } catch (e: Exception) {
                println("TrainingLog: could not delete the photo of entry $id: ${e.message}")
            }
        }
    }

    override suspend fun setPhotoPath(id: Long, photoPath: String?): String? {
        val previous = dao.getById(id) ?: throw IllegalStateException("Training log entry $id does not exist")
        withContext(NonCancellable) {
            changes.inTransaction {
                dao.updatePhotoPath(id, photoPath, clock.nowMillis())
                shares.onPhotoChanged(previous.uuid)
            }
        }
        return previous.photoPath
    }

    override suspend fun getPhotoPaths(): List<String> = dao.getPhotoPaths()

    /**
     * Stores a new entry completed now. The insert is not cancelled with the caller
     * (e.g. a ViewModel cleared right after a workout ends), so the entry is not lost.
     */
    override suspend fun record(source: TrainingSource, refId: String?, title: String, durationSeconds: Int?): Long {
        val now = clock.nowMillis()
        val entry = TrainingLogEntry(
            id = 0,
            uuid = newUuid(),
            dateLocal = localDateProvider.dateOf(now),
            completedAt = now,
            source = source,
            refId = refId,
            title = title,
            durationSeconds = durationSeconds,
            createdAt = now,
            updatedAt = now
        )
        return withContext(NonCancellable) { insertTracked(entry) }
    }

    /** Like [record], with the end time and the date's moment given (a gym session closed later). */
    override suspend fun recordSession(
        source: TrainingSource,
        refId: String?,
        title: String,
        durationSeconds: Int,
        completedAt: Long,
        dateOfMillis: Long
    ): Long {
        val now = clock.nowMillis()
        val entry = TrainingLogEntry(
            id = 0,
            uuid = newUuid(),
            dateLocal = localDateProvider.dateOf(dateOfMillis),
            completedAt = completedAt,
            source = source,
            refId = refId,
            title = title,
            durationSeconds = durationSeconds,
            createdAt = now,
            updatedAt = now
        )
        return withContext(NonCancellable) { insertTracked(entry) }
    }

    /** Inserts the entry, enqueues its upload and marks what to share, in one transaction. */
    private suspend fun insertTracked(entry: TrainingLogEntry): Long {
        val id = changes.inTransaction {
            val id = dao.insert(entry.toEntity())
            changes.enqueue(DocKey(SyncCollection.TRAINING_LOG, entry.uuid))
            shares.onRecorded(entry.copy(id = id))
            id
        }
        shares.onRecordCommitted(entry.copy(id = id))
        return id
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
