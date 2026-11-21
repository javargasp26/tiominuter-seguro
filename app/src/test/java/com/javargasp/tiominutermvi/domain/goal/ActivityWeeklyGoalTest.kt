package com.javargasp.tiominutermvi.domain.goal

import com.javargasp.tiominutermvi.data.local.dao.TrainingLogDao
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoFiles
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Logged activities count toward the weekly goal through countsTowardGoal, with no goal
 * logic of their own: the real repository (fake DAO) feeding ObserveCurrentWeek.
 * Now: Monday 2026-10-26T10:00:00Z, device zone UTC.
 */
class ActivityWeeklyGoalTest {

    private val photoFiles = FakePhotoFiles()

    private val now = 1_793_008_800_000L
    private val dates = LocalDateProvider({ now }, { TimeZone.getTimeZone("UTC") })
    private val dao = FakeTrainingLogDao()
    private val trainingLog = TrainingLogRepositoryImpl(dao, { now }, dates, photoFiles)
    private val goals = MutableStateFlow(listOf(WeeklyGoal(2, "2026-10-19")))
    private val observeCurrentWeek = ObserveCurrentWeek(
        weeklyGoalRepository = object : WeeklyGoalRepository {
            override fun observeGoals(): Flow<List<WeeklyGoal>> = goals
            override suspend fun setGoal(workoutsPerWeek: Int) = Unit
        },
        trainingLogRepository = trainingLog,
        localDateProvider = dates
    )

    private fun activity(minutes: Int, completedAt: Long = now - HOUR) =
        ActivityEntryInput(ActivityType.RUNNING, "", minutes, Intensity.MODERATE, completedAt, "")

    @Test
    fun `an activity of at least 10 minutes counts, one under 10 does not`() = runTest {
        trainingLog.addActivityEntry(activity(minutes = 10))
        trainingLog.addActivityEntry(activity(minutes = 9))
        trainingLog.addActivityEntry(activity(minutes = 90))

        val week = observeCurrentWeek().first()

        assertEquals("2026-10-26", week.progress.weekStart)
        assertEquals(2, week.progress.workoutCount)
        assertEquals(2, week.progress.workoutsOn("2026-10-26"))
        assertTrue(week.progress.isGoalMet)
    }

    @Test
    fun `a backdated activity counts in the week of its day`() = runTest {
        // Yesterday, Sunday 25 October at 18:00: the previous week
        val sundayEvening = dates.epochMillisAt("2026-10-25", 18, 0)
        trainingLog.addActivityEntry(activity(minutes = 45, completedAt = sundayEvening))
        trainingLog.addActivityEntry(activity(minutes = 45, completedAt = sundayEvening - DAY))

        val current = observeCurrentWeek().first()
        assertEquals(0, current.progress.workoutCount)
        assertFalse(current.progress.isGoalMet)
        // Last week (goal 2) is met by the two backdated activities, so the streak is 1
        assertEquals(1, current.streak)

        val entries = trainingLog.observeEntries().first()
        assertEquals(listOf("2026-10-25", "2026-10-24"), entries.map { it.dateLocal })
        val lastWeek = WeeklyProgressCalculator.progress("2026-10-19", goals.value, countedWorkoutsPerDay(entries))
        assertEquals(2, lastWeek.workoutCount)
        assertTrue(lastWeek.isGoalMet)
    }

    private class FakeTrainingLogDao : TrainingLogDao {
        val rows = MutableStateFlow<List<TrainingLogEntity>>(emptyList())

        override suspend fun insert(entry: TrainingLogEntity): Long {
            val id = rows.value.size + 1L
            rows.value = rows.value + entry.copy(id = id)
            return id
        }

        override fun observeAll(): Flow<List<TrainingLogEntity>> =
            rows.map { list -> list.sortedWith(compareByDescending<TrainingLogEntity> { it.completedAt }.thenByDescending { it.id }) }

        override fun observeBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntity>> =
            observeAll().map { list -> list.filter { it.dateLocal in fromDate..toDate } }

        override suspend fun getById(id: Long): TrainingLogEntity? = rows.value.firstOrNull { it.id == id }
        override suspend fun getByUuid(uuid: String): TrainingLogEntity? = rows.value.firstOrNull { it.uuid == uuid }

        override suspend fun update(entry: TrainingLogEntity): Int = 0

        override suspend fun delete(id: Long) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun updatePhotoPath(id: Long, photoPath: String?, updatedAt: Long): Int = 0
        override suspend fun getPhotoPaths(): List<String> = emptyList()
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
    }

    /** Records the photos deleted through [TrainingPhotoFiles.deletePhoto]; nothing else is used. */
    private class FakePhotoFiles : TrainingPhotoFiles {
        val deletedPhotos = mutableListOf<String>()
        override fun fileOf(relativePath: String) = File(relativePath)
        override fun newStagedPhoto() = throw UnsupportedOperationException()
        override fun captureFile() = throw UnsupportedOperationException()
        override fun deleteCapture() = Unit
        override fun deletePhoto(photoPath: String) {
            deletedPhotos += photoPath
        }
        override fun storedFiles(): List<String> = emptyList()
        override fun deleteStoredFile(relativePath: String) = Unit
        override fun deleteStaleTemporaryFiles(olderThanMillis: Long) = Unit
    }
}
