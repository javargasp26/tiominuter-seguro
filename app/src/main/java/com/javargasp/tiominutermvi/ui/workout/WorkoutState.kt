package com.javargasp.tiominutermvi.ui.workout

import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.model.RoutineExercise
import com.javargasp.tiominutermvi.ui.base.MviState

/**
 * Represents the UI state of the Workout screen.
 */
data class WorkoutState(
    val isLoading: Boolean = false,
    val routine: Routine? = null,
    val currentExerciseIndex: Int = 0,
    val currentExercise: Exercise? = null,
    val currentRoutineExercise: RoutineExercise? = null,
    val completedReps: Int = 0,
    val isWorkoutComplete: Boolean = false,
    val hasNavigatedToCompletion: Boolean = false,
    val error: String? = null,
    val timerDurationSeconds: Int = 0,
    val timerRemainingSeconds: Int = 0,
    val isTimerRunning: Boolean = false,
    val isReadyCountdown: Boolean = false,
    val readyCountdownSeconds: Int = 0
) : MviState {
    val currentExerciseNumber: Int get() = currentExerciseIndex + 1
    val totalExercises: Int get() = routine?.exercises?.size ?: 0
    val isLastExercise: Boolean get() = currentExerciseIndex >= totalExercises - 1
    val isRepsExercise: Boolean get() = currentRoutineExercise?.type == ExerciseType.REPS
    val isTimeExercise: Boolean get() = currentRoutineExercise?.type == ExerciseType.TIME
}