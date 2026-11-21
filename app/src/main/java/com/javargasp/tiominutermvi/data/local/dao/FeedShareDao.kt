package com.javargasp.tiominutermvi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.javargasp.tiominutermvi.data.local.entity.FeedShareEntity
import kotlinx.coroutines.flow.Flow

/** DAO for the group feed's queue ([FeedShareEntity]). */
@Dao
interface FeedShareDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: FeedShareEntity)

    @Query("SELECT * FROM feed_share WHERE entryUuid = :uuid")
    suspend fun get(uuid: String): FeedShareEntity?

    @Query("SELECT * FROM feed_share WHERE dirty = 1 ORDER BY createdAt")
    suspend fun dirty(): List<FeedShareEntity>

    @Query("SELECT COUNT(*) FROM feed_share WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    /** A local change: the flush in progress (if any) won't clear [FeedShareEntity.dirty]. */
    @Query("UPDATE feed_share SET share = :share, version = version + 1, dirty = 1 WHERE entryUuid = :uuid")
    suspend fun setShare(uuid: String, share: Boolean): Int

    @Query("UPDATE feed_share SET entryDeleted = 1, version = version + 1, dirty = 1 WHERE entryUuid = :uuid")
    suspend fun markDeleted(uuid: String): Int

    @Query("UPDATE feed_share SET version = version + 1, dirty = 1 WHERE entryUuid = :uuid")
    suspend fun touch(uuid: String): Int

    /** What a flush did; clears dirty only when the row wasn't changed meanwhile. */
    @Query(
        """
        UPDATE feed_share SET targetGroupIds = :targetGroupIds, postedGroupIds = :postedGroupIds,
            refusedGroupIds = :refusedGroupIds, postedPhotoPath = :postedPhotoPath,
            dirty = CASE WHEN :stillDirty OR version != :version THEN 1 ELSE 0 END
        WHERE entryUuid = :uuid
        """
    )
    suspend fun saveProgress(
        uuid: String,
        targetGroupIds: String?,
        postedGroupIds: String,
        refusedGroupIds: String,
        postedPhotoPath: String?,
        version: Long,
        stillDirty: Boolean
    )

    @Query("DELETE FROM feed_share WHERE entryUuid = :uuid")
    suspend fun delete(uuid: String)

    @Query("DELETE FROM feed_share WHERE createdAt < :millis AND dirty = 0")
    suspend fun deleteDoneBefore(millis: Long)

    /** Local wipe (account switch or deletion). */
    @Query("DELETE FROM feed_share")
    suspend fun deleteAll()
}
