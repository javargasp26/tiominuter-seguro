package com.javargasp.tiominutermvi.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineExerciseEntity

/**
 * A gym routine together with its exercise slots, loaded via @Relation.
 * Ordering by [GymRoutineExerciseEntity.position] is left to the mapper.
 */
data class GymRoutineWithExercises(
    @Embedded
    val routine: GymRoutineEntity,
    @Relation(
        parentColumn = "routineId",
        entityColumn = "routineId"
    )
    val exercises: List<GymRoutineExerciseEntity>
)
