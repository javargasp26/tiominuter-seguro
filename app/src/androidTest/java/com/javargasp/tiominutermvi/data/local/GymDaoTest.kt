package com.javargasp.tiominutermvi.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.javargasp.tiominutermvi.data.local.entity.ExerciseEntity
import com.javargasp.tiominutermvi.data.local.entity.GymExerciseProgressEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineExerciseEntity
import com.javargasp.tiominutermvi.data.local.entity.RepsListConverter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * In-memory Room tests for the Gym Routines DAOs.
 */
class GymDaoTest {

    private lateinit var database: TioMinuterDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TioMinuterDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun routine(id: Int, name: String = "Routine $id") = GymRoutineEntity(id, name)

    private fun exercise(
        routineId: Int,
        position: Int,
        exerciseId: Int = 100 + routineId,
        type: String = "REPS",
        reps: List<Int> = listOf(10, 12),
        restSeconds: Int = 60
    ) = GymRoutineExerciseEntity(
        routineId = routineId,
        position = position,
        exerciseId = exerciseId,
        type = type,
        reps = reps,
        restSeconds = restSeconds
    )

    // ------------------------------------------------------------------
    // replaceCatalog transaction
    // ------------------------------------------------------------------

    @Test
    fun `replaceCatalogInsertsNewRoutinesWithExercises`() = runTest {
        val dao = database.gymRoutineDao()

        dao.replaceCatalog(
            routines = listOf(routine(1), routine(2)),
            exercisesByRoutine = mapOf(
                1 to listOf(exercise(1, 0), exercise(1, 1)),
                2 to listOf(exercise(2, 0))
            )
        )

        val routines = dao.observeRoutinesWithExercises().first()
        assertEquals(2, routines.size)
        val routine1 = routines.first { it.routine.routineId == 1 }
        assertEquals(2, routine1.exercises.size)
    }

    @Test
    fun `replaceCatalogUpdatesExistingRoutineAndKeepsProgress`() = runTest {
        val dao = database.gymRoutineDao()
        val progressDao = database.gymProgressDao()

        dao.replaceCatalog(listOf(routine(1, "Old Name")), emptyMap())
        progressDao.markCompleted(GymExerciseProgressEntity(routineId = 1, position = 0, completedAt = 100L))

        dao.replaceCatalog(
            routines = listOf(routine(1, "New Name")),
            exercisesByRoutine = mapOf(1 to listOf(exercise(1, 0)))
        )

        val routine = dao.observeRoutineWithExercises(1).first()
        assertEquals("New Name", routine?.routine?.routineName)
        assertEquals(1, routine?.exercises?.size)
        // Progress survives the refresh because the routine row was upserted, not reinserted.
        val progress = database.gymProgressDao().observeProgress(1).first()
        assertEquals(1, progress.size)
        assertEquals(100L, progress.first().completedAt)
    }

    @Test
    fun `replaceCatalogRemovesDisappearedRoutinesAndCascadesProgress`() = runTest {
        val dao = database.gymRoutineDao()
        val progressDao = database.gymProgressDao()

        dao.replaceCatalog(
            routines = listOf(routine(1), routine(2)),
            exercisesByRoutine = mapOf(
                1 to listOf(exercise(1, 0)),
                2 to listOf(exercise(2, 0))
            )
        )
        progressDao.markCompleted(GymExerciseProgressEntity(1, 0, completedAt = 1L))
        progressDao.markCompleted(GymExerciseProgressEntity(2, 0, completedAt = 2L))

        // Routine 2 disappears from the catalog.
        dao.replaceCatalog(
            routines = listOf(routine(1)),
            exercisesByRoutine = mapOf(1 to listOf(exercise(1, 0)))
        )

        val remainingIds = dao.observeRoutinesWithExercises().first().map { it.routine.routineId }
        assertTrue(remainingIds.contains(1))
        assertFalse(remainingIds.contains(2))
        // Progress for the removed routine is cascaded away.
        assertTrue(progressDao.observeProgress(2).first().isEmpty())
        // Progress for the surviving routine is kept.
        assertTrue(progressDao.observeProgress(1).first().isNotEmpty())
    }

    @Test
    fun `replaceCatalogReplacesExerciseSlotsForSurvivingRoutines`() = runTest {
        val dao = database.gymRoutineDao()

        dao.replaceCatalog(
            routines = listOf(routine(1)),
            exercisesByRoutine = mapOf(1 to listOf(exercise(1, 0, exerciseId = 100)))
        )
        dao.replaceCatalog(
            routines = listOf(routine(1)),
            exercisesByRoutine = mapOf(1 to listOf(exercise(1, 0, exerciseId = 200), exercise(1, 1, exerciseId = 101)))
        )

        val routine = dao.observeRoutineWithExercises(1).first()
        assertEquals(2, routine?.exercises?.size)
        assertTrue(routine?.exercises?.any { it.exerciseId == 200 } == true)
        assertTrue(routine?.exercises?.none { it.exerciseId == 100 } == true)
    }

    // ------------------------------------------------------------------
    // Reps TypeConverter round-trip
    // ------------------------------------------------------------------

    @Test
    fun `repsConverterRoundTripsListOfDurations`() {
        val converter = RepsListConverter()
        val reps = listOf(30, 45, 60)

        val encoded = converter.fromRepsList(reps)
        assertEquals("30,45,60", encoded)
        assertEquals(reps, converter.toRepsList(encoded))
    }

    @Test
    fun `repsConverterRoundTripsThroughTheDatabase`() = runTest {
        val dao = database.gymRoutineDao()

        val reps = listOf(30, 45, 60)
        dao.replaceCatalog(
            routines = listOf(routine(1)),
            exercisesByRoutine = mapOf(1 to listOf(exercise(1, 0, type = "TIME", reps = reps)))
        )

        val stored = dao.observeRoutineWithExercises(1).first()?.exercises?.first()
        assertEquals(reps, stored?.reps)
        assertEquals(3, stored?.reps?.size) // sets = reps.size
    }

    // ------------------------------------------------------------------
    // Progress DAO
    // ------------------------------------------------------------------

    @Test
    fun `progressMarkObserveClear`() = runTest {
        val progressDao = database.gymProgressDao()

        // Create the parent routine first: progress has a FK to gym_routines.
        database.gymRoutineDao().replaceCatalog(
            routines = listOf(routine(1)),
            exercisesByRoutine = mapOf(1 to listOf(exercise(1, 0)))
        )

        // Observe on an empty table.
        assertTrue(progressDao.observeProgress(1).first().isEmpty())

        // Mark two slots completed.
        progressDao.markCompleted(GymExerciseProgressEntity(1, 0, completedAt = 1_000L))
        progressDao.markCompleted(GymExerciseProgressEntity(1, 1, completedAt = 2_000L))
        val progress = progressDao.observeProgress(1).first()
        assertEquals(2, progress.size)
        assertEquals(2_000L, progress.first { it.position == 1 }.completedAt)

        // Re-marking the same slot replaces the row (REPLACE strategy).
        progressDao.markCompleted(GymExerciseProgressEntity(1, 0, completedAt = 10L))
        val afterUpdate = progressDao.observeProgress(1).first()
        assertEquals(2, afterUpdate.size)
        assertEquals(10L, afterUpdate.first { it.position == 0 }.completedAt)

        // Clearing removes all markers for the routine.
        progressDao.clearRoutine(1)
        assertTrue(progressDao.observeProgress(1).first().isEmpty())
    }

    // ------------------------------------------------------------------
    // ExerciseDao
    // ------------------------------------------------------------------

    @Test
    fun `exerciseDaoUpsertsObservesAndQueriesByIds`() = runTest {
        val exerciseDao = database.exerciseDao()

        exerciseDao.upsertAll(
            listOf(
                ExerciseEntity(1, "Push-ups", "1_push_ups.gif"),
                ExerciseEntity(2, "Squats", "2_squats.gif")
            )
        )
        assertEquals(2, exerciseDao.observeAll().first().size)

        // Upsert again with a changed name: updates, does not duplicate.
        exerciseDao.upsertAll(listOf(ExerciseEntity(1, "Push-ups", "1_push_ups.gif")))
        val all = exerciseDao.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("Push-ups", all.first { it.exerciseId == 1 }.name)

        val byIds = exerciseDao.getByIds(setOf(2))
        assertEquals(1, byIds.size)
        assertEquals("Squats", byIds.first().name)
    }
}
