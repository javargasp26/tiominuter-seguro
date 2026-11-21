package com.javargasp.tiominutermvi.data.traininglog

import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingLogMappersTest {

    private val entry = TrainingLogEntry(
        id = 5,
        uuid = "3f1c2a9e-0000-4000-8000-000000000005",
        dateLocal = "2026-10-19",
        completedAt = 1_792_404_000_000L,
        source = TrainingSource.PROGRAM,
        refId = "program:2:1:0",
        title = "Full body A",
        durationSeconds = 2_520,
        activityType = "run",
        intensity = "high",
        note = "Felt good",
        photoPath = "photos/5.jpg",
        createdAt = 1_792_404_000_000L,
        updatedAt = 1_792_404_100_000L
    )

    @Test
    fun `domain to entity stores the source by name and keeps every field`() {
        val entity = entry.toEntity()

        assertEquals(
            TrainingLogEntity(
                id = 5,
                uuid = "3f1c2a9e-0000-4000-8000-000000000005",
                dateLocal = "2026-10-19",
                completedAt = 1_792_404_000_000L,
                source = "PROGRAM",
                refId = "program:2:1:0",
                title = "Full body A",
                durationSeconds = 2_520,
                activityType = "run",
                intensity = "high",
                note = "Felt good",
                photoPath = "photos/5.jpg",
                createdAt = 1_792_404_000_000L,
                updatedAt = 1_792_404_100_000L
            ),
            entity
        )
    }

    @Test
    fun `entity to domain round trip for every source and null optionals`() {
        TrainingSource.entries.forEach { source ->
            val original = entry.copy(
                source = source,
                refId = null,
                durationSeconds = null,
                activityType = null,
                intensity = null,
                note = null,
                photoPath = null
            )
            assertEquals(original, original.toEntity().toDomainOrNull())
        }
    }

    @Test
    fun `an unknown stored source is skipped`() {
        assertNull(entry.toEntity().copy(source = "YOGA").toDomainOrNull())
        assertNull(entry.toEntity().copy(source = "gym").toDomainOrNull())
    }
}
