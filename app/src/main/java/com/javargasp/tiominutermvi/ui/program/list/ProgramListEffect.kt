package com.javargasp.tiominutermvi.ui.program.list

import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface ProgramListEffect : MviEffect {
    data object NavigateToCreate : ProgramListEffect
    data class NavigateToDetail(val programId: Long) : ProgramListEffect
    data object NavigateBack : ProgramListEffect

    /** An unfinished program workout was closed for inactivity and saved with this duration. */
    data class ShowSessionSaved(val durationSeconds: Int) : ProgramListEffect
}
