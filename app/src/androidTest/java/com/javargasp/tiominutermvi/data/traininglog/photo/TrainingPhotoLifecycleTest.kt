package com.javargasp.tiominutermvi.data.traininglog.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoPaths
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.TimeZone

/**
 * Photos end to end on the device: in-memory Room, the real processor and real files (in
 * test folders, never the app's own photo directory). Replacing, removing and deleting an
 * entry delete the files; the cleanup removes unreferenced ones.
 */
@RunWith(AndroidJUnit4::class)
class TrainingPhotoLifecycleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var root: File
    private lateinit var database: TioMinuterDatabase
    private lateinit var files: TrainingPhotoFileStore
    private lateinit var trainingLog: TrainingLogRepositoryImpl
    private lateinit var photos: TrainingPhotoRepositoryImpl
    private var now = 1_792_663_200_000L

    @Before
    fun setUp() {
        root = File(context.cacheDir, "photo-lifecycle-test").apply { deleteRecursively(); mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, TioMinuterDatabase::class.java).build()
        files = TrainingPhotoFileStore(File(root, "files"), File(root, "cache"))
        val dates = LocalDateProvider({ now }, { TimeZone.getTimeZone("UTC") })
        trainingLog = TrainingLogRepositoryImpl(database.trainingLogDao(), { now }, dates, files)
        photos = TrainingPhotoRepositoryImpl(trainingLog, AndroidTrainingPhotoProcessor(context.contentResolver), files, { now })
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    private fun jpeg(name: String): TrainingPhotoSource {
        val file = File(root, name)
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return TrainingPhotoSource(Uri.fromFile(file).toString(), fromCamera = false)
    }

    private fun exists(photoPath: String) =
        TrainingPhotoPaths.filesOf(photoPath).map { files.fileOf(it).isFile }

    private suspend fun gymEntry(): Long = trainingLog.record(TrainingSource.GYM, "gym:64", "Pull Day", 1_800)

    @Test
    fun attachingStoresBothFilesAndThePath() = runBlocking {
        val id = gymEntry()

        val photoPath = photos.attach(id, jpeg("a.jpg"))

        assertEquals(photoPath, trainingLog.getEntry(id)!!.photoPath)
        assertEquals(listOf(true, true), exists(photoPath))
        assertTrue(photoPath.startsWith("training_photos/${trainingLog.getEntry(id)!!.uuid}-"))
    }

    @Test
    fun replacingDeletesTheOldFiles() = runBlocking {
        val id = gymEntry()
        val old = photos.attach(id, jpeg("a.jpg"))
        now += 1_000

        val new = photos.attach(id, jpeg("b.jpg"))

        assertEquals(listOf(false, false), exists(old))
        assertEquals(listOf(true, true), exists(new))
        assertEquals(TrainingPhotoPaths.filesOf(new).sorted(), files.storedFiles().sorted())
    }

    @Test
    fun removingClearsThePathAndDeletesTheFiles() = runBlocking {
        val id = gymEntry()
        val photoPath = photos.attach(id, jpeg("a.jpg"))

        photos.remove(id)

        assertNull(trainingLog.getEntry(id)!!.photoPath)
        assertEquals(listOf(false, false), exists(photoPath))
    }

    @Test
    fun deletingTheEntryDeletesItsFiles() = runBlocking {
        val id = gymEntry()
        val photoPath = photos.attach(id, jpeg("a.jpg"))

        trainingLog.deleteEntry(id)

        assertNull(trainingLog.getEntry(id))
        assertEquals(listOf(false, false), exists(photoPath))
    }

    @Test
    fun cleanupKeepsReferencedPhotosAndDeletesOrphans() = runBlocking {
        val id = gymEntry()
        val kept = photos.attach(id, jpeg("a.jpg"))
        val orphan = files.fileOf("training_photos/deleted-entry-1.jpg").apply { writeText("x") }

        val deleted = photos.cleanUp()

        assertEquals(1, deleted)
        assertFalse(orphan.exists())
        assertEquals(listOf(true, true), exists(kept))
    }
}
