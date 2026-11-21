package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One weekly goal of [workoutsPerWeek] workouts, in force from the week starting
 * [effectiveFromWeek] (a Monday, "yyyy-MM-dd"). One row per week: setting the goal again
 * in that week replaces it. The column was `daysPerWeek` until version 5.
 */
@Entity(tableName = "weekly_goal")
data class WeeklyGoalEntity(
    @PrimaryKey
    val effectiveFromWeek: String,
    val workoutsPerWeek: Int,
    val updatedAt: Long
)
