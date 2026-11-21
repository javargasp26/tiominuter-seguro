package com.javargasp.tiominutermvi.ui.gymroutineselection

import com.javargasp.tiominutermvi.ui.base.MviEffect

/**
 * Effects for the Gym Routine Selection screen
 */
sealed class GymRoutineSelectionEffect : MviEffect {
    data class NavigateToGymRoutine(val routineId: Int) : GymRoutineSelectionEffect()
    object NavigateBack : GymRoutineSelectionEffect()
    object ShowRefreshError : GymRoutineSelectionEffect()

    /** An unfinished workout was closed for inactivity and saved with this duration. */
    data class ShowSessionSaved(val durationSeconds: Int) : GymRoutineSelectionEffect()
}
