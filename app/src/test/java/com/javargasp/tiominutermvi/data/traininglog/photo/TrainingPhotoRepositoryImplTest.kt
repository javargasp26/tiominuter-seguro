package com.javargasp.tiominutermvi.data.traininglog.photo

import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoException
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoProcessor
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * [TrainingPhotoRepositoryImpl] with the real file store on a temporary folder, a fake
 * processor (writes small files or fails) and a fake training log.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainingPhotoRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val filesDir by lazy { folder.newFolder("files") }
    private val cacheDir by lazy { folder.newFolder("cache") }
    private val files by lazy { TrainingPhotoFileStore(filesDir, cacheDir) }
    private val processor = FakeProcessor()
    private val log = FakeTrainingLog()
    private var now = 1_000L
    private val repository by lazy { TrainingPhotoRepositoryImpl(log, processor, files, { now }, dispatcher) }

    private val gallery = TrainingPhotoSource("content://picker/1", fromCamera = false)

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private fun file(relative: String) = File(filesDir, relative)

    private suspend fun expectError(error: TrainingPhotoError, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $error")
        } catch (e: TrainingPhotoException) {
            assertEquals(error, e.error)
        }
    }

    // ------------------------------------------------------------------
    // Attach, replace, remove
    // ------------------------------------------------------------------

    @Test
    fun `attach stores the photo and thumbnail under the entry uuid and sets photoPath`() = test {
        log.add(id = 7, uuid = "uuid-7")

        val photoPath = repository.attach(7, gallery)

        assertEquals("training_photos/uuid-7-1000.jpg", photoPath)
        assertEquals(photoPath, log.entry(7).photoPath)
        assertEquals("photo of content://picker/1", file(photoPath).readText())
        assertEquals("thumb of content://picker/1", file("training_photos/uuid-7-1000_thumb.jpg").readText())
        assertTrue(File(cacheDir, "training_photo_staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `replacing writes the new files and deletes the old ones`() = test {
        log.add(id = 7, uuid = "uuid-7")
        val old = repository.attach(7, gallery)
        now = 2_000L

        val new = repository.attach(7, TrainingPhotoSource("content://picker/2", fromCamera = false))

        assertEquals("training_photos/uuid-7-2000.jpg", new)
        assertEquals(new, log.entry(7).photoPath)
        assertFalse(file(old).exists())
        assertFalse(file("training_photos/uuid-7-1000_thumb.jpg").exists())
        assertTrue(file(new).exists())
        assertTrue(file("training_photos/uuid-7-2000_thumb.jpg").exists())
    }

    @Test
    fun `removing clears photoPath and deletes the files`() = test {
        log.add(id = 7, uuid = "uuid-7")
        val photoPath = repository.attach(7, gallery)

        repository.remove(7)

        assertNull(log.entry(7).photoPath)
        assertFalse(file(photoPath).exists())
        assertTrue(files.storedFiles().isEmpty())
    }

    @Test
    fun `removing an entry without photo changes nothing, a missing entry is an error`() = test {
        log.add(id = 7, uuid = "uuid-7")

        repository.remove(7)

        assertNull(log.entry(7).photoPath)
        expectError(TrainingPhotoError.ENTRY_NOT_FOUND) { repository.remove(99) }
    }

    // ------------------------------------------------------------------
    // Staging and camera
    // ------------------------------------------------------------------

    @Test
    fun `a staged photo is only attached on commit, and discard deletes it`() = test {
        log.add(id = 7, uuid = "uuid-7")

        val staged = repository.stage(gallery)
        assertTrue(staged.photoFile.exists())
        assertNull(log.entry(7).photoPath)

        val other = repository.stage(gallery)
        repository.discard(other)
        assertFalse(other.photoFile.exists())
        assertFalse(other.thumbnailFile.exists())

        val photoPath = repository.commit(7, staged)
        assertEquals(photoPath, log.entry(7).photoPath)
        assertFalse(staged.photoFile.exists())
    }

    @Test
    fun `the camera capture is deleted after processing, also when it fails`() = test {
        log.add(id = 7, uuid = "uuid-7")
        val camera = TrainingPhotoSource("content://app.trainingphotos/capture.jpg", fromCamera = true)
        files.captureFile().writeText("raw")

        repository.attach(7, camera)
        assertFalse(files.captureFile().exists())

        files.captureFile().writeText("raw")
        processor.error = TrainingPhotoException(TrainingPhotoError.UNREADABLE_IMAGE)
        expectError(TrainingPhotoError.UNREADABLE_IMAGE) { repository.attach(7, camera) }
        assertFalse(files.captureFile().exists())
    }

    // ------------------------------------------------------------------
    // Errors never touch the entry or leave files behind
    // ------------------------------------------------------------------

    @Test
    fun `a processing failure keeps the entry and its current photo`() = test {
        log.add(id = 7, uuid = "uuid-7")
        val current = repository.attach(7, gallery)
        now = 2_000L
        processor.error = TrainingPhotoException(TrainingPhotoError.UNREADABLE_IMAGE)

        expectError(TrainingPhotoError.UNREADABLE_IMAGE) { repository.attach(7, gallery) }

        assertEquals(current, log.entry(7).photoPath)
        assertTrue(file(current).exists())
        assertEquals(2, files.storedFiles().size)
        assertTrue(File(cacheDir, "training_photo_staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a full disk is reported as no space`() = test {
        log.add(id = 7, uuid = "uuid-7")
        processor.error = IOException("write failed: ENOSPC (No space left on device)")

        expectError(TrainingPhotoError.NO_SPACE) { repository.attach(7, gallery) }

        assertNull(log.entry(7).photoPath)
    }

    @Test
    fun `an unexpected failure is reported as failed`() = test {
        log.add(id = 7, uuid = "uuid-7")
        processor.error = IllegalArgumentException("boom")

        expectError(TrainingPhotoError.FAILED) { repository.attach(7, gallery) }
    }

    @Test
    fun `committing to a missing entry discards the staged photo`() = test {
        val staged = repository.stage(gallery)

        expectError(TrainingPhotoError.ENTRY_NOT_FOUND) { repository.commit(42, staged) }

        assertFalse(staged.photoFile.exists())
        assertTrue(files.storedFiles().isEmpty())
    }

    @Test
    fun `when storing photoPath fails, the new files go and the old photo stays`() = test {
        log.add(id = 7, uuid = "uuid-7")
        val current = repository.attach(7, gallery)
        now = 2_000L
        log.setPhotoPathError = IllegalArgumentException("disk I/O error")

        expectError(TrainingPhotoError.FAILED) { repository.attach(7, gallery) }

        assertEquals(current, log.entry(7).photoPath)
        assertEquals(listOf(current, "training_photos/uuid-7-1000_thumb.jpg").sorted(), files.storedFiles().sorted())
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    @Test
    fun `cleanup deletes unreferenced photo files and old temporary files only`() = test {
        log.add(id = 7, uuid = "uuid-7")
        val kept = repository.attach(7, gallery)
        listOf("training_photos/gone-1.jpg", "training_photos/gone-1_thumb.jpg", "training_photos/x.jpg.tmp")
            .forEach { file(it).writeText("orphan") }
        val oldCapture = files.captureFile().apply { writeText("x"); setLastModified(0L) }
        val freshStaged = files.newStagedPhoto().photoFile.apply { writeText("x") }
        now = System.currentTimeMillis()

        val deleted = repository.cleanUp()

        assertEquals(3, deleted)
        assertEquals(listOf(kept, "training_photos/uuid-7-1000_thumb.jpg").sorted(), files.storedFiles().sorted())
        assertFalse(oldCapture.exists())
        assertTrue(freshStaged.exists())
    }

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    /** Writes "photo of <uri>" / "thumb of <uri>", or throws [error]. */
    private class FakeProcessor : TrainingPhotoProcessor {
        var error: Exception? = null

        override suspend fun process(sourceUri: String, photoFile: File, thumbnailFile: File) {
            error?.let { throw it }
            photoFile.writeText("photo of $sourceUri")
            thumbnailFile.writeText("thumb of $sourceUri")
        }
    }

    /** Only what the photo repository uses: getEntry, setPhotoPath and getPhotoPaths. */
    private class FakeTrainingLog : TrainingLogRepository {
        private val entries = mutableMapOf<Long, TrainingLogEntry>()
        var setPhotoPathError: Exception? = null

        fun add(id: Long, uuid: String) {
            entries[id] = TrainingLogEntry(
                id = id, uuid = uuid, dateLocal = "2026-10-19", completedAt = 0, source = TrainingSource.GYM,
                refId = "gym:64", title = "Pull Day", durationSeconds = 1800, createdAt = 0, updatedAt = 0
            )
        }

        fun entry(id: Long) = entries.getValue(id)

        override suspend fun getEntry(id: Long): TrainingLogEntry? = entries[id]

        override suspend fun setPhotoPath(id: Long, photoPath: String?): String? {
            setPhotoPathError?.let { throw it }
            val entry = entries[id] ?: throw IllegalStateException("Training log entry $id does not exist")
            entries[id] = entry.copy(photoPath = photoPath)
            return entry.photoPath
        }

        override suspend fun getPhotoPaths(): List<String> = entries.values.mapNotNull { it.photoPath }

        override fun observeEntries(): Flow<List<TrainingLogEntry>> = emptyFlow()
        override fun observeEntriesBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntry>> = emptyFlow()
        override suspend fun addActivityEntry(input: ActivityEntryInput): Long = throw UnsupportedOperationException()
        override suspend fun updateActivityEntry(id: Long, input: ActivityEntryInput) = throw UnsupportedOperationException()
        override suspend fun deleteEntry(id: Long) = throw UnsupportedOperationException()
    }
}
