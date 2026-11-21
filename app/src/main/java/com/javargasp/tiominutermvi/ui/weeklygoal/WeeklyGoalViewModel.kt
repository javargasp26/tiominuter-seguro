package com.javargasp.tiominutermvi.ui.weeklygoal

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoalRepository
import com.javargasp.tiominutermvi.domain.goal.WeeklyProgressCalculator
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for the Weekly goal screen: choose 1..14 workouts per week with − / + and
 * save. The saved goal applies from the current week (the repository stores it under this
 * week's Monday).
 */
class WeeklyGoalViewModel(
    private val weeklyGoalRepository: WeeklyGoalRepository,
    private val localDateProvider: LocalDateProvider
) : BaseViewModel<WeeklyGoalIntent, WeeklyGoalState, WeeklyGoalEffect>(
    initialState = WeeklyGoalState()
) {

    init {
        viewModelScope.launch {
            val goal = try {
                WeeklyProgressCalculator.goalForWeek(
                    weeklyGoalRepository.observeGoals().first(),
                    localDateProvider.currentWeekStart()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            setState(
                state.value.copy(
                    isLoading = false,
                    currentGoal = goal,
                    selectedWorkouts = goal?.coerceIn(WeeklyGoal.WORKOUTS_RANGE) ?: WeeklyGoalState.DEFAULT_WORKOUTS
                )
            )
        }
    }

    override suspend fun reduce(intent: WeeklyGoalIntent, currentState: WeeklyGoalState) {
        when (intent) {
            is WeeklyGoalIntent.OnIncrement -> select(state.value.selectedWorkouts + 1)
            is WeeklyGoalIntent.OnDecrement -> select(state.value.selectedWorkouts - 1)
            is WeeklyGoalIntent.OnSave -> save()
            is WeeklyGoalIntent.OnBackPressed -> sendEffect(WeeklyGoalEffect.NavigateBack)
        }
    }

    /** Values outside 1..14 are ignored. */
    private fun select(workouts: Int) {
        if (state.value.isLoading || workouts !in WeeklyGoal.WORKOUTS_RANGE) return
        setState(state.value.copy(selectedWorkouts = workouts, saveFailed = false))
    }

    private suspend fun save() {
        val current = state.value
        if (current.isLoading || current.isSaving) return
        setState(current.copy(isSaving = true, saveFailed = false))
        try {
            weeklyGoalRepository.setGoal(current.selectedWorkouts)
            setState(state.value.copy(isSaving = false, currentGoal = current.selectedWorkouts))
            sendEffect(WeeklyGoalEffect.NavigateBack)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("WeeklyGoal: could not save the goal: ${e.message}")
            setState(state.value.copy(isSaving = false, saveFailed = true))
        }
    }
}
