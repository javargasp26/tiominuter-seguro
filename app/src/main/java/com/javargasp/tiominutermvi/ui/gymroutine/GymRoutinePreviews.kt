package com.javargasp.tiominutermvi.ui.gymroutine

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.model.GymRoutineExercise
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

/**
 * Previews for the Gym Routine screen.
 */
@Preview(name = "None completed", showBackground = true)
@Composable
private fun GymRoutineNoneCompletedPreview() {
    TioMinuterMVITheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GymRoutineContent(
                state = GymRoutineState(
                    isLoading = false,
                    routine = previewRoutine(),
                    completedPositions = emptySet()
                ),
                onExerciseClicked = {},
                onResetClicked = {},
                onConfirmReset = {},
                onDismissReset = {},
                onRoutineCompletedDismissed = {},
                onBackPressed = {}
            )
        }
    }
}

@Preview(name = "Some completed", showBackground = true)
@Composable
private fun GymRoutineSomeCompletedPreview() {
    TioMinuterMVITheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GymRoutineContent(
                state = GymRoutineState(
                    isLoading = false,
                    routine = previewRoutine(),
                    completedPositions = setOf(0, 1)
                ),
                onExerciseClicked = {},
                onResetClicked = {},
                onConfirmReset = {},
                onDismissReset = {},
                onRoutineCompletedDismissed = {},
                onBackPressed = {}
            )
        }
    }
}

@Preview(name = "All completed with dialog", showBackground = true)
@Composable
private fun GymRoutineAllCompletedPreview() {
    TioMinuterMVITheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GymRoutineContent(
                state = GymRoutineState(
                    isLoading = false,
                    routine = previewRoutine(),
                    completedPositions = setOf(0, 1, 2),
                    showRoutineCompleted = true,
                    completionPhoto = CompletionPhotoState(entryId = 1)
                ),
                onExerciseClicked = {},
                onResetClicked = {},
                onConfirmReset = {},
                onDismissReset = {},
                onRoutineCompletedDismissed = {},
                onBackPressed = {}
            )
        }
    }
}

@Preview(name = "TIME exercise", showBackground = true)
@Composable
private fun GymRoutineTimeExercisePreview() {
    TioMinuterMVITheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GymRoutineContent(
                state = GymRoutineState(
                    isLoading = false,
                    routine = previewRoutine(timeOnly = true),
                    completedPositions = setOf(0)
                ),
                onExerciseClicked = {},
                onResetClicked = {},
                onConfirmReset = {},
                onDismissReset = {},
                onRoutineCompletedDismissed = {},
                onBackPressed = {}
            )
        }
    }
}

@Preview(name = "Not found", showBackground = true)
@Composable
private fun GymRoutineNotFoundPreview() {
    TioMinuterMVITheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GymRoutineContent(
                state = GymRoutineState(
                    isLoading = false,
                    routine = null,
                    isNotFound = true
                ),
                onExerciseClicked = {},
                onResetClicked = {},
                onConfirmReset = {},
                onDismissReset = {},
                onRoutineCompletedDismissed = {},
                onBackPressed = {}
            )
        }
    }
}

@Preview(name = "Routine without exercises", showBackground = true)
@Composable
private fun GymRoutineEmptyPreview() {
    TioMinuterMVITheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GymRoutineContent(
                state = GymRoutineState(
                    isLoading = false,
                    routine = GymRoutine(routineId = 1, routineName = "Empty Day", exercises = emptyList())
                ),
                onExerciseClicked = {},
                onResetClicked = {},
                onConfirmReset = {},
                onDismissReset = {},
                onRoutineCompletedDismissed = {},
                onBackPressed = {}
            )
        }
    }
}

@Preview(name = "Dark theme", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GymRoutineDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            GymRoutineContent(
                state = GymRoutineState(
                    isLoading = false,
                    routine = previewRoutine(),
                    completedPositions = setOf(0, 1)
                ),
                onExerciseClicked = {},
                onResetClicked = {},
                onConfirmReset = {},
                onDismissReset = {},
                onRoutineCompletedDismissed = {},
                onBackPressed = {}
            )
        }
    }
}

private fun previewRoutine(
    exerciseCount: Int = 3,
    timeOnly: Boolean = false
): GymRoutine = GymRoutine(
    routineId = 1,
    routineName = "Push Day",
    exercises = List(exerciseCount) { index ->
        val type = if (timeOnly) ExerciseType.TIME else ExerciseType.REPS
        GymRoutineExercise(
            position = index,
            exerciseId = index + 1,
            exercise = Exercise(
                exerciseId = index + 1,
                name = "Exercise ${index + 1}",
                animation = "anim${index + 1}.gif"
            ),
            type = type,
            reps = if (timeOnly) listOf(30, 45, 60) else listOf(10, 10, 10),
            restSeconds = 60
        )
    }
)
