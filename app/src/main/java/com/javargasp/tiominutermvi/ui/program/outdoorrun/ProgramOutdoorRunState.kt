package com.javargasp.tiominutermvi.ui.program.outdoorrun

import com.javargasp.tiominutermvi.ui.base.MviState
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState

/** Outdoor run (or brisk walk): a target distance and a stopwatch. No GPS. */
data class ProgramOutdoorRunState(
    val week: Int = 1,
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val distanceKm: Double = 0.0,
    val walk: Boolean = false,
    val elapsedSeconds: Int = 0,
    val isStarted: Boolean = false,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val showFinishConfirmation: Boolean = false,
    val showExitConfirmation: Boolean = false,
    /** Photo of this completion, offered on the finished view (training-history entry + photo). */
    val completionPhoto: CompletionPhotoState = CompletionPhotoState()
) : MviState
