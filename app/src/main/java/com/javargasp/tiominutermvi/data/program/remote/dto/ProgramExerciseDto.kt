package com.javargasp.tiominutermvi.data.program.remote.dto

import kotlinx.serialization.Serializable

/**
 * Raw entry of `program_exercises.json`. Enum values stay strings until
 * the program mappers convert them, so unknown values can be skipped.
 */
@Serializable
data class ProgramExerciseDto(
    val exerciseId: Int,
    val name: String,
    val nameEs: String,
    val animation: String,
    val primaryMuscles: List<String>,
    val equipment: String,
    val mechanic: String,
    val impact: String,
    val locations: List<String>,
    val perSide: Boolean = false,
    val duplicateOf: Int? = null
)
