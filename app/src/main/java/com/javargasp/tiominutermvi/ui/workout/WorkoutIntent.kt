package com.javargasp.tiominutermvi.ui.workout

import com.javargasp.tiominutermvi.ui.base.MviIntent

/**
 * Represents user actions on the Workout screen.
 */
sealed class WorkoutIntent : MviIntent {
    data object OnLoad : WorkoutIntent()
    data object OnNextExercise : WorkoutIntent()
    data object OnCompleteReps : WorkoutIntent()
    data object OnSkipExercise : WorkoutIntent()
    data object OnFinishWorkout : WorkoutIntent()
    data object OnBackPressed : WorkoutIntent()
    data object OnTimerFinished : WorkoutIntent()
    data object OnStartTimerWithReady : WorkoutIntent()

    /** The screen became visible / stopped being visible: a REPS exercise counts only while visible. */
    data object OnScreenVisible : WorkoutIntent()
    data object OnScreenHidden : WorkoutIntent()
}