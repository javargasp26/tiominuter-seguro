package com.javargasp.tiominutermvi.ui.program.list

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Program list: observes every stored program once, in init. */
class ProgramListViewModel(
    private val repository: ProgramRepository
) : BaseViewModel<ProgramListIntent, ProgramListState, ProgramListEffect>(ProgramListState()) {

    init {
        repository.observePrograms()
            .onEach { programs -> setState(state.value.copy(isLoading = false, programs = programs)) }
            .launchIn(viewModelScope)
    }

    override suspend fun reduce(intent: ProgramListIntent, currentState: ProgramListState) {
        when (intent) {
            ProgramListIntent.OnScreenResumed -> {
                // Lazy close of abandoned program workouts; each saved one is announced once
                repository.closeInactiveSessions()
                repository.takeClosedSessions().forEach { sendEffect(ProgramListEffect.ShowSessionSaved(it.durationSeconds)) }
            }
            is ProgramListIntent.OnProgramClicked -> sendEffect(ProgramListEffect.NavigateToDetail(intent.programId))
            ProgramListIntent.OnCreateClicked -> sendEffect(ProgramListEffect.NavigateToCreate)
            is ProgramListIntent.OnDeleteRequested -> setState(state.value.copy(pendingDeleteId = intent.programId))
            ProgramListIntent.OnConfirmDelete -> {
                val id = state.value.pendingDeleteId ?: return
                setState(state.value.copy(pendingDeleteId = null))
                repository.deleteProgram(id)
            }
            ProgramListIntent.OnDismissDelete -> setState(state.value.copy(pendingDeleteId = null))
            ProgramListIntent.OnBackPressed -> sendEffect(ProgramListEffect.NavigateBack)
        }
    }
}
