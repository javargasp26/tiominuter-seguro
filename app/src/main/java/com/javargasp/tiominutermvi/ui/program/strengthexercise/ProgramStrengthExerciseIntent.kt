package com.javargasp.tiominutermvi.ui.program.strengthexercise

import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface ProgramStrengthExerciseIntent : MviIntent {
    data object OnSetDone : ProgramStrengthExerciseIntent
    data object OnStartTimedSet : ProgramStrengthExerciseIntent
    data object OnSkipRest : ProgramStrengthExerciseIntent
    data object OnBackPressed : ProgramStrengthExerciseIntent
    data object OnConfirmExit : ProgramStrengthExerciseIntent
    data object OnDismissExit : ProgramStrengthExerciseIntent
}
