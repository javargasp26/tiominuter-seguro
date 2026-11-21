package com.javargasp.tiominutermvi.domain.goal

import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class ObserveCurrentWeekTest {

    // Thursday 2026-10-22T10:00:00Z
    private var now = 1_792_663_200_000L
    private val goals = MutableStateFlow(listOf(WeeklyGoal(3, "2026-10-19")))
    private val entries = MutableStateFlow(listOf(entry(1, "2026-10-19", 1_800)))
    private val observe = ObserveCurrentWeek(
        weeklyGoalRepository = object : WeeklyGoalRepository {
            override fun observeGoals(): Flow<List<WeeklyGoal>> = goals
            override suspend fun setGoal(workoutsPerWeek: Int) = Unit
        },
        trainingLogRepository = object : TrainingLogRepository {
            override fun observeEntries(): Flow<List<TrainingLogEntry>> = entries
            override fun observeEntriesBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntry>> =
                entries.map { list -> list.filter { it.dateLocal in fromDate..toDate } }
            override suspend fun deleteEntry(id: Long) = Unit
            override suspend fun getEntry(id: Long): TrainingLogEntry? = null
            override suspend fun addActivityEntry(input: ActivityEntryInput): Long = throw UnsupportedOperationException()
            override suspend fun updateActivityEntry(id: Long, input: ActivityEntryInput) = throw UnsupportedOperationException()
            override suspend fun setPhotoPath(id: Long, photoPath: String?): String? = throw UnsupportedOperationException()
            override suspend fun getPhotoPaths(): List<String> = emptyList()
        },
        localDateProvider = LocalDateProvider({ now }, { TimeZone.getTimeZone("UTC") })
    )

    @Test
    fun `combines goals and counted workouts into the current week`() = runTest {
        val summary = observe().first()

        assertEquals("2026-10-22", summary.today)
        assertEquals("2026-10-19", summary.progress.weekStart)
        assertEquals(3, summary.progress.goal)
        assertEquals(1, summary.progress.workoutCount)
        assertEquals(2, summary.workoutsNeeded)
    }

    @Test
    fun `only entries that count toward the goal are counted`() = runTest {
        entries.value = listOf(
            entry(1, "2026-10-22", 3_600),
            entry(2, "2026-10-22", null),
            entry(3, "2026-10-22", MIN_COUNTED_DURATION_SECONDS - 1)
        )
        goals.value = listOf(WeeklyGoal(2, "2026-10-19"))

        val summary = observe().first()

        assertEquals(2, summary.progress.workoutCount)
        assertEquals(2, summary.progress.workoutsOn("2026-10-22"))
        assertEquals(1, summary.streak)
    }

    @Test
    fun `today is read on every collection`() = runTest {
        now += 4 * 24 * 60 * 60 * 1000L // Monday 26

        val summary = observe().first()

        assertEquals("2026-10-26", summary.progress.weekStart)
        assertEquals(0, summary.progress.workoutCount)
    }

    private fun entry(id: Long, date: String, durationSeconds: Int?) = TrainingLogEntry(
        id = id, uuid = "uuid-$id", dateLocal = date, completedAt = id, source = TrainingSource.GYM,
        refId = null, title = "Workout $id", durationSeconds = durationSeconds, createdAt = id, updatedAt = id
    )
}
