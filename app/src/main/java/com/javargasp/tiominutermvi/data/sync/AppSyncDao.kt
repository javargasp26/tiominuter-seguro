package com.javargasp.tiominutermvi.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.data.local.entity.WeeklyGoalEntity

/** The sync's reads and writes of the training log, the weekly goals and the daily progress (never enqueued). */
@Dao
interface AppSyncDao {

    @Query("SELECT * FROM training_log WHERE uuid = :uuid")
    suspend fun getEntry(uuid: String): TrainingLogEntity?

    @Query("SELECT uuid FROM training_log")
    suspend fun entryUuids(): List<String>

    @Insert
    suspend fun insertEntry(entry: TrainingLogEntity): Long

    @Update
    suspend fun updateEntry(entry: TrainingLogEntity)

    @Query("DELETE FROM training_log WHERE uuid = :uuid")
    suspend fun deleteEntry(uuid: String)

    @Query("SELECT * FROM weekly_goal WHERE effectiveFromWeek = :week")
    suspend fun getGoal(week: String): WeeklyGoalEntity?

    @Query("SELECT effectiveFromWeek FROM weekly_goal")
    suspend fun goalWeeks(): List<String>

    @Upsert
    suspend fun upsertGoal(goal: WeeklyGoalEntity)

    @Query("DELETE FROM weekly_goal WHERE effectiveFromWeek = :week")
    suspend fun deleteGoal(week: String)

    @Query("SELECT * FROM daily_progress LIMIT 1")
    suspend fun getDailyProgress(): DailyProgressEntity?

    @Upsert
    suspend fun upsertDailyProgress(progress: DailyProgressEntity)

    @Query("SELECT refId FROM training_log WHERE source = 'DAILY'")
    suspend fun dailyRefIds(): List<String?>
}
