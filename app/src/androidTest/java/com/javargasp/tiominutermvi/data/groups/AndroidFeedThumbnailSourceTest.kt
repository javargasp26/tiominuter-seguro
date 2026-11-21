package com.javargasp.tiominutermvi.data.groups

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoFileStore
import com.javargasp.tiominutermvi.domain.groups.FeedLimits
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoPaths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

/** The real feed thumbnail encoder: always ≤ 60 KB (under the rules' 65,536-byte cap), also from noise. */
@RunWith(AndroidJUnit4::class)
class AndroidFeedThumbnailSourceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val root = File(context.cacheDir, "feed-thumb-test").apply { deleteRecursively(); mkdirs() }
    private val files = TrainingPhotoFileStore(File(root, "files"), File(root, "cache"))
    private val source = AndroidFeedThumbnailSource(files)

    /** Writes [bitmap] as the stored thumbnail (or the photo) of a photoPath, at JPEG [quality]. */
    private fun store(bitmap: Bitmap, quality: Int = 100, asThumbnail: Boolean = true): String {
        val photoPath = TrainingPhotoPaths.photoPath("3f2c8a1e-5b7d-4c9a-8e21-0d6f4b3a9c10", System.nanoTime())
        val file = files.fileOf(if (asThumbnail) TrainingPhotoPaths.thumbnailPath(photoPath) else photoPath)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return photoPath
    }

    /** Random pixels: the hardest case for JPEG. */
    private fun noise(width: Int, height: Int): Bitmap {
        val random = Random(42)
        val pixels = IntArray(width * height) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun noisyThumbnailIsFittedUnderTheCapAndDecodes() = runBlocking {
        val bytes = assertNotNullAndGet(source.thumbnailOf(store(noise(400, 300))))
        assertTrue("${bytes.size} bytes", bytes.size <= FeedLimits.THUMB_TARGET_BYTES)
        assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    @Test
    fun hugePhotoWithoutThumbnailIsDownscaledUnderTheCap() = runBlocking {
        val bytes = assertNotNullAndGet(source.thumbnailOf(store(noise(4000, 3000), asThumbnail = false)))
        assertTrue("${bytes.size} bytes", bytes.size <= FeedLimits.THUMB_MAX_BYTES)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue(maxOf(decoded.width, decoded.height) <= 400)
    }

    @Test
    fun missingOrInvalidPhotoGivesNoThumbnail() = runBlocking {
        assertNull(source.thumbnailOf("training_photos/missing-1.jpg"))
        assertNull(source.thumbnailOf("../etc/passwd"))
    }

    private fun assertNotNullAndGet(bytes: ByteArray?): ByteArray {
        assertNotNull(bytes)
        return bytes!!
    }
}
