package com.javargasp.tiominutermvi.ui.program.strengthexercise

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.feedback.WorkoutFeedback
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.program.common.ProgramCountdown
import com.javargasp.tiominutermvi.ui.program.common.ProgramCountdown.Companion.GET_READY_SECONDS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Guides the user through every set of one exercise ([position]) of a strength workout:
 * REPS set → rest → next set; TIME sets start with a 5-second get-ready. No rest after
 * the last set; the last set marks the exercise done, plays sound + vibration and goes back.
 * A per-side TIME set ([com.javargasp.tiominutermvi.domain.program.model.StrengthSlot.perSide])
 * runs left side → [SWITCH_SIDES_SECONDS] switch (sound + vibration when it starts) → right
 * side, then rests. A per-side REPS set is still one "Set done" tap, after both sides.
 * One countdown at a time ([ProgramCountdown]); every phase change cancels it.
 */
class ProgramStrengthExerciseViewModel(
    private val programId: Long,
    private val week: Int,
    private val dayIndex: Int,
    private val position: Int,
    private val repository: ProgramRepository,
    private val feedback: WorkoutFeedback,
    timeSource: MonotonicTimeSource
) : BaseViewModel<ProgramStrengthExerciseIntent, ProgramStrengthExerciseState, ProgramStrengthExerciseEffect>(
    ProgramStrengthExerciseState(position = position)
) {

    private val countdown = ProgramCountdown(viewModelScope, timeSource)
    private var isFinishing = false
    private var exerciseLookup: Map<Int, ProgramExercise> = emptyMap()

    init {
        viewModelScope.launch {
            exerciseLookup = repository.getExercises()
            setState(state.value.copy(exercise = state.value.slot?.let { exerciseLookup[it.exerciseId] }))
        }
        repository.observeWorkout(programId, week, dayIndex)
            .onEach { onWorkoutEmitted(it) }
            .launchIn(viewModelScope)
    }

    private fun onWorkoutEmitted(detail: ProgramWorkoutDetail?) {
        val current = state.value
        if (current.isNotFound || isFinishing) return
        val slots = (detail?.workout as? ProgramWorkout.Strength)?.slots
        val index = slots?.indexOfFirst { it.position == position } ?: -1
        val slot = slots?.getOrNull(index)
        if (slot == null || slot.sets == 0) {
            showNotFound()
            return
        }
        if (current.slot == null) {
            setState(
                current.copy(
                    isLoading = false,
                    slot = slot,
                    exercise = exerciseLookup[slot.exerciseId],
                    exerciseNumber = index + 1,
                    totalExercises = slots.size
                )
            )
            startSet(0)
        }
    }

    override suspend fun reduce(intent: ProgramStrengthExerciseIntent, currentState: ProgramStrengthExerciseState) {
        val phase = state.value.phase
        when (intent) {
            ProgramStrengthExerciseIntent.OnSetDone ->
                if (phase is ProgramExercisePhase.PerformingReps) finishSet(phase.setIndex)
            ProgramStrengthExerciseIntent.OnStartTimedSet ->
                if (phase is ProgramExercisePhase.ReadyToStartTimed) startGetReady(phase.setIndex)
            ProgramStrengthExerciseIntent.OnSkipRest ->
                if (phase is ProgramExercisePhase.Resting) startSet(phase.nextSetIndex)
            ProgramStrengthExerciseIntent.OnBackPressed -> onBackPressed()
            ProgramStrengthExerciseIntent.OnConfirmExit -> {
                countdown.cancel()
                setState(state.value.copy(showExitConfirmation = false))
                sendEffect(ProgramStrengthExerciseEffect.NavigateBack)
            }
            ProgramStrengthExerciseIntent.OnDismissExit -> setState(state.value.copy(showExitConfirmation = false))
        }
    }

    private fun showNotFound() {
        countdown.cancel()
        setState(
            state.value.copy(isLoading = false, isNotFound = true, phase = null, showExitConfirmation = false)
        )
    }

    private fun startSet(setIndex: Int) {
        val slot = state.value.slot ?: return
        setPhase(
            when (slot.type) {
                ProgramSlotType.REPS -> ProgramExercisePhase.PerformingReps(setIndex)
                ProgramSlotType.TIME -> ProgramExercisePhase.ReadyToStartTimed(setIndex)
            }
        )
    }

    private fun startGetReady(setIndex: Int) {
        countdown.start(
            durationSeconds = GET_READY_SECONDS,
            onTick = { setPhaseKeepingTimer(ProgramExercisePhase.GetReady(setIndex, it)) },
            onFinished = { startTimedSet(setIndex) }
        )
    }

    private fun startTimedSet(setIndex: Int) {
        val perSide = state.value.slot?.perSide ?: return
        startTimedSide(setIndex, if (perSide) ProgramSide.LEFT else null)
    }

    /** Runs one TIME set, or one side of it when [side] is not null. */
    private fun startTimedSide(setIndex: Int, side: ProgramSide?) {
        val duration = state.value.slot?.reps?.getOrNull(setIndex) ?: return
        countdown.start(
            durationSeconds = duration,
            onTick = { setPhaseKeepingTimer(ProgramExercisePhase.PerformingTimed(setIndex, it, duration, side)) },
            onFinished = {
                if (side == ProgramSide.LEFT) {
                    playFeedback()
                    startSwitchSides(setIndex)
                } else {
                    // The last set gets its feedback once, on completion.
                    if (!isLastSet(setIndex)) playFeedback()
                    finishSet(setIndex)
                }
            }
        )
    }

    private fun startSwitchSides(setIndex: Int) {
        countdown.start(
            durationSeconds = SWITCH_SIDES_SECONDS,
            onTick = { setPhaseKeepingTimer(ProgramExercisePhase.SwitchingSides(setIndex, it)) },
            onFinished = { startTimedSide(setIndex, ProgramSide.RIGHT) }
        )
    }

    /**
     * After a set: rest (if any) before the next one, or complete the exercise. The workout's
     * session notes the set (closing an abandoned session first) without delaying the next phase;
     * the last set is noted in [completeExercise], before the slot is marked.
     */
    private suspend fun finishSet(setIndex: Int) {
        if (isLastSet(setIndex)) {
            completeExercise()
            return
        }
        viewModelScope.launch { repository.onStrengthSetCompleted(programId, week, dayIndex) }
        val next = setIndex + 1
        val rest = state.value.slot?.restSeconds ?: 0
        if (rest > 0) {
            countdown.start(
                durationSeconds = rest,
                onTick = { setPhaseKeepingTimer(ProgramExercisePhase.Resting(next, it, rest)) },
                onFinished = {
                    playFeedback()
                    startSet(next)
                }
            )
        } else {
            startSet(next)
        }
    }

    private suspend fun completeExercise() {
        if (isFinishing) return
        isFinishing = true
        countdown.cancel()
        // Before the slot: completing the workout reads the session's last set
        repository.onStrengthSetCompleted(programId, week, dayIndex)
        try {
            repository.markSlotCompleted(programId, week, dayIndex, position)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            isFinishing = false
            showNotFound()
            return
        }
        playFeedback()
        sendEffect(ProgramStrengthExerciseEffect.NavigateBack)
    }

    private fun onBackPressed() {
        if (isFinishing) return
        if (state.value.completedSets > 0) {
            setState(state.value.copy(showExitConfirmation = true))
        } else {
            countdown.cancel()
            sendEffect(ProgramStrengthExerciseEffect.NavigateBack)
        }
    }

    private fun isLastSet(setIndex: Int): Boolean = setIndex >= state.value.totalSets - 1

    /** A phase change from the user: stops any running countdown. */
    private fun setPhase(phase: ProgramExercisePhase) {
        countdown.cancel()
        setState(state.value.copy(phase = phase))
    }

    /** A tick of the running countdown: updates the phase without cancelling it. */
    private fun setPhaseKeepingTimer(phase: ProgramExercisePhase) {
        setState(state.value.copy(phase = phase))
    }

    private fun playFeedback() {
        feedback.playCompletionSound()
        feedback.vibrate()
    }

    override fun onCleared() {
        countdown.cancel()
    }

    companion object {
        /** Pause between the left and the right side of a per-side TIME set. */
        const val SWITCH_SIDES_SECONDS = 5
    }
}
