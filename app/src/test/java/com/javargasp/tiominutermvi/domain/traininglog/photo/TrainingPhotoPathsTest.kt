package com.javargasp.tiominutermvi.domain.traininglog.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPhotoPathsTest {

    @Test
    fun `the photo is named after the entry uuid and the attach time`() {
        assertEquals("training_photos/abc-123.jpg", TrainingPhotoPaths.photoPath("abc", 123L))
    }

    @Test
    fun `the thumbnail is derived from the photo path`() {
        assertEquals("training_photos/abc-123_thumb.jpg", TrainingPhotoPaths.thumbnailPath("training_photos/abc-123.jpg"))
        assertEquals(
            listOf("training_photos/abc-123.jpg", "training_photos/abc-123_thumb.jpg"),
            TrainingPhotoPaths.filesOf("training_photos/abc-123.jpg")
        )
    }

    @Test
    fun `only plain jpg names inside the photo directory are valid`() {
        assertTrue(TrainingPhotoPaths.isValidPhotoPath(TrainingPhotoPaths.photoPath("4f1c-uuid", 1L)))

        listOf(
            "training_photos/../databases/tiominuter.db",
            "training_photos/sub/a.jpg",
            "other/a.jpg",
            "a.jpg",
            "training_photos/a.png",
            "training_photos/.jpg",
            "training_photos/a_thumb.jpg",
            "/data/user/0/app/files/training_photos/a.jpg"
        ).forEach { assertFalse(it, TrainingPhotoPaths.isValidPhotoPath(it)) }
    }

    @Test
    fun `orphans are the files no entry references, thumbnails included`() {
        val stored = listOf(
            "training_photos/a-1.jpg",
            "training_photos/a-1_thumb.jpg",
            "training_photos/b-2.jpg", // replaced long ago
            "training_photos/b-2_thumb.jpg",
            "training_photos/c-3.jpg.tmp", // an interrupted write
            "training_photos/d-4_thumb.jpg" // thumbnail whose photo is gone
        )

        val orphans = TrainingPhotoPaths.orphans(stored, referencedPhotoPaths = listOf("training_photos/a-1.jpg"))

        assertEquals(
            listOf(
                "training_photos/b-2.jpg",
                "training_photos/b-2_thumb.jpg",
                "training_photos/c-3.jpg.tmp",
                "training_photos/d-4_thumb.jpg"
            ),
            orphans
        )
    }

    @Test
    fun `nothing is an orphan when every file is referenced, and no files means no orphans`() {
        val referenced = listOf("training_photos/a-1.jpg", "training_photos/b-2.jpg")

        assertTrue(TrainingPhotoPaths.orphans(referenced.flatMap(TrainingPhotoPaths::filesOf), referenced).isEmpty())
        assertTrue(TrainingPhotoPaths.orphans(emptyList(), referenced).isEmpty())
    }
}
