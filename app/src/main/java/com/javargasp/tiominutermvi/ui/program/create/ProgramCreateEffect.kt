package com.javargasp.tiominutermvi.ui.program.create

import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface ProgramCreateEffect : MviEffect {
    /** Opens the new program, replacing the create screen in the back stack. */
    data class NavigateToDetail(val programId: Long) : ProgramCreateEffect
    data object NavigateBack : ProgramCreateEffect
}
