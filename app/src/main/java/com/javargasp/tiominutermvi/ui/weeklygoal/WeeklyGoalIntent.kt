package com.javargasp.tiominutermvi.ui.weeklygoal

import com.javargasp.tiominutermvi.ui.base.MviIntent

/** Intents for the Weekly goal screen. */
sealed class WeeklyGoalIntent : MviIntent {
    /** "+": one more workout per week, up to 14. */
    data object OnIncrement : WeeklyGoalIntent()
    /** "−": one fewer workout per week, down to 1. */
    data object OnDecrement : WeeklyGoalIntent()
    data object OnSave : WeeklyGoalIntent()
    data object OnBackPressed : WeeklyGoalIntent()
}
