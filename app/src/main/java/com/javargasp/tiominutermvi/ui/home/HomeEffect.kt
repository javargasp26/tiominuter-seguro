package com.javargasp.tiominutermvi.ui.home

import com.javargasp.tiominutermvi.ui.base.MviEffect

/**
 * Effects for the Home screen
 */
sealed class HomeEffect : MviEffect {
    object NavigateToDailyRoutineSelection : HomeEffect()
    object NavigateToGymRoutineSelection : HomeEffect()
    object NavigateToProgramList : HomeEffect()
    object NavigateToTrainingHistory : HomeEffect()
    object NavigateToWeeklyGoal : HomeEffect()
    object NavigateToLogActivity : HomeEffect()
    object NavigateToAccount : HomeEffect()
    object NavigateToGroups : HomeEffect()
    data class NavigateToGroupDetail(val groupId: String) : HomeEffect()
}