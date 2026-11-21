package com.javargasp.tiominutermvi.ui.program.outdoorrun

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface ProgramOutdoorRunEffect : MviEffect {
    data object NavigateBack : ProgramOutdoorRunEffect
    data class ShowPhotoError(val error: TrainingPhotoError) : ProgramOutdoorRunEffect

    /** An unfinished cardio workout was closed (inactive, or replaced by this one) and saved. */
    data class ShowSessionSaved(val durationSeconds: Int) : ProgramOutdoorRunEffect
}
