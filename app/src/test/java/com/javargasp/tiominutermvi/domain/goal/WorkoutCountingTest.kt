package com.javargasp.tiominutermvi.domain.goal

import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCountingTest {

    private fun entry(id: Long, date: String, durationSeconds: Int?) = TrainingLogEntry(
        id = id,
        uuid = "uuid-$id",
        dateLocal = date,
        completedAt = id,
        source = TrainingSource.GYM,
        refId = null,
        title = "Workout $id",
        durationSeconds = durationSeconds,
        createdAt = id,
        updatedAt = id
    )

    @Test
    fun `the minimum is 10 minutes`() {
        assertEquals(600, MIN_COUNTED_DURATION_SECONDS)
    }

    @Test
    fun `an entry counts from exactly the minimum`() {
        assertFalse(countsTowardGoal(entry(1, "2026-10-19", MIN_COUNTED_DURATION_SECONDS - 1)))
        assertTrue(countsTowardGoal(entry(2, "2026-10-19", MIN_COUNTED_DURATION_SECONDS)))
        assertTrue(countsTowardGoal(entry(3, "2026-10-19", MIN_COUNTED_DURATION_SECONDS + 1)))
        assertFalse(countsTowardGoal(entry(4, "2026-10-19", 0)))
    }

    @Test
    fun `an unknown duration counts`() {
        assertTrue(countsTowardGoal(entry(1, "2026-10-19", null)))
    }

    @Test
    fun `workouts per day count every counted entry, also several in one day`() {
        val entries = listOf(
            entry(1, "2026-10-19", 3_600), // gym in the morning
            entry(2, "2026-10-19", null), // basketball in the evening
            entry(3, "2026-10-19", 300), // too short
            entry(4, "2026-10-21", 600),
            entry(5, "2026-10-22", 599) // too short: the day is absent
        )

        assertEquals(mapOf("2026-10-19" to 2, "2026-10-21" to 1), countedWorkoutsPerDay(entries))
    }
}
