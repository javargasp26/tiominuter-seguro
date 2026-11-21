package com.javargasp.tiominutermvi.ui.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.model.RoutineExercise
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

// Mock data for previews
private val mockRoutine = Routine(
    routineId = 1,
    routineDay = "Day 1",
    title = "Morning Workout",
    exercises = listOf(
        RoutineExercise(
            exerciseId = 1,
            type = ExerciseType.TIME,
            amount = 30
        ),
        RoutineExercise(
            exerciseId = 2,
            type = ExerciseType.REPS,
            amount = 10
        )
    )
)

private val mockExercise = Exercise(
    exerciseId = 1,
    name = "Jumping Jacks",
    animation = "https://example.com/animation.gif"
)

private val mockWorkoutState = WorkoutState(
    isLoading = false,
    routine = mockRoutine,
    currentExerciseIndex = 0,
    currentExercise = mockExercise,
    currentRoutineExercise = mockRoutine.exercises[0],
    completedReps = 5,
    isWorkoutComplete = false,
    hasNavigatedToCompletion = false,
    error = null,
    timerDurationSeconds = 30,
    timerRemainingSeconds = 15,
    isTimerRunning = true,
    isReadyCountdown = false,
    readyCountdownSeconds = 0
)

@Preview(showBackground = true)
@Composable
private fun WorkoutScreenPreview() {
    MaterialTheme {
        WorkoutContent(
            state = mockWorkoutState,
            onIntent = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun WorkoutScreenPreviewMobile() {
    MaterialTheme {
        WorkoutContent(
            state = mockWorkoutState,
            onIntent = {}
        )
    }
}

/**
 * Button layouts in English and Spanish on a common phone width; the Spanish labels
 * are longer ("Saltar ejercicio", "Completar repeticiones") and must stay on one line.
 */
@Preview(name = "English", showBackground = true, widthDp = 360, heightDp = 720)
@Preview(name = "Spanish", locale = "es", showBackground = true, widthDp = 360, heightDp = 720)
private annotation class ButtonLayoutPreviews

@ButtonLayoutPreviews
@Composable
private fun WorkoutTimeIdlePreview() {
    TioMinuterMVITheme {
        WorkoutContent(
            state = mockWorkoutState.copy(
                timerRemainingSeconds = 30,
                isTimerRunning = false
            ),
            onIntent = {}
        )
    }
}

@ButtonLayoutPreviews
@Composable
private fun WorkoutGetReadyPreview() {
    TioMinuterMVITheme {
        WorkoutContent(
            state = mockWorkoutState.copy(
                timerRemainingSeconds = 30,
                isTimerRunning = false,
                isReadyCountdown = true,
                readyCountdownSeconds = 3
            ),
            onIntent = {}
        )
    }
}

@ButtonLayoutPreviews
@Composable
private fun WorkoutTimerRunningPreview() {
    TioMinuterMVITheme {
        WorkoutContent(
            state = mockWorkoutState,
            onIntent = {}
        )
    }
}

@ButtonLayoutPreviews
@Composable
private fun WorkoutRepsPreview() {
    TioMinuterMVITheme {
        WorkoutContent(
            state = mockWorkoutState.copy(
                currentRoutineExercise = mockRoutine.exercises[1], // REPS exercise
                timerDurationSeconds = 0,
                timerRemainingSeconds = 0,
                isTimerRunning = false
            ),
            onIntent = {}
        )
    }
}
