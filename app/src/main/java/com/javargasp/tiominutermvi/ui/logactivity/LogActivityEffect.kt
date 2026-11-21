package com.javargasp.tiominutermvi.ui.logactivity

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.ui.base.MviEffect

/** Effects for the log-activity screen. */
sealed class LogActivityEffect : MviEffect {
    data object NavigateBack : LogActivityEffect()
    data class ShowPhotoError(val error: TrainingPhotoError) : LogActivityEffect()
}
