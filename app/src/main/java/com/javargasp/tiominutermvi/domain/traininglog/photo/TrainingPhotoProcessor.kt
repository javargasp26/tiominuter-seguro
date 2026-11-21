package com.javargasp.tiominutermvi.domain.traininglog.photo

import java.io.File

/**
 * Turns a picked image into the two stored JPEGs. The implementation decodes with
 * downsampling (never the full-resolution bitmap), applies the EXIF orientation and
 * re-encodes [PhotoSizing.PHOTO] into [photoFile] and [PhotoSizing.THUMBNAIL] into
 * [thumbnailFile]. Re-encoding drops every EXIF / metadata field, location included.
 *
 * Throws [TrainingPhotoException] (UNREADABLE_IMAGE, NO_SPACE, FAILED); on failure no
 * output file is left behind.
 */
interface TrainingPhotoProcessor {
    suspend fun process(sourceUri: String, photoFile: File, thumbnailFile: File)
}
