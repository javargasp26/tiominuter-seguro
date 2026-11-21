package com.javargasp.tiominutermvi.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.remote.DailyRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.GymRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineExerciseDto
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineExerciseDto
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.model.RoutineExercise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * In-memory Room tests for the offline-first daily [WorkoutRepositoryImpl],
 * using a fake [DailyRemoteDataSource].
 */
@RunWith(AndroidJUnit4::class)
class DailyRepositoryTest {

    private lateinit var database: TioMinuterDatabase
    private lateinit var fakeRemote: FakeDailyRemoteDataSource

    /** Mutable fake remote source the tests can reprogram between refreshes. */
    private class FakeDailyRemoteDataSource : DailyRemoteDataSource {
        var routines: List<DailyRoutineDto> = emptyList()
        var exercises: List<ExerciseDto> = emptyList()
        var error: Throwable? = null
        var fetchRoutinesCalls = 0

        /** When set, fetchDailyRoutines suspends until it completes. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun fetchDailyRoutines(): List<DailyRoutineDto> {
            fetchRoutinesCalls++
            gate?.await()
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
        fakeRemote = FakeDailyRemoteDataSource().apply {
            exercises = listOf(exerciseDto(1), exerciseDto(2), exerciseDto(13))
            routines = listOf(
                routineDto(1, slots = listOf(slot(1, "TIME", 60), slot(2, "REPS", 15), slot(13, "TIME", 30))),
                routineDto(2, title = "Legs", slots = listOf(slot(2, "REPS", 20)))
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** A new repository = a new process: its first read refreshes again. */
    private fun newRepository() = WorkoutRepositoryImpl(database, fakeRemote)

    private fun routineDto(id: Int, title: String? = null, slots: List<DailyRoutineExerciseDto>) =
        DailyRoutineDto(routineId = id, routineDay = "Day $id", title = title, exercises = slots)

    private fun slot(exerciseId: Int, type: String, amount: Int) =
        DailyRoutineExerciseDto(exerciseId = exerciseId, type = type, amount = amount)

    private fun exerciseDto(id: Int) = ExerciseDto(id, "Exercise $id", "anim_$id.gif")

    // ------------------------------------------------------------------
    // Refresh once per process
    // ------------------------------------------------------------------

    @Test
    fun firstReadRefreshesAndReturnsMappedRoutinesInOrder() = runTest {
        val repository = newRepository()

        val routines = repository.getRoutines()

        assertEquals(
            listOf(
                Routine(
                    routineId = 1,
                    routineDay = "Day 1",
                    exercises = listOf(
                        RoutineExercise(1, ExerciseType.TIME, 60),
                        RoutineExercise(2, ExerciseType.REPS, 15),
                        RoutineExercise(13, ExerciseType.TIME, 30, isRest = true)
                    )
                ),
                Routine(
                    routineId = 2,
                    routineDay = "Day 2",
                    title = "Legs",
                    exercises = listOf(RoutineExercise(2, ExerciseType.REPS, 20))
                )
            ),
            routines
        )
        assertEquals(1, fakeRemote.fetchRoutinesCalls)
    }

    @Test
    fun laterReadsDoNotCallTheRemoteAgain() = runTest {
        val repository = newRepository()

        repository.getRoutines()
        repository.getRoutines()
        repository.getRoutine(1)
        repository.getExercises()
        repository.getExercise(2)

        assertEquals(1, fakeRemote.fetchRoutinesCalls)
    }

    @Test
    fun concurrentFirstReadsTriggerOnlyOneRefresh() = runTest {
        val repository = newRepository()
        val gate = CompletableDeferred<Unit>()
        fakeRemote.gate = gate

        val reads = listOf(
            async { repository.getRoutines().size },
            async { repository.getRoutine(1)?.routineId },
            async { repository.getExercises().size },
            async { repository.getExercise(13)?.exerciseId }
        )
        testScheduler.advanceUntilIdle()
        gate.complete(Unit)

        assertEquals(listOf(2, 1, 3, 13), reads.map { it.await() })
        assertEquals(1, fakeRemote.fetchRoutinesCalls)
    }

    // ------------------------------------------------------------------
    // Refresh failure
    // ------------------------------------------------------------------

    @Test
    fun refreshFailureWithStoredDataServesTheStoredData() = runTest {
        // A previous process stored the catalog.
        newRepository().getRoutines()

        fakeRemote.error = IOException("offline")
        fakeRemote.routines = emptyList()
        val repository = newRepository()

        val routines = repository.getRoutines()

        assertEquals(listOf(1, 2), routines.map { it.routineId })
        assertEquals("Legs", repository.getRoutine(2)?.title)
        assertEquals(2, fakeRemote.fetchRoutinesCalls)
    }

    @Test
    fun refreshFailureWithEmptyDatabaseThrows() = runTest {
        fakeRemote.error = IOException("offline")
        val repository = newRepository()

        try {
            repository.getRoutines()
            fail("Expected the read to throw")
        } catch (e: IOException) {
            assertEquals("offline", e.message)
        }
        assertEquals(0, database.dailyRoutineDao().countRoutines())

        // The next read tries again and succeeds once the remote is back.
        fakeRemote.error = null
        assertEquals(2, repository.getRoutines().size)
    }

    @Test
    fun explicitRefreshFailureLeavesTheDatabaseUntouched() = runTest {
        val repository = newRepository()
        repository.getRoutines()

        fakeRemote.error = IOException("offline")
        val result = repository.refresh()

        assertTrue(result.isFailure)
        assertEquals(listOf(1, 2), repository.getRoutines().map { it.routineId })
    }

    // ------------------------------------------------------------------
    // Single reads
    // ------------------------------------------------------------------

    @Test
    fun getRoutineAndGetExerciseReturnTheItemOrNull() = runTest {
        val repository = newRepository()

        assertEquals(listOf(2), repository.getRoutine(2)?.exercises?.map { it.exerciseId })
        assertNull(repository.getRoutine(99))
        assertEquals(Exercise(13, "Exercise 13", "anim_13.gif"), repository.getExercise(13))
        assertNull(repository.getExercise(99))
        assertEquals(listOf(1, 2, 13), repository.getExercises().map { it.exerciseId })
    }

    @Test
    fun refreshThatRemovesARoutineRemovesItAndItsSlots() = runTest {
        val repository = newRepository()
        repository.getRoutines()

        fakeRemote.routines = fakeRemote.routines.filter { it.routineId == 1 }
        assertTrue(repository.refresh().isSuccess)

        assertEquals(listOf(1), repository.getRoutines().map { it.routineId })
        assertNull(repository.getRoutine(2))
        assertNull(database.dailyRoutineDao().getRoutineWithExercises(2))
        assertEquals(3, countRows("daily_routine_exercises"))
    }

    // ------------------------------------------------------------------
    // Shared exercises table
    // ------------------------------------------------------------------

    @Test
    fun dailyAndGymRefreshesShareTheExercisesTable() = runTest {
        val gymRemote = object : GymRemoteDataSource {
            override suspend fun fetchGymRoutines() = listOf(
                GymRoutineDto(
                    routineId = 64,
                    routineName = "Pull Day",
                    exercises = listOf(
                        GymRoutineExerciseDto(exerciseId = 18, type = "REPS", sets = 2, reps = listOf(10, 10), restSeconds = 45)
                    )
                )
            )

            override suspend fun fetchExercises() = listOf(exerciseDto(18), exerciseDto(2))
        }
        val gymRepository = GymRoutineRepositoryImpl(database, gymRemote)
        val dailyRepository = newRepository()

        assertTrue(gymRepository.refresh().isSuccess)
        dailyRepository.getRoutines()
        assertTrue(gymRepository.refresh().isSuccess)
        assertTrue(dailyRepository.refresh().isSuccess)

        // Both catalogs are intact and resolve their exercises.
        val gymRoutine = gymRepository.observeGymRoutine(64).first()
        assertEquals("Exercise 18", gymRoutine?.exercises?.single()?.exercise?.name)
        assertEquals(listOf(1, 2), dailyRepository.getRoutines().map { it.routineId })
        assertEquals("Exercise 18", dailyRepository.getExercise(18)?.name)
        assertEquals(listOf(1, 2, 13, 18), dailyRepository.getExercises().map { it.exerciseId })
    }

    private fun countRows(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
