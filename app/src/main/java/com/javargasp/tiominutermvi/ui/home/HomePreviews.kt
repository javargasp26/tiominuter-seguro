package com.javargasp.tiominutermvi.ui.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.goal.CurrentWeekSummary
import com.javargasp.tiominutermvi.domain.groups.GroupMember
import com.javargasp.tiominutermvi.domain.groups.GroupMembers
import com.javargasp.tiominutermvi.domain.groups.HomeGroups
import com.javargasp.tiominutermvi.domain.groups.MemberProfile
import com.javargasp.tiominutermvi.domain.groups.MemberSummary
import com.javargasp.tiominutermvi.domain.goal.WeeklyProgress
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

/** Small phone (360x640 dp) and regular phone (411x891 dp), light and dark. */
@Preview(name = "Small · light", showBackground = true, widthDp = 360, heightDp = 640)
@Preview(name = "Small · dark", showBackground = true, widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Regular · light", showBackground = true, widthDp = 411, heightDp = 891)
@Preview(name = "Regular · dark", showBackground = true, widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class HomeSizePreviews

// Week of Monday 19 October 2026; today is Thursday 22
private fun previewState(goal: Int?, workoutsPerDay: Map<String, Int>, streak: Int) = HomeState(
    isLoading = false,
    currentDay = 12,
    currentDayName = WorkoutName.Daily(12),
    week = CurrentWeekSummary(
        today = "2026-10-22",
        progress = WeeklyProgress("2026-10-19", goal = goal, workoutsPerDay = workoutsPerDay),
        streak = streak
    )
)

@HomeSizePreviews
@Composable
private fun HomeWithGoalPreview() {
    TioMinuterMVITheme {
        HomeContent(
            state = previewState(goal = 4, workoutsPerDay = mapOf("2026-10-19" to 1, "2026-10-21" to 2), streak = 2),
            onIntent = {}
        )
    }
}

@HomeSizePreviews
@Composable
private fun HomeWithoutGoalPreview() {
    TioMinuterMVITheme {
        HomeContent(state = previewState(goal = null, workoutsPerDay = emptyMap(), streak = 0), onIntent = {})
    }
}

@HomeSizePreviews
@Composable
private fun HomeGoalMetPreview() {
    TioMinuterMVITheme {
        HomeContent(
            state = previewState(
                goal = 4,
                workoutsPerDay = mapOf("2026-10-19" to 2, "2026-10-20" to 1, "2026-10-22" to 2),
                streak = 3
            ),
            onIntent = {}
        )
    }
}

@HomeSizePreviews
@Composable
private fun HomeGuestBannerPreview() {
    TioMinuterMVITheme {
        HomeContent(
            state = previewState(goal = 4, workoutsPerDay = mapOf("2026-10-19" to 1), streak = 0).copy(showGuestBanner = true),
            onIntent = {}
        )
    }
}

@Preview(name = "Guest banner · Spanish", showBackground = true, widthDp = 360, heightDp = 640, locale = "es")
@Composable
private fun HomeGuestBannerSpanishPreview() {
    TioMinuterMVITheme {
        HomeContent(
            state = previewState(goal = null, workoutsPerDay = emptyMap(), streak = 0).copy(showGuestBanner = true),
            onIntent = {}
        )
    }
}

@HomeSizePreviews
@Composable
private fun HomeTrainedTodayBannerPreview() {
    val member = { uid: String, name: String ->
        GroupMember(uid, MemberProfile(name, null), 0L, MemberSummary("2026-10-19", 2, 3, 1, emptyMap(), "2026-10-22"))
    }
    TioMinuterMVITheme {
        HomeContent(
            state = previewState(goal = 4, workoutsPerDay = mapOf("2026-10-19" to 1), streak = 1).copy(
                session = AuthSession.SignedIn("me", "Alex", null, null),
                groups = HomeGroups("me", listOf(GroupMembers("g1", listOf(member("ana", "Ana"), member("luis", "Luis"), member("sam", "Sam")))))
            ),
            onIntent = {}
        )
    }
}
