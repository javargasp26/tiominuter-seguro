package com.javargasp.tiominutermvi.data.sync

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.javargasp.tiominutermvi.domain.sync.ChangeTracker
import com.javargasp.tiominutermvi.domain.sync.DocKey
import com.javargasp.tiominutermvi.domain.sync.PendingChange
import com.javargasp.tiominutermvi.domain.sync.SyncCollection
import com.javargasp.tiominutermvi.domain.time.WallClock
import kotlinx.coroutines.flow.Flow

/**
 * One pending change of a synced document (the outbox), in the database of the row it describes
 * (the app database and the program database each have this table). One row per document:
 * writing it again bumps [version] and keeps the latest operation, so the sync removes a row only
 * if it wasn't written again while its batch was being uploaded. [parentId] is the program uuid
 * of a workout or completion, "" otherwise.
 */
@Entity(tableName = "sync_outbox", primaryKeys = ["collection", "parentId", "docId"])
data class SyncOutboxEntity(
    val collection: String,
    val parentId: String,
    val docId: String,
    val isDelete: Boolean,
    val version: Long,
    val enqueuedAt: Long
)

@Dao
interface SyncOutboxDao {

    @Query("SELECT * FROM sync_outbox WHERE collection = :collection AND parentId = :parentId AND docId = :docId")
    suspend fun get(collection: String, parentId: String, docId: String): SyncOutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox ORDER BY enqueuedAt, version LIMIT :limit")
    suspend fun oldest(limit: Int): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observeCount(): Flow<Int>

    /** Removes the change only if it wasn't written again (same [version]). */
    @Query(
        "DELETE FROM sync_outbox WHERE collection = :collection AND parentId = :parentId AND docId = :docId " +
            "AND version = :version"
    )
    suspend fun deleteIfVersion(collection: String, parentId: String, docId: String, version: Long)

    @Query("DELETE FROM sync_outbox WHERE collection = :collection AND parentId = :parentId AND docId = :docId")
    suspend fun delete(collection: String, parentId: String, docId: String)

    /** The pending changes of a program's workouts and completions (the program is gone). */
    @Query("DELETE FROM sync_outbox WHERE parentId = :parentId")
    suspend fun deleteChildrenOf(parentId: String)

    /** Empties the outbox (local wipe). */
    @Query("DELETE FROM sync_outbox")
    suspend fun deleteAll()
}

fun SyncOutboxEntity.toPendingChange(): PendingChange? {
    val collection = SyncCollection.fromName(collection) ?: return null
    return PendingChange(DocKey(collection, docId, parentId.ifEmpty { null }), isDelete, version)
}

/** Enqueues a change in [outbox], in the caller's transaction. */
suspend fun SyncOutboxDao.enqueue(key: DocKey, delete: Boolean, now: Long) {
    val parentId = key.parentId.orEmpty()
    val previous = get(key.collection.name, parentId, key.id)
    put(SyncOutboxEntity(key.collection.name, parentId, key.id, delete, (previous?.version ?: 0L) + 1, now))
}

suspend fun SyncOutboxDao.isPending(key: DocKey): Boolean =
    get(key.collection.name, key.parentId.orEmpty(), key.id) != null

suspend fun SyncOutboxDao.drop(key: DocKey) = delete(key.collection.name, key.parentId.orEmpty(), key.id)

/** [ChangeTracker] on a Room database and its [outbox]: the row and its change in one transaction. */
class RoomChangeTracker(
    private val database: RoomDatabase,
    private val outbox: SyncOutboxDao,
    private val clock: WallClock
) : ChangeTracker {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction { block() }

    override suspend fun enqueue(key: DocKey, delete: Boolean) {
        outbox.enqueue(key, delete, clock.nowMillis())
    }
}
