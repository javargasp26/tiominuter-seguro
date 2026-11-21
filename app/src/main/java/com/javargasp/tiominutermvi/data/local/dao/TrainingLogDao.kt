package com.javargasp.tiominutermvi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import kotlinx.coroutines.flow.Flow

/** DAO for the training history. Lists are newest first (completedAt, then id). */
@Dao
interface TrainingLogDao {

    /** Inserts an entry and returns its id; a duplicate uuid throws. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: TrainingLogEntity): Long

    @Query("SELECT * FROM training_log ORDER BY completedAt DESC, id DESC")
    fun observeAll(): Flow<List<TrainingLogEntity>>

    @Query(
        """
        SELECT * FROM training_log
        WHERE dateLocal BETWEEN :fromDate AND :toDate
        ORDER BY completedAt DESC, id DESC
        """
    )
    fun observeBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntity>>

    @Query("SELECT * FROM training_log WHERE id = :id")
    suspend fun getById(id: Long): TrainingLogEntity?

    @Query("SELECT * FROM training_log WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): TrainingLogEntity?

    /** Updates the row with the same id; returns the number of rows changed. */
    @Update
    suspend fun update(entry: TrainingLogEntity): Int

    @Query("DELETE FROM training_log WHERE id = :id")
    suspend fun delete(id: Long)

    /** Removes the whole history (account deletion with a local wipe). */
    @Query("DELETE FROM training_log")
    suspend fun deleteAll()

    /** Sets the photo of one entry; returns the number of rows changed. */
    @Query("UPDATE training_log SET photoPath = :photoPath, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePhotoPath(id: Long, photoPath: String?, updatedAt: Long): Int

    @Query("SELECT photoPath FROM training_log WHERE photoPath IS NOT NULL")
    suspend fun getPhotoPaths(): List<String>
}
