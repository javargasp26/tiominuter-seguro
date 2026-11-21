package com.javargasp.tiominutermvi.ui.program.strength

import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.ui.base.MviState
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState

data class ProgramStrengthState(
    val week: Int = 1,
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val workout: ProgramWorkout.Strength? = null,
    val exercises: Map<Int, ProgramExercise> = emptyMap(),
    val completedPositions: Set<Int> = emptySet(),
    val isWorkoutCompleted: Boolean = false,
    val showResetConfirmation: Boolean = false,
    /** "Workout completed!" dialog, shown when the last exercise is completed during this visit. */
    val showWorkoutCompleted: Boolean = false,
    /** Photo of the completion shown in that dialog (the training-history entry and its photo). */
    val completionPhoto: CompletionPhotoState = CompletionPhotoState()
) : MviState {
    val totalCount: Int get() = workout?.slots?.size ?: 0
    val completedCount: Int get() = workout?.slots?.count { it.position in completedPositions } ?: 0
    val allCompleted: Boolean get() = totalCount > 0 && completedCount == totalCount
    val canReset: Boolean get() = completedCount > 0
}
