package com.javargasp.tiominutermvi.ui.weeklygoal

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

@Preview(name = "Weekly goal - light", showBackground = true)
@Composable
private fun WeeklyGoalPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        WeeklyGoalContent(state = WeeklyGoalState(isLoading = false, currentGoal = 3, selectedWorkouts = 5), onIntent = {})
    }
}

@Preview(name = "Weekly goal - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeeklyGoalDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        WeeklyGoalContent(state = WeeklyGoalState(isLoading = false, currentGoal = 3, selectedWorkouts = 5), onIntent = {})
    }
}

@Preview(name = "Weekly goal - maximum, Spanish", showBackground = true, locale = "es")
@Composable
private fun WeeklyGoalNoGoalPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        WeeklyGoalContent(state = WeeklyGoalState(isLoading = false, selectedWorkouts = 14, saveFailed = true), onIntent = {})
    }
}
