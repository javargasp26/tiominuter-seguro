package com.javargasp.tiominutermvi.ui.program.strength

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.ui.program.common.ProgramPreviewData
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

private val previewState = ProgramStrengthState(
    isLoading = false,
    workout = ProgramPreviewData.strength,
    exercises = ProgramPreviewData.exercises,
    completedPositions = setOf(0, 1)
)

@Preview(name = "Strength workout - light", showBackground = true)
@Composable
private fun ProgramStrengthPreview() {
    TioMinuterMVITheme(darkTheme = false) { ProgramStrengthContent(state = previewState, onIntent = {}) }
}

@Preview(name = "Strength workout - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProgramStrengthDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) { ProgramStrengthContent(state = previewState, onIntent = {}) }
}

@Preview(name = "Strength workout - completed dialog", showBackground = true)
@Composable
private fun ProgramStrengthCompletedPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramStrengthContent(
            state = previewState.copy(completedPositions = setOf(0, 1, 2, 3), showWorkoutCompleted = true),
            onIntent = {}
        )
    }
}
