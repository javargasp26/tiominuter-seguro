package com.javargasp.tiominutermvi.ui.gymroutineselection

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.model.GymRoutineExercise
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

/**
 * Previews for the Gym Routine Selection screen.
 */
private fun previewRoutine(
    routineId: Int = 1,
    name: String = "Push Day",
    exercises: List<GymRoutineExercise> = listOf(
        GymRoutineExercise(
            position = 0,
            exerciseId = 1,
            exercise = Exercise(exerciseId = 1, name = "Bench Press", animation = "bench.gif"),
            type = ExerciseType.REPS,
            reps = listOf(10, 10, 10, 10),
            restSeconds = 90
        ),
        GymRoutineExercise(
            position = 1,
            exerciseId = 2,
            exercise = Exercise(exerciseId = 2, name = "Shoulder Press", animation = "shoulder.gif"),
            type = ExerciseType.REPS,
            reps = listOf(10, 10, 10),
            restSeconds = 60
        )
    )
): GymRoutine = GymRoutine(routineId = routineId, routineName = name, exercises = exercises)

@Preview(showBackground = true, name = "Collapsed")
@Composable
private fun GymRoutineSelectionCollapsedPreview() {
    TioMinuterMVITheme {
        GymRoutineSelectionContent(
            state = GymRoutineSelectionState(
                isLoading = false,
                routines = listOf(previewRoutine())
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onToggleExpanded = {},
            onStartRoutine = {},
            onRetry = {},
            onBackPressed = {}
        )
    }
}

@Preview(showBackground = true, name = "Expanded")
@Composable
private fun GymRoutineSelectionExpandedPreview() {
    TioMinuterMVITheme {
        GymRoutineSelectionContent(
            state = GymRoutineSelectionState(
                isLoading = false,
                routines = listOf(previewRoutine()),
                expandedRoutineIds = setOf(1)
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onToggleExpanded = {},
            onStartRoutine = {},
            onRetry = {},
            onBackPressed = {}
        )
    }
}

@Preview(showBackground = true, name = "Long exercise name fade")
@Composable
private fun GymRoutineSelectionLongNamePreview() {
    TioMinuterMVITheme {
        GymRoutineSelectionContent(
            state = GymRoutineSelectionState(
                isLoading = false,
                routines = listOf(
                    previewRoutine(
                        exercises = listOf(
                            GymRoutineExercise(
                                position = 0,
                                exerciseId = 1,
                                exercise = Exercise(
                                    exerciseId = 1,
                                    name = "Incline Dumbbell Press With Very Long Name That Overflows",
                                    animation = "incline.gif"
                                ),
                                type = ExerciseType.REPS,
                                reps = listOf(10, 10, 10),
                                restSeconds = 60
                            )
                        )
                    )
                ),
                expandedRoutineIds = setOf(1)
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onToggleExpanded = {},
            onStartRoutine = {},
            onRetry = {},
            onBackPressed = {}
        )
    }
}

@Preview(showBackground = true, name = "TIME exercise")
@Composable
private fun GymRoutineSelectionTimeExercisePreview() {
    TioMinuterMVITheme {
        GymRoutineSelectionContent(
            state = GymRoutineSelectionState(
                isLoading = false,
                routines = listOf(
                    previewRoutine(
                        exercises = listOf(
                            GymRoutineExercise(
                                position = 0,
                                exerciseId = 3,
                                exercise = Exercise(exerciseId = 3, name = "Plank", animation = "plank.gif"),
                                type = ExerciseType.TIME,
                                reps = listOf(30, 45, 60),
                                restSeconds = 30
                            )
                        )
                    )
                ),
                expandedRoutineIds = setOf(1)
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onToggleExpanded = {},
            onStartRoutine = {},
            onRetry = {},
            onBackPressed = {}
        )
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun GymRoutineSelectionLoadingPreview() {
    TioMinuterMVITheme {
        GymRoutineSelectionContent(
            state = GymRoutineSelectionState(isLoading = true),
            snackbarHostState = remember { SnackbarHostState() },
            onToggleExpanded = {},
            onStartRoutine = {},
            onRetry = {},
            onBackPressed = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun GymRoutineSelectionEmptyPreview() {
    TioMinuterMVITheme {
        GymRoutineSelectionContent(
            state = GymRoutineSelectionState(isLoading = false, routines = emptyList()),
            snackbarHostState = remember { SnackbarHostState() },
            onToggleExpanded = {},
            onStartRoutine = {},
            onRetry = {},
            onBackPressed = {}
        )
    }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun GymRoutineSelectionErrorPreview() {
    TioMinuterMVITheme {
        GymRoutineSelectionContent(
            state = GymRoutineSelectionState(isLoading = false, errorMessage = "error"),
            snackbarHostState = remember { SnackbarHostState() },
            onToggleExpanded = {},
            onStartRoutine = {},
            onRetry = {},
            onBackPressed = {}
        )
    }
}

@Preview(showBackground = true, name = "Routine without exercises")
@Composable
private fun GymRoutineSelectionNoExercisesPreview() {
    TioMinuterMVITheme {
        GymRoutineSelectionContent(
            state = GymRoutineSelectionState(
                isLoading = false,
                routines = listOf(previewRoutine(exercises = emptyList())),
                expandedRoutineIds = setOf(1)
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onToggleExpanded = {},
            onStartRoutine = {},
            onRetry = {},
            onBackPressed = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GymRoutineSelectionDarkPreview() {
    TioMinuterMVITheme {
        GymRoutineSelectionContent(
            state = GymRoutineSelectionState(
                isLoading = false,
                routines = listOf(previewRoutine(), previewRoutine(routineId = 2, name = "Pull Day")),
                expandedRoutineIds = setOf(1)
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onToggleExpanded = {},
            onStartRoutine = {},
            onRetry = {},
            onBackPressed = {}
        )
    }
}
