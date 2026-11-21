package com.javargasp.tiominutermvi.ui.gymexercise

/**
 * Where the user is inside the sets of one gym exercise.
 * Set indexes are 0-based; the UI shows them 1-based.
 */
sealed interface GymExercisePhase {

    /** REPS set in progress; the user taps "Set done" when finished. */
    data class PerformingReps(val setIndex: Int) : GymExercisePhase

    /** TIME set waiting for the user to tap "Start". */
    data class ReadyToStartTimed(val setIndex: Int) : GymExercisePhase

    /** 5-second countdown before a TIME set starts. */
    data class GetReady(val setIndex: Int, val remainingSeconds: Int) : GymExercisePhase

    /** TIME set countdown. */
    data class PerformingTimed(
        val setIndex: Int,
        val remainingSeconds: Int,
        val totalSeconds: Int
    ) : GymExercisePhase

    /** Rest between sets, before [nextSetIndex]. */
    data class Resting(
        val nextSetIndex: Int,
        val remainingSeconds: Int,
        val totalSeconds: Int
    ) : GymExercisePhase
}
