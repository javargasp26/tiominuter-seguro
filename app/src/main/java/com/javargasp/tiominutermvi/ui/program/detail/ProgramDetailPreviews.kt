package com.javargasp.tiominutermvi.ui.program.detail

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.program.model.ProgramDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.domain.program.model.ProgramWeekDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.ui.program.common.ProgramPreviewData
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

private fun workout(week: Int, day: Int, workout: ProgramWorkout, done: Boolean) =
    ProgramWorkoutDetail(1, week, day, workout, done, emptySet())

private val previewProgram = ProgramDetail(
    summary = ProgramSummary(
        id = 1,
        profile = ProgramProfile(30, 80.0, 178, ProgramLevel.INTERMEDIATE, ProgramGoal.LOSE_FAT, ProgramLocation.GYM, 6, 3, ProgramFocus.CORE),
        createdAt = 0,
        completedWorkouts = 2,
        totalWorkouts = 18
    ),
    weeks = listOf(
        ProgramWeekDetail(
            1, false,
            listOf(
                workout(1, 0, ProgramPreviewData.strength, true),
                workout(1, 1, ProgramPreviewData.treadmill, true),
                workout(1, 2, ProgramPreviewData.strength, false)
            )
        ),
        ProgramWeekDetail(
            4, true,
            listOf(
                workout(4, 0, ProgramPreviewData.strength, false),
                workout(4, 1, ProgramWorkout.OutdoorRun(2.1, walk = false), false),
                workout(4, 2, ProgramPreviewData.circuit, false)
            )
        )
    )
)

@Preview(name = "Program detail - light", showBackground = true)
@Composable
private fun ProgramDetailPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        ProgramDetailContent(state = ProgramDetailState(isLoading = false, program = previewProgram), onIntent = {})
    }
}

@Preview(name = "Program detail - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProgramDetailDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        ProgramDetailContent(state = ProgramDetailState(isLoading = false, program = previewProgram), onIntent = {})
    }
}
