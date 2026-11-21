package com.javargasp.tiominutermvi.ui.program.list

import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface ProgramListIntent : MviIntent {
    data class OnProgramClicked(val programId: Long) : ProgramListIntent
    data object OnCreateClicked : ProgramListIntent
    data class OnDeleteRequested(val programId: Long) : ProgramListIntent
    data object OnConfirmDelete : ProgramListIntent
    data object OnDismissDelete : ProgramListIntent
    data object OnBackPressed : ProgramListIntent

    /** The screen was opened or came back to the foreground: close inactive program sessions. */
    data object OnScreenResumed : ProgramListIntent
}
