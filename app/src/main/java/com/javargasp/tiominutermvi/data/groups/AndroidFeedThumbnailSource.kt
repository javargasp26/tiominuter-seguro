package com.javargasp.tiominutermvi.data.groups

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.javargasp.tiominutermvi.domain.groups.FeedLimits
import com.javargasp.tiominutermvi.domain.groups.FeedThumbnailSource
import com.javargasp.tiominutermvi.domain.groups.ThumbnailFitter
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoFiles
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The feed thumbnail of a local photo: its stored 400 px thumbnail (EXIF already stripped by the
 * photo processor), decoded and re-encoded as JPEG by [ThumbnailFitter] under 60 KB. `compress`
 * writes no EXIF. Null when the photo is invalid, missing or can't be decoded.
 */
class AndroidFeedThumbnailSource(private val files: TrainingPhotoFiles) : FeedThumbnailSource {

    override suspend fun thumbnailOf(photoPath: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!TrainingPhotoPaths.isValidPhotoPath(photoPath)) return@withContext null
        val thumbFile = files.fileOf(TrainingPhotoPaths.thumbnailPath(photoPath))
        val source = thumbFile.takeIf { it.exists() } ?: files.fileOf(photoPath).takeIf { it.exists() } ?: return@withContext null
        val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sampleSizeOf(source.path) })
            ?: return@withContext null
        try {
            val longest = max(bitmap.width, bitmap.height)
            ThumbnailFitter.fit(longest) { side, quality -> encode(bitmap, longest, side, quality) }
                ?.takeIf { it.size <= FeedLimits.THUMB_MAX_BYTES }
        } finally {
            bitmap.recycle()
        }
    }

    /** A power of two that keeps the longest side ≥ the fitter's largest size (never the full photo in memory). */
    private fun sampleSizeOf(path: String): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= ThumbnailFitter.MAX_SIDE) sample *= 2
        return sample
    }

    private fun encode(bitmap: Bitmap, longest: Int, side: Int, quality: Int): ByteArray? {
        val scaled = if (side >= longest) {
            bitmap
        } else {
            val ratio = side.toFloat() / longest
            bitmap.scale(
                (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
            )
        }
        return try {
            ByteArrayOutputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) null else out.toByteArray()
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }
}
