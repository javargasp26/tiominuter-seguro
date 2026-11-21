package com.javargasp.tiominutermvi.ui.workout

import com.javargasp.tiominutermvi.ui.base.MviEffect

/**
 * Represents one-time events from the Workout screen.
 */
sealed class WorkoutEffect : MviEffect {
    data object NavigateBack : WorkoutEffect()
    data object NavigateToHome : WorkoutEffect()
    data object NavigateToCompletion : WorkoutEffect()
    data object PlayCompletionSound : WorkoutEffect()
    data object TriggerHapticFeedback : WorkoutEffect()

    /** An unfinished daily workout was closed (inactive, or replaced by this one) and saved. */
    data class ShowSessionSaved(val durationSeconds: Int) : WorkoutEffect()
}