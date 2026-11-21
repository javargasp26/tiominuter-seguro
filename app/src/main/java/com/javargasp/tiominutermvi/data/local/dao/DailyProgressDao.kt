package com.javargasp.tiominutermvi.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import kotlinx.coroutines.flow.Flow

/** The single row of the daily progress (the sync writes it through `AppSyncDao`). */
@Dao
interface DailyProgressDao {

    @Query("SELECT * FROM daily_progress LIMIT 1")
    suspend fun get(): DailyProgressEntity?

    @Query("SELECT * FROM daily_progress LIMIT 1")
    fun observe(): Flow<DailyProgressEntity?>

    @Upsert
    suspend fun upsert(progress: DailyProgressEntity)

    /** The refId of every DAILY entry: the first progress comes from them. */
    @Query("SELECT refId FROM training_log WHERE source = 'DAILY'")
    suspend fun dailyRefIds(): List<String?>

    /** Removes the progress (local wipe). */
    @Query("DELETE FROM daily_progress")
    suspend fun deleteAll()
}
