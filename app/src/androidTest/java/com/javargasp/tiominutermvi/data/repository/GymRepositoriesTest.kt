package com.javargasp.tiominutermvi.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.remote.GymRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineExerciseDto
import com.javargasp.tiominutermvi.domain.repository.GymProgressRepository
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.javargasp.tiominutermvi.domain.time.WallClock

/**
 * In-memory Room tests for the offline-first gym repositories, using a fake
 * [GymRemoteDataSource].
 */
@RunWith(AndroidJUnit4::class)
class GymRepositoriesTest {

    private lateinit var database: TioMinuterDatabase
    private lateinit var fakeRemote: FakeGymRemoteDataSource
    private lateinit var routineRepository: GymRoutineRepository
    private lateinit var progressRepository: GymProgressRepository

    /** Mutable fake remote source the tests can reprogram between refreshes. */
    private class FakeGymRemoteDataSource : GymRemoteDataSource {
        var routines: List<GymRoutineDto> = emptyList()
        var exercises: List<ExerciseDto> = emptyList()
        var error: Throwable? = null

        override suspend fun fetchGymRoutines(): List<GymRoutineDto> {
            error?.let { throw it }
            return routines
        }

        override suspend fun fetchExercises(): List<ExerciseDto> = exercises
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TioMinuterDatabase::class.java
        ).build()
        fakeRemote = FakeGymRemoteDataSource()
        routineRepository = GymRoutineRepositoryImpl(database, fakeRemote)
        progressRepository = GymProgressRepositoryImpl(
            database,
            WallClock { 1_000L }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun routineDto(
        id: Int,
        name: String = "Routine $id",
        exercises: List<GymRoutineExerciseDto> = listOf(
            GymRoutineExerciseDto(exerciseId = 101, type = "REPS", sets = 2, reps = listOf(10, 12), restSeconds = 60)
        )
    ) = GymRoutineDto(routineId = id, routineName = name, exercises = exercises)

    private fun exerciseDto(id: Int) = ExerciseDto(id, "Exercise $id", "anim_$id.gif")

    // ------------------------------------------------------------------
    // refresh + observe
    // ------------------------------------------------------------------

    @Test
    fun `refreshFillsSpanishNamesAndKeepsProgressWhenOnlyNamesChange`() = runTest {
        // First refresh without Spanish names (data stored before database version 6).
        fakeRemote.exercises = listOf(exerciseDto(101))
        fakeRemote.routines = listOf(routineDto(id = 64, name = "Pull Day"))
        routineRepository.refresh().getOrThrow()
        progressRepository.markCompleted(routineId = 64, position = 0)
        assertNull(routineRepository.observeGymRoutine(64).first()!!.routineNameEs)

        fakeRemote.exercises = listOf(exerciseDto(101).copy(nameEs = "Ejercicio 101"))
        fakeRemote.routines = listOf(routineDto(id = 64, name = "Pull Day").copy(routineNameEs = "Día de tirón"))
        routineRepository.refresh().getOrThrow()

        val routine = routineRepository.observeGymRoutine(64).first()!!
        assertEquals("Día de tirón", routine.routineNameEs)
        assertEquals("Ejercicio 101", routine.exercises.single().exercise?.nameEs)
        // Same slots, so the progress survives the refresh.
        assertEquals(setOf(0), progressRepository.observeCompletedPositions(64).first())
    }

    @Test
    fun `refreshFillsDatabaseAndEmitsMappedRoutinesInOrder`() = runTest {
        fakeRemote.exercises = listOf(exerciseDto(1), exerciseDto(2))
        fakeRemote.routines = listOf(
            routineDto(id = 1, exercises = listOf(
                GymRoutineExerciseDto(exerciseId = 1, type = "REPS", sets = 2, reps = listOf(10, 12), restSeconds = 60),
                GymRoutineExerciseDto(exerciseId = 2, type = "TIME", sets = 2, reps = listOf(30, 45), restSeconds = 30)
            )),
            routineDto(id = 2)
        )

        val result = routineRepository.refresh()

        assertTrue(result.isSuccess)
        val routines = routineRepository.observeGymRoutines().first()
        assertEquals(listOf(1, 2), routines.map { it.routineId })
        assertEquals(listOf(0, 1), routines[0].exercises.map { it.position })
        assertEquals(2, routines[0].exercises[0].sets)
        assertEquals("Exercise 1", routines[0].exercises[0].exercise?.name)
        assertEquals(2, routines[0].exercises[1].sets)

        val single = routineRepository.observeGymRoutine(2).first()
        assertEquals("Routine 2", single?.routineName)
        assertNull(routineRepository.observeGymRoutine(999).first())
    }

    @Test
    fun `refreshFailureKeepsPreviousDataAndReturnsFailure`() = runTest {
        fakeRemote.exercises = listOf(exerciseDto(1))
        fakeRemote.routines = listOf(routineDto(1))
        assertTrue(routineRepository.refresh().isSuccess)
        assertEquals(1, routineRepository.observeGymRoutines().first().size)

        fakeRemote.error = RuntimeException("network down")
        val result = routineRepository.refresh()

        assertTrue(result.isFailure)
        assertEquals(1, routineRepository.observeGymRoutines().first().size)
        assertEquals("Routine 1", routineRepository.observeGymRoutine(1).first()?.routineName)
    }

    @Test
    fun `progressKeptWhenRoutineUnchangedAfterRefresh`() = runTest {
        fakeRemote.exercises = listOf(exerciseDto(1))
        fakeRemote.routines = listOf(routineDto(1))
        assertTrue(routineRepository.refresh().isSuccess)
        progressRepository.markCompleted(routineId = 1, position = 0)

        // Same content again.
        assertTrue(routineRepository.refresh().isSuccess)

        assertEquals(setOf(0), progressRepository.observeCompletedPositions(1).first())
    }

    @Test
    fun `progressClearedWhenRoutineContentChangedAfterRefresh`() = runTest {
        fakeRemote.exercises = listOf(exerciseDto(1))
        fakeRemote.routines = listOf(routineDto(1))
        assertTrue(routineRepository.refresh().isSuccess)
        progressRepository.markCompleted(routineId = 1, position = 0)

        // Change the routine's slots (different reps).
        fakeRemote.routines = listOf(routineDto(1, exercises = listOf(
            GymRoutineExerciseDto(exerciseId = 1, type = "REPS", sets = 3, reps = listOf(10, 12, 15), restSeconds = 60)
        )))
        assertTrue(routineRepository.refresh().isSuccess)

        assertEquals(emptySet<Int>(), progressRepository.observeCompletedPositions(1).first())
    }

    @Test
    fun `routineRemovedRemotelyDisappearsWithItsProgress`() = runTest {
        fakeRemote.exercises = listOf(exerciseDto(1), exerciseDto(2))
        fakeRemote.routines = listOf(routineDto(1), routineDto(2))
        assertTrue(routineRepository.refresh().isSuccess)
        progressRepository.markCompleted(routineId = 2, position = 0)

        // Routine 2 disappears remotely.
        fakeRemote.routines = listOf(routineDto(1))
        assertTrue(routineRepository.refresh().isSuccess)

        assertEquals(listOf(1), routineRepository.observeGymRoutines().first().map { it.routineId })
        assertNull(routineRepository.observeGymRoutine(2).first())
        assertEquals(emptySet<Int>(), progressRepository.observeCompletedPositions(2).first())
    }

    // ------------------------------------------------------------------
    // GymProgressRepository
    // ------------------------------------------------------------------

    @Test
    fun `markCompletedObserveReset`() = runTest {
        fakeRemote.exercises = listOf(exerciseDto(1))
        fakeRemote.routines = listOf(routineDto(1))
        assertTrue(routineRepository.refresh().isSuccess)

        assertTrue(progressRepository.observeCompletedPositions(1).first().isEmpty())

        progressRepository.markCompleted(routineId = 1, position = 0)
        progressRepository.markCompleted(routineId = 1, position = 2)
        assertEquals(setOf(0, 2), progressRepository.observeCompletedPositions(1).first())

        // Re-marking the same position keeps a single entry.
        progressRepository.markCompleted(routineId = 1, position = 0)
        assertEquals(setOf(0, 2), progressRepository.observeCompletedPositions(1).first())

        progressRepository.resetRoutine(1)
        assertTrue(progressRepository.observeCompletedPositions(1).first().isEmpty())
    }
}
