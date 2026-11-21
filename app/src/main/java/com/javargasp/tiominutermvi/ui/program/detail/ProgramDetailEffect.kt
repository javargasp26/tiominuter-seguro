package com.javargasp.tiominutermvi.ui.program.detail

import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutKind
import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface ProgramDetailEffect : MviEffect {
    /** Opens the workout screen that matches [kind]. */
    data class NavigateToWorkout(
        val kind: ProgramWorkoutKind,
        val week: Int,
        val dayIndex: Int
    ) : ProgramDetailEffect

    data object NavigateBack : ProgramDetailEffect

    /** An unfinished program workout was closed for inactivity and saved with this duration. */
    data class ShowSessionSaved(val durationSeconds: Int) : ProgramDetailEffect
}
