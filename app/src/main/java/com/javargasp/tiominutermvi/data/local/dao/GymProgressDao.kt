package com.javargasp.tiominutermvi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.javargasp.tiominutermvi.data.local.entity.GymExerciseProgressEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for per-exercise-slot completion markers of gym routines.
 */
@Dao
interface GymProgressDao {

    @Query(
        """
        SELECT * FROM gym_exercise_progress
        WHERE routineId = :routineId
        """
    )
    fun observeProgress(routineId: Int): Flow<List<GymExerciseProgressEntity>>

    @Query("SELECT completedAt FROM gym_exercise_progress WHERE routineId = :routineId")
    suspend fun getCompletionTimes(routineId: Int): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markCompleted(entity: GymExerciseProgressEntity)

    @Query(
        """
        DELETE FROM gym_exercise_progress
        WHERE routineId = :routineId
        """
    )
    suspend fun clearRoutine(routineId: Int)

    /** Removes the progress of every routine (account deletion with a local wipe). */
    @Query("DELETE FROM gym_exercise_progress")
    suspend fun deleteAll()
}
