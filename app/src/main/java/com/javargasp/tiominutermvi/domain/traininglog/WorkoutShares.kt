package com.javargasp.tiominutermvi.domain.traininglog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Sharing a finished workout in my groups (the group feed, docs/firestore-model.md "Feed"). The
 * training history only marks what to share; the groups feature posts it. One row per entry
 * ([WorkoutShare]), written in the entry's own transaction, so a workout finished offline or right
 * before the app is killed is still posted later.
 */
interface WorkoutShareQueue {

    /**
     * A counted workout was just recorded while signed in: shared by default. Called inside the
     * transaction that inserts [entry] (with its id).
     */
    suspend fun onRecorded(entry: TrainingLogEntry)

    /** After that transaction committed: [entry] (with its id) is the latest of its source. */
    fun onRecordCommitted(entry: TrainingLogEntry) = Unit

    /** The entry was deleted: its posts go too. Called inside the deleting transaction. */
    suspend fun onDeleted(uuid: String)

    /** The entry's photo was set, replaced or removed: its posts' thumbnail follows. */
    suspend fun onPhotoChanged(uuid: String)

    /** Nothing is ever shared (tests, guests-only builds). */
    object None : WorkoutShareQueue {
        override suspend fun onRecorded(entry: TrainingLogEntry) = Unit
        override suspend fun onDeleted(uuid: String) = Unit
        override suspend fun onPhotoChanged(uuid: String) = Unit
    }
}

/**
 * What the app knows about the posts of one entry. [targetGroupIds] are the groups I was in when
 * it was first posted (null until then: a workout is posted to the groups of that moment, never
 * to a group joined later); [postedGroupIds] hold its post; [refusedGroupIds] refused it (removed,
 * group gone: never retried); [postedPhotoPath] is the photo whose thumbnail the posts carry (null:
 * none). [version] grows with every local change, so a flush that raced one stays [dirty].
 */
data class WorkoutShare(
    val entryUuid: String,
    val ownerUid: String,
    val share: Boolean,
    val entryDeleted: Boolean,
    val targetGroupIds: List<String>?,
    val postedGroupIds: List<String>,
    val refusedGroupIds: List<String>,
    val postedPhotoPath: String?,
    val createdAt: Long,
    val version: Long,
    val dirty: Boolean
)

/** The queue as the groups feature reads and updates it. */
interface WorkoutShareStore {

    fun observeDirtyCount(): Flow<Int>

    suspend fun dirty(): List<WorkoutShare>

    suspend fun get(uuid: String): WorkoutShare?

    /** The person's choice for this workout (the completion screen's switch). */
    suspend fun setShare(uuid: String, share: Boolean)

    /**
     * What a flush did on the server. Clears [WorkoutShare.dirty] only if nothing changed locally
     * since [WorkoutShare.version] was read.
     */
    suspend fun saveProgress(share: WorkoutShare, stillDirty: Boolean)

    suspend fun remove(uuid: String)

    /** Rows created before [millis] that have nothing left to do. */
    suspend fun removeDoneBefore(millis: Long)

    /** The id of the latest entry recorded locally in this process for [source] (the daily completion screen). */
    fun latestRecorded(source: TrainingSource): Flow<RecordedEntry?>

    object None : WorkoutShareStore {
        override fun observeDirtyCount(): Flow<Int> = emptyFlow()
        override suspend fun dirty(): List<WorkoutShare> = emptyList()
        override suspend fun get(uuid: String): WorkoutShare? = null
        override suspend fun setShare(uuid: String, share: Boolean) = Unit
        override suspend fun saveProgress(share: WorkoutShare, stillDirty: Boolean) = Unit
        override suspend fun remove(uuid: String) = Unit
        override suspend fun removeDoneBefore(millis: Long) = Unit
        override fun latestRecorded(source: TrainingSource): Flow<RecordedEntry?> = emptyFlow()
    }
}

/** An entry recorded in this process: its id and when (WallClock millis of the recording). */
data class RecordedEntry(val id: Long, val source: TrainingSource, val recordedAt: Long)

/** The share switch of a completion screen for one entry. */
data class ShareOptions(
    /** Shared (the switch is on). */
    val share: Boolean,
    /** The one-time explanation of what group members see hasn't been shown yet. */
    val needsIntro: Boolean
)

/**
 * The completion screens' (and Log activity's) view of sharing, implemented by the groups feature.
 * Nothing shows for a guest, without groups, or for an entry that isn't shared (not counted).
 */
interface WorkoutSharing {

    /** Null when the switch must not show. */
    suspend fun options(entryId: Long): ShareOptions?

    /** Whether a new workout would be offered for sharing (Log activity, before it is saved). */
    suspend fun isAvailable(): Boolean

    suspend fun setShare(entryId: Long, share: Boolean)

    /** The explanation was shown (OK or "Don't share this one"): never again for this account. */
    suspend fun acknowledgeIntro()

    /** The latest entry of [source] recorded in this process (the daily completion screen). */
    fun latestRecorded(source: TrainingSource): Flow<RecordedEntry?>

    object None : WorkoutSharing {
        override suspend fun options(entryId: Long): ShareOptions? = null
        override suspend fun isAvailable(): Boolean = false
        override suspend fun setShare(entryId: Long, share: Boolean) = Unit
        override suspend fun acknowledgeIntro() = Unit
        override fun latestRecorded(source: TrainingSource): Flow<RecordedEntry?> = emptyFlow()
    }
}
