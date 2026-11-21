package com.javargasp.tiominutermvi.domain.traininglog.photo

import java.io.File

/**
 * A picture chosen by the person: a content uri from the Photo Picker or from the camera
 * (the app's own FileProvider). [fromCamera] marks the temporary capture file, which is
 * deleted once it has been processed.
 */
data class TrainingPhotoSource(val uri: String, val fromCamera: Boolean)

/**
 * A processed photo that is not attached to an entry yet (the log-activity form before
 * Save). Its files live in the cache until [TrainingPhotoRepository.commit] moves them.
 */
data class StagedPhoto(val photoFile: File, val thumbnailFile: File)

/** Why a photo could not be added; each one has a short message. */
enum class TrainingPhotoError { UNREADABLE_IMAGE, NO_SPACE, ENTRY_NOT_FOUND, FAILED }

class TrainingPhotoException(val error: TrainingPhotoError, cause: Throwable? = null) :
    Exception("Training photo: $error", cause)

/**
 * Photos of training-log entries, local only (a later sync will upload the files this
 * produces). Every change keeps the database and the files consistent: replacing deletes
 * the old files, removing clears `photoPath` and deletes the files. Failures throw
 * [TrainingPhotoException] and never change the entry's other data.
 */
interface TrainingPhotoRepository {

    /** Processes [source] into a [StagedPhoto] (re-encoded, no metadata). */
    suspend fun stage(source: TrainingPhotoSource): StagedPhoto

    /** Moves [staged] into place as the photo of [entryId] and returns its photoPath. */
    suspend fun commit(entryId: Long, staged: StagedPhoto): String

    /** Deletes a staged photo that won't be committed. Safe to call more than once. */
    fun discard(staged: StagedPhoto)

    /** [stage] + [commit]: attaches (or replaces) the photo of [entryId] and returns its photoPath. */
    suspend fun attach(entryId: Long, source: TrainingPhotoSource): String

    /** Removes the photo of [entryId] (photoPath cleared, files deleted). */
    suspend fun remove(entryId: Long)

    /**
     * Deletes photo files no entry references, plus staged and camera files older than an
     * hour. Run at app start; returns the number of photo files deleted.
     */
    suspend fun cleanUp(): Int
}
