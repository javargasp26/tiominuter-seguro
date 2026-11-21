package com.javargasp.tiominutermvi.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Exercise catalog entry as returned by the (simulated) remote API.
 * [nameEs] is the optional Spanish name; entries without it still decode.
 */
@Serializable
data class ExerciseDto(
    val exerciseId: Int,
    val name: String,
    val animation: String,
    val nameEs: String? = null
)
