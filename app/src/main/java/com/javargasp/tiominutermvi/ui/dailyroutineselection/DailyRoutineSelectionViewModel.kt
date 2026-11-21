package com.javargasp.tiominutermvi.ui.dailyroutineselection

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.daily.DailyProgressRepository
import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.domain.session.ActiveSessionTracker
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel for the Daily Routine Selection screen: the routines with their unlock state
 * ([DailyUnlockRules], observed once, so a pull or a finished day shows at once), starting an
 * unlocked day, and "Restart days" after a confirmation.
 */
class DailyRoutineSelectionViewModel(
    private val repository: WorkoutRepository,
    private val dailySessions: ActiveSessionTracker,
    private val dailyProgress: DailyProgressRepository
) : BaseViewModel<DailyRoutineSelectionIntent, DailyRoutineSelectionState, DailyRoutineSelectionEffect>(
    initialState = DailyRoutineSelectionState()
) {

    init {
        dailyProgress.observeProgress()
            .onEach { setState(state.value.copy(progress = it)) }
            .catch { e -> println("DailyRoutineSelection: progress unavailable: ${e.message}") }
            .launchIn(viewModelScope)
    }

    override suspend fun reduce(intent: DailyRoutineSelectionIntent, currentState: DailyRoutineSelectionState) {
        when (intent) {
            is DailyRoutineSelectionIntent.OnLoad -> {
                loadRoutines()
            }
            is DailyRoutineSelectionIntent.OnResume -> {
                // Lazy close of an abandoned daily workout; announced once
                dailySessions.closeInactive()
                dailySessions.takeClosedSessions().forEach {
                    sendEffect(DailyRoutineSelectionEffect.ShowSessionSaved(it.durationSeconds))
                }
            }
            is DailyRoutineSelectionIntent.OnRoutineSelected -> {
                if (currentState.statusOf(intent.routineId) != DailyDayStatus.LOCKED) {
                    sendEffect(DailyRoutineSelectionEffect.NavigateToWorkout(intent.routineId))
                }
            }
            is DailyRoutineSelectionIntent.OnBackPressed -> {
                sendEffect(DailyRoutineSelectionEffect.NavigateBack)
            }
            is DailyRoutineSelectionIntent.OnRestartClicked -> setState(currentState.copy(showRestartDialog = true))
            is DailyRoutineSelectionIntent.OnRestartDismissed -> setState(currentState.copy(showRestartDialog = false))
            is DailyRoutineSelectionIntent.OnRestartConfirmed -> {
                setState(currentState.copy(showRestartDialog = false))
                dailyProgress.restart()
            }
        }
    }

    private suspend fun loadRoutines() {
        var currentState = state.value
        setState(currentState.copy(isLoading = true, error = null))
        
        try {
            val routines = repository.getRoutines()
            currentState = state.value
            setState(
                currentState.copy(
                    isLoading = false,
                    routines = routines
                )
            )
        } catch (e: Exception) {
            currentState = state.value
            setState(
                currentState.copy(
                    isLoading = false,
                    error = e.message
                )
            )
        }
    }
}
