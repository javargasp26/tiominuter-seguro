package com.javargasp.tiominutermvi.data.mapper

import com.javargasp.tiominutermvi.data.local.GymRoutineWithExercises
import com.javargasp.tiominutermvi.data.local.entity.ExerciseEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineExerciseEntity
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineExerciseDto
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.model.GymRoutineExercise

/**
 * Pure mapping functions between the gym DTOs, Room entities and domain models.
 */

// ---------------------------------------------------------------------------
// DTO -> Entity
// ---------------------------------------------------------------------------

fun ExerciseDto.toEntity(): ExerciseEntity =
    ExerciseEntity(
        exerciseId = exerciseId,
        name = name,
        animation = animation,
        nameEs = nameEs
    )

fun List<ExerciseDto>.toExerciseEntities(): List<ExerciseEntity> = map { it.toEntity() }

/**
 * Maps a routine DTO to its entity plus one entity per exercise slot.
 * [GymRoutineExerciseEntity.position] is the slot's index in the routine's list.
 */
fun GymRoutineDto.toEntities(): Pair<GymRoutineEntity, List<GymRoutineExerciseEntity>> =
    Pair(
        GymRoutineEntity(
            routineId = routineId,
            routineName = routineName,
            routineNameEs = routineNameEs
        ),
        exercises.mapIndexed { index, dto ->
            dto.toEntity(routineId = routineId, position = index)
        }
    )

fun GymRoutineExerciseDto.toEntity(routineId: Int, position: Int): GymRoutineExerciseEntity =
    GymRoutineExerciseEntity(
        routineId = routineId,
        position = position,
        exerciseId = exerciseId,
        type = type,
        reps = reps,
        restSeconds = restSeconds
    )

// ---------------------------------------------------------------------------
// Entity -> Domain
// ---------------------------------------------------------------------------

fun ExerciseEntity.toDomain(): Exercise =
    Exercise(
        exerciseId = exerciseId,
        name = name,
        animation = animation,
        nameEs = nameEs
    )

/**
 * Maps a routine (with its slots) to the domain model, sorted by position.
 * Slots whose [String] type is not a known [ExerciseType] are skipped (logged),
 * so malformed remote data cannot crash the app.
 *
 * [GymRoutineExercise.exercise] is null when the slot's exerciseId is missing
 * from [exercisesById].
 */
fun GymRoutineWithExercises.toDomain(exercisesById: Map<Int, Exercise>): GymRoutine? {
    val mappedExercises = exercises
        .mapNotNull { slot -> slot.toDomain(exercisesById) ?: run { logSkippedSlot(slot); null } }
        .sortedBy { it.position }

    return GymRoutine(
        routineId = routine.routineId,
        routineName = routine.routineName,
        exercises = mappedExercises,
        routineNameEs = routine.routineNameEs
    )
}

fun GymRoutineExerciseEntity.toDomain(exercisesById: Map<Int, Exercise>): GymRoutineExercise? {
    val type = type.toExerciseType() ?: return null
    return GymRoutineExercise(
        position = position,
        exerciseId = exerciseId,
        exercise = exercisesById[exerciseId],
        type = type,
        reps = reps,
        restSeconds = restSeconds
    )
}

/**
 * Maps the raw type string to [ExerciseType]; null when unknown.
 */
fun String.toExerciseType(): ExerciseType? = when (uppercase()) {
    "TIME" -> ExerciseType.TIME
    "REPS" -> ExerciseType.REPS
    else -> null
}

private fun logSkippedSlot(slot: GymRoutineExerciseEntity) {
    println("GymMappers: skipping exercise slot (routineId=${slot.routineId}, position=${slot.position}) with unknown type '${slot.type}'")
}
