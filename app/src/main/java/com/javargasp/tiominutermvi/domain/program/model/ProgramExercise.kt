package com.javargasp.tiominutermvi.domain.program.model

/**
 * One entry of the program exercise catalog (`program_exercises.json`).
 * Entries with [duplicateOf] repeat another exercise and are never picked by the generator.
 * [perSide] marks a unilateral exercise: every set is done on the left side, then the right.
 */
data class ProgramExercise(
    val exerciseId: Int,
    val name: String,
    val nameEs: String,
    val animation: String,
    val primaryMuscles: List<MuscleGroup>,
    val equipment: Equipment,
    val mechanic: Mechanic,
    val impact: Impact,
    val locations: Set<ProgramLocation>,
    val perSide: Boolean = false,
    val duplicateOf: Int? = null
) {
    /** Spanish name when [spanish] is true, English otherwise. */
    fun displayName(spanish: Boolean): String = if (spanish) nameEs else name
}
