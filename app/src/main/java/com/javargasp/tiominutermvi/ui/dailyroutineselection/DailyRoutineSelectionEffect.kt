package com.javargasp.tiominutermvi.ui.dailyroutineselection

import com.javargasp.tiominutermvi.ui.base.MviEffect

/**
 * Represents one-time events from the Daily Routine Selection screen.
 */
sealed class DailyRoutineSelectionEffect : MviEffect {
    data class NavigateToWorkout(val routineId: Int) : DailyRoutineSelectionEffect()
    data object NavigateBack : DailyRoutineSelectionEffect()

    /** An unfinished daily workout was closed for inactivity and saved with this duration. */
    data class ShowSessionSaved(val durationSeconds: Int) : DailyRoutineSelectionEffect()
}