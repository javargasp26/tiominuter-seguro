package com.javargasp.tiominutermvi.data.traininglog.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.javargasp.tiominutermvi.domain.traininglog.photo.ExifTransform
import com.javargasp.tiominutermvi.domain.traininglog.photo.PhotoSizing
import com.javargasp.tiominutermvi.domain.traininglog.photo.PhotoSpec
import com.javargasp.tiominutermvi.domain.traininglog.photo.PixelSize
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoException
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * [TrainingPhotoProcessor] with BitmapFactory and AndroidX ExifInterface (reads the
 * orientation from a stream, JPEG / HEIF / WebP / PNG), on [ioDispatcher].
 *
 * 1. Bounds only (`inJustDecodeBounds`), then a decode with [PhotoSizing.sampleSize], so the
 *    full-resolution bitmap is never in memory.
 * 2. Exact scale to [PhotoSizing.PHOTO] and the EXIF orientation ([ExifTransform]).
 * 3. JPEG encode of the photo and of a [PhotoSizing.THUMBNAIL] made from it. Bitmap.compress
 *    writes a bare JFIF file: no EXIF block, so no location, camera or date metadata.
 *
 * Files are written to a ".tmp" sibling and renamed, so a failure never leaves a half file.
 */
class AndroidTrainingPhotoProcessor(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TrainingPhotoProcessor {

    override suspend fun process(sourceUri: String, photoFile: File, thumbnailFile: File) =
        withContext(ioDispatcher) {
            val uri = sourceUri.toUri()
            try {
                val bounds = readBounds(uri) ?: throw unreadable()
                val transform = ExifTransform.fromExifOrientation(readOrientation(uri))
                val decoded = decode(uri, PhotoSizing.sampleSize(bounds, PhotoSizing.PHOTO.maxLongSide))
                    ?: throw unreadable()
                val photo = resizeAndOrient(decoded, PhotoSizing.PHOTO, transform)
                if (photo !== decoded) decoded.recycle()
                try {
                    writeJpeg(photo, PhotoSizing.PHOTO.jpegQuality, photoFile)
                    val thumbnail = resizeAndOrient(photo, PhotoSizing.THUMBNAIL, ExifTransform.NONE)
                    try {
                        writeJpeg(thumbnail, PhotoSizing.THUMBNAIL.jpegQuality, thumbnailFile)
                    } finally {
                        if (thumbnail !== photo) thumbnail.recycle()
                    }
                } finally {
                    photo.recycle()
                }
            } catch (e: CancellationException) {
                photoFile.delete()
                thumbnailFile.delete()
                throw e
            } catch (e: Throwable) {
                // Throwable: an OutOfMemoryError on a huge image must not crash the workout
                photoFile.delete()
                thumbnailFile.delete()
                throw when (e) {
                    is TrainingPhotoException -> e
                    is IOException -> TrainingPhotoException(
                        if (e.isNoSpace()) TrainingPhotoError.NO_SPACE else TrainingPhotoError.FAILED, e
                    )
                    else -> TrainingPhotoException(TrainingPhotoError.FAILED, e)
                }
            }
        }

    private fun readBounds(uri: Uri): PixelSize? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return PixelSize(options.outWidth, options.outHeight)
    }

    /** EXIF orientation, or "normal" when the image has none (PNG...) or it can't be read. */
    private fun readOrientation(uri: Uri): Int =
        try {
            open(uri).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        } catch (e: RuntimeException) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun decode(uri: Uri, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return open(uri).use { BitmapFactory.decodeStream(it, null, options) }
    }

    /**
     * Scales [source] to the [spec] size (never up) and applies [transform]. Returns
     * [source] itself when nothing changes; [source] is never recycled here (the caller
     * owns it), intermediate bitmaps are.
     */
    private fun resizeAndOrient(source: Bitmap, spec: PhotoSpec, transform: ExifTransform): Bitmap {
        val target = PhotoSizing.targetSize(PixelSize(source.width, source.height), spec.maxLongSide)
        val scaled = if (target.width == source.width && target.height == source.height) {
            source
        } else {
            source.scale(target.width, target.height)
        }
        if (transform.isIdentity) return scaled
        val matrix = Matrix().apply {
            if (transform.flipHorizontally) postScale(-1f, 1f)
            postRotate(transform.rotationDegrees.toFloat())
        }
        val oriented = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
        if (oriented !== scaled && scaled !== source) scaled.recycle()
        return oriented
    }

    private fun writeJpeg(bitmap: Bitmap, quality: Int, target: File) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            val written = FileOutputStream(temporary).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            if (!written) throw TrainingPhotoException(TrainingPhotoError.FAILED)
            if (!temporary.renameTo(target)) throw IOException("Could not rename $temporary")
        } finally {
            temporary.delete()
        }
    }

    private fun open(uri: Uri) = contentResolver.openInputStream(uri) ?: throw unreadable()

    private fun unreadable() = TrainingPhotoException(TrainingPhotoError.UNREADABLE_IMAGE)
}
