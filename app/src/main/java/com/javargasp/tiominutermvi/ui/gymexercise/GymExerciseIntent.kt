package com.javargasp.tiominutermvi.ui.gymexercise

import com.javargasp.tiominutermvi.ui.base.MviIntent

/**
 * Intents for the Gym Exercise screen
 */
sealed class GymExerciseIntent : MviIntent {
    object OnLoad : GymExerciseIntent()
    object OnSetDone : GymExerciseIntent()
    object OnStartTimedSet : GymExerciseIntent()
    object OnSkipRest : GymExerciseIntent()
    object OnBackPressed : GymExerciseIntent()
    object OnConfirmExit : GymExerciseIntent()
    object OnDismissExit : GymExerciseIntent()
}
