package com.javargasp.tiominutermvi.ui.program.create

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

private val previewRules = ImpactRules(30.0, 60, 60, 15, setOf(87))

private val filledState = ProgramCreateState(
    ageText = "34",
    weightText = "98",
    heightText = "172",
    goal = ProgramGoal.LOSE_FAT,
    focus = ProgramFocus.LOWER_BODY,
    impactRules = previewRules
)

@Preview(name = "Create - light", showBackground = true, heightDp = 1400)
@Composable
private fun ProgramCreatePreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramCreateContent(state = filledState, onIntent = {})
    }
}

@Preview(name = "Create - dark", showBackground = true, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProgramCreateDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        ProgramCreateContent(state = filledState, onIntent = {})
    }
}

@Preview(name = "Create - errors", showBackground = true, heightDp = 1400)
@Composable
private fun ProgramCreateErrorsPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramCreateContent(state = ProgramCreateState(ageText = "12", showAllErrors = true), onIntent = {})
    }
}

@Preview(name = "Create - underweight warning", showBackground = true, heightDp = 1400)
@Composable
private fun ProgramCreateUnderweightPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramCreateContent(
            state = ProgramCreateState(ageText = "25", weightText = "50", heightText = "180", goal = ProgramGoal.LOSE_FAT),
            onIntent = {}
        )
    }
}
