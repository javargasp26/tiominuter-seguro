package com.javargasp.tiominutermvi.ui.program.strengthexercise

import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.StrengthSlot
import com.javargasp.tiominutermvi.ui.base.MviState

/** Where the user is within the sets of one exercise. Set indices start at 0. */
sealed interface ProgramExercisePhase {
    /** REPS set in progress; waits for "Set done". */
    data class PerformingReps(val setIndex: Int) : ProgramExercisePhase

    /** TIME set not started yet; waits for "Start". */
    data class ReadyToStartTimed(val setIndex: Int) : ProgramExercisePhase

    /** 5-second countdown before a TIME set. */
    data class GetReady(val setIndex: Int, val remainingSeconds: Int) : ProgramExercisePhase

    /** TIME set running; [side] is the side being worked for a per-side slot, null otherwise. */
    data class PerformingTimed(
        val setIndex: Int,
        val remainingSeconds: Int,
        val totalSeconds: Int,
        val side: ProgramSide? = null
    ) : ProgramExercisePhase

    /** Per-side TIME set: 5-second pause between the left and the right side. */
    data class SwitchingSides(val setIndex: Int, val remainingSeconds: Int) : ProgramExercisePhase

    /** Rest before [nextSetIndex]; never after the last set. */
    data class Resting(val nextSetIndex: Int, val remainingSeconds: Int, val totalSeconds: Int) : ProgramExercisePhase
}

/** Side worked in a per-side TIME set: always left first, then right. */
enum class ProgramSide { LEFT, RIGHT }

data class ProgramStrengthExerciseState(
    val position: Int = 0,
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val slot: StrengthSlot? = null,
    val exercise: ProgramExercise? = null,
    /** 1-based place of the exercise in the workout, for "Exercise 2 of 6". */
    val exerciseNumber: Int = 0,
    val totalExercises: Int = 0,
    val phase: ProgramExercisePhase? = null,
    val showExitConfirmation: Boolean = false
) : MviState {
    val totalSets: Int get() = slot?.sets ?: 0

    /** Sets finished so far. */
    val completedSets: Int
        get() = when (val p = phase) {
            is ProgramExercisePhase.PerformingReps -> p.setIndex
            is ProgramExercisePhase.ReadyToStartTimed -> p.setIndex
            is ProgramExercisePhase.GetReady -> p.setIndex
            is ProgramExercisePhase.PerformingTimed -> p.setIndex
            is ProgramExercisePhase.SwitchingSides -> p.setIndex
            is ProgramExercisePhase.Resting -> p.nextSetIndex
            null -> 0
        }
}
