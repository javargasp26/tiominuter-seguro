package com.javargasp.tiominutermvi.domain.traininglog.photo

/**
 * Where training photos live, relative to the app's private files dir. Pure naming rules.
 *
 * - Photo: `training_photos/<entry uuid>-<version>.jpg`, stored in `training_log.photoPath`.
 * - Thumbnail: the same name with `_thumb` before the extension; derived, never stored.
 *
 * `version` is the time the photo was attached: a replacement gets new file names, so it is
 * written completely before the old files are deleted (a failed replace keeps the old photo)
 * and image caches never show an old picture under a reused name.
 */
object TrainingPhotoPaths {

    const val DIRECTORY = "training_photos"
    private const val EXTENSION = ".jpg"
    private const val THUMBNAIL_SUFFIX = "_thumb"

    /** The relative photo path for the entry [uuid], attached at [version] (epoch millis). */
    fun photoPath(uuid: String, version: Long): String = "$DIRECTORY/$uuid-$version$EXTENSION"

    /** The thumbnail of [photoPath]: "training_photos/a-1.jpg" → "training_photos/a-1_thumb.jpg". */
    fun thumbnailPath(photoPath: String): String =
        photoPath.removeSuffix(EXTENSION) + THUMBNAIL_SUFFIX + EXTENSION

    /** Every file of one photo: the photo and its thumbnail. */
    fun filesOf(photoPath: String): List<String> = listOf(photoPath, thumbnailPath(photoPath))

    /**
     * True for a path this feature writes: a .jpg directly inside [DIRECTORY], no "..".
     * Paths read back from the database are checked before touching a file.
     */
    fun isValidPhotoPath(path: String): Boolean {
        if (!path.startsWith("$DIRECTORY/") || !path.endsWith(EXTENSION)) return false
        val name = path.removePrefix("$DIRECTORY/")
        return name.removeSuffix(EXTENSION).isNotEmpty() && '/' !in name && '\\' !in name && ".." !in name &&
            !name.endsWith(THUMBNAIL_SUFFIX + EXTENSION)
    }

    /**
     * The files of [storedFiles] (relative paths inside [DIRECTORY]) that no entry references:
     * neither one of [referencedPhotoPaths] nor its thumbnail. Unfinished writes and files of
     * deleted entries end up here.
     */
    fun orphans(storedFiles: Collection<String>, referencedPhotoPaths: Collection<String>): List<String> {
        val referenced = referencedPhotoPaths.flatMap(::filesOf).toSet()
        return storedFiles.filter { it !in referenced }.sorted()
    }
}
