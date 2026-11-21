package com.javargasp.tiominutermvi.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the definition of an exercise.
 * Contains static information about what the exercise is.
 * [nameEs] is the optional Spanish name; screens pick the name with `localizedName`.
 */
@Serializable
data class Exercise(
    val exerciseId: Int,
    val name: String,
    val animation: String,
    val nameEs: String? = null
)