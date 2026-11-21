package com.javargasp.tiominutermvi.ui.gymroutine

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionRepository
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.repository.GymProgressRepository
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.PhotoResult
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.photoAction
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for the Gym Routine screen.
 *
 * Both repository observations start exactly once per ViewModel instance (in
 * [init]), so re-sending [GymRoutineIntent.OnLoad] after a configuration
 * change does not re-subscribe. The collector always reads the latest state
 * via [state.value] when updating, never a stale snapshot.
 *
 * When the "Routine completed!" dialog is first shown (every exercise of a non-empty
 * routine done) a GYM entry is recorded in the training history, once per completion:
 * the dialog flag stays set until the dismiss resets the progress. The dialog can attach
 * a photo to that entry ([GymRoutineState.completionPhoto]); a photo still being saved
 * when the dialog closes is finished in the background, and a failure is only a message.
 */
class GymRoutineViewModel(
    private val routineId: Int,
    private val gymRoutineRepository: GymRoutineRepository,
    private val gymProgressRepository: GymProgressRepository,
    private val gymSessions: GymSessionRepository,
    private val photos: TrainingPhotoRepository
) : BaseViewModel<GymRoutineIntent, GymRoutineState, GymRoutineEffect>(
    initialState = GymRoutineState()
) {

    init {
        observeRoutine()
    }

    override suspend fun reduce(intent: GymRoutineIntent, currentState: GymRoutineState) {
        when (intent) {
            is GymRoutineIntent.OnLoad -> Unit // Observation is already running.
            is GymRoutineIntent.OnResume -> closeInactiveSessions()
            is GymRoutineIntent.OnExerciseClicked -> sendEffect(
                GymRoutineEffect.NavigateToExercise(routineId, intent.position)
            )
            is GymRoutineIntent.OnResetClicked -> showResetConfirmation(currentState)
            is GymRoutineIntent.OnConfirmReset -> confirmReset(currentState)
            is GymRoutineIntent.OnDismissReset -> setState(
                state.value.copy(showResetConfirmation = false)
            )
            is GymRoutineIntent.OnRoutineCompletedDismissed -> dismissRoutineCompleted()
            is GymRoutineIntent.OnCompletionPhotoSelected -> attachCompletionPhoto(intent.source)
            is GymRoutineIntent.OnBackPressed -> sendEffect(GymRoutineEffect.NavigateBack)
        }
    }

    private fun observeRoutine() {
        combine(
            gymRoutineRepository.observeGymRoutine(routineId),
            gymProgressRepository.observeCompletedPositions(routineId)
        ) { routine, completedPositions ->
            routine to completedPositions
        }
            .onEach { (routine, completedPositions) ->
                val current = state.value
                // A routine removed from the catalog while the screen is open
                // shows the not-found state and closes any open dialog.
                val isNotFound = routine == null
                val showRoutineCompleted = !isNotFound && (current.showRoutineCompleted ||
                    isJustCompleted(routine, completedPositions))
                setState(
                    current.copy(
                        isLoading = false,
                        routine = routine,
                        isNotFound = isNotFound,
                        completedPositions = completedPositions,
                        showResetConfirmation = current.showResetConfirmation && !isNotFound,
                        showRoutineCompleted = showRoutineCompleted
                    )
                )
                if (routine != null && showRoutineCompleted && !current.showRoutineCompleted) {
                    recordCompletion(routine)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * True when the routine is non-empty and every position is completed.
     * Used to trigger the celebration dialog exactly once (the state flag
     * keeps it true afterwards until the user dismisses it).
     */
    private fun isJustCompleted(routine: GymRoutine?, completedPositions: Set<Int>): Boolean {
        if (routine == null || routine.exercises.isEmpty()) return false
        val positions = routine.exercises.map { it.position }.toSet()
        return completedPositions.containsAll(positions)
    }

    /**
     * Records the completed routine before any reset: its session's entry (first → last set).
     * No entry when the session was already recorded (the dialog shown again after the process
     * was killed) or recording failed; failures are only logged.
     */
    private fun recordCompletion(routine: GymRoutine) {
        setState(state.value.copy(completionPhoto = CompletionPhotoState()))
        viewModelScope.launch {
            val entryId = gymSessions.completeSession(routineId, routine.routineName)
            // Without an entry (recording failed) there is nothing to attach a photo to
            if (entryId != null) setState(state.value.copy(completionPhoto = CompletionPhotoState(entryId = entryId)))
        }
    }

    /** Attaches (or replaces) the photo of the entry recorded for this completion. */
    private suspend fun attachCompletionPhoto(source: TrainingPhotoSource) {
        val photo = state.value.completionPhoto
        val entryId = photo.entryId ?: return
        if (!photo.canAddPhoto) return
        setState(state.value.copy(completionPhoto = photo.copy(isProcessing = true)))
        val result = photoAction { photos.attach(entryId, source) }
        val current = state.value.completionPhoto
        // A newer completion (dismiss + redo) has its own entry: leave it alone
        if (current.entryId != entryId) {
            if (result is PhotoResult.Failure) sendEffect(GymRoutineEffect.ShowPhotoError(result.error))
            return
        }
        when (result) {
            is PhotoResult.Success ->
                setState(state.value.copy(completionPhoto = current.copy(isProcessing = false, photoPath = result.value)))
            is PhotoResult.Failure -> {
                setState(state.value.copy(completionPhoto = current.copy(isProcessing = false)))
                sendEffect(GymRoutineEffect.ShowPhotoError(result.error))
            }
        }
    }

    private suspend fun showResetConfirmation(currentState: GymRoutineState) {
        if (currentState.canReset) {
            setState(state.value.copy(showResetConfirmation = true))
        }
    }

    private suspend fun confirmReset(currentState: GymRoutineState) {
        if (!currentState.showResetConfirmation) return
        setState(state.value.copy(showResetConfirmation = false))
        gymProgressRepository.resetRoutine(routineId)
        // Reset by hand: the work is discarded, not saved
        gymSessions.discardSession(routineId)
    }

    /** Lazy close of abandoned gym sessions; each saved one is announced once. */
    private suspend fun closeInactiveSessions() {
        gymSessions.closeInactiveSessions()
        gymSessions.takeClosedSessions().forEach { sendEffect(GymRoutineEffect.ShowSessionSaved(it.durationSeconds)) }
    }

    private suspend fun dismissRoutineCompleted() {
        gymProgressRepository.resetRoutine(routineId)
        setState(state.value.copy(showRoutineCompleted = false))
    }
}
