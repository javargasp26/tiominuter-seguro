package com.javargasp.tiominutermvi.domain.traininglog.photo

import java.io.File

/**
 * The files behind training photos: stored photos (private files dir, see
 * [TrainingPhotoPaths]), staged photos and the camera capture file (both in the cache).
 */
interface TrainingPhotoFiles {

    /** The file of a relative path such as a photoPath or its thumbnail. */
    fun fileOf(relativePath: String): File

    /** A new pair of files for a staged photo. */
    fun newStagedPhoto(): StagedPhoto

    /** The file the camera app writes into (shared through the FileProvider). */
    fun captureFile(): File

    /** Deletes the camera capture file, if any. */
    fun deleteCapture()

    /** Deletes the photo and the thumbnail of [photoPath]; invalid paths are ignored. */
    fun deletePhoto(photoPath: String)

    /** Relative paths of every file in the photo directory. */
    fun storedFiles(): List<String>

    /** Deletes one stored file (relative path). */
    fun deleteStoredFile(relativePath: String)

    /**
     * Deletes staged photos and camera captures last modified before [olderThanMillis]
     * (epoch millis), leaving files that may still be in use.
     */
    fun deleteStaleTemporaryFiles(olderThanMillis: Long)
}
