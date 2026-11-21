package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoalRepository
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** My current summary from Room (the read APIs of the training log and the weekly goal). */
class MySummarySource(
    private val trainingLog: TrainingLogRepository,
    private val goals: WeeklyGoalRepository,
    private val dates: LocalDateProvider
) {
    /** Emits on every change of the history or the goals ("today" is read when computing). */
    fun changes(): Flow<Pair<List<TrainingLogEntry>, List<WeeklyGoal>>> =
        combine(trainingLog.observeEntries(), goals.observeGoals()) { entries, goals -> entries to goals }

    suspend fun current(): MemberSummary? =
        GroupSummaryCalculator.summary(dates.today(), trainingLog.observeEntries().first(), goals.observeGoals().first())

    suspend fun hasGoal(): Boolean = current() != null

    fun summaryOf(entries: List<TrainingLogEntry>, goals: List<WeeklyGoal>): MemberSummary? =
        GroupSummaryCalculator.summary(dates.today(), entries, goals)
}
