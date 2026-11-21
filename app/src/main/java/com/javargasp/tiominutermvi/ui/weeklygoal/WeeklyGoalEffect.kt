package com.javargasp.tiominutermvi.ui.weeklygoal

import com.javargasp.tiominutermvi.ui.base.MviEffect

/** Effects for the Weekly goal screen. */
sealed class WeeklyGoalEffect : MviEffect {
    data object NavigateBack : WeeklyGoalEffect()
}
