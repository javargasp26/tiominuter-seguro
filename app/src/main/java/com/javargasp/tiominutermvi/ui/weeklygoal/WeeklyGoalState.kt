package com.javargasp.tiominutermvi.ui.weeklygoal

import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.ui.base.MviState

/**
 * State for the Weekly goal screen. [currentGoal] is the goal in force this week (null
 * without one); [selectedWorkouts] starts from it, or from [DEFAULT_WORKOUTS]. [saveFailed]
 * reports a storage error.
 */
data class WeeklyGoalState(
    val isLoading: Boolean = true,
    val currentGoal: Int? = null,
    val selectedWorkouts: Int = DEFAULT_WORKOUTS,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false
) : MviState {

    val canDecrement: Boolean get() = selectedWorkouts > WeeklyGoal.WORKOUTS_RANGE.first
    val canIncrement: Boolean get() = selectedWorkouts < WeeklyGoal.WORKOUTS_RANGE.last

    companion object {
        /** Suggested starting value when there is no goal yet. */
        const val DEFAULT_WORKOUTS = 3
    }
}
