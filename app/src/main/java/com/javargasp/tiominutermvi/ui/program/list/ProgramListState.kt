package com.javargasp.tiominutermvi.ui.program.list

import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.ui.base.MviState

data class ProgramListState(
    val isLoading: Boolean = true,
    val programs: List<ProgramSummary> = emptyList(),
    /** Program waiting for delete confirmation; null when no dialog is shown. */
    val pendingDeleteId: Long? = null
) : MviState {
    val isEmpty: Boolean get() = !isLoading && programs.isEmpty()
}
