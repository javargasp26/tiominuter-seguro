package com.javargasp.tiominutermvi.ui.program.outdoorrun

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

private val runningState = ProgramOutdoorRunState(
    isLoading = false,
    distanceKm = 3.3,
    elapsedSeconds = 754,
    isStarted = true,
    isRunning = true
)

@Preview(name = "Outdoor run - light", showBackground = true)
@Composable
private fun OutdoorRunPreview() {
    TioMinuterMVITheme(darkTheme = false) { ProgramOutdoorRunContent(state = runningState, onIntent = {}) }
}

@Preview(name = "Outdoor run - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OutdoorRunDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) { ProgramOutdoorRunContent(state = runningState, onIntent = {}) }
}

@Preview(name = "Brisk walk - finish dialog", showBackground = true)
@Composable
private fun BriskWalkPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramOutdoorRunContent(
            state = runningState.copy(walk = true, isRunning = false, showFinishConfirmation = true),
            onIntent = {}
        )
    }
}
