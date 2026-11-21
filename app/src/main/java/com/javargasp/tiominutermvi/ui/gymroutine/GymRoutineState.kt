package com.javargasp.tiominutermvi.ui.gymroutine

import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.ui.base.MviState
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState

/**
 * State for the Gym Routine screen.
 *
 * [isLoading] is true only until the first routine emission arrives; a null
 * routine after loading means the routine does not exist ([isNotFound]).
 * [completedPositions] may contain positions that are not in the routine;
 * the derived counts filter them out.
 */
data class GymRoutineState(
    val isLoading: Boolean = true,
    val routine: GymRoutine? = null,
    val completedPositions: Set<Int> = emptySet(),
    val isNotFound: Boolean = false,
    val showResetConfirmation: Boolean = false,
    val showRoutineCompleted: Boolean = false,
    /** Photo of the completion shown in the dialog: the entry recorded for it and its photo. */
    val completionPhoto: CompletionPhotoState = CompletionPhotoState()
) : MviState {

    /** Positions of the routine that the user has completed, in position order. */
    val validCompletedPositions: Set<Int>
        get() {
            val positions = routine?.exercises?.map { it.position } ?: return emptySet()
            return completedPositions.intersect(positions.toSet())
        }

    /** Number of completed exercises that exist in the routine. */
    val completedCount: Int get() = validCompletedPositions.size

    /** Total number of exercises in the routine. */
    val totalCount: Int get() = routine?.exercises?.size ?: 0

    /** True when the routine is non-empty and every exercise is completed. */
    val isAllCompleted: Boolean
        get() = routine != null && totalCount > 0 && completedCount == totalCount

    /** True when there is at least one completion mark to clear. */
    val canReset: Boolean get() = completedCount > 0
}
