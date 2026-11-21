package com.javargasp.tiominutermvi.ui.program.circuit

import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.ui.base.MviState
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState

/** Where the user is in the circuit. [round] is 1-based, exercise indices 0-based. */
sealed interface CircuitPhase {
    data class Work(val round: Int, val exerciseIndex: Int, val remainingSeconds: Int, val totalSeconds: Int) : CircuitPhase

    /** Rest between two exercises of a round; shows the next one. */
    data class Rest(val round: Int, val nextExerciseIndex: Int, val remainingSeconds: Int, val totalSeconds: Int) : CircuitPhase

    /** Rest between rounds, before [nextRound]. */
    data class RoundRest(val nextRound: Int, val remainingSeconds: Int, val totalSeconds: Int) : CircuitPhase
}

data class ProgramCircuitState(
    val week: Int = 1,
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val workout: ProgramWorkout.HomeCircuit? = null,
    val exercises: Map<Int, ProgramExercise> = emptyMap(),
    /** null before Start. */
    val phase: CircuitPhase? = null,
    val isFinished: Boolean = false,
    val showExitConfirmation: Boolean = false,
    /** Photo of this completion, offered on the finished view (training-history entry + photo). */
    val completionPhoto: CompletionPhotoState = CompletionPhotoState()
) : MviState {
    val totalRounds: Int get() = workout?.rounds ?: 0

    val currentRound: Int
        get() = when (val p = phase) {
            is CircuitPhase.Work -> p.round
            is CircuitPhase.Rest -> p.round
            is CircuitPhase.RoundRest -> p.nextRound
            null -> 1
        }

    /** The exercise being worked, or the next one during a rest. */
    val shownExerciseId: Int?
        get() {
            val ids = workout?.exerciseIds ?: return null
            return when (val p = phase) {
                is CircuitPhase.Work -> ids.getOrNull(p.exerciseIndex)
                is CircuitPhase.Rest -> ids.getOrNull(p.nextExerciseIndex)
                is CircuitPhase.RoundRest, null -> ids.firstOrNull()
            }
        }
}
