package com.javargasp.tiominutermvi.domain.goal

import com.javargasp.tiominutermvi.domain.traininglog.CalendarDates

/**
 * Pure weekly-goal rules. Weeks start on Monday; dates are local "yyyy-MM-dd" strings,
 * so they compare correctly as text. Inputs are counted workouts per date
 * ([countedWorkoutsPerDay]).
 */
object WeeklyProgressCalculator {

    /**
     * The goal in force in the week starting [weekStart]: the latest goal whose
     * effectiveFromWeek is on or before it; null before the first goal.
     */
    fun goalForWeek(goals: List<WeeklyGoal>, weekStart: String): Int? =
        goals.filter { it.effectiveFromWeek <= weekStart }
            .maxByOrNull { it.effectiveFromWeek }
            ?.workoutsPerWeek

    /** Progress of the week that contains [date]: its counted workouts, per day. */
    fun progress(date: String, goals: List<WeeklyGoal>, workoutsPerDay: Map<String, Int>): WeeklyProgress {
        val week = CalendarDates.weekDates(date)
        return WeeklyProgress(
            weekStart = week.first(),
            goal = goalForWeek(goals, week.first()),
            workoutsPerDay = workoutsPerDay.filter { (day, count) -> day in week && count > 0 }
        )
    }

    /**
     * Consecutive weeks with the goal met, ending at the week of [today]. Completed weeks
     * count back from the previous week until one without its goal met or without a goal;
     * the current week adds 1 only once its goal is met, and never breaks the streak.
     */
    fun streak(today: String, goals: List<WeeklyGoal>, workoutsPerDay: Map<String, Int>): Int {
        val firstGoalWeek = goals.minOfOrNull { it.effectiveFromWeek } ?: return 0
        var streak = if (progress(today, goals, workoutsPerDay).isGoalMet) 1 else 0
        var week = CalendarDates.plusWeeks(today, -1)
        while (week >= firstGoalWeek && progress(week, goals, workoutsPerDay).isGoalMet) {
            streak++
            week = CalendarDates.plusWeeks(week, -1)
        }
        return streak
    }

    /** Everything Home needs about the week of [today]. */
    fun currentWeek(today: String, goals: List<WeeklyGoal>, workoutsPerDay: Map<String, Int>): CurrentWeekSummary =
        CurrentWeekSummary(
            today = today,
            progress = progress(today, goals, workoutsPerDay),
            streak = streak(today, goals, workoutsPerDay)
        )
}
