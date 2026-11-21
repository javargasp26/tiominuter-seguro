package com.javargasp.tiominutermvi.ui.program.strength

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface ProgramStrengthEffect : MviEffect {
    data class NavigateToExercise(val position: Int) : ProgramStrengthEffect
    data object NavigateBack : ProgramStrengthEffect
    data class ShowPhotoError(val error: TrainingPhotoError) : ProgramStrengthEffect

    /** An unfinished program workout was closed for inactivity and saved with this duration. */
    data class ShowSessionSaved(val durationSeconds: Int) : ProgramStrengthEffect
}
