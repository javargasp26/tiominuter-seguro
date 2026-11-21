package com.javargasp.tiominutermvi.ui.logactivity

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryError
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme
import com.javargasp.tiominutermvi.ui.traininglog.DayLabel

// Today is Thursday 22 October 2026
private val previewDays = listOf(
    DayOption("2026-10-22", DayLabel.Today),
    DayOption("2026-10-21", DayLabel.Yesterday)
) + (2..6).map { DayOption("2026-10-${22 - it}", DayLabel.Date("2026-10-${22 - it}")) }

private val filledForm = LogActivityForm(
    type = ActivityType.TEAM_SPORT,
    durationText = "60",
    intensity = Intensity.HARD,
    date = "2026-10-21",
    hour = 19,
    minute = 30,
    note = "Basketball with friends"
)

private val newState = LogActivityState(
    isLoading = false,
    form = LogActivityForm(date = "2026-10-22", hour = 9, minute = 41),
    initialForm = LogActivityForm(date = "2026-10-22", hour = 9, minute = 41),
    dayOptions = previewDays
)

@Preview(name = "Log activity - new, light", showBackground = true, heightDp = 1400)
@Composable
private fun LogActivityNewPreview() {
    TioMinuterMVITheme(darkTheme = false) { LogActivityContent(state = newState, onIntent = {}) }
}

@Preview(name = "Log activity - edit, dark", showBackground = true, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogActivityEditDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        LogActivityContent(
            state = newState.copy(entryId = 7, form = filledForm, initialForm = filledForm),
            onIntent = {}
        )
    }
}

@Preview(name = "Log activity - other, short, errors, Spanish", showBackground = true, heightDp = 1400, locale = "es")
@Composable
private fun LogActivityErrorsPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        LogActivityContent(
            state = newState.copy(
                form = LogActivityForm(type = ActivityType.OTHER, durationText = "8", date = "2026-10-22", hour = 23, minute = 50),
                showErrors = true,
                errors = setOf(ActivityEntryError.NAME_MISSING, ActivityEntryError.INTENSITY_MISSING, ActivityEntryError.DATE_IN_FUTURE)
            ),
            onIntent = {}
        )
    }
}

@Preview(name = "Log activity - discard dialog", showBackground = true)
@Composable
private fun LogActivityDiscardPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        LogActivityContent(state = newState.copy(form = filledForm, showDiscardConfirmation = true), onIntent = {})
    }
}
