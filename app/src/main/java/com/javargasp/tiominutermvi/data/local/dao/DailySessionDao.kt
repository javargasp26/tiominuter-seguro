package com.javargasp.tiominutermvi.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.local.entity.DailySessionEntity

@Dao
interface DailySessionDao {

    @Query("SELECT * FROM daily_session LIMIT 1")
    suspend fun get(): DailySessionEntity?

    @Upsert
    suspend fun upsert(session: DailySessionEntity)

    /** Removes the session (there is at most one); also used by the local wipe. */
    @Query("DELETE FROM daily_session")
    suspend fun deleteAll()
}
