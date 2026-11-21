package com.javargasp.tiominutermvi.domain.traininglog

import kotlinx.coroutines.flow.Flow

/** Reads and edits the training history. The implementation also is the [TrainingLogRecorder]. */
interface TrainingLogRepository {

    /** Every entry, newest first (by completedAt). */
    fun observeEntries(): Flow<List<TrainingLogEntry>>

    /** Entries whose dateLocal is between [fromDate] and [toDate] ("yyyy-MM-dd", inclusive), newest first. */
    fun observeEntriesBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntry>>

    /** One entry, or null when it doesn't exist (or has an unknown source). */
    suspend fun getEntry(id: Long): TrainingLogEntry?

    /** One entry by its uuid (the group feed's posts are named by it), or null. */
    suspend fun getEntryByUuid(uuid: String): TrainingLogEntry? = null

    /**
     * Stores a logged activity (source ACTIVITY) and returns its id. Throws
     * [InvalidActivityEntryException] when [input] breaks [ActivityEntryRules] (date window:
     * 00:00 six days ago to now).
     */
    suspend fun addActivityEntry(input: ActivityEntryInput): Long

    /**
     * Replaces the data of the ACTIVITY entry [id], keeping its uuid and createdAt and
     * setting updatedAt to now. The entry's own day stays allowed even when it is older
     * than the window. Throws [InvalidActivityEntryException] when [input] is invalid and
     * IllegalStateException when [id] doesn't exist or isn't an ACTIVITY entry.
     */
    suspend fun updateActivityEntry(id: Long, input: ActivityEntryInput)

    /** Deletes the entry and its photo files, if any. */
    suspend fun deleteEntry(id: Long)

    /**
     * Sets (or clears, with null) the photoPath of entry [id] and its updatedAt; returns the
     * previous photoPath. Only the photo feature calls it, which also handles the files.
     * Throws IllegalStateException when [id] doesn't exist.
     */
    suspend fun setPhotoPath(id: Long, photoPath: String?): String?

    /** Every photoPath in use (for the orphan-file cleanup). */
    suspend fun getPhotoPaths(): List<String>
}
