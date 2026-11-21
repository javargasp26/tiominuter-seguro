package com.javargasp.tiominutermvi.data.program.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramSlotProgressEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutProgressEntity
import kotlinx.coroutines.flow.Flow

/** Completion markers for exercises (slots) and whole workouts. */
@Dao
interface ProgramProgressDao {

    @Upsert
    suspend fun upsertSlot(progress: ProgramSlotProgressEntity)

    @Upsert
    suspend fun upsertWorkout(progress: ProgramWorkoutProgressEntity)

    @Query("SELECT * FROM program_slot_progress WHERE programId = :programId")
    fun observeSlots(programId: Long): Flow<List<ProgramSlotProgressEntity>>

    @Query(
        "SELECT position FROM program_slot_progress " +
            "WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex"
    )
    fun observeCompletedPositions(programId: Long, week: Int, dayIndex: Int): Flow<List<Int>>

    @Query(
        "SELECT position FROM program_slot_progress " +
            "WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex"
    )
    suspend fun getCompletedPositions(programId: Long, week: Int, dayIndex: Int): List<Int>

    @Query(
        "SELECT completedAt FROM program_slot_progress " +
            "WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex"
    )
    suspend fun getSlotCompletionTimes(programId: Long, week: Int, dayIndex: Int): List<Long>

    @Query("SELECT * FROM program_workout_progress WHERE programId = :programId")
    fun observeWorkouts(programId: Long): Flow<List<ProgramWorkoutProgressEntity>>

    @Query(
        "SELECT COUNT(*) > 0 FROM program_workout_progress " +
            "WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex"
    )
    fun observeWorkoutCompleted(programId: Long, week: Int, dayIndex: Int): Flow<Boolean>

    @Query("DELETE FROM program_slot_progress WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex")
    suspend fun clearSlots(programId: Long, week: Int, dayIndex: Int)
}
