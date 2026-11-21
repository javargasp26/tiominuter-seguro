package com.javargasp.tiominutermvi.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Exercise entry inside a gym routine as returned by the (simulated) remote API.
 *
 * [type] is kept as the raw "REPS"/"TIME" string; for TIME, each value in [reps]
 * is that set's duration in seconds.
 */
@Serializable
data class GymRoutineExerciseDto(
    val exerciseId: Int,
    val type: String,
    @SerialName("set") val sets: Int,
    val reps: List<Int>,
    @SerialName("rest") val restSeconds: Int
)
