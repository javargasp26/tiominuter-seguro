package com.javargasp.tiominutermvi.ui.program.strengthexercise

import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface ProgramStrengthExerciseEffect : MviEffect {
    data object NavigateBack : ProgramStrengthExerciseEffect
}
