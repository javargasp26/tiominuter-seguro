package com.javargasp.tiominutermvi.data.program.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramCardioSessionEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramStrengthSessionEntity

@Dao
interface ProgramSessionDao {

    @Query("SELECT * FROM program_strength_session WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex")
    suspend fun getStrength(programId: Long, week: Int, dayIndex: Int): ProgramStrengthSessionEntity?

    @Query("SELECT * FROM program_strength_session ORDER BY programId, week, dayIndex")
    suspend fun getAllStrength(): List<ProgramStrengthSessionEntity>

    @Upsert
    suspend fun upsertStrength(session: ProgramStrengthSessionEntity)

    @Query("DELETE FROM program_strength_session WHERE programId = :programId AND week = :week AND dayIndex = :dayIndex")
    suspend fun deleteStrength(programId: Long, week: Int, dayIndex: Int)

    @Query("SELECT * FROM program_cardio_session LIMIT 1")
    suspend fun getCardio(): ProgramCardioSessionEntity?

    @Upsert
    suspend fun upsertCardio(session: ProgramCardioSessionEntity)

    /** Removes the cardio session (there is at most one). */
    @Query("DELETE FROM program_cardio_session")
    suspend fun deleteCardio()
}
