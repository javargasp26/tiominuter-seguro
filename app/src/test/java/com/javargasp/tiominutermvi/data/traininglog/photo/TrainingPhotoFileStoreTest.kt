package com.javargasp.tiominutermvi.data.traininglog.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The file store on a temporary folder (plain java.io, no Android). */
class TrainingPhotoFileStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val filesDir by lazy { folder.newFolder("files") }
    private val cacheDir by lazy { folder.newFolder("cache") }
    private val store by lazy { TrainingPhotoFileStore(filesDir, cacheDir, newId = { "staged-id" }) }

    private fun stored(name: String) = File(filesDir, "training_photos/$name").apply {
        parentFile!!.mkdirs()
        writeText("x")
    }

    @Test
    fun `relative paths resolve inside the files dir`() {
        assertEquals(File(filesDir, "training_photos/a-1.jpg"), store.fileOf("training_photos/a-1.jpg"))
    }

    @Test
    fun `staged photos and the camera capture live in the cache`() {
        val staged = store.newStagedPhoto()

        assertEquals(File(cacheDir, "training_photo_staging/staged-id.jpg"), staged.photoFile)
        assertEquals(File(cacheDir, "training_photo_staging/staged-id_thumb.jpg"), staged.thumbnailFile)
        assertEquals(File(cacheDir, "training_photo_capture/capture.jpg"), store.captureFile())
        assertTrue(staged.photoFile.parentFile!!.isDirectory)
    }

    @Test
    fun `deleting a photo deletes the photo and its thumbnail only`() {
        val photo = stored("a-1.jpg")
        val thumbnail = stored("a-1_thumb.jpg")
        val other = stored("b-2.jpg")

        store.deletePhoto("training_photos/a-1.jpg")

        assertFalse(photo.exists())
        assertFalse(thumbnail.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `an invalid photo path never deletes a file outside the photo directory`() {
        val database = File(filesDir, "tiominuter.db").apply { writeText("db") }

        store.deletePhoto("training_photos/../tiominuter.db")
        store.deleteStoredFile("training_photos/../tiominuter.db")

        assertTrue(database.exists())
    }

    @Test
    fun `stored files are listed as relative paths`() {
        stored("a-1.jpg")
        stored("a-1_thumb.jpg")
        File(filesDir, "training_photos/folder").mkdirs()

        assertEquals(
            setOf("training_photos/a-1.jpg", "training_photos/a-1_thumb.jpg"),
            store.storedFiles().toSet()
        )
        assertTrue(TrainingPhotoFileStore(File(folder.root, "empty"), cacheDir).storedFiles().isEmpty())
    }

    @Test
    fun `a stored file is deleted by its relative path`() {
        val file = stored("c-3.jpg.tmp")

        store.deleteStoredFile("training_photos/c-3.jpg.tmp")

        assertFalse(file.exists())
    }

    @Test
    fun `only old temporary files are deleted`() {
        val oldStaged = store.newStagedPhoto().photoFile.apply { writeText("x"); setLastModified(1_000L) }
        val newStaged = File(cacheDir, "training_photo_staging/new.jpg").apply { writeText("x"); setLastModified(5_000L) }
        val oldCapture = store.captureFile().apply { writeText("x"); setLastModified(1_000L) }

        store.deleteStaleTemporaryFiles(olderThanMillis = 2_000L)

        assertFalse(oldStaged.exists())
        assertFalse(oldCapture.exists())
        assertTrue(newStaged.exists())
    }

    @Test
    fun `the capture file is deleted on request`() {
        val capture = store.captureFile().apply { writeText("x") }

        store.deleteCapture()

        assertFalse(capture.exists())
    }
}
