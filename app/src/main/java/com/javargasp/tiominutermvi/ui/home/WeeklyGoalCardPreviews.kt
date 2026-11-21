package com.javargasp.tiominutermvi.ui.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.goal.CurrentWeekSummary
import com.javargasp.tiominutermvi.domain.goal.WeeklyProgress
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

// Week of Monday 19 October 2026; today is Thursday 22
private fun previewWeek(goal: Int?, workouts: Map<String, Int>, streak: Int = 0) = CurrentWeekSummary(
    today = "2026-10-22",
    progress = WeeklyProgress("2026-10-19", goal, workouts),
    streak = streak
)

@Composable
private fun CardPreview(week: CurrentWeekSummary, darkTheme: Boolean = false) {
    TioMinuterMVITheme(darkTheme = darkTheme) {
        WeeklyGoalCompactCard(week = week, message = HomeState(week = week).weeklyMessage, onClick = {}, onSeeHistory = {}, onLogActivity = {})
    }
}

@Preview(name = "Week - workouts left", showBackground = true)
@Composable
private fun WeeklyGoalCardWorkoutsLeftPreview() {
    CardPreview(previewWeek(goal = 5, workouts = mapOf("2026-10-19" to 1, "2026-10-21" to 2), streak = 3))
}

@Preview(name = "Week - workouts left, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeeklyGoalCardDarkPreview() {
    CardPreview(previewWeek(goal = 5, workouts = mapOf("2026-10-19" to 1, "2026-10-21" to 2), streak = 3), darkTheme = true)
}

@Preview(name = "Week - goal exceeded, Spanish", showBackground = true, locale = "es")
@Composable
private fun WeeklyGoalCardMetPreview() {
    CardPreview(previewWeek(goal = 4, workouts = mapOf("2026-10-19" to 2, "2026-10-20" to 1, "2026-10-22" to 2), streak = 4))
}

@Preview(name = "Week - no goal", showBackground = true)
@Composable
private fun WeeklyGoalCardNoGoalPreview() {
    CardPreview(previewWeek(goal = null, workouts = emptyMap()))
}
