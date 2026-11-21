package com.javargasp.tiominutermvi.ui.program.circuit

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.feedback.WorkoutFeedback
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.PhotoResult
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.photoAction
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState
import com.javargasp.tiominutermvi.ui.program.common.ProgramCountdown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.javargasp.tiominutermvi.domain.session.ActiveWorkoutSession
import com.javargasp.tiominutermvi.domain.session.ActiveTimeMeter
import com.javargasp.tiominutermvi.domain.session.ActiveSessionTracker
import com.javargasp.tiominutermvi.domain.program.repository.programRefId
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Home circuit: for each round, every exercise is worked for workSeconds with
 * restSeconds between them, then restBetweenRoundsSeconds before the next round.
 * Sound + vibration at every change; Skip ends the current phase early.
 * The workout is marked completed after the last exercise of the last round.
 */
class ProgramCircuitViewModel(
    private val programId: Long,
    private val week: Int,
    private val dayIndex: Int,
    private val repository: ProgramRepository,
    private val feedback: WorkoutFeedback,
    private val timeSource: MonotonicTimeSource,
    private val photos: TrainingPhotoRepository,
    cardioSessions: ActiveSessionTracker
) : BaseViewModel<ProgramCircuitIntent, ProgramCircuitState, ProgramCircuitEffect>(
    ProgramCircuitState(week = week)
) {

    private val countdown = ProgramCountdown(viewModelScope, timeSource)

    /** Active time: from Start while visible (the background excluded; no pause), persisted. */
    private val session = ActiveWorkoutSession(
        viewModelScope, cardioSessions, ActiveTimeMeter(timeSource), programRefId(programId, week, dayIndex)
    )

    init {
        // Active time = from Start to the end: the countdown always runs (work, rest, rest between
        // rounds), in the foreground or the background
        state.map { it.phase != null && !it.isFinished }
            .distinctUntilChanged()
            .onEach { session.setTimerRunning(it) }
            .launchIn(viewModelScope)
    }

    init {
        repository.observeCompletionEntryId(programId, week, dayIndex)
            .drop(1) // The value before this visit belongs to an earlier completion
            .onEach { entryId -> setState(state.value.copy(completionPhoto = CompletionPhotoState(entryId = entryId))) }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            val workout = repository.observeWorkout(programId, week, dayIndex).first()?.workout
            if (workout is ProgramWorkout.HomeCircuit && workout.exerciseIds.isNotEmpty() && workout.rounds > 0) {
                setState(state.value.copy(isLoading = false, workout = workout))
                setState(state.value.copy(exercises = repository.getExercises()))
            } else {
                setState(state.value.copy(isLoading = false, isNotFound = true))
            }
        }
    }

    /** Attaches (or replaces) the photo of this completion's entry; a failure is a message. */
    private suspend fun attachCompletionPhoto(source: TrainingPhotoSource) {
        val photo = state.value.completionPhoto
        val entryId = photo.entryId ?: return
        if (!photo.canAddPhoto) return
        setState(state.value.copy(completionPhoto = photo.copy(isProcessing = true)))
        val result = photoAction { photos.attach(entryId, source) }
        val current = state.value.completionPhoto
        if (current.entryId == entryId) {
            val photoPath = (result as? PhotoResult.Success)?.value ?: current.photoPath
            setState(state.value.copy(completionPhoto = current.copy(isProcessing = false, photoPath = photoPath)))
        }
        if (result is PhotoResult.Failure) sendEffect(ProgramCircuitEffect.ShowPhotoError(result.error))
    }

    override suspend fun reduce(intent: ProgramCircuitIntent, currentState: ProgramCircuitState) {
        val s = state.value
        if (intent.isUserAction) session.onUserAction()
        when (intent) {
            ProgramCircuitIntent.OnStart -> if (s.phase == null && s.workout != null && !s.isFinished) {
                session.start().forEach { sendEffect(ProgramCircuitEffect.ShowSessionSaved(it.durationSeconds)) }
                startWork(1, 0)
            }
            ProgramCircuitIntent.OnSkip -> if (s.phase != null && !s.isFinished) endPhase()
            ProgramCircuitIntent.OnDone -> sendEffect(ProgramCircuitEffect.NavigateBack)
            is ProgramCircuitIntent.OnCompletionPhotoSelected -> attachCompletionPhoto(intent.source)
            ProgramCircuitIntent.OnBackPressed ->
                if (s.phase != null && !s.isFinished) {
                    setState(s.copy(showExitConfirmation = true))
                } else {
                    countdown.cancel()
                    sendEffect(ProgramCircuitEffect.NavigateBack)
                }
            ProgramCircuitIntent.OnConfirmExit -> {
                countdown.cancel()
                // The phase stays in the state while leaving: the timer stops here (recorded after
                // 2 hours of inactivity)
                session.setTimerRunning(false)
                setState(s.copy(showExitConfirmation = false))
                sendEffect(ProgramCircuitEffect.NavigateBack)
            }
            ProgramCircuitIntent.OnDismissExit -> setState(s.copy(showExitConfirmation = false))
        }
    }

    private fun startWork(round: Int, index: Int) {
        val seconds = state.value.workout?.workSeconds ?: return
        countdown.start(
            durationSeconds = seconds,
            onTick = { setState(state.value.copy(phase = CircuitPhase.Work(round, index, it, seconds))) },
            onFinished = { endPhase() }
        )
    }

    /** End of the current phase (time is up, or Skip): feedback, then the next phase. */
    private suspend fun endPhase() {
        val workout = state.value.workout ?: return
        countdown.cancel()
        playFeedback()
        when (val phase = state.value.phase) {
            is CircuitPhase.Work -> {
                val lastInRound = phase.exerciseIndex >= workout.exerciseIds.size - 1
                when {
                    lastInRound && phase.round >= workout.rounds -> finish()
                    lastInRound -> startRest(
                        seconds = workout.restBetweenRoundsSeconds,
                        phaseFor = { remaining, total -> CircuitPhase.RoundRest(phase.round + 1, remaining, total) },
                        then = { startWork(phase.round + 1, 0) }
                    )
                    else -> startRest(
                        seconds = workout.restSeconds,
                        phaseFor = { remaining, total ->
                            CircuitPhase.Rest(phase.round, phase.exerciseIndex + 1, remaining, total)
                        },
                        then = { startWork(phase.round, phase.exerciseIndex + 1) }
                    )
                }
            }
            is CircuitPhase.Rest -> startWork(phase.round, phase.nextExerciseIndex)
            is CircuitPhase.RoundRest -> startWork(phase.nextRound, 0)
            null -> Unit
        }
    }

    private fun startRest(seconds: Int, phaseFor: (Int, Int) -> CircuitPhase, then: () -> Unit) {
        if (seconds <= 0) {
            then()
            return
        }
        countdown.start(
            durationSeconds = seconds,
            onTick = { setState(state.value.copy(phase = phaseFor(it, seconds))) },
            onFinished = { endPhase() }
        )
    }

    private suspend fun finish() {
        setState(state.value.copy(isFinished = true, phase = null))
        try {
            repository.markWorkoutCompleted(programId, week, dayIndex, session.finish())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setState(state.value.copy(isNotFound = true))
        }
    }

    private fun playFeedback() {
        feedback.playCompletionSound()
        feedback.vibrate()
    }

    override fun onCleared() {
        countdown.cancel()
        session.close()
    }
}

/** Actions of the person in the workout (not leaving or the screen's visibility). */
private val ProgramCircuitIntent.isUserAction: Boolean
    get() = this is ProgramCircuitIntent.OnSkip || this is ProgramCircuitIntent.OnBackPressed ||
        this is ProgramCircuitIntent.OnDismissExit
