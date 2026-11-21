package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyProgressCalculator
import com.javargasp.tiominutermvi.domain.goal.countedWorkoutsPerDay
import com.javargasp.tiominutermvi.domain.goal.countsTowardGoal
import com.javargasp.tiominutermvi.domain.traininglog.CalendarDates
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry

/**
 * My weekly summary for my groups, from the local history (pure). It reuses the weekly-goal rules:
 * only entries that count ([countsTowardGoal], the 10-minute rule), the goal in force this week and
 * the same streak as Home. The week is Monday–Sunday of [today] (the phone's own local date).
 */
object GroupSummaryCalculator {

    /** Null without a goal this week (a group member always has one; the app asks before joining). */
    fun summary(today: String, entries: List<TrainingLogEntry>, goals: List<WeeklyGoal>): MemberSummary? {
        val weekStart = CalendarDates.mondayOf(today)
        val goal = WeeklyProgressCalculator.goalForWeek(goals, weekStart) ?: return null
        val counted = entries.filter(::countsTowardGoal)
        val perDay = countedWorkoutsPerDay(counted)
        val week = CalendarDates.weekDates(today)
        val days = week.mapIndexedNotNull { index, date ->
            val sources = counted.filter { it.dateLocal == date }.map { it.source }.distinct().sortedBy { it.ordinal }
            if (sources.isEmpty()) null else (index + 1) to sources
        }.toMap()
        return MemberSummary(
            weekStartLocal = weekStart,
            workoutsThisWeek = WeeklyProgressCalculator.progress(today, goals, perDay).workoutCount
                .coerceAtMost(GroupLimits.MAX_WORKOUTS),
            weeklyGoal = goal,
            streakWeeks = WeeklyProgressCalculator.streak(today, goals, perDay).coerceAtMost(GroupLimits.MAX_STREAK),
            daysThisWeek = days,
            // A date after today only comes from a wrong clock: never "trained in the future"
            lastTrainedLocal = counted.map { it.dateLocal }.filter { it <= today }.maxOrNull()
        )
    }
}
