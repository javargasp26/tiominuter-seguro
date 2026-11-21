package com.javargasp.tiominutermvi.ui.program.treadmill

import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegment
import com.javargasp.tiominutermvi.ui.base.MviState
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState

data class ProgramTreadmillState(
    val week: Int = 1,
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val segments: List<TreadmillSegment> = emptyList(),
    val currentIndex: Int = 0,
    /** Remaining seconds of the current segment. */
    val remainingSeconds: Int = 0,
    val isStarted: Boolean = false,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val showExitConfirmation: Boolean = false,
    /** Photo of this completion, offered on the finished view (training-history entry + photo). */
    val completionPhoto: CompletionPhotoState = CompletionPhotoState()
) : MviState {
    val currentSegment: TreadmillSegment? get() = segments.getOrNull(currentIndex)
    val nextSegment: TreadmillSegment? get() = segments.getOrNull(currentIndex + 1)

    /** Current segment's remaining time plus every later segment. */
    val totalRemainingSeconds: Int
        get() = if (isFinished) 0 else remainingSeconds + segments.drop(currentIndex + 1).sumOf { it.durationSeconds }
}
