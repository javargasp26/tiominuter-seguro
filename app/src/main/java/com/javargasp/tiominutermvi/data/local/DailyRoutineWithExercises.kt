package com.javargasp.tiominutermvi.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineExerciseEntity

/**
 * A daily routine together with its exercise slots, loaded via @Relation.
 * Ordering by [DailyRoutineExerciseEntity.position] is left to the caller.
 */
data class DailyRoutineWithExercises(
    @Embedded
    val routine: DailyRoutineEntity,
    @Relation(
        parentColumn = "routineId",
        entityColumn = "routineId"
    )
    val exercises: List<DailyRoutineExerciseEntity>
)
