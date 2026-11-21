package com.javargasp.tiominutermvi.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * In-memory Room tests for the daily-routine DAO.
 */
class DailyDaoTest {

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

    private fun routine(id: Int, day: String = "Day $id", title: String? = null) =
        DailyRoutineEntity(routineId = id, routineDay = day, title = title)

    private fun slot(
        routineId: Int,
        position: Int,
        exerciseId: Int = 100 + position,
        type: String = "REPS",
        amount: Int = 15
    ) = DailyRoutineExerciseEntity(
        routineId = routineId,
        position = position,
        exerciseId = exerciseId,
        type = type,
        amount = amount
    )

    // ------------------------------------------------------------------
    // replaceCatalog transaction
    // ------------------------------------------------------------------

    @Test
    fun replaceCatalogInsertsRoutinesWithTheirSlots() = runTest {
        val dao = database.dailyRoutineDao()

        dao.replaceCatalog(
            routines = listOf(routine(1, title = "Legs"), routine(2)),
            exercisesByRoutine = mapOf(
                1 to listOf(slot(1, 0), slot(1, 1, type = "TIME", amount = 60)),
                2 to listOf(slot(2, 0))
            )
        )

        val routines = dao.getRoutinesWithExercises().associateBy { it.routine.routineId }
        assertEquals(setOf(1, 2), routines.keys)
        assertEquals(routine(1, title = "Legs"), routines.getValue(1).routine)
        assertEquals(
            listOf(slot(1, 0), slot(1, 1, type = "TIME", amount = 60)),
            routines.getValue(1).exercises.sortedBy { it.position }
        )
        assertEquals(listOf(slot(2, 0)), routines.getValue(2).exercises)
        assertEquals(routines.values.toSet(), dao.observeRoutinesWithExercises().first().toSet())
    }

    @Test
    fun replaceCatalogUpdatesExistingRoutineAndReplacesItsSlots() = runTest {
        val dao = database.dailyRoutineDao()
        dao.replaceCatalog(
            routines = listOf(routine(1, day = "Old day")),
            exercisesByRoutine = mapOf(1 to listOf(slot(1, 0, exerciseId = 100), slot(1, 1, exerciseId = 101)))
        )

        dao.replaceCatalog(
            routines = listOf(routine(1, day = "New day", title = "Core")),
            exercisesByRoutine = mapOf(1 to listOf(slot(1, 0, exerciseId = 200)))
        )

        val stored = dao.getRoutineWithExercises(1)
        assertEquals(routine(1, day = "New day", title = "Core"), stored?.routine)
        assertEquals(listOf(slot(1, 0, exerciseId = 200)), stored?.exercises)
    }

    @Test
    fun replaceCatalogRemovesDisappearedRoutinesAndCascadesTheirSlots() = runTest {
        val dao = database.dailyRoutineDao()
        dao.replaceCatalog(
            routines = listOf(routine(1), routine(2)),
            exercisesByRoutine = mapOf(
                1 to listOf(slot(1, 0)),
                2 to listOf(slot(2, 0), slot(2, 1))
            )
        )

        // Routine 2 disappears from the catalog.
        dao.replaceCatalog(
            routines = listOf(routine(1)),
            exercisesByRoutine = mapOf(1 to listOf(slot(1, 0)))
        )

        assertEquals(listOf(1), dao.getRoutinesWithExercises().map { it.routine.routineId })
        assertNull(dao.getRoutineWithExercises(2))
        // The removed routine's slots were cascaded away.
        assertEquals(1, countSlots())
    }

    @Test
    fun replaceCatalogWithAnEmptyListRemovesEveryRoutine() = runTest {
        val dao = database.dailyRoutineDao()
        dao.replaceCatalog(listOf(routine(1)), mapOf(1 to listOf(slot(1, 0))))

        dao.replaceCatalog(emptyList(), emptyMap())

        assertEquals(0, dao.countRoutines())
        assertEquals(0, countSlots())
    }

    @Test
    fun sameExerciseCanAppearTwiceInOneRoutine() = runTest {
        val dao = database.dailyRoutineDao()
        val rest = 13

        dao.replaceCatalog(
            routines = listOf(routine(1)),
            exercisesByRoutine = mapOf(
                1 to listOf(
                    slot(1, 0, exerciseId = rest, type = "TIME", amount = 30),
                    slot(1, 1, exerciseId = 2),
                    slot(1, 2, exerciseId = rest, type = "TIME", amount = 45)
                )
            )
        )

        val slots = dao.getRoutineWithExercises(1)?.exercises.orEmpty().sortedBy { it.position }
        assertEquals(listOf(rest, 2, rest), slots.map { it.exerciseId })
        assertEquals(listOf(30, 15, 45), slots.map { it.amount })
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Test
    fun countRoutinesReflectsTheCatalog() = runTest {
        val dao = database.dailyRoutineDao()
        assertEquals(0, dao.countRoutines())

        dao.replaceCatalog(listOf(routine(1), routine(2), routine(3)), emptyMap())

        assertEquals(3, dao.countRoutines())
    }

    @Test
    fun getRoutineWithExercisesReturnsNullForAnUnknownRoutine() = runTest {
        assertNull(database.dailyRoutineDao().getRoutineWithExercises(42))
    }

    @Test
    fun dailyCatalogDoesNotTouchGymTables() = runTest {
        database.dailyRoutineDao().replaceCatalog(
            routines = listOf(routine(1)),
            exercisesByRoutine = mapOf(1 to listOf(slot(1, 0)))
        )

        assertTrue(database.gymRoutineDao().observeRoutinesWithExercises().first().isEmpty())
    }

    private fun countSlots(): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM daily_routine_exercises")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
}
