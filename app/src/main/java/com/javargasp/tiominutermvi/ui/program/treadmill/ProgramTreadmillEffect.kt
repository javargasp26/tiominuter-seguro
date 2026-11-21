package com.javargasp.tiominutermvi.ui.program.treadmill

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface ProgramTreadmillEffect : MviEffect {
    data object NavigateBack : ProgramTreadmillEffect
    data class ShowPhotoError(val error: TrainingPhotoError) : ProgramTreadmillEffect

    /** An unfinished cardio workout was closed (inactive, or replaced by this one) and saved. */
    data class ShowSessionSaved(val durationSeconds: Int) : ProgramTreadmillEffect
}
