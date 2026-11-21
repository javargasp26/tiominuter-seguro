package com.javargasp.tiominutermvi.ui.program.treadmill

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface ProgramTreadmillIntent : MviIntent {
    data object OnStart : ProgramTreadmillIntent
    data object OnPause : ProgramTreadmillIntent
    data object OnResume : ProgramTreadmillIntent
    data object OnSkipSegment : ProgramTreadmillIntent
    data object OnDone : ProgramTreadmillIntent
    data object OnBackPressed : ProgramTreadmillIntent
    data object OnConfirmExit : ProgramTreadmillIntent
    data object OnDismissExit : ProgramTreadmillIntent
    data class OnCompletionPhotoSelected(val source: TrainingPhotoSource) : ProgramTreadmillIntent
}
