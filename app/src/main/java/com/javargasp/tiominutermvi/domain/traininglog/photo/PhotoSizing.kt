package com.javargasp.tiominutermvi.domain.traininglog.photo

import kotlin.math.max
import kotlin.math.roundToInt

/** Width and height in pixels. */
data class PixelSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Invalid size ${width}x$height" }
    }

    val longSide: Int get() = max(width, height)
}

/** One output of the processor: the longest side in pixels and the JPEG quality (0-100). */
data class PhotoSpec(val maxLongSide: Int, val jpegQuality: Int)

/**
 * Size rules of training photos. Pure, so the arithmetic is unit-tested without Android.
 */
object PhotoSizing {

    /** The stored photo: longest side 1080 px, JPEG quality 70. */
    val PHOTO = PhotoSpec(maxLongSide = 1080, jpegQuality = 70)

    /** The thumbnail shown in lists: longest side 400 px, JPEG quality 60 (about 40 KB). */
    val THUMBNAIL = PhotoSpec(maxLongSide = 400, jpegQuality = 60)

    /**
     * The size to encode [source] at: the longest side becomes [maxLongSide] and the aspect
     * ratio is kept (rounded, at least 1 px). A source already within the limit keeps its
     * size: small images are never upscaled.
     */
    fun targetSize(source: PixelSize, maxLongSide: Int): PixelSize {
        require(maxLongSide > 0) { "maxLongSide $maxLongSide" }
        if (source.longSide <= maxLongSide) return source
        val scale = maxLongSide.toDouble() / source.longSide
        return if (source.width >= source.height) {
            PixelSize(maxLongSide, (source.height * scale).roundToInt().coerceAtLeast(1))
        } else {
            PixelSize((source.width * scale).roundToInt().coerceAtLeast(1), maxLongSide)
        }
    }

    /**
     * The decoder's `inSampleSize`: the largest power of two that still decodes the image
     * with its longest side at or above [maxLongSide], so the full-resolution bitmap is
     * never loaded and the final scale-down keeps its quality. 1 for small images.
     */
    fun sampleSize(source: PixelSize, maxLongSide: Int): Int {
        require(maxLongSide > 0) { "maxLongSide $maxLongSide" }
        var sample = 1
        while (source.longSide / (sample * 2) >= maxLongSide) sample *= 2
        return sample
    }
}

/**
 * How to turn a decoded image upright: rotate clockwise by [rotationDegrees], then mirror
 * horizontally when [flipHorizontally].
 */
data class ExifTransform(val rotationDegrees: Int, val flipHorizontally: Boolean) {

    /** 90 and 270 degree rotations swap width and height. */
    fun apply(size: PixelSize): PixelSize =
        if (rotationDegrees % 180 == 0) size else PixelSize(size.height, size.width)

    val isIdentity: Boolean get() = rotationDegrees == 0 && !flipHorizontally

    companion object {
        val NONE = ExifTransform(0, false)

        /**
         * The transform for an EXIF `Orientation` value (1-8, as in ExifInterface):
         * 1 normal, 2 mirrored, 3 rotated 180, 4 mirrored vertically (180 + mirror),
         * 5 transposed (90 + mirror), 6 rotated 90, 7 transversed (270 + mirror), 8 rotated 270.
         * Unknown values (0 "undefined" included) need no transform.
         */
        fun fromExifOrientation(orientation: Int): ExifTransform = when (orientation) {
            2 -> ExifTransform(0, true)
            3 -> ExifTransform(180, false)
            4 -> ExifTransform(180, true)
            5 -> ExifTransform(90, true)
            6 -> ExifTransform(90, false)
            7 -> ExifTransform(270, true)
            8 -> ExifTransform(270, false)
            else -> NONE
        }
    }
}
