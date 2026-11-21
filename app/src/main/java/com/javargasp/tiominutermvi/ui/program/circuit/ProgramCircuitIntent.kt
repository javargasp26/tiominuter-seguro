package com.javargasp.tiominutermvi.ui.program.circuit

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface ProgramCircuitIntent : MviIntent {
    data object OnStart : ProgramCircuitIntent
    data object OnSkip : ProgramCircuitIntent
    data object OnDone : ProgramCircuitIntent
    data object OnBackPressed : ProgramCircuitIntent
    data object OnConfirmExit : ProgramCircuitIntent
    data object OnDismissExit : ProgramCircuitIntent
    data class OnCompletionPhotoSelected(val source: TrainingPhotoSource) : ProgramCircuitIntent
}
