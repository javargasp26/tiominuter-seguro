package com.javargasp.tiominutermvi.ui.home

import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.goal.CurrentWeekSummary
import com.javargasp.tiominutermvi.domain.groups.HomeGroups
import com.javargasp.tiominutermvi.domain.groups.TrainedTodayBanner
import com.javargasp.tiominutermvi.domain.groups.TrainedTodayRule
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.ui.base.MviState

/**
 * State for the Home screen. [week] is the current week's goal progress (null until
 * the first emission). [session] drives the top-bar account icon (the Google photo when
 * signed in); [showGuestBanner] follows `GuestBannerRule`. [groups] are my groups' members (a
 * one-shot read on every OnStart, cached 10 minutes) for the "already trained today" banner.
 */
data class HomeState(
    val isLoading: Boolean = false,
    val currentDay: Int? = null,
    /** The name of [currentDay]'s routine ("Day 5"), rendered in the viewer's language. */
    val currentDayName: WorkoutName.Daily? = null,
    val week: CurrentWeekSummary? = null,
    val session: AuthSession = AuthSession.Guest,
    val showGuestBanner: Boolean = false,
    val groups: HomeGroups? = null,
    val trainedTodayDismissedOn: String? = null
) : MviState {

    /** "Ana already trained today…": see [TrainedTodayRule]; null when hidden. */
    val trainedTodayBanner: TrainedTodayBanner?
        get() {
            val summary = week ?: return null
            val myGroups = groups ?: return null
            val signedIn = session as? AuthSession.SignedIn ?: return null
            if (signedIn.uid != myGroups.myUid) return null
            return TrainedTodayRule.banner(
                myUid = signedIn.uid,
                today = summary.today,
                iTrainedToday = summary.progress.workoutsOn(summary.today) > 0,
                dismissedOn = trainedTodayDismissedOn,
                groups = myGroups.groups
            )
        }

    /** The message of the "This week" card; null without a goal (or before loading). */
    val weeklyMessage: WeeklyGoalMessage?
        get() {
            val summary = week ?: return null
            if (summary.progress.goal == null) return null
            return if (summary.progress.isGoalMet) {
                WeeklyGoalMessage.GoalMet
            } else {
                WeeklyGoalMessage.WorkoutsLeft(summary.workoutsNeeded)
            }
        }
}

/** What the "This week" card says under the progress. */
sealed interface WeeklyGoalMessage {
    data object GoalMet : WeeklyGoalMessage
    data class WorkoutsLeft(val workouts: Int) : WeeklyGoalMessage
}
