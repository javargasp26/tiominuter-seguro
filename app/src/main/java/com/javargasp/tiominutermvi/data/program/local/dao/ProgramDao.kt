package com.javargasp.tiominutermvi.data.program.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.javargasp.tiominutermvi.data.program.local.ProgramSummaryRow
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutEntity
import kotlinx.coroutines.flow.Flow

/** Programs and their generated workouts. Workouts are written once, at creation. */
@Dao
interface ProgramDao {

    @Insert
    suspend fun insertProgram(program: ProgramEntity): Long

    @Insert
    suspend fun insertWorkouts(workouts: List<ProgramWorkoutEntity>)

    /** Newest first, with total and completed workout counts. */
    @Query(
        """
        SELECT p.*,
            (SELECT COUNT(*) FROM program_workouts w WHERE w.programId = p.id) AS totalWorkouts,
            (SELECT COUNT(*) FROM program_workout_progress c WHERE c.programId = p.id) AS completedWorkouts
        FROM programs p
        ORDER BY p.createdAt DESC, p.id DESC
        """
    )
    fun observeProgramSummaries(): Flow<List<ProgramSummaryRow>>

    @Query("SELECT * FROM programs WHERE id = :programId")
    fun observeProgram(programId: Long): Flow<ProgramEntity?>

    @Query("SELECT * FROM programs WHERE id = :programId")
    suspend fun getProgram(programId: Long): ProgramEntity?

    @Query("SELECT * FROM program_workouts WHERE programId = :programId ORDER BY week, dayIndex")
    fun observeWorkouts(programId: Long): Flow<List<ProgramWorkoutEntity>>

    @Query("SELECT * FROM program_workouts WHERE programId = :programId ORDER BY week, dayIndex")
    suspend fun getWorkouts(programId: Long): List<ProgramWorkoutEntity>

    @Query("SELECT * FROM program_workouts WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex")
    fun observeWorkout(programId: Long, week: Int, dayIndex: Int): Flow<ProgramWorkoutEntity?>

    @Query("SELECT * FROM program_workouts WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex")
    suspend fun getWorkout(programId: Long, week: Int, dayIndex: Int): ProgramWorkoutEntity?

    /** Deletes the program; workouts and progress go with it (CASCADE). */
    @Query("DELETE FROM programs WHERE id = :programId")
    suspend fun deleteProgram(programId: Long)
}
