package com.javargasp.tiominutermvi.ui.program.treadmill

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.ui.program.common.ProgramPreviewData
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

private val runningState = ProgramTreadmillState(
    isLoading = false,
    segments = ProgramPreviewData.treadmill.segments,
    currentIndex = 1,
    remainingSeconds = 42,
    isStarted = true,
    isRunning = true
)

@Preview(name = "Treadmill - light", showBackground = true, heightDp = 1000)
@Composable
private fun TreadmillPreview() {
    TioMinuterMVITheme(darkTheme = false) { ProgramTreadmillContent(state = runningState, onIntent = {}) }
}

@Preview(name = "Treadmill - dark", showBackground = true, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TreadmillDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) { ProgramTreadmillContent(state = runningState, onIntent = {}) }
}

@Preview(name = "Treadmill - finished", showBackground = true)
@Composable
private fun TreadmillFinishedPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramTreadmillContent(state = runningState.copy(isFinished = true, isRunning = false), onIntent = {})
    }
}
