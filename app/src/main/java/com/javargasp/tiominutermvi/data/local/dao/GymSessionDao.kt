package com.javargasp.tiominutermvi.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.local.entity.GymSessionEntity

@Dao
interface GymSessionDao {

    @Query("SELECT * FROM gym_session WHERE routineId = :routineId")
    suspend fun get(routineId: Int): GymSessionEntity?

    @Query("SELECT * FROM gym_session ORDER BY routineId")
    suspend fun getAll(): List<GymSessionEntity>

    @Upsert
    suspend fun upsert(session: GymSessionEntity)

    @Query("DELETE FROM gym_session WHERE routineId = :routineId")
    suspend fun delete(routineId: Int)

    /** Removes every session (account deletion with a local wipe). */
    @Query("DELETE FROM gym_session")
    suspend fun deleteAll()
}
