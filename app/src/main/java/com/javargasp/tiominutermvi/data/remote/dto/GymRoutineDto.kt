package com.javargasp.tiominutermvi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Gym routine as returned by the (simulated) remote API.
 * [routineNameEs] is the optional Spanish name; routines without it still decode.
 */
@Serializable
data class GymRoutineDto(
    val routineId: Int,
    val routineName: String,
    val exercises: List<GymRoutineExerciseDto>,
    val routineNameEs: String? = null
)
