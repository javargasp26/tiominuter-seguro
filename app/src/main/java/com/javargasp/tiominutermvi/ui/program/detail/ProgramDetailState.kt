package com.javargasp.tiominutermvi.ui.program.detail

import com.javargasp.tiominutermvi.domain.program.model.ProgramDetail
import com.javargasp.tiominutermvi.ui.base.MviState

data class ProgramDetailState(
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val program: ProgramDetail? = null
) : MviState
