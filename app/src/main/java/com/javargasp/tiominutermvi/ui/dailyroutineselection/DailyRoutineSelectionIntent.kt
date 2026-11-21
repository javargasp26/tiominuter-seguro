package com.javargasp.tiominutermvi.ui.dailyroutineselection

import com.javargasp.tiominutermvi.ui.base.MviIntent

/**
 * Represents user actions on the Daily Routine Selection screen.
 */
sealed class DailyRoutineSelectionIntent : MviIntent {
    data object OnLoad : DailyRoutineSelectionIntent()

    /** The screen was opened or came back to the foreground: close an inactive daily session. */
    data object OnResume : DailyRoutineSelectionIntent()

    /** A day was tapped; a locked day can't be started. */
    data class OnRoutineSelected(val routineId: Int) : DailyRoutineSelectionIntent()
    data object OnBackPressed : DailyRoutineSelectionIntent()

    /** "Restart days" (or "Start again from Day 1"): asks first. */
    data object OnRestartClicked : DailyRoutineSelectionIntent()
    data object OnRestartConfirmed : DailyRoutineSelectionIntent()
    data object OnRestartDismissed : DailyRoutineSelectionIntent()
}
