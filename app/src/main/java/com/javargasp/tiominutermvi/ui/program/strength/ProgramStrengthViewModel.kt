package com.javargasp.tiominutermvi.ui.program.strength

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.PhotoResult
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.photoAction
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Strength workout: its exercises and which are done. Observes the workout once,
 * in init. When the last exercise becomes completed while this screen is in the
 * back stack, it shows the "Workout completed!" dialog, which can attach a photo to the
 * training-history entry of that completion ([ProgramRepository.observeCompletionEntryId]).
 */
class ProgramStrengthViewModel(
    private val programId: Long,
    private val week: Int,
    private val dayIndex: Int,
    private val repository: ProgramRepository,
    private val photos: TrainingPhotoRepository
) : BaseViewModel<ProgramStrengthIntent, ProgramStrengthState, ProgramStrengthEffect>(
    ProgramStrengthState(week = week)
) {

    init {
        viewModelScope.launch {
            setState(state.value.copy(exercises = repository.getExercises()))
        }
        repository.observeWorkout(programId, week, dayIndex)
            .onEach { onWorkoutEmitted(it) }
            .launchIn(viewModelScope)
        repository.observeCompletionEntryId(programId, week, dayIndex)
            .drop(1) // The value before this visit belongs to an earlier completion
            .onEach { entryId -> setState(state.value.copy(completionPhoto = CompletionPhotoState(entryId = entryId))) }
            .launchIn(viewModelScope)
    }

    private fun onWorkoutEmitted(detail: ProgramWorkoutDetail?) {
        val current = state.value
        val workout = detail?.workout as? ProgramWorkout.Strength
        if (workout == null) {
            setState(
                current.copy(
                    isLoading = false,
                    isNotFound = true,
                    workout = null,
                    showResetConfirmation = false,
                    showWorkoutCompleted = false
                )
            )
            return
        }
        val updated = current.copy(
            isLoading = false,
            isNotFound = false,
            workout = workout,
            completedPositions = detail.completedPositions,
            isWorkoutCompleted = detail.isCompleted
        )
        val justCompleted = !current.isLoading && current.workout != null && !current.allCompleted && updated.allCompleted
        setState(if (justCompleted) updated.copy(showWorkoutCompleted = true) else updated)
    }

    override suspend fun reduce(intent: ProgramStrengthIntent, currentState: ProgramStrengthState) {
        val s = state.value
        when (intent) {
            ProgramStrengthIntent.OnScreenResumed -> {
                // Lazy close of abandoned program workouts; each saved one is announced once
                repository.closeInactiveSessions()
                repository.takeClosedSessions().forEach { sendEffect(ProgramStrengthEffect.ShowSessionSaved(it.durationSeconds)) }
            }
            is ProgramStrengthIntent.OnExerciseClicked ->
                sendEffect(ProgramStrengthEffect.NavigateToExercise(intent.position))
            ProgramStrengthIntent.OnResetClicked ->
                if (s.canReset) setState(s.copy(showResetConfirmation = true))
            ProgramStrengthIntent.OnConfirmReset -> {
                setState(s.copy(showResetConfirmation = false))
                repository.resetWorkout(programId, week, dayIndex)
            }
            ProgramStrengthIntent.OnDismissReset -> setState(s.copy(showResetConfirmation = false))
            ProgramStrengthIntent.OnWorkoutCompletedDismissed -> {
                setState(s.copy(showWorkoutCompleted = false))
                sendEffect(ProgramStrengthEffect.NavigateBack)
            }
            ProgramStrengthIntent.OnBackPressed -> sendEffect(ProgramStrengthEffect.NavigateBack)
            is ProgramStrengthIntent.OnCompletionPhotoSelected -> attachCompletionPhoto(intent.source)
        }
    }

    /** Attaches (or replaces) the photo of the completion's entry; a failure is a message. */
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
        if (result is PhotoResult.Failure) sendEffect(ProgramStrengthEffect.ShowPhotoError(result.error))
    }
}
