package com.javargasp.tiominutermvi.domain.traininglog.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSizingTest {

    @Test
    fun `the specs are 1080 px at quality 70 and 400 px at quality 60`() {
        assertEquals(PhotoSpec(1080, 70), PhotoSizing.PHOTO)
        assertEquals(PhotoSpec(400, 60), PhotoSizing.THUMBNAIL)
    }

    @Test
    fun `the longest side becomes the limit and the aspect ratio is kept`() {
        assertEquals(PixelSize(1080, 810), PhotoSizing.targetSize(PixelSize(4000, 3000), 1080))
        assertEquals(PixelSize(810, 1080), PhotoSizing.targetSize(PixelSize(3000, 4000), 1080))
        assertEquals(PixelSize(1080, 1080), PhotoSizing.targetSize(PixelSize(2048, 2048), 1080))
        assertEquals(PixelSize(400, 225), PhotoSizing.targetSize(PixelSize(1920, 1080), 400))
    }

    @Test
    fun `small images are never upscaled`() {
        assertEquals(PixelSize(640, 480), PhotoSizing.targetSize(PixelSize(640, 480), 1080))
        assertEquals(PixelSize(1080, 720), PhotoSizing.targetSize(PixelSize(1080, 720), 1080))
        assertEquals(PixelSize(300, 400), PhotoSizing.targetSize(PixelSize(300, 400), 400))
    }

    @Test
    fun `a very thin image keeps at least one pixel`() {
        assertEquals(PixelSize(1080, 1), PhotoSizing.targetSize(PixelSize(10_000, 2), 1080))
    }

    @Test
    fun `the sample size is the largest power of two that keeps the limit`() {
        assertEquals(2, PhotoSizing.sampleSize(PixelSize(4000, 3000), 1080)) // 2000 >= 1080, 1000 < 1080
        assertEquals(4, PhotoSizing.sampleSize(PixelSize(8000, 6000), 1080)) // 2000
        assertEquals(2, PhotoSizing.sampleSize(PixelSize(2160, 1000), 1080)) // exactly 1080
        assertEquals(1, PhotoSizing.sampleSize(PixelSize(2159, 1000), 1080))
        assertEquals(1, PhotoSizing.sampleSize(PixelSize(640, 480), 1080))
    }

    @Test
    fun `the sampled image never drops below the limit before the final scale`() {
        listOf(PixelSize(4032, 3024), PixelSize(12_000, 9_000), PixelSize(1200, 5000)).forEach { size ->
            val sample = PhotoSizing.sampleSize(size, PhotoSizing.PHOTO.maxLongSide)
            assertTrue("$size / $sample", size.longSide / sample >= PhotoSizing.PHOTO.maxLongSide)
        }
    }

    @Test
    fun `exif orientations map to rotation and mirroring`() {
        val expected = mapOf(
            1 to ExifTransform(0, false),
            2 to ExifTransform(0, true),
            3 to ExifTransform(180, false),
            4 to ExifTransform(180, true),
            5 to ExifTransform(90, true),
            6 to ExifTransform(90, false),
            7 to ExifTransform(270, true),
            8 to ExifTransform(270, false),
            0 to ExifTransform.NONE,
            42 to ExifTransform.NONE
        )

        expected.forEach { (orientation, transform) ->
            assertEquals("orientation $orientation", transform, ExifTransform.fromExifOrientation(orientation))
        }
    }

    @Test
    fun `quarter turns swap width and height`() {
        val size = PixelSize(4000, 3000)

        assertEquals(PixelSize(3000, 4000), ExifTransform.fromExifOrientation(6).apply(size))
        assertEquals(PixelSize(3000, 4000), ExifTransform.fromExifOrientation(8).apply(size))
        assertEquals(size, ExifTransform.fromExifOrientation(3).apply(size))
        assertEquals(size, ExifTransform.fromExifOrientation(2).apply(size))
    }
}
