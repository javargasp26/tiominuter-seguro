package com.javargasp.tiominutermvi.data.mapper

import com.javargasp.tiominutermvi.data.local.DailyRoutineWithExercises
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineExerciseEntity
import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineExerciseDto
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.model.RoutineExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM unit tests for the daily-routine mappers.
 */
class DailyMappersTest {

    // ------------------------------------------------------------------
    // DTO -> Entity
    // ------------------------------------------------------------------

    @Test
    fun `routineDto maps position as index in the routine list`() {
        val dto = DailyRoutineDto(
            routineId = 1,
            routineDay = "Day 1",
            exercises = listOf(
                DailyRoutineExerciseDto(exerciseId = 1, type = "TIME", amount = 60),
                DailyRoutineExerciseDto(exerciseId = 2, type = "REPS", amount = 15),
                DailyRoutineExerciseDto(exerciseId = 3, type = "REPS", amount = 10)
            )
        )

        val (entity, slots) = dto.toEntities()

        assertEquals(DailyRoutineEntity(routineId = 1, routineDay = "Day 1", title = null), entity)
        assertEquals(
            listOf(
                DailyRoutineExerciseEntity(1, position = 0, exerciseId = 1, type = "TIME", amount = 60),
                DailyRoutineExerciseEntity(1, position = 1, exerciseId = 2, type = "REPS", amount = 15),
                DailyRoutineExerciseEntity(1, position = 2, exerciseId = 3, type = "REPS", amount = 10)
            ),
            slots
        )
    }

    @Test
    fun `title is kept when present and null when absent`() {
        val withTitle = DailyRoutineDto(2, "Day 2", title = "Legs", exercises = emptyList())
        val withoutTitle = DailyRoutineDto(3, "Day 3", exercises = emptyList())

        assertEquals("Legs", withTitle.toEntities().first.title)
        assertNull(withoutTitle.toEntities().first.title)
        assertEquals("Legs", routineWith(withTitle.toEntities()).toDomain().title)
        assertNull(routineWith(withoutTitle.toEntities()).toDomain().title)
    }

    @Test
    fun `same exercise twice in a routine gets two positions`() {
        val dto = DailyRoutineDto(
            routineId = 1,
            routineDay = "Day 1",
            exercises = listOf(
                DailyRoutineExerciseDto(exerciseId = 13, type = "TIME", amount = 30),
                DailyRoutineExerciseDto(exerciseId = 2, type = "REPS", amount = 15),
                DailyRoutineExerciseDto(exerciseId = 13, type = "TIME", amount = 45)
            )
        )

        val (_, slots) = dto.toEntities()
        val routine = routineWith(dto.toEntities()).toDomain()

        assertEquals(listOf(0, 1, 2), slots.map { it.position })
        assertEquals(listOf(13, 2, 13), routine.exercises.map { it.exerciseId })
        assertEquals(listOf(30, 15, 45), routine.exercises.map { it.amount })
    }

    // ------------------------------------------------------------------
    // Entity -> Domain
    // ------------------------------------------------------------------

    @Test
    fun `routine maps to domain with slots sorted by position`() {
        val routine = DailyRoutineWithExercises(
            routine = DailyRoutineEntity(routineId = 4, routineDay = "Day 4", title = "Core"),
            exercises = listOf(
                DailyRoutineExerciseEntity(4, position = 2, exerciseId = 30, type = "REPS", amount = 12),
                DailyRoutineExerciseEntity(4, position = 0, exerciseId = 10, type = "TIME", amount = 60),
                DailyRoutineExerciseEntity(4, position = 1, exerciseId = 20, type = "REPS", amount = 15)
            )
        )

        assertEquals(
            Routine(
                routineId = 4,
                routineDay = "Day 4",
                title = "Core",
                exercises = listOf(
                    RoutineExercise(exerciseId = 10, type = ExerciseType.TIME, amount = 60),
                    RoutineExercise(exerciseId = 20, type = ExerciseType.REPS, amount = 15),
                    RoutineExercise(exerciseId = 30, type = ExerciseType.REPS, amount = 12)
                )
            ),
            routine.toDomain()
        )
    }

    @Test
    fun `slot with unknown type is skipped without crashing`() {
        val routine = DailyRoutineWithExercises(
            routine = DailyRoutineEntity(routineId = 5, routineDay = "Day 5", title = null),
            exercises = listOf(
                DailyRoutineExerciseEntity(5, position = 0, exerciseId = 1, type = "TIME", amount = 60),
                DailyRoutineExerciseEntity(5, position = 1, exerciseId = 2, type = "JUMPS", amount = 15),
                DailyRoutineExerciseEntity(5, position = 2, exerciseId = 3, type = "REPS", amount = 10)
            )
        )

        val domain = routine.toDomain()

        assertEquals(listOf(1, 3), domain.exercises.map { it.exerciseId })
    }

    @Test
    fun `type string maps case-insensitively to ExerciseType`() {
        val slot = DailyRoutineExerciseEntity(1, position = 0, exerciseId = 1, type = "reps", amount = 10)

        assertEquals(ExerciseType.REPS, slot.toDomain()?.type)
    }

    @Test
    fun `only slots with the rest exercise id are marked as rest`() {
        val routine = DailyRoutineWithExercises(
            routine = DailyRoutineEntity(routineId = 6, routineDay = "Day 6", title = null),
            exercises = listOf(
                DailyRoutineExerciseEntity(6, position = 0, exerciseId = 1, type = "TIME", amount = 60),
                DailyRoutineExerciseEntity(6, position = 1, exerciseId = DAILY_REST_EXERCISE_ID, type = "TIME", amount = 30),
                DailyRoutineExerciseEntity(6, position = 2, exerciseId = 2, type = "REPS", amount = 15)
            )
        )

        val domain = routine.toDomain()

        assertEquals(listOf(false, true, false), domain.exercises.map { it.isRest })
    }

    private fun routineWith(
        entities: Pair<DailyRoutineEntity, List<DailyRoutineExerciseEntity>>
    ) = DailyRoutineWithExercises(routine = entities.first, exercises = entities.second)
}
