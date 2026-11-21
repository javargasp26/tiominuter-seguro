package com.javargasp.tiominutermvi.domain.model

/**
 * A gym routine with its exercise slots, as exposed to the UI.
 * Plain domain model (not serialized). [routineNameEs] is the optional Spanish name;
 * screens pick the name with `localizedName`.
 */
data class GymRoutine(
    val routineId: Int,
    val routineName: String,
    val exercises: List<GymRoutineExercise>,
    val routineNameEs: String? = null
)
