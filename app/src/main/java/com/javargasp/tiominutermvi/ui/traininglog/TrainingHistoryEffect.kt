package com.javargasp.tiominutermvi.ui.traininglog

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.ui.base.MviEffect

/** Effects for the Training history screen. */
sealed class TrainingHistoryEffect : MviEffect {
    data object NavigateBack : TrainingHistoryEffect()
    data object NavigateToLogActivity : TrainingHistoryEffect()
    data class NavigateToEditActivity(val entryId: Long) : TrainingHistoryEffect()
    data class ShowPhotoError(val error: TrainingPhotoError) : TrainingHistoryEffect()
}
