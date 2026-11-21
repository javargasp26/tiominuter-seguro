package com.javargasp.tiominutermvi.ui.program.outdoorrun

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.PhotoResult
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.photoAction
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState
import com.javargasp.tiominutermvi.ui.program.common.ProgramCountdown.Companion.MILLIS_PER_SECOND
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * Outdoor run: a stopwatch with Start / Pause / Resume and a confirmed Finish that
 * marks the workout completed. Elapsed time comes from [timeSource] (accumulated
 * time plus the current running stretch), so it stays correct in the background.
 * The training history gets the stopwatch time (pauses excluded).
 */
class ProgramOutdoorRunViewModel(
    private val programId: Long,
    private val week: Int,
    private val dayIndex: Int,
    private val repository: ProgramRepository,
    private val timeSource: MonotonicTimeSource,
    private val photos: TrainingPhotoRepository,
    cardioSessions: ActiveSessionTracker
) : BaseViewModel<ProgramOutdoorRunIntent, ProgramOutdoorRunState, ProgramOutdoorRunEffect>(
    ProgramOutdoorRunState(week = week)
) {

    /**
     * The run's stopwatch (pauses excluded) persisted as a session. It counts in the background
     * too: a run is done with the phone in a pocket.
     */
    private val session = ActiveWorkoutSession(
        viewModelScope, cardioSessions, ActiveTimeMeter(timeSource),
        programRefId(programId, week, dayIndex)
    )

    init {
        // Active time = while the stopwatch runs (pauses excluded), in the foreground or the background:
        // a run is done with the phone in a pocket
        state.map { it.isRunning }
            .distinctUntilChanged()
            .onEach { session.setTimerRunning(it) }
            .launchIn(viewModelScope)
    }

    private var accumulatedMillis = 0L
    private var runningSinceMillis = 0L
    private var tickJob: Job? = null

    init {
        repository.observeCompletionEntryId(programId, week, dayIndex)
            .drop(1) // The value before this visit belongs to an earlier completion
            .onEach { entryId -> setState(state.value.copy(completionPhoto = CompletionPhotoState(entryId = entryId))) }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            val workout = repository.observeWorkout(programId, week, dayIndex).first()?.workout
            if (workout is ProgramWorkout.OutdoorRun) {
                setState(state.value.copy(isLoading = false, distanceKm = workout.distanceKm, walk = workout.walk))
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
        if (result is PhotoResult.Failure) sendEffect(ProgramOutdoorRunEffect.ShowPhotoError(result.error))
    }

    override suspend fun reduce(intent: ProgramOutdoorRunIntent, currentState: ProgramOutdoorRunState) {
        val s = state.value
        if (intent.isUserAction) session.onUserAction()
        when (intent) {
            ProgramOutdoorRunIntent.OnStart -> if (!s.isStarted) {
                setState(s.copy(isStarted = true))
                session.start().forEach { sendEffect(ProgramOutdoorRunEffect.ShowSessionSaved(it.durationSeconds)) }
                startStopwatch()
            }
            ProgramOutdoorRunIntent.OnPause -> if (s.isRunning) pauseStopwatch()
            ProgramOutdoorRunIntent.OnResume -> if (s.isStarted && !s.isRunning && !s.isFinished) startStopwatch()
            ProgramOutdoorRunIntent.OnFinishClicked -> if (s.isStarted && !s.isFinished) {
                setState(s.copy(showFinishConfirmation = true))
            }
            ProgramOutdoorRunIntent.OnConfirmFinish -> finish()
            ProgramOutdoorRunIntent.OnDismissFinish -> setState(s.copy(showFinishConfirmation = false))
            ProgramOutdoorRunIntent.OnDone -> sendEffect(ProgramOutdoorRunEffect.NavigateBack)
            is ProgramOutdoorRunIntent.OnCompletionPhotoSelected -> attachCompletionPhoto(intent.source)
            ProgramOutdoorRunIntent.OnBackPressed ->
                if (s.isStarted && !s.isFinished) {
                    setState(s.copy(showExitConfirmation = true))
                } else {
                    stopTicking()
                    sendEffect(ProgramOutdoorRunEffect.NavigateBack)
                }
            ProgramOutdoorRunIntent.OnConfirmExit -> {
                stopTicking()
                setState(s.copy(showExitConfirmation = false, isRunning = false))
                sendEffect(ProgramOutdoorRunEffect.NavigateBack)
            }
            ProgramOutdoorRunIntent.OnDismissExit -> setState(s.copy(showExitConfirmation = false))
        }
    }

    private fun startStopwatch() {
        runningSinceMillis = timeSource.nowMillis()
        setState(state.value.copy(isRunning = true))
        stopTicking()
        tickJob = viewModelScope.launch {
            while (true) {
                val elapsed = elapsedMillis()
                setState(state.value.copy(elapsedSeconds = (elapsed / MILLIS_PER_SECOND).toInt()))
                delay(MILLIS_PER_SECOND - elapsed % MILLIS_PER_SECOND)
            }
        }
    }

    private fun pauseStopwatch() {
        accumulatedMillis = elapsedMillis()
        stopTicking()
        setState(state.value.copy(isRunning = false, elapsedSeconds = (accumulatedMillis / MILLIS_PER_SECOND).toInt()))
    }

    private suspend fun finish() {
        if (state.value.isRunning) pauseStopwatch()
        setState(state.value.copy(showFinishConfirmation = false))
        try {
            // The session's active time is the stopwatch (both exclude pauses), from its own start
            // when an inactive session was closed meanwhile
            repository.markWorkoutCompleted(programId, week, dayIndex, session.finish())
            setState(state.value.copy(isFinished = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setState(state.value.copy(isNotFound = true))
        }
    }

    private fun elapsedMillis(): Long =
        if (state.value.isRunning) accumulatedMillis + timeSource.nowMillis() - runningSinceMillis else accumulatedMillis

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    override fun onCleared() {
        stopTicking()
        session.close()
    }
}

/** Actions of the person in the workout (not leaving or the screen's visibility). */
private val ProgramOutdoorRunIntent.isUserAction: Boolean
    get() = this is ProgramOutdoorRunIntent.OnPause || this is ProgramOutdoorRunIntent.OnResume ||
        this is ProgramOutdoorRunIntent.OnFinishClicked || this is ProgramOutdoorRunIntent.OnDismissFinish ||
        this is ProgramOutdoorRunIntent.OnBackPressed || this is ProgramOutdoorRunIntent.OnDismissExit
