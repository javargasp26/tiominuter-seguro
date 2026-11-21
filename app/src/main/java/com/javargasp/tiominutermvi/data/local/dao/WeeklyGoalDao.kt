package com.javargasp.tiominutermvi.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.local.entity.WeeklyGoalEntity
import kotlinx.coroutines.flow.Flow

/** DAO for the weekly goal history. */
@Dao
interface WeeklyGoalDao {

    /** Inserts the goal, or replaces the one of the same week. */
    @Upsert
    suspend fun upsert(goal: WeeklyGoalEntity)

    @Query("SELECT * FROM weekly_goal ORDER BY effectiveFromWeek")
    fun observeAll(): Flow<List<WeeklyGoalEntity>>

    /** Removes every goal (account deletion with a local wipe). */
    @Query("DELETE FROM weekly_goal")
    suspend fun deleteAll()
}
