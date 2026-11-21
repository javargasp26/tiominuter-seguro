package com.javargasp.tiominutermvi.domain.traininglog

import com.javargasp.tiominutermvi.data.traininglog.toDomainOrNull
import com.javargasp.tiominutermvi.data.traininglog.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TrainingSource.FREE was renamed to ACTIVITY ("free" reads as "free of charge"). No FREE
 * entry was ever written; this pins that nothing refers to the old name any more.
 * Reads src/main by relative path (Gradle runs unit tests from the module directory).
 */
class TrainingSourceRenameTest {

    @Test
    fun `the sources are DAILY, GYM, PROGRAM and ACTIVITY`() {
        assertEquals(listOf("DAILY", "GYM", "PROGRAM", "ACTIVITY"), TrainingSource.entries.map { it.name })
    }

    @Test
    fun `ACTIVITY is stored by name and an old FREE row would be skipped, not crash`() {
        val entry = TrainingLogEntry(
            id = 1, uuid = "u", dateLocal = "2026-10-22", completedAt = 0, source = TrainingSource.ACTIVITY,
            refId = null, title = "", durationSeconds = 600, createdAt = 0, updatedAt = 0
        )
        assertEquals("ACTIVITY", entry.toEntity().source)
        assertNull(entry.toEntity().copy(source = "FREE").toDomainOrNull())
    }

    @Test
    fun `no source file or string resource refers to FREE`() {
        val main = File("src/main")
        assertTrue("run from the app module", main.isDirectory)
        val offenders = main.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filter { file ->
                val text = file.readText()
                "TrainingSource.FREE" in text || "training_source_free" in text || Regex("\\bFREE\\b").containsMatchIn(text)
            }
            .map { it.path }
            .toList()

        assertEquals(emptyList<String>(), offenders)
    }
}
