package com.javargasp.tiominutermvi.domain.goal

/**
 * How many workouts per week ([workoutsPerWeek], 1..14) the person wants to do, from the
 * week that starts on [effectiveFromWeek] (a Monday, "yyyy-MM-dd") until a later goal
 * replaces it.
 */
data class WeeklyGoal(
    val workoutsPerWeek: Int,
    val effectiveFromWeek: String
) {
    companion object {
        val WORKOUTS_RANGE = 1..14
    }
}

/**
 * One week's progress. [workoutsPerDay] maps each local date of that week to its number
 * of counted workouts (see [countsTowardGoal]); dates without one are absent. [goal] is
 * the goal in force that week (null before the first goal).
 */
data class WeeklyProgress(
    val weekStart: String,
    val goal: Int?,
    val workoutsPerDay: Map<String, Int>
) {
    /** Counted workouts of the week; two on the same day count as two. */
    val workoutCount: Int get() = workoutsPerDay.values.sum()

    /** Dates with at least one counted workout. */
    val trainedDates: Set<String> get() = workoutsPerDay.filterValues { it > 0 }.keys

    fun workoutsOn(date: String): Int = workoutsPerDay[date] ?: 0

    val isGoalMet: Boolean get() = goal != null && workoutCount >= goal
}

/** The current week for Home: [progress] and the [streak] of weeks with the goal met. */
data class CurrentWeekSummary(
    val today: String,
    val progress: WeeklyProgress,
    val streak: Int
) {
    /** Counted workouts still needed to meet the goal (0 when met or without a goal). */
    val workoutsNeeded: Int get() = progress.goal?.let { maxOf(0, it - progress.workoutCount) } ?: 0
}
