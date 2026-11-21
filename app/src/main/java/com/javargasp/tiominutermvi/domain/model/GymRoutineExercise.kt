package com.javargasp.tiominutermvi.domain.model

/**
 * One exercise slot inside a [GymRoutine].
 *
 * [exercise] is null when the slot's [exerciseId] is not in the exercise
 * catalog; the UI will show a fallback in that case.
 *
 * [reps] holds one entry per set: a duration in seconds for [ExerciseType.TIME],
 * a rep count for [ExerciseType.REPS]. The number of sets is derived.
 */
data class GymRoutineExercise(
    val position: Int,
    val exerciseId: Int,
    val exercise: Exercise?,
    val type: ExerciseType,
    val reps: List<Int>,
    val restSeconds: Int
) {
    /** Number of sets: one entry in [reps] per set. */
    val sets: Int get() = reps.size
}
