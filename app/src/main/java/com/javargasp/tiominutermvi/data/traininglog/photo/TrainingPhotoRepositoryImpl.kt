package com.javargasp.tiominutermvi.data.traininglog.photo

import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.StagedPhoto
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoException
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoFiles
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoPaths
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoProcessor
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [TrainingPhotoRepository] over the training log, a [TrainingPhotoProcessor] and the
 * [TrainingPhotoFiles]. Everything runs on [ioDispatcher].
 *
 * Commit, remove and the cleanup hold one [Mutex], so the cleanup never sees a photo file
 * whose photoPath is not written yet. Commit and remove are not cancelled with the caller
 * (a dialog dismissed mid-way), so the files and the database never disagree.
 */
class TrainingPhotoRepositoryImpl(
    private val trainingLog: TrainingLogRepository,
    private val processor: TrainingPhotoProcessor,
    private val files: TrainingPhotoFiles,
    private val clock: WallClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TrainingPhotoRepository {

    private val mutex = Mutex()

    override suspend fun stage(source: TrainingPhotoSource): StagedPhoto = withContext(ioDispatcher) {
        val staged = files.newStagedPhoto()
        try {
            processor.process(source.uri, staged.photoFile, staged.thumbnailFile)
            staged
        } catch (e: CancellationException) {
            discard(staged)
            throw e
        } catch (e: Exception) {
            discard(staged)
            throw e.toPhotoException()
        } finally {
            // The camera's temporary file is not needed once processed (or unreadable)
            if (source.fromCamera) files.deleteCapture()
        }
    }

    override suspend fun commit(entryId: Long, staged: StagedPhoto): String =
        withContext(ioDispatcher + NonCancellable) {
            mutex.withLock { commitLocked(entryId, staged) }
        }

    private suspend fun commitLocked(entryId: Long, staged: StagedPhoto): String {
        val entry = trainingLog.getEntry(entryId)
        if (entry == null) {
            discard(staged)
            throw TrainingPhotoException(TrainingPhotoError.ENTRY_NOT_FOUND)
        }
        val photoPath = TrainingPhotoPaths.photoPath(entry.uuid, clock.nowMillis())
        try {
            move(staged.photoFile, files.fileOf(photoPath))
            move(staged.thumbnailFile, files.fileOf(TrainingPhotoPaths.thumbnailPath(photoPath)))
            val previous = trainingLog.setPhotoPath(entryId, photoPath)
            // The new files are in place and stored: only now the old ones go
            if (previous != null && previous != photoPath) files.deletePhoto(previous)
            return photoPath
        } catch (e: Exception) {
            files.deletePhoto(photoPath)
            discard(staged)
            throw if (e is IllegalStateException) {
                TrainingPhotoException(TrainingPhotoError.ENTRY_NOT_FOUND, e)
            } else {
                e.toPhotoException()
            }
        }
    }

    override fun discard(staged: StagedPhoto) {
        staged.photoFile.delete()
        staged.thumbnailFile.delete()
    }

    override suspend fun attach(entryId: Long, source: TrainingPhotoSource): String =
        commit(entryId, stage(source))

    override suspend fun remove(entryId: Long) = withContext(ioDispatcher + NonCancellable) {
        mutex.withLock {
            val previous = try {
                trainingLog.setPhotoPath(entryId, null)
            } catch (e: IllegalStateException) {
                throw TrainingPhotoException(TrainingPhotoError.ENTRY_NOT_FOUND, e)
            } catch (e: Exception) {
                throw TrainingPhotoException(TrainingPhotoError.FAILED, e)
            }
            if (previous != null) files.deletePhoto(previous)
        }
    }

    override suspend fun cleanUp(): Int = withContext(ioDispatcher) {
        mutex.withLock {
            val orphans = TrainingPhotoPaths.orphans(files.storedFiles(), trainingLog.getPhotoPaths())
            orphans.forEach(files::deleteStoredFile)
            files.deleteStaleTemporaryFiles(clock.nowMillis() - TEMPORARY_FILE_MAX_AGE_MILLIS)
            orphans.size
        }
    }

    /** Rename within the same volume; copy + delete otherwise (cache and files dirs may differ). */
    private fun move(from: File, to: File) {
        to.parentFile?.mkdirs()
        if (from.renameTo(to)) return
        from.copyTo(to, overwrite = true)
        from.delete()
    }

    private fun Exception.toPhotoException(): TrainingPhotoException = when {
        this is TrainingPhotoException -> this
        this is IOException && isNoSpace() -> TrainingPhotoException(TrainingPhotoError.NO_SPACE, this)
        else -> TrainingPhotoException(TrainingPhotoError.FAILED, this)
    }

    private companion object {
        /** Staged and camera files younger than this may still be in use. */
        const val TEMPORARY_FILE_MAX_AGE_MILLIS = 60 * 60 * 1000L
    }
}

/** An IOException caused by a full disk (ENOSPC), as reported by the platform. */
internal fun IOException.isNoSpace(): Boolean {
    val text = generateSequence<Throwable>(this) { it.cause }.mapNotNull { it.message }.joinToString(" ")
    return "ENOSPC" in text || "No space left" in text
}
