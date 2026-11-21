package com.javargasp.tiominutermvi.data.mapper

import com.javargasp.tiominutermvi.data.local.DailyRoutineWithExercises
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineExerciseEntity
import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineExerciseDto
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.model.RoutineExercise

/**
 * Pure mapping functions between the daily-routine DTOs, Room entities and
 * domain models. Exercise mappings (ExerciseDto -> ExerciseEntity -> Exercise)
 * and [toExerciseType] are shared with the gym flow (GymMappers.kt).
 */

/**
 * Id of the "Rest" exercise in the daily catalog (`exercises.json`). The only
 * place in the app that knows it: slots with this id map to
 * [RoutineExercise.isRest] = true.
 */
internal const val DAILY_REST_EXERCISE_ID = 13

// ---------------------------------------------------------------------------
// DTO -> Entity
// ---------------------------------------------------------------------------

/**
 * Maps a routine DTO to its entity plus one entity per exercise slot.
 * [DailyRoutineExerciseEntity.position] is the slot's index in the routine's list.
 */
fun DailyRoutineDto.toEntities(): Pair<DailyRoutineEntity, List<DailyRoutineExerciseEntity>> =
    Pair(
        DailyRoutineEntity(
            routineId = routineId,
            routineDay = routineDay,
            // titleEs has no column yet: storing it needs a database version (no routine has one today)
            title = title
        ),
        exercises.mapIndexed { index, dto ->
            dto.toEntity(routineId = routineId, position = index)
        }
    )

fun DailyRoutineExerciseDto.toEntity(routineId: Int, position: Int): DailyRoutineExerciseEntity =
    DailyRoutineExerciseEntity(
        routineId = routineId,
        position = position,
        exerciseId = exerciseId,
        type = type,
        amount = amount
    )

// ---------------------------------------------------------------------------
// Entity -> Domain
// ---------------------------------------------------------------------------

/**
 * Maps a routine (with its slots) to the domain model, slots sorted by position.
 * Slots whose [String] type is not a known
 * [com.javargasp.tiominutermvi.domain.model.ExerciseType] are skipped (logged),
 * so malformed remote data cannot crash the app.
 */
fun DailyRoutineWithExercises.toDomain(): Routine =
    Routine(
        routineId = routine.routineId,
        routineDay = routine.routineDay,
        title = routine.title,
        exercises = exercises
            .sortedBy { it.position }
            .mapNotNull { slot -> slot.toDomain() ?: run { logSkippedSlot(slot); null } }
    )

fun DailyRoutineExerciseEntity.toDomain(): RoutineExercise? {
    val type = type.toExerciseType() ?: return null
    return RoutineExercise(
        exerciseId = exerciseId,
        type = type,
        amount = amount,
        isRest = exerciseId == DAILY_REST_EXERCISE_ID
    )
}

private fun logSkippedSlot(slot: DailyRoutineExerciseEntity) {
    println("DailyMappers: skipping exercise slot (routineId=${slot.routineId}, position=${slot.position}) with unknown type '${slot.type}'")
}
