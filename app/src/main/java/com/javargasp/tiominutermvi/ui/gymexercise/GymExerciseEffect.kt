package com.javargasp.tiominutermvi.ui.gymexercise

import com.javargasp.tiominutermvi.ui.base.MviEffect

/**
 * Effects for the Gym Exercise screen
 */
sealed class GymExerciseEffect : MviEffect {
    object NavigateBack : GymExerciseEffect()
}
