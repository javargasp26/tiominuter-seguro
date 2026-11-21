package com.javargasp.tiominutermvi.ui.gymexercise

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.GymRoutineExercise
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

/**
 * Previews for the Gym Exercise screen.
 */
@Preview(name = "Reps set", showBackground = true)
@Composable
private fun GymExerciseRepsSetPreview() {
    GymExercisePreview(
        state = previewState(
            exercise = previewRepsExercise(),
            phase = GymExercisePhase.PerformingReps(setIndex = 1)
        )
    )
}

@Preview(name = "Timed set ready", showBackground = true)
@Composable
private fun GymExerciseTimedReadyPreview() {
    GymExercisePreview(
        state = previewState(
            exercise = previewTimedExercise(),
            phase = GymExercisePhase.ReadyToStartTimed(setIndex = 0)
        )
    )
}

@Preview(name = "Get ready", showBackground = true)
@Composable
private fun GymExerciseGetReadyPreview() {
    GymExercisePreview(
        state = previewState(
            exercise = previewTimedExercise(),
            phase = GymExercisePhase.GetReady(setIndex = 0, remainingSeconds = 3)
        )
    )
}

@Preview(name = "Timed set running", showBackground = true)
@Composable
private fun GymExerciseTimedRunningPreview() {
    GymExercisePreview(
        state = previewState(
            exercise = previewTimedExercise(),
            phase = GymExercisePhase.PerformingTimed(setIndex = 1, remainingSeconds = 27, totalSeconds = 45)
        )
    )
}

@Preview(name = "Resting", showBackground = true)
@Composable
private fun GymExerciseRestingPreview() {
    GymExercisePreview(
        state = previewState(
            exercise = previewRepsExercise(),
            phase = GymExercisePhase.Resting(nextSetIndex = 2, remainingSeconds = 32, totalSeconds = 45)
        )
    )
}

@Preview(name = "Last set", showBackground = true)
@Composable
private fun GymExerciseLastSetPreview() {
    GymExercisePreview(
        state = previewState(
            exercise = previewRepsExercise(),
            phase = GymExercisePhase.PerformingReps(setIndex = 3)
        )
    )
}

@Preview(name = "Dark theme", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GymExerciseDarkPreview() {
    GymExercisePreview(
        state = previewState(
            exercise = previewRepsExercise(),
            phase = GymExercisePhase.Resting(nextSetIndex = 1, remainingSeconds = 20, totalSeconds = 45)
        )
    )
}

@Composable
private fun GymExercisePreview(state: GymExerciseState) {
    TioMinuterMVITheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GymExerciseContent(
                state = state,
                onSetDone = {},
                onStartTimedSet = {},
                onSkipRest = {},
                onBackPressed = {},
                onConfirmExit = {},
                onDismissExit = {}
            )
        }
    }
}

private fun previewState(
    exercise: GymRoutineExercise,
    phase: GymExercisePhase
) = GymExerciseState(
    isLoading = false,
    routineName = "Pull Day",
    exercise = exercise,
    position = 1,
    exerciseNumber = 2,
    totalExercises = 11,
    phase = phase
)

private fun previewRepsExercise() = GymRoutineExercise(
    position = 1,
    exerciseId = 18,
    exercise = Exercise(exerciseId = 18, name = "Pull-ups", animation = "18_pull_ups.gif"),
    type = ExerciseType.REPS,
    reps = listOf(10, 10, 8, 8),
    restSeconds = 45
)

private fun previewTimedExercise() = GymRoutineExercise(
    position = 1,
    exerciseId = 4,
    exercise = Exercise(exerciseId = 4, name = "Plank", animation = "4_plank.gif"),
    type = ExerciseType.TIME,
    reps = listOf(30, 45, 60),
    restSeconds = 30
)
