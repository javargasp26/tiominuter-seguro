package com.javargasp.tiominutermvi.ui.logactivity

import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.base.MviIntent

/** Intents for the log-activity screen (create and edit). */
sealed class LogActivityIntent : MviIntent {
    data class OnTypeSelected(val type: ActivityType) : LogActivityIntent()
    data class OnCustomNameChanged(val name: String) : LogActivityIntent()
    data class OnDurationChanged(val text: String) : LogActivityIntent()
    data class OnQuickDurationSelected(val minutes: Int) : LogActivityIntent()
    data class OnIntensitySelected(val intensity: Intensity) : LogActivityIntent()
    data class OnDateSelected(val date: String) : LogActivityIntent()
    data class OnTimeSelected(val hour: Int, val minute: Int) : LogActivityIntent()
    data class OnNoteChanged(val note: String) : LogActivityIntent()
    data class OnPhotoSelected(val source: TrainingPhotoSource) : LogActivityIntent()
    data object OnRemovePhoto : LogActivityIntent()
    data object OnSave : LogActivityIntent()
    data object OnDeleteClicked : LogActivityIntent()
    data object OnConfirmDelete : LogActivityIntent()
    data object OnDismissDelete : LogActivityIntent()
    data object OnBackPressed : LogActivityIntent()
    data object OnConfirmDiscard : LogActivityIntent()
    data object OnDismissDiscard : LogActivityIntent()
    data class OnShareChanged(val share: Boolean) : LogActivityIntent()
    data object OnShareIntroOk : LogActivityIntent()
    data object OnShareIntroDontShare : LogActivityIntent()
    data object OnShareIntroDismissed : LogActivityIntent()
}
