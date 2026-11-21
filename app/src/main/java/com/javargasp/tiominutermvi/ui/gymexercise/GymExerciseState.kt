package com.javargasp.tiominutermvi.ui.gymexercise

import com.javargasp.tiominutermvi.domain.model.GymRoutineExercise
import com.javargasp.tiominutermvi.ui.base.MviState

/**
 * State for the Gym Exercise screen.
 *
 * [position] is the exercise slot inside the routine (navigation argument);
 * [exerciseNumber] is its 1-based place among the routine's exercises, for
 * display ("Exercise 2 of 11"). [phase] is null while loading or not found.
 */
data class GymExerciseState(
    val isLoading: Boolean = true,
    val routineName: String = "",
    /** Spanish routine name; the screen shows it through `localizedName`. */
    val routineNameEs: String? = null,
    val exercise: GymRoutineExercise? = null,
    val position: Int = 0,
    val exerciseNumber: Int = 0,
    val totalExercises: Int = 0,
    val phase: GymExercisePhase? = null,
    val showExitConfirmation: Boolean = false,
    val isNotFound: Boolean = false
) : MviState {

    /** Number of sets already finished in this visit to the screen. */
    val completedSets: Int
        get() = when (val phase = phase) {
            null -> 0
            is GymExercisePhase.PerformingReps -> phase.setIndex
            is GymExercisePhase.ReadyToStartTimed -> phase.setIndex
            is GymExercisePhase.GetReady -> phase.setIndex
            is GymExercisePhase.PerformingTimed -> phase.setIndex
            is GymExercisePhase.Resting -> phase.nextSetIndex
        }

    /** Total number of sets of the exercise. */
    val totalSets: Int get() = exercise?.sets ?: 0
}
