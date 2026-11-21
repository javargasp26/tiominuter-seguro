package com.javargasp.tiominutermvi.ui.gymroutine

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.base.MviIntent

/**
 * Intents for the Gym Routine screen
 */
sealed class GymRoutineIntent : MviIntent {
    object OnLoad : GymRoutineIntent()

    /** The screen was opened or came back to the foreground: close inactive gym sessions. */
    object OnResume : GymRoutineIntent()
    data class OnExerciseClicked(val position: Int) : GymRoutineIntent()
    object OnResetClicked : GymRoutineIntent()
    object OnConfirmReset : GymRoutineIntent()
    object OnDismissReset : GymRoutineIntent()
    object OnRoutineCompletedDismissed : GymRoutineIntent()
    data class OnCompletionPhotoSelected(val source: TrainingPhotoSource) : GymRoutineIntent()
    object OnBackPressed : GymRoutineIntent()
}
