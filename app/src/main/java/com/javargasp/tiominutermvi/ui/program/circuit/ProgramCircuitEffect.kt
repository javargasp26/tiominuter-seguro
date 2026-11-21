package com.javargasp.tiominutermvi.ui.program.circuit

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface ProgramCircuitEffect : MviEffect {
    data object NavigateBack : ProgramCircuitEffect
    data class ShowPhotoError(val error: TrainingPhotoError) : ProgramCircuitEffect

    /** An unfinished cardio workout was closed (inactive, or replaced by this one) and saved. */
    data class ShowSessionSaved(val durationSeconds: Int) : ProgramCircuitEffect
}
