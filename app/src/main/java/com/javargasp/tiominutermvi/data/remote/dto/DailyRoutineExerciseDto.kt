package com.javargasp.tiominutermvi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Exercise entry inside a daily routine as returned by the (simulated) remote API.
 *
 * [type] is kept as the raw "REPS"/"TIME" string; [amount] is seconds for TIME
 * and target reps for REPS.
 */
@Serializable
data class DailyRoutineExerciseDto(
    val exerciseId: Int,
    val type: String,
    val amount: Int
)
