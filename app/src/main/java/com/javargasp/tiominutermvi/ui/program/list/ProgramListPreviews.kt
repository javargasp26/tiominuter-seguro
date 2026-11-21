package com.javargasp.tiominutermvi.ui.program.list

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

private val previewPrograms = listOf(
    ProgramSummary(
        id = 1,
        profile = ProgramProfile(30, 70.0, 175, ProgramLevel.INTERMEDIATE, ProgramGoal.GAIN_MUSCLE, ProgramLocation.GYM, 6, 3),
        createdAt = 0,
        completedWorkouts = 6,
        totalWorkouts = 18
    ),
    ProgramSummary(
        id = 2,
        profile = ProgramProfile(45, 95.0, 170, ProgramLevel.BEGINNER, ProgramGoal.LOSE_FAT, ProgramLocation.HOME, 8, 4, ProgramFocus.LOWER_BODY),
        createdAt = 0,
        completedWorkouts = 0,
        totalWorkouts = 32
    )
)

@Preview(name = "Programs - light", showBackground = true)
@Composable
private fun ProgramListPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramListContent(state = ProgramListState(isLoading = false, programs = previewPrograms), onIntent = {})
    }
}

@Preview(name = "Programs - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProgramListDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        ProgramListContent(state = ProgramListState(isLoading = false, programs = previewPrograms), onIntent = {})
    }
}

@Preview(name = "Programs - empty", showBackground = true)
@Composable
private fun ProgramListEmptyPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramListContent(state = ProgramListState(isLoading = false), onIntent = {})
    }
}

@Preview(name = "Programs - delete dialog", showBackground = true)
@Composable
private fun ProgramListDeletePreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramListContent(
            state = ProgramListState(isLoading = false, programs = previewPrograms, pendingDeleteId = 1),
            onIntent = {}
        )
    }
}
