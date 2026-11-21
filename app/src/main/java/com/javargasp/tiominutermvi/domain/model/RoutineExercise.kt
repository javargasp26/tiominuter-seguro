package com.javargasp.tiominutermvi.domain.model

import com.javargasp.tiominutermvi.domain.serialization.ExerciseTypeSerializer
import kotlinx.serialization.Serializable

/**
 * Represents how an exercise is performed within a specific routine.
 * Contains the exercise reference and performance parameters.
 *
 * [isRest] marks a rest slot (no "Get Ready" countdown). It is set by the data
 * layer's mappers; the UI never decides by exerciseId.
 */
@Serializable
data class RoutineExercise(
    val exerciseId: Int,
    @Serializable(with = ExerciseTypeSerializer::class)
    val type: ExerciseType,
    val amount: Int,
    val isRest: Boolean = false
)