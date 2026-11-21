package com.javargasp.tiominutermvi.data.traininglog.photo

import com.javargasp.tiominutermvi.domain.traininglog.photo.StagedPhoto
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoFiles
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoPaths
import java.io.File
import java.util.UUID

/**
 * [TrainingPhotoFiles] on plain java.io (no Android types, so it is unit-tested with a
 * temporary folder).
 *
 * - Photos: `<filesDir>/training_photos/` (private storage, excluded from backups).
 * - Staged photos: `<cacheDir>/training_photo_staging/`.
 * - Camera capture: `<cacheDir>/training_photo_capture/capture.jpg`, shared with the camera
 *   app through the FileProvider (`res/xml/training_photo_paths.xml`).
 */
class TrainingPhotoFileStore(
    private val filesDir: File,
    cacheDir: File,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : TrainingPhotoFiles {

    private val photoDir = File(filesDir, TrainingPhotoPaths.DIRECTORY)
    private val stagingDir = File(cacheDir, STAGING_DIRECTORY)
    private val captureDir = File(cacheDir, CAPTURE_DIRECTORY)

    override fun fileOf(relativePath: String): File = File(filesDir, relativePath)

    override fun newStagedPhoto(): StagedPhoto {
        stagingDir.mkdirs()
        val id = newId()
        return StagedPhoto(File(stagingDir, "$id.jpg"), File(stagingDir, "${id}_thumb.jpg"))
    }

    override fun captureFile(): File {
        captureDir.mkdirs()
        return File(captureDir, CAPTURE_FILE_NAME)
    }

    override fun deleteCapture() {
        File(captureDir, CAPTURE_FILE_NAME).delete()
    }

    override fun deletePhoto(photoPath: String) {
        if (!TrainingPhotoPaths.isValidPhotoPath(photoPath)) {
            println("TrainingPhoto: ignoring invalid photo path '$photoPath'")
            return
        }
        TrainingPhotoPaths.filesOf(photoPath).forEach { fileOf(it).delete() }
    }

    override fun storedFiles(): List<String> =
        photoDir.listFiles().orEmpty()
            .filter { it.isFile }
            .map { "${TrainingPhotoPaths.DIRECTORY}/${it.name}" }

    override fun deleteStoredFile(relativePath: String) {
        val name = relativePath.removePrefix("${TrainingPhotoPaths.DIRECTORY}/")
        // Only plain names inside the photo directory
        if (name == relativePath || name.isEmpty() || '/' in name || ".." in name) return
        File(photoDir, name).delete()
    }

    override fun deleteStaleTemporaryFiles(olderThanMillis: Long) {
        listOf(stagingDir, captureDir).forEach { dir ->
            dir.listFiles().orEmpty()
                .filter { it.isFile && it.lastModified() < olderThanMillis }
                .forEach { it.delete() }
        }
    }

    companion object {
        const val STAGING_DIRECTORY = "training_photo_staging"
        const val CAPTURE_DIRECTORY = "training_photo_capture"
        const val CAPTURE_FILE_NAME = "capture.jpg"
    }
}
