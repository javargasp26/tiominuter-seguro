package com.javargasp.tiominutermvi.ui.program.treadmill

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
 * Treadmill session: one countdown per segment, Start / Pause / Resume and Skip.
 * Sound + vibration at every segment change; the workout is marked completed after
 * the last segment.
 */
class ProgramTreadmillViewModel(
    private val programId: Long,
    private val week: Int,
    private val dayIndex: Int,
    private val repository: ProgramRepository,
    private val feedback: WorkoutFeedback,
    private val timeSource: MonotonicTimeSource,
    private val photos: TrainingPhotoRepository,
    cardioSessions: ActiveSessionTracker
) : BaseViewModel<ProgramTreadmillIntent, ProgramTreadmillState, ProgramTreadmillEffect>(
    ProgramTreadmillState(week = week)
) {

    private val countdown = ProgramCountdown(viewModelScope, timeSource)

    /** Active time: while running and visible (pauses and the background excluded), persisted. */
    private val session = ActiveWorkoutSession(
        viewModelScope, cardioSessions, ActiveTimeMeter(timeSource), programRefId(programId, week, dayIndex)
    )

    init {
        // Active time = while the countdown runs (not paused, not before Start or after the end),
        // in the foreground or the background
        state.map { it.isRunning }
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
            if (workout is ProgramWorkout.Treadmill && workout.segments.isNotEmpty()) {
                setState(
                    state.value.copy(
                        isLoading = false,
                        segments = workout.segments,
                        remainingSeconds = workout.segments.first().durationSeconds
                    )
                )
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
        if (result is PhotoResult.Failure) sendEffect(ProgramTreadmillEffect.ShowPhotoError(result.error))
    }

    override suspend fun reduce(intent: ProgramTreadmillIntent, currentState: ProgramTreadmillState) {
        val s = state.value
        if (intent.isUserAction) session.onUserAction()
        when (intent) {
            ProgramTreadmillIntent.OnStart -> if (!s.isStarted && s.segments.isNotEmpty()) {
                setState(s.copy(isStarted = true))
                session.start().forEach { sendEffect(ProgramTreadmillEffect.ShowSessionSaved(it.durationSeconds)) }
                run()
            }
            ProgramTreadmillIntent.OnPause -> if (s.isRunning) {
                countdown.cancel()
                setState(s.copy(isRunning = false))
            }
            ProgramTreadmillIntent.OnResume -> if (s.isStarted && !s.isRunning && !s.isFinished) run()
            ProgramTreadmillIntent.OnSkipSegment -> if (s.isStarted && !s.isFinished) nextSegment()
            ProgramTreadmillIntent.OnDone -> sendEffect(ProgramTreadmillEffect.NavigateBack)
            is ProgramTreadmillIntent.OnCompletionPhotoSelected -> attachCompletionPhoto(intent.source)
            ProgramTreadmillIntent.OnBackPressed ->
                if (s.isStarted && !s.isFinished) {
                    setState(s.copy(showExitConfirmation = true))
                } else {
                    countdown.cancel()
                    sendEffect(ProgramTreadmillEffect.NavigateBack)
                }
            ProgramTreadmillIntent.OnConfirmExit -> {
                countdown.cancel()
                setState(s.copy(showExitConfirmation = false, isRunning = false))
                sendEffect(ProgramTreadmillEffect.NavigateBack)
            }
            ProgramTreadmillIntent.OnDismissExit -> setState(s.copy(showExitConfirmation = false))
        }
    }

    /** Runs the current segment's countdown from its remaining time. */
    private fun run() {
        setState(state.value.copy(isRunning = true))
        countdown.start(
            durationSeconds = state.value.remainingSeconds,
            onTick = { setState(state.value.copy(remainingSeconds = it)) },
            onFinished = { nextSegment() }
        )
    }

    /** Moves to the next segment (or finishes), with sound + vibration. Keeps paused if paused. */
    private suspend fun nextSegment() {
        countdown.cancel()
        playFeedback()
        val s = state.value
        val next = s.currentIndex + 1
        if (next >= s.segments.size) {
            finish()
            return
        }
        setState(s.copy(currentIndex = next, remainingSeconds = s.segments[next].durationSeconds))
        if (s.isRunning) run()
    }

    private suspend fun finish() {
        setState(state.value.copy(isRunning = false, isFinished = true, remainingSeconds = 0))
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
private val ProgramTreadmillIntent.isUserAction: Boolean
    get() = this is ProgramTreadmillIntent.OnPause || this is ProgramTreadmillIntent.OnResume ||
        this is ProgramTreadmillIntent.OnSkipSegment || this is ProgramTreadmillIntent.OnBackPressed ||
        this is ProgramTreadmillIntent.OnDismissExit
