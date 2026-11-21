package com.javargasp.tiominutermvi.ui.program.strengthexercise

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.ui.program.common.ProgramPreviewData
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

private val repsState = ProgramStrengthExerciseState(
    isLoading = false,
    slot = ProgramPreviewData.strength.slots[0],
    exercise = ProgramPreviewData.exercises[2],
    exerciseNumber = 1,
    totalExercises = 4,
    phase = ProgramExercisePhase.PerformingReps(1)
)

private val holdState = repsState.copy(
    slot = ProgramPreviewData.strength.slots[3],
    exercise = ProgramPreviewData.exercises[4],
    exerciseNumber = 4,
    phase = ProgramExercisePhase.PerformingTimed(0, 22, 30)
)

private val sideHoldState = repsState.copy(
    slot = ProgramPreviewData.strength.slots[4],
    exercise = ProgramPreviewData.exercises[120],
    exerciseNumber = 5,
    totalExercises = 5,
    phase = ProgramExercisePhase.PerformingTimed(1, 18, 30, ProgramSide.LEFT)
)

@Preview(name = "Exercise reps - light", showBackground = true)
@Composable
private fun RepsPreview() {
    TioMinuterMVITheme(darkTheme = false) { ProgramStrengthExerciseContent(state = repsState, onIntent = {}) }
}

@Preview(name = "Exercise reps - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RepsDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) { ProgramStrengthExerciseContent(state = repsState, onIntent = {}) }
}

@Preview(name = "Exercise resting", showBackground = true)
@Composable
private fun RestingPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramStrengthExerciseContent(state = repsState.copy(phase = ProgramExercisePhase.Resting(2, 64, 90)), onIntent = {})
    }
}

@Preview(name = "Exercise hold", showBackground = true)
@Composable
private fun HoldPreview() {
    TioMinuterMVITheme(darkTheme = false) { ProgramStrengthExerciseContent(state = holdState, onIntent = {}) }
}

@Preview(name = "Exercise per side - left", showBackground = true)
@Composable
private fun SideHoldPreview() {
    TioMinuterMVITheme(darkTheme = false) { ProgramStrengthExerciseContent(state = sideHoldState, onIntent = {}) }
}

@Preview(name = "Exercise per side - switch, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SwitchSidesDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        ProgramStrengthExerciseContent(
            state = sideHoldState.copy(phase = ProgramExercisePhase.SwitchingSides(1, 3)),
            onIntent = {}
        )
    }
}
