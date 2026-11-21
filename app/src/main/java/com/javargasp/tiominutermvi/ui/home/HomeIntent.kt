package com.javargasp.tiominutermvi.ui.home

import com.javargasp.tiominutermvi.ui.base.MviIntent

/**
 * Intents for the Home screen
 */
sealed class HomeIntent : MviIntent {
    object OnStart : HomeIntent()
    object OnStartRoutineClicked : HomeIntent()
    object OnGymRoutinesClicked : HomeIntent()
    object OnProgramsClicked : HomeIntent()
    object OnHistoryClicked : HomeIntent()
    object OnWeeklyGoalClicked : HomeIntent()
    object OnLogActivityClicked : HomeIntent()
    object OnAccountClicked : HomeIntent()
    object OnGuestBannerSignInClicked : HomeIntent()
    object OnGuestBannerDismissed : HomeIntent()
    object OnGroupsClicked : HomeIntent()
    object OnTrainedTodayBannerClicked : HomeIntent()
    object OnTrainedTodayBannerDismissed : HomeIntent()
}