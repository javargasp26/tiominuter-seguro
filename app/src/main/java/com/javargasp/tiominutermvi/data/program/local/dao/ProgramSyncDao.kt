package com.javargasp.tiominutermvi.data.program.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutProgressEntity

/** The sync's reads and writes of programs, their workouts and completions (never enqueued). */
@Dao
interface ProgramSyncDao {

    @Query("SELECT * FROM programs ORDER BY id")
    suspend fun getPrograms(): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE uuid = :uuid")
    suspend fun getProgramByUuid(uuid: String): ProgramEntity?

    @Query("SELECT uuid FROM programs WHERE id = :programId")
    suspend fun uuidOf(programId: Long): String?

    @Update
    suspend fun updateProgram(program: ProgramEntity)

    @Query("DELETE FROM programs WHERE uuid = :uuid")
    suspend fun deleteProgram(uuid: String)

    /** Insert-or-update without a delete, so the workout's progress is not cascaded away. */
    @Upsert
    suspend fun upsertWorkout(workout: ProgramWorkoutEntity)

    @Query("DELETE FROM program_workouts WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex")
    suspend fun deleteWorkout(programId: Long, week: Int, dayIndex: Int)

    @Query("SELECT * FROM program_workout_progress ORDER BY programId, week, dayIndex")
    suspend fun getCompletions(): List<ProgramWorkoutProgressEntity>

    @Query("SELECT * FROM program_workout_progress WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex")
    suspend fun getCompletion(programId: Long, week: Int, dayIndex: Int): ProgramWorkoutProgressEntity?

    @Upsert
    suspend fun upsertCompletion(completion: ProgramWorkoutProgressEntity)

    @Query("DELETE FROM program_workout_progress WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex")
    suspend fun deleteCompletion(programId: Long, week: Int, dayIndex: Int)
}
