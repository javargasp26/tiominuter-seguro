package com.javargasp.tiominutermvi.ui.gymroutineselection

import com.javargasp.tiominutermvi.ui.base.MviIntent

/**
 * Intents for the Gym Routine Selection screen
 */
sealed class GymRoutineSelectionIntent : MviIntent {
    object OnLoad : GymRoutineSelectionIntent()

    /** The screen was opened or came back to the foreground: close inactive gym sessions. */
    object OnResume : GymRoutineSelectionIntent()
    data class OnToggleExpanded(val routineId: Int) : GymRoutineSelectionIntent()
    data class OnStartRoutine(val routineId: Int) : GymRoutineSelectionIntent()
    object OnRetry : GymRoutineSelectionIntent()
    object OnBackPressed : GymRoutineSelectionIntent()
}
