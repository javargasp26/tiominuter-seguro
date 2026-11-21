package com.javargasp.tiominutermvi.ui.program.detail

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.program.model.kind
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Program detail: weeks, workouts and completion, observed once in init. */
class ProgramDetailViewModel(
    private val programId: Long,
    private val repository: ProgramRepository
) : BaseViewModel<ProgramDetailIntent, ProgramDetailState, ProgramDetailEffect>(ProgramDetailState()) {

    init {
        repository.observeProgram(programId)
            .onEach { program ->
                setState(
                    state.value.copy(isLoading = false, isNotFound = program == null, program = program)
                )
            }
            .launchIn(viewModelScope)
    }

    override suspend fun reduce(intent: ProgramDetailIntent, currentState: ProgramDetailState) {
        when (intent) {
            ProgramDetailIntent.OnScreenResumed -> {
                // Lazy close of abandoned program workouts; each saved one is announced once
                repository.closeInactiveSessions()
                repository.takeClosedSessions().forEach { sendEffect(ProgramDetailEffect.ShowSessionSaved(it.durationSeconds)) }
            }
            is ProgramDetailIntent.OnWorkoutClicked -> {
                val workout = state.value.program?.weeks
                    ?.firstOrNull { it.weekNumber == intent.week }
                    ?.workouts?.firstOrNull { it.dayIndex == intent.dayIndex }
                    ?: return
                sendEffect(ProgramDetailEffect.NavigateToWorkout(workout.workout.kind, intent.week, intent.dayIndex))
            }
            ProgramDetailIntent.OnBackPressed -> sendEffect(ProgramDetailEffect.NavigateBack)
        }
    }
}
