package com.javargasp.tiominutermvi.ui.traininglog

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.goal.WeeklyProgress
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

private fun previewEntry(
    id: Long,
    date: String,
    source: TrainingSource,
    title: String,
    durationSeconds: Int?,
    completedAt: Long
) = TrainingLogEntry(
    id = id,
    uuid = "uuid-$id",
    dateLocal = date,
    completedAt = completedAt,
    source = source,
    refId = null,
    title = title,
    durationSeconds = durationSeconds,
    createdAt = completedAt,
    updatedAt = completedAt
)

private val previewDays = listOf(
    TrainingDay(
        dateLocal = "2026-10-19",
        label = DayLabel.Today,
        entries = listOf(
            previewEntry(4, "2026-10-19", TrainingSource.PROGRAM, "Full body A", 2_520, 1_760_900_000_000),
            previewEntry(3, "2026-10-19", TrainingSource.DAILY, "Day 3", 960, 1_760_880_000_000)
        )
    ),
    TrainingDay(
        dateLocal = "2026-10-18",
        label = DayLabel.Yesterday,
        entries = listOf(
            previewEntry(2, "2026-10-18", TrainingSource.GYM, "Pull Day", null, 1_760_800_000_000)
        )
    ),
    TrainingDay(
        dateLocal = "2026-10-12",
        label = DayLabel.Date("2026-10-12"),
        entries = listOf(
            previewEntry(1, "2026-10-12", TrainingSource.PROGRAM, "Treadmill", 420, 1_760_270_000_000)
        )
    )
)

/** Newest week first: this week (goal 1, 2 workouts) and last week (goal 4, 1 workout: Pull Day has no duration and counts, the treadmill entry is shown as not counted). */
private val previewWeeks = listOf(
    TrainingWeek(
        weekStart = "2026-10-19",
        progress = WeeklyProgress("2026-10-19", goal = 1, workoutsPerDay = mapOf("2026-10-19" to 2)),
        days = previewDays.take(1)
    ),
    TrainingWeek(
        weekStart = "2026-10-12",
        progress = WeeklyProgress("2026-10-12", goal = 4, workoutsPerDay = mapOf("2026-10-18" to 1)),
        days = previewDays.drop(1)
    )
)

@Preview(name = "History - light", showBackground = true)
@Composable
private fun TrainingHistoryPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        TrainingHistoryContent(state = TrainingHistoryState(isLoading = false, weeks = previewWeeks), onIntent = {})
    }
}

@Preview(name = "History - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrainingHistoryDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        TrainingHistoryContent(state = TrainingHistoryState(isLoading = false, weeks = previewWeeks), onIntent = {})
    }
}

@Preview(name = "History - Spanish", showBackground = true, locale = "es")
@Composable
private fun TrainingHistorySpanishPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        TrainingHistoryContent(state = TrainingHistoryState(isLoading = false, weeks = previewWeeks), onIntent = {})
    }
}

@Preview(name = "History - empty", showBackground = true)
@Composable
private fun TrainingHistoryEmptyPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        TrainingHistoryContent(state = TrainingHistoryState(isLoading = false), onIntent = {})
    }
}

@Preview(name = "History - delete dialog", showBackground = true)
@Composable
private fun TrainingHistoryDeletePreview() {
    TioMinuterMVITheme(darkTheme = false) {
        TrainingHistoryContent(
            state = TrainingHistoryState(isLoading = false, weeks = previewWeeks, pendingDeleteId = 2),
            onIntent = {}
        )
    }
}
