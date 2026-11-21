package com.javargasp.tiominutermvi.data.mapper

import com.javargasp.tiominutermvi.data.local.GymRoutineWithExercises
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineExerciseEntity
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineExerciseDto
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the gym mappers.
 */
class GymMappersTest {

    // ------------------------------------------------------------------
    // DTO -> Entity
    // ------------------------------------------------------------------

    @Test
    fun `routineDto maps position as index in the routine list`() {
        val dto = GymRoutineDto(
            routineId = 1,
            routineName = "Push Day",
            exercises = listOf(
                GymRoutineExerciseDto(exerciseId = 10, type = "REPS", sets = 3, reps = listOf(10, 12, 8), restSeconds = 60),
                GymRoutineExerciseDto(exerciseId = 11, type = "TIME", sets = 2, reps = listOf(30, 45), restSeconds = 30)
            )
        )

        val (entity, slots) = dto.toEntities()

        assertEquals(1, entity.routineId)
        assertEquals("Push Day", entity.routineName)
        assertEquals(listOf(0, 1), slots.map { it.position })
        assertEquals(1, slots[0].routineId)
    }

    @Test
    fun `exerciseDto maps to entity`() {
        val entity = ExerciseDto(exerciseId = 5, name = "Bench Press", animation = "bench.gif").toEntity()

        assertEquals(5, entity.exerciseId)
        assertEquals("Bench Press", entity.name)
        assertEquals("bench.gif", entity.animation)
        assertNull(entity.nameEs)
    }

    @Test
    fun `Spanish names go from the DTOs through the entities to the domain`() {
        val exercise = ExerciseDto(exerciseId = 4, name = "Plank", animation = "4_plank.gif", nameEs = "Plancha")
            .toEntity()
        val (routineEntity, slots) = GymRoutineDto(
            routineId = 64,
            routineName = "Pull Day",
            exercises = listOf(GymRoutineExerciseDto(exerciseId = 4, type = "TIME", sets = 1, reps = listOf(30), restSeconds = 0)),
            routineNameEs = "Día de tirón"
        ).toEntities()

        assertEquals("Plancha", exercise.nameEs)
        assertEquals("Día de tirón", routineEntity.routineNameEs)

        val domainExercise = exercise.toDomain()
        val routine = requireNotNull(
            GymRoutineWithExercises(routineEntity, slots).toDomain(mapOf(4 to domainExercise))
        )
        assertEquals(Exercise(4, "Plank", "4_plank.gif", nameEs = "Plancha"), domainExercise)
        assertEquals("Día de tirón", routine.routineNameEs)
        assertEquals("Plancha", routine.exercises.single().exercise?.nameEs)
    }

    @Test
    fun `missing Spanish names stay null`() {
        val routine = requireNotNull(
            GymRoutineWithExercises(GymRoutineEntity(1, "Push Day"), emptyList()).toDomain(emptyMap())
        )

        assertNull(routine.routineNameEs)
        assertNull(ExerciseDto(5, "Bench Press", "bench.gif").toEntity().toDomain().nameEs)
    }

    // ------------------------------------------------------------------
    // Entity -> Domain
    // ------------------------------------------------------------------

    @Test
    fun `toDomain sorts slots by position`() {
        val routine = GymRoutineWithExercises(
            routine = GymRoutineEntity(1, "Push Day"),
            exercises = listOf(
                slot(routineId = 1, position = 2, exerciseId = 103),
                slot(routineId = 1, position = 0, exerciseId = 101),
                slot(routineId = 1, position = 1, exerciseId = 102)
            )
        )

        val domain = requireNotNull(routine.toDomain(exercisesById(101, 102, 103)))

        assertEquals(listOf(0, 1, 2), domain.exercises.map { it.position })
    }

    @Test
    fun `sets equals reps size`() {
        val domain = requireNotNull(
            slot(routineId = 1, position = 0, reps = listOf(10, 12, 8, 6)).toDomain(emptyMap())
        )

        assertEquals(4, domain.sets)
        assertEquals(4, domain.reps.size)
    }

    @Test
    fun `unknown type skips the exercise slot`() {
        val routine = GymRoutineWithExercises(
            routine = GymRoutineEntity(1, "Push Day"),
            exercises = listOf(
                slot(routineId = 1, position = 0, exerciseId = 101, type = "REPS"),
                slot(routineId = 1, position = 1, exerciseId = 102, type = "HOLD"),
                slot(routineId = 1, position = 2, exerciseId = 103, type = "TIME")
            )
        )

        val domain = requireNotNull(routine.toDomain(emptyMap()))

        assertEquals(2, domain.exercises.size)
        assertTrue(domain.exercises.none { it.exerciseId == 102 })
    }

    @Test
    fun `missing exercise maps exercise to null`() {
        val routine = GymRoutineWithExercises(
            routine = GymRoutineEntity(1, "Push Day"),
            exercises = listOf(slot(routineId = 1, position = 0, exerciseId = 999))
        )

        val domain = routine.toDomain(exercisesById = emptyMap())

        assertNull(domain?.exercises?.first()?.exercise)
    }

    @Test
    fun `toExerciseType maps case-insensitively and rejects unknown`() {
        assertEquals(ExerciseType.TIME, "time".toExerciseType())
        assertEquals(ExerciseType.REPS, "REPS".toExerciseType())
        assertNull("cardio".toExerciseType())
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun slot(
        routineId: Int,
        position: Int,
        exerciseId: Int = 100 + position,
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

    /** Domain exercise map for the given ids. */
    private fun exercisesById(vararg ids: Int): Map<Int, Exercise> =
        ids.associate { id -> id to Exercise(id, "Exercise $id", "anim_$id.gif") }
}
