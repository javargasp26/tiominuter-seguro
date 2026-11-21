package com.javargasp.tiominutermvi.domain.goal

import kotlinx.coroutines.flow.Flow

/** The goal history: one goal per week in which it was set. */
interface WeeklyGoalRepository {

    /** Every goal, oldest effectiveFromWeek first. */
    fun observeGoals(): Flow<List<WeeklyGoal>>

    /**
     * Sets [workoutsPerWeek] (1..14, otherwise IllegalArgumentException) from the current
     * week; a goal already set this week is replaced, past weeks keep theirs.
     */
    suspend fun setGoal(workoutsPerWeek: Int)
}
