package com.javargasp.tiominutermvi.ui.workout

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.daily.DailyProgressRepository
import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.base.MviEffect
import com.javargasp.tiominutermvi.ui.base.MviIntent
import com.javargasp.tiominutermvi.ui.base.MviState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * [offersStartAgain]: [routineId] is the last daily routine and every day of the cycle is finished
 * ([DailyUnlockRules.isCycleComplete]; the progress is written as this screen opens, so it shows
 * once that write arrives).
 */
data class WorkoutCompletionState(
    val routineId: Int = 0,
    val routines: List<Routine> = emptyList(),
    val progress: DailyProgress? = null,
    val showRestartDialog: Boolean = false
) : MviState {
    private val lastRoutineId: Int? get() = routines.maxOfOrNull { it.routineId }

    val offersStartAgain: Boolean
        get() = routineId == lastRoutineId && progress?.let { DailyUnlockRules.isCycleComplete(it, lastRoutineId) } == true

    val firstDay: WorkoutName.Daily
        get() = routines.firstOrNull { it.routineId == 1 }?.let(WorkoutNameResolver::dailyName) ?: WorkoutName.Daily(1)
}

sealed interface WorkoutCompletionIntent : MviIntent {
    data object OnStartAgainClicked : WorkoutCompletionIntent
    data object OnRestartConfirmed : WorkoutCompletionIntent
    data object OnRestartDismissed : WorkoutCompletionIntent
}

sealed interface WorkoutCompletionEffect : MviEffect {
    /** Restarted: the days, from Day 1. */
    data object NavigateToDailySelection : WorkoutCompletionEffect
}

/** The daily completion screen: after the last day, "Start again from Day 1" (the same restart). */
class WorkoutCompletionViewModel(
    routineId: Int,
    workoutRepository: WorkoutRepository,
    private val dailyProgress: DailyProgressRepository
) : BaseViewModel<WorkoutCompletionIntent, WorkoutCompletionState, WorkoutCompletionEffect>(
    initialState = WorkoutCompletionState(routineId = routineId)
) {

    init {
        viewModelScope.launch {
            val routines = runCatching { workoutRepository.getRoutines() }.getOrDefault(emptyList())
            setState(state.value.copy(routines = routines))
        }
        dailyProgress.observeProgress()
            .onEach { setState(state.value.copy(progress = it)) }
            .catch { e -> println("WorkoutCompletion: progress unavailable: ${e.message}") }
            .launchIn(viewModelScope)
    }

    override suspend fun reduce(intent: WorkoutCompletionIntent, currentState: WorkoutCompletionState) {
        when (intent) {
            WorkoutCompletionIntent.OnStartAgainClicked -> setState(currentState.copy(showRestartDialog = true))
            WorkoutCompletionIntent.OnRestartDismissed -> setState(currentState.copy(showRestartDialog = false))
            WorkoutCompletionIntent.OnRestartConfirmed -> {
                setState(currentState.copy(showRestartDialog = false))
                dailyProgress.restart()
                sendEffect(WorkoutCompletionEffect.NavigateToDailySelection)
            }
        }
    }
}
