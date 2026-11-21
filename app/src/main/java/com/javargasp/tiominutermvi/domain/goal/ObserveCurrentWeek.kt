package com.javargasp.tiominutermvi.domain.goal

import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines the goal history and the training history (counted workouts per day, see
 * [countsTowardGoal]) into the current week's summary. "Today" is read on every emission; collect again to pick up a
 * new day (Home does it on every OnStart).
 */
class ObserveCurrentWeek(
    private val weeklyGoalRepository: WeeklyGoalRepository,
    private val trainingLogRepository: TrainingLogRepository,
    private val localDateProvider: LocalDateProvider
) {
    operator fun invoke(): Flow<CurrentWeekSummary> =
        combine(
            weeklyGoalRepository.observeGoals(),
            trainingLogRepository.observeEntries()
        ) { goals, entries ->
            WeeklyProgressCalculator.currentWeek(localDateProvider.today(), goals, countedWorkoutsPerDay(entries))
        }
}
