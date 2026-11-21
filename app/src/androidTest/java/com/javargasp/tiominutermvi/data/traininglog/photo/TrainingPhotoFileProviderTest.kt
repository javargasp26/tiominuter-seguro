package com.javargasp.tiominutermvi.data.traininglog.photo

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javargasp.tiominutermvi.ui.components.photo.trainingPhotoAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The manifest's FileProvider and `res/xml/training_photo_paths.xml` share the camera
 * capture file, and nothing else.
 */
@RunWith(AndroidJUnit4::class)
class TrainingPhotoFileProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val files = TrainingPhotoFileStore(context.filesDir, context.cacheDir)

    @Test
    fun theCaptureFileHasAContentUriTheCameraAppCanWrite() {
        val capture = files.captureFile().apply { writeText("x") }

        val uri = FileProvider.getUriForFile(context, trainingPhotoAuthority(context), capture)

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.trainingphotos", uri.authority)
        context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }
        assertEquals(3L, capture.length())
        files.deleteCapture()
    }

    @Test
    fun storedPhotosAreNotShared() {
        val photo = files.fileOf("training_photos/private-1.jpg")

        val refused = try {
            FileProvider.getUriForFile(context, trainingPhotoAuthority(context), photo)
            false
        } catch (e: IllegalArgumentException) {
            true
        }

        assertTrue("stored photos must not be reachable through the FileProvider", refused)
        assertTrue(File(context.filesDir, "training_photos").let { !it.exists() || it.isDirectory })
    }
}
