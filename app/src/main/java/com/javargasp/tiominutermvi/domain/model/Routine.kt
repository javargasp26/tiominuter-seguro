package com.javargasp.tiominutermvi.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a workout routine containing multiple exercises. Its name is shown through
 * WorkoutNameResolver ("Day N", plus [title] / [titleEs] when the content has them).
 * [titleEs] is accepted from the content but not stored in Room yet (no column), so it is
 * null for routines read from the database.
 */
@Serializable
data class Routine(
    val routineId: Int,
    val routineDay: String,
    val title: String? = null,
    val exercises: List<RoutineExercise>,
    val titleEs: String? = null
)