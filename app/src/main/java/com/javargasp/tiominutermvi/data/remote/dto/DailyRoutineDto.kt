package com.javargasp.tiominutermvi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Daily routine as returned by the (simulated) remote API. [title] and its Spanish [titleEs]
 * are optional (no routine has them today); a titleEs needs a title.
 */
@Serializable
data class DailyRoutineDto(
    val routineId: Int,
    val routineDay: String,
    val title: String? = null,
    val exercises: List<DailyRoutineExerciseDto>,
    val titleEs: String? = null
)
