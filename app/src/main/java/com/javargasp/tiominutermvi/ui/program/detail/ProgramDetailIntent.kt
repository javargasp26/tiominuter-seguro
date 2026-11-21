package com.javargasp.tiominutermvi.ui.program.detail

import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface ProgramDetailIntent : MviIntent {
    data class OnWorkoutClicked(val week: Int, val dayIndex: Int) : ProgramDetailIntent
    data object OnBackPressed : ProgramDetailIntent

    /** The screen was opened or came back to the foreground: close inactive program sessions. */
    data object OnScreenResumed : ProgramDetailIntent
}
