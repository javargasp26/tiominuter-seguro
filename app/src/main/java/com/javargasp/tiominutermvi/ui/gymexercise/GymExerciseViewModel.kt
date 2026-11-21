package com.javargasp.tiominutermvi.ui.gymexercise

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.feedback.WorkoutFeedback
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionRepository
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.repository.GymProgressRepository
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for the Gym Exercise screen: guides the user through every set of
 * one exercise slot ([position]) of a gym routine and marks it as completed
 * after the last set. Every completed set is reported to the gym session
 * ([GymSessionRepository.onSetCompleted]), which keeps the workout's real duration.
 *
 * Countdowns (get ready, timed set, rest) are computed from an end timestamp
 * of [timeSource], so they stay correct if the app goes to the background.
 * At most one countdown job runs at a time; it is cancelled on every phase
 * change and in [onCleared].
 *
 * The routine is observed for the whole life of the screen: the first
 * emission sets the exercise up; if the routine or the slot later disappears
 * (removed from the catalog by a refresh), the countdown stops and the screen
 * shows the not-found state. Later emissions never restart the exercise.
 */
class GymExerciseViewModel(
    private val routineId: Int,
    private val position: Int,
    private val gymRoutineRepository: GymRoutineRepository,
    private val gymProgressRepository: GymProgressRepository,
    private val workoutFeedback: WorkoutFeedback,
    private val timeSource: MonotonicTimeSource,
    private val gymSessions: GymSessionRepository
) : BaseViewModel<GymExerciseIntent, GymExerciseState, GymExerciseEffect>(
    initialState = GymExerciseState(position = position)
) {

    private var hasStartedLoading = false
    private var isFinishing = false
    private var timerJob: Job? = null

    override suspend fun reduce(intent: GymExerciseIntent, currentState: GymExerciseState) {
        when (intent) {
            is GymExerciseIntent.OnLoad -> load()
            is GymExerciseIntent.OnSetDone -> {
                val phase = state.value.phase
                if (phase is GymExercisePhase.PerformingReps) finishSet(phase.setIndex)
            }
            is GymExerciseIntent.OnStartTimedSet -> {
                val phase = state.value.phase
                if (phase is GymExercisePhase.ReadyToStartTimed) startGetReady(phase.setIndex)
            }
            is GymExerciseIntent.OnSkipRest -> {
                val phase = state.value.phase
                if (phase is GymExercisePhase.Resting) startSet(phase.nextSetIndex)
            }
            is GymExerciseIntent.OnBackPressed -> onBackPressed()
            is GymExerciseIntent.OnConfirmExit -> {
                cancelTimer()
                setState(state.value.copy(showExitConfirmation = false))
                sendEffect(GymExerciseEffect.NavigateBack)
            }
            is GymExerciseIntent.OnDismissExit -> setState(
                state.value.copy(showExitConfirmation = false)
            )
        }
    }

    /**
     * Starts observing the routine once; re-sending OnLoad after a
     * configuration change does nothing.
     */
    private fun load() {
        if (hasStartedLoading) return
        hasStartedLoading = true

        gymRoutineRepository.observeGymRoutine(routineId)
            .onEach { routine -> onRoutineEmitted(routine) }
            .launchIn(viewModelScope)
    }

    private fun onRoutineEmitted(routine: GymRoutine?) {
        val current = state.value
        if (current.isNotFound || isFinishing) return

        val index = routine?.exercises?.indexOfFirst { it.position == position } ?: -1
        val exercise = routine?.exercises?.getOrNull(index)
        if (routine == null || exercise == null || exercise.sets == 0) {
            showNotFound()
            return
        }

        if (current.exercise == null) {
            setState(
                current.copy(
                    isLoading = false,
                    routineName = routine.routineName,
                    routineNameEs = routine.routineNameEs,
                    exercise = exercise,
                    exerciseNumber = index + 1,
                    totalExercises = routine.exercises.size
                )
            )
            startSet(0)
        } else {
            // Keep the exercise in progress; only refresh what is displayed around it.
            setState(
                current.copy(
                    routineName = routine.routineName,
                    routineNameEs = routine.routineNameEs,
                    exerciseNumber = index + 1,
                    totalExercises = routine.exercises.size
                )
            )
        }
    }

    private fun showNotFound() {
        cancelTimer()
        setState(
            state.value.copy(
                isLoading = false,
                isNotFound = true,
                phase = null,
                showExitConfirmation = false
            )
        )
    }

    /** Shows the set: REPS waits for "Set done", TIME waits for "Start". */
    private fun startSet(setIndex: Int) {
        val exercise = state.value.exercise ?: return
        val phase = when (exercise.type) {
            ExerciseType.REPS -> GymExercisePhase.PerformingReps(setIndex)
            ExerciseType.TIME -> GymExercisePhase.ReadyToStartTimed(setIndex)
        }
        setPhase(phase)
    }

    private fun startGetReady(setIndex: Int) {
        startCountdown(
            durationSeconds = GET_READY_SECONDS,
            phaseFor = { remaining -> GymExercisePhase.GetReady(setIndex, remaining) },
            onFinished = { startTimedSet(setIndex) }
        )
    }

    private fun startTimedSet(setIndex: Int) {
        val duration = state.value.exercise?.reps?.getOrNull(setIndex) ?: return
        startCountdown(
            durationSeconds = duration,
            phaseFor = { remaining ->
                GymExercisePhase.PerformingTimed(setIndex, remaining, duration)
            },
            onFinished = {
                // The last set gets its feedback once, from completeExercise().
                if (!isLastSet(setIndex)) playFeedback()
                finishSet(setIndex)
            }
        )
    }

    /**
     * After a set: rest (if any) before the next one, or complete the exercise. The gym session
     * notes the set (closing an abandoned session first) without delaying the next phase, so a
     * second tap can't finish the same set twice; the last set is noted in [completeExercise].
     */
    private suspend fun finishSet(setIndex: Int) {
        if (isLastSet(setIndex)) {
            completeExercise()
            return
        }
        viewModelScope.launch { gymSessions.onSetCompleted(routineId) }

        val nextSetIndex = setIndex + 1
        val restSeconds = state.value.exercise?.restSeconds ?: 0
        if (restSeconds > 0) {
            startCountdown(
                durationSeconds = restSeconds,
                phaseFor = { remaining ->
                    GymExercisePhase.Resting(nextSetIndex, remaining, restSeconds)
                },
                onFinished = {
                    playFeedback()
                    startSet(nextSetIndex)
                }
            )
        } else {
            startSet(nextSetIndex)
        }
    }

    private suspend fun completeExercise() {
        if (isFinishing) return
        isFinishing = true
        cancelTimer()
        // Before the marker: completing the routine reads the session's last set
        gymSessions.onSetCompleted(routineId)
        try {
            gymProgressRepository.markCompleted(routineId, position)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // E.g. the routine was removed from the catalog meanwhile (the
            // progress row's foreign key fails): show not-found, don't crash.
            isFinishing = false
            showNotFound()
            return
        }
        playFeedback()
        sendEffect(GymExerciseEffect.NavigateBack)
    }

    private fun onBackPressed() {
        // Completing already navigates back; avoid a second NavigateBack.
        if (isFinishing) return
        if (state.value.completedSets > 0) {
            setState(state.value.copy(showExitConfirmation = true))
        } else {
            cancelTimer()
            sendEffect(GymExerciseEffect.NavigateBack)
        }
    }

    private fun isLastSet(setIndex: Int): Boolean = setIndex >= state.value.totalSets - 1

    /** Every phase change goes through here, so a stale countdown never survives it. */
    private fun setPhase(phase: GymExercisePhase) {
        cancelTimer()
        setState(state.value.copy(phase = phase))
    }

    /**
     * Starts the single countdown job. The remaining time is recomputed from
     * the end timestamp on every tick, and the next tick is scheduled for when
     * the displayed second changes.
     */
    private fun startCountdown(
        durationSeconds: Int,
        phaseFor: (remainingSeconds: Int) -> GymExercisePhase,
        onFinished: suspend () -> Unit
    ) {
        setPhase(phaseFor(durationSeconds))
        val endAtMillis = timeSource.nowMillis() + durationSeconds * MILLIS_PER_SECOND
        timerJob = viewModelScope.launch {
            while (true) {
                val remainingMillis = endAtMillis - timeSource.nowMillis()
                if (remainingMillis <= 0) break
                val remainingSeconds = ((remainingMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
                setState(state.value.copy(phase = phaseFor(remainingSeconds)))
                delay(remainingMillis - (remainingSeconds - 1) * MILLIS_PER_SECOND)
            }
            // Detach before onFinished, which changes the phase and would
            // otherwise cancel this very job.
            timerJob = null
            onFinished()
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun playFeedback() {
        workoutFeedback.playCompletionSound()
        workoutFeedback.vibrate()
    }

    override fun onCleared() {
        cancelTimer()
    }

    companion object {
        const val GET_READY_SECONDS = 5
        private const val MILLIS_PER_SECOND = 1000L
    }
}
