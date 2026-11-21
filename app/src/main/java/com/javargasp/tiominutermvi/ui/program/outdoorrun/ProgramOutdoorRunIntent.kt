package com.javargasp.tiominutermvi.ui.program.outdoorrun

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface ProgramOutdoorRunIntent : MviIntent {
    data object OnStart : ProgramOutdoorRunIntent
    data object OnPause : ProgramOutdoorRunIntent
    data object OnResume : ProgramOutdoorRunIntent
    data object OnFinishClicked : ProgramOutdoorRunIntent
    data object OnConfirmFinish : ProgramOutdoorRunIntent
    data object OnDismissFinish : ProgramOutdoorRunIntent
    data object OnDone : ProgramOutdoorRunIntent
    data object OnBackPressed : ProgramOutdoorRunIntent
    data object OnConfirmExit : ProgramOutdoorRunIntent
    data object OnDismissExit : ProgramOutdoorRunIntent
    data class OnCompletionPhotoSelected(val source: TrainingPhotoSource) : ProgramOutdoorRunIntent
}
