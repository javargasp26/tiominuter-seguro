package com.javargasp.tiominutermvi.ui.dailyroutineselection

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

/**
 * Previews for the Daily Routine Selection screen: days completed, the next one and locked ones,
 * and the end of a cycle.
 */
private fun previewRoutines(count: Int) = (1..count).map { Routine(it, "Day $it", exercises = emptyList()) }

@Composable
private fun Preview(progress: DailyProgress, count: Int = 5) {
    TioMinuterMVITheme {
        DailyRoutineSelectionContent(
            state = DailyRoutineSelectionState(routines = previewRoutines(count), progress = progress),
            onIntent = {}
        )
    }
}

@Preview(showBackground = true, name = "In progress")
@Composable
private fun InProgressPreview() = Preview(DailyProgress(lastCompletedDay = 2))

@Preview(showBackground = true, name = "In progress (dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InProgressDarkPreview() = Preview(DailyProgress(lastCompletedDay = 2))

@Preview(showBackground = true, name = "New: only Day 1", locale = "es")
@Composable
private fun NewSpanishPreview() = Preview(DailyProgress())

@Preview(showBackground = true, name = "Every day completed")
@Composable
private fun CycleCompletePreview() = Preview(DailyProgress(lastCompletedDay = 3), count = 3)

@Preview(showBackground = true, name = "Every day completed (dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CycleCompleteDarkPreview() = Preview(DailyProgress(lastCompletedDay = 3), count = 3)
