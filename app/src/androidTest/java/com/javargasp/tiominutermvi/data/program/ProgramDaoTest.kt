package com.javargasp.tiominutermvi.data.program

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramSlotProgressEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory Room tests for [ProgramDatabase]'s DAOs. */
@RunWith(AndroidJUnit4::class)
class ProgramDaoTest {

    private lateinit var database: ProgramDatabase
    private val programDao get() = database.programDao()
    private val progressDao get() = database.programProgressDao()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ProgramDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertProgram(createdAt: Long = 1L, workouts: Int = 2): Long {
        val id = programDao.insertProgram(program(createdAt))
        programDao.insertWorkouts(List(workouts) { day -> workout(id, week = 1, dayIndex = day) })
        return id
    }

    @Test
    fun insert_and_observe_program_with_workouts_in_order() = runTest {
        val id = programDao.insertProgram(program(1L))
        programDao.insertWorkouts(
            listOf(workout(id, 2, 0), workout(id, 1, 1), workout(id, 1, 0))
        )

        assertEquals(program(1L).copy(id = id), programDao.observeProgram(id).first())
        assertEquals(
            listOf(1 to 0, 1 to 1, 2 to 0),
            programDao.observeWorkouts(id).first().map { it.week to it.dayIndex }
        )
    }

    @Test
    fun summaries_count_total_and_completed_workouts_newest_first() = runTest {
        val older = insertProgram(createdAt = 1L, workouts = 3)
        val newer = insertProgram(createdAt = 2L, workouts = 2)
        progressDao.upsertWorkout(ProgramWorkoutProgressEntity(older, 1, 0, 5L))

        val summaries = programDao.observeProgramSummaries().first()

        assertEquals(listOf(newer, older), summaries.map { it.program.id })
        assertEquals(listOf(2, 3), summaries.map { it.totalWorkouts })
        assertEquals(listOf(0, 1), summaries.map { it.completedWorkouts })
    }

    @Test
    fun delete_cascades_to_workouts_and_progress() = runTest {
        val id = insertProgram()
        progressDao.upsertSlot(ProgramSlotProgressEntity(id, 1, 0, 0, 5L))
        progressDao.upsertWorkout(ProgramWorkoutProgressEntity(id, 1, 0, 5L))

        programDao.deleteProgram(id)

        assertNull(programDao.observeProgram(id).first())
        assertTrue(programDao.getWorkouts(id).isEmpty())
        assertTrue(progressDao.observeSlots(id).first().isEmpty())
        assertTrue(progressDao.observeWorkouts(id).first().isEmpty())
    }

    @Test
    fun slot_progress_is_per_workout_and_cleared_by_reset() = runTest {
        val id = insertProgram()
        progressDao.upsertSlot(ProgramSlotProgressEntity(id, 1, 0, 0, 5L))
        progressDao.upsertSlot(ProgramSlotProgressEntity(id, 1, 0, 2, 6L))
        progressDao.upsertSlot(ProgramSlotProgressEntity(id, 1, 1, 0, 7L))

        assertEquals(setOf(0, 2), progressDao.observeCompletedPositions(id, 1, 0).first().toSet())

        progressDao.clearSlots(id, 1, 0)

        assertTrue(progressDao.getCompletedPositions(id, 1, 0).isEmpty())
        assertEquals(listOf(0), progressDao.getCompletedPositions(id, 1, 1))
    }

    @Test
    fun workout_completion_upsert_keeps_one_row() = runTest {
        val id = insertProgram()
        assertFalse(progressDao.observeWorkoutCompleted(id, 1, 0).first())

        progressDao.upsertWorkout(ProgramWorkoutProgressEntity(id, 1, 0, 5L))
        progressDao.upsertWorkout(ProgramWorkoutProgressEntity(id, 1, 0, 9L))

        assertTrue(progressDao.observeWorkoutCompleted(id, 1, 0).first())
        assertEquals(listOf(9L), progressDao.observeWorkouts(id).first().map { it.completedAt })
    }

    @Test
    fun progress_for_a_missing_workout_is_rejected() = runTest {
        val id = insertProgram(workouts = 1)

        try {
            progressDao.upsertSlot(ProgramSlotProgressEntity(id, 3, 0, 0, 5L))
            fail("Expected a foreign key failure")
        } catch (expected: SQLiteConstraintException) {
            // FK to program_workouts
        }
    }

    private fun program(createdAt: Long) = ProgramEntity(
        goal = "GAIN_MUSCLE",
        level = "BEGINNER",
        location = "GYM",
        weeks = 4,
        daysPerWeek = 2,
        age = 30,
        weightKg = 70.0,
        heightCm = 175,
        createdAt = createdAt
    )

    private fun workout(programId: Long, week: Int, dayIndex: Int) = ProgramWorkoutEntity(
        programId = programId,
        week = week,
        dayIndex = dayIndex,
        kind = "OUTDOOR_RUN",
        isDeload = false,
        payload = """{"kind":"OUTDOOR_RUN","distanceKm":1.5,"walk":false}"""
    )
}
