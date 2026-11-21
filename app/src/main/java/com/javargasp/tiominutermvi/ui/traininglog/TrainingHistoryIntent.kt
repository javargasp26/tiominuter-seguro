package com.javargasp.tiominutermvi.ui.traininglog

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.base.MviIntent

/** Intents for the Training history screen. */
sealed class TrainingHistoryIntent : MviIntent {
    data class OnDeleteRequested(val entryId: Long) : TrainingHistoryIntent()
    data object OnConfirmDelete : TrainingHistoryIntent()
    data object OnDismissDelete : TrainingHistoryIntent()
    data object OnBackPressed : TrainingHistoryIntent()

    /** Tap on an entry: only ACTIVITY entries open (in edit mode). */
    data class OnEntryClicked(val entryId: Long) : TrainingHistoryIntent()
    data object OnLogActivityClicked : TrainingHistoryIntent()
    /** Adds or replaces the photo of an automatic (daily, gym, program) entry. */
    data class OnPhotoSelected(val entryId: Long, val source: TrainingPhotoSource) : TrainingHistoryIntent()
    data class OnRemovePhotoRequested(val entryId: Long) : TrainingHistoryIntent()
    data object OnConfirmRemovePhoto : TrainingHistoryIntent()
    data object OnDismissRemovePhoto : TrainingHistoryIntent()
}
