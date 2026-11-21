package com.javargasp.tiominutermvi.ui.program.strength

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface ProgramStrengthIntent : MviIntent {
    data class OnExerciseClicked(val position: Int) : ProgramStrengthIntent
    data object OnResetClicked : ProgramStrengthIntent
    data object OnConfirmReset : ProgramStrengthIntent
    data object OnDismissReset : ProgramStrengthIntent
    data object OnWorkoutCompletedDismissed : ProgramStrengthIntent
    data class OnCompletionPhotoSelected(val source: TrainingPhotoSource) : ProgramStrengthIntent
    data object OnBackPressed : ProgramStrengthIntent

    /** The screen was opened or came back to the foreground: close inactive program sessions. */
    data object OnScreenResumed : ProgramStrengthIntent
}
