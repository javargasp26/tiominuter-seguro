package com.javargasp.tiominutermvi.ui.gymroutineselection

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionRepository
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for the Gym Routine Selection screen.
 *
 * The repository observation starts exactly once per ViewModel instance (in
 * [init]), so re-sending [GymRoutineSelectionIntent.OnLoad] after a
 * configuration change does not re-subscribe. The collector always reads the
 * latest state via [state.value] when updating, never a stale snapshot.
 *
 * [GymRoutineSelectionIntent.OnLoad] refreshes only the first time (the screen
 * re-sends it after every configuration change); [GymRoutineSelectionIntent.OnRetry]
 * always refreshes. [GymRoutineSelectionIntent.OnResume] (every open and return to the
 * foreground) closes inactive gym sessions and announces the saved ones.
 */
class GymRoutineSelectionViewModel(
    private val gymRoutineRepository: GymRoutineRepository,
    private val gymSessions: GymSessionRepository
) : BaseViewModel<GymRoutineSelectionIntent, GymRoutineSelectionState, GymRoutineSelectionEffect>(
    initialState = GymRoutineSelectionState()
) {

    private var hasRequestedInitialRefresh = false

    init {
        observeRoutines()
    }

    override suspend fun reduce(intent: GymRoutineSelectionIntent, currentState: GymRoutineSelectionState) {
        when (intent) {
            is GymRoutineSelectionIntent.OnLoad -> {
                if (!hasRequestedInitialRefresh) {
                    hasRequestedInitialRefresh = true
                    refresh(currentState)
                }
            }
            is GymRoutineSelectionIntent.OnResume -> closeInactiveSessions()
            is GymRoutineSelectionIntent.OnRetry -> refresh(currentState)
            is GymRoutineSelectionIntent.OnToggleExpanded -> toggleExpanded(intent.routineId, currentState)
            is GymRoutineSelectionIntent.OnStartRoutine -> sendEffect(
                GymRoutineSelectionEffect.NavigateToGymRoutine(intent.routineId)
            )
            is GymRoutineSelectionIntent.OnBackPressed -> sendEffect(GymRoutineSelectionEffect.NavigateBack)
        }
    }

    /** Lazy close of abandoned gym sessions; each saved one is announced once. */
    private suspend fun closeInactiveSessions() {
        gymSessions.closeInactiveSessions()
        gymSessions.takeClosedSessions().forEach {
            sendEffect(GymRoutineSelectionEffect.ShowSessionSaved(it.durationSeconds))
        }
    }

    private fun observeRoutines() {
        gymRoutineRepository.observeGymRoutines()
            .onEach { routines ->
                val current = state.value
                setState(
                    current.copy(
                        routines = routines,
                        isLoading = false,
                        errorMessage = null
                    )
                )
            }
            .launchIn(viewModelScope)
    }

    private suspend fun refresh(currentState: GymRoutineSelectionState) {
        setState(state.value.copy(isRefreshing = true, errorMessage = null))
        gymRoutineRepository.refresh()
            .onSuccess {
                setState(state.value.copy(isRefreshing = false))
            }
            .onFailure {
                // The observation keeps the screen up to date; on failure just
                // stop the spinner and surface the error appropriately.
                val current = state.value
                if (current.routines.isEmpty()) {
                    setState(current.copy(isRefreshing = false, errorMessage = it.message))
                } else {
                    setState(current.copy(isRefreshing = false))
                    sendEffect(GymRoutineSelectionEffect.ShowRefreshError)
                }
            }
    }

    private fun toggleExpanded(routineId: Int, currentState: GymRoutineSelectionState) {
        val updated = currentState.expandedRoutineIds.toMutableSet()
        if (!updated.add(routineId)) {
            updated.remove(routineId)
        }
        setState(currentState.copy(expandedRoutineIds = updated))
    }
}
