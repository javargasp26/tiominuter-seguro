package com.javargasp.tiominutermvi.ui.program.circuit

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.ui.program.common.ProgramPreviewData
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

private val workState = ProgramCircuitState(
    isLoading = false,
    workout = ProgramPreviewData.circuit,
    exercises = ProgramPreviewData.exercises,
    phase = CircuitPhase.Work(round = 2, exerciseIndex = 1, remainingSeconds = 18, totalSeconds = 30)
)

@Preview(name = "Circuit work - light", showBackground = true)
@Composable
private fun CircuitWorkPreview() {
    TioMinuterMVITheme(darkTheme = false) { ProgramCircuitContent(state = workState, onIntent = {}) }
}

@Preview(name = "Circuit work - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CircuitWorkDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) { ProgramCircuitContent(state = workState, onIntent = {}) }
}

@Preview(name = "Circuit rest", showBackground = true)
@Composable
private fun CircuitRestPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramCircuitContent(state = workState.copy(phase = CircuitPhase.Rest(2, 2, 12, 30)), onIntent = {})
    }
}

@Preview(name = "Circuit not started", showBackground = true, heightDp = 900)
@Composable
private fun CircuitReadyPreview() {
    TioMinuterMVITheme(darkTheme = false) { ProgramCircuitContent(state = workState.copy(phase = null), onIntent = {}) }
}
