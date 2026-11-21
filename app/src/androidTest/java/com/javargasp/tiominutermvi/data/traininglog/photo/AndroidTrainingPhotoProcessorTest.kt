package com.javargasp.tiominutermvi.data.traininglog.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Real processing on the device: downsampling, EXIF orientation, output sizes and the
 * removal of every EXIF field (GPS location included).
 */
@RunWith(AndroidJUnit4::class)
class AndroidTrainingPhotoProcessorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private val processor by lazy { AndroidTrainingPhotoProcessor(context.contentResolver) }

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "processor-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** A camera-like JPEG of [width]x[height] with GPS, camera and orientation EXIF tags. */
    private fun cameraJpeg(width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL): File {
        val file = File(dir, "source-${width}x$height.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Left half red, right half blue: lets the test see the rotation
        Canvas(bitmap).apply {
            drawColor(Color.BLUE)
            drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
        }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "40/1,25/1,1234/100")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "3/1,42/1,1234/100")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
            setAttribute(ExifInterface.TAG_MAKE, "TestCam")
            setAttribute(ExifInterface.TAG_MODEL, "Model X")
            setAttribute(ExifInterface.TAG_DATETIME, "2026:10:19 07:30:00")
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    @Suppress("DEPRECATION") // getLatLong(FloatArray) is the variant available on every API level
    private fun hasLocation(exif: ExifInterface): Boolean = exif.getLatLong(FloatArray(2))

    private fun isMostly(pixel: Int, color: Int): Boolean =
        Math.abs(Color.red(pixel) - Color.red(color)) < 40 &&
            Math.abs(Color.green(pixel) - Color.green(color)) < 40 &&
            Math.abs(Color.blue(pixel) - Color.blue(color)) < 40

    private fun boundsOf(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    /** True when the JPEG has no APP1 "Exif" block at all. */
    private fun hasNoExifBlock(file: File): Boolean {
        val bytes = file.readBytes()
        val marker = "Exif".toByteArray() + byteArrayOf(0, 0)
        return bytes.toList().windowed(marker.size).none { it == marker.toList() }
    }

    private fun assertNoMetadata(file: File) {
        val exif = ExifInterface(file.absolutePath)
        assertFalse("location", hasLocation(exif))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MODEL))
        assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME))
        assertTrue("an EXIF block is present in ${file.name}", hasNoExifBlock(file))
    }

    @Test
    fun largePhotoIsDownsizedAndLosesEveryExifField() = runBlocking {
        val source = cameraJpeg(3000, 2000)
        assertTrue("the test image must carry a location", hasLocation(ExifInterface(source.absolutePath)))
        val photo = File(dir, "out/photo.jpg")
        val thumbnail = File(dir, "out/photo_thumb.jpg")

        processor.process(Uri.fromFile(source).toString(), photo, thumbnail)

        assertEquals(1080 to 720, boundsOf(photo))
        assertEquals(400 to 267, boundsOf(thumbnail))
        assertNoMetadata(photo)
        assertNoMetadata(thumbnail)
        assertTrue("thumbnail ${thumbnail.length()} bytes", thumbnail.length() < 100 * 1024)
        assertTrue(File(dir, "out").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun exifOrientationIsAppliedToThePixels() = runBlocking {
        val source = cameraJpeg(3000, 2000, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val photo = File(dir, "out/rotated.jpg")
        val thumbnail = File(dir, "out/rotated_thumb.jpg")

        processor.process(Uri.fromFile(source).toString(), photo, thumbnail)

        // Upright: portrait, and no orientation tag left to rotate it again
        assertEquals(720 to 1080, boundsOf(photo))
        // Rotated 90 degrees clockwise: the red left half is now on top
        val pixels = BitmapFactory.decodeFile(photo.absolutePath)
        assertTrue("top should be red", isMostly(pixels.getPixel(360, 100), Color.RED))
        assertTrue("bottom should be blue", isMostly(pixels.getPixel(360, 980), Color.BLUE))
        pixels.recycle()
        assertEquals(267 to 400, boundsOf(thumbnail))
        val orientation = ExifInterface(photo.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        assertTrue("orientation $orientation", orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL)
        assertNoMetadata(photo)
    }

    @Test
    fun smallPhotoIsNotUpscaled() = runBlocking {
        val source = cameraJpeg(600, 400)
        val photo = File(dir, "out/small.jpg")
        val thumbnail = File(dir, "out/small_thumb.jpg")

        processor.process(Uri.fromFile(source).toString(), photo, thumbnail)

        assertEquals(600 to 400, boundsOf(photo))
        assertEquals(400 to 267, boundsOf(thumbnail))
        assertNoMetadata(photo)
    }

    @Test
    fun aFileThatIsNotAnImageIsUnreadableAndLeavesNothing() = runBlocking {
        val source = File(dir, "notes.txt").apply { writeText("not an image") }
        val photo = File(dir, "out/bad.jpg")
        val thumbnail = File(dir, "out/bad_thumb.jpg")

        try {
            processor.process(Uri.fromFile(source).toString(), photo, thumbnail)
            fail("expected UNREADABLE_IMAGE")
        } catch (e: TrainingPhotoException) {
            assertEquals(TrainingPhotoError.UNREADABLE_IMAGE, e.error)
        }
        assertFalse(photo.exists())
        assertFalse(thumbnail.exists())
    }
}
