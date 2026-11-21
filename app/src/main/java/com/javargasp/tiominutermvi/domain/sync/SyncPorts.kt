package com.javargasp.tiominutermvi.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Records local changes of synced data. Repositories write their row and [enqueue] its change in
 * ONE [inTransaction], so no change is ever lost or invented. [None] (the default in tests and
 * fakes) runs the block and records nothing.
 */
interface ChangeTracker {
    suspend fun <T> inTransaction(block: suspend () -> T): T
    suspend fun enqueue(key: DocKey, delete: Boolean = false)

    object None : ChangeTracker {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
        override suspend fun enqueue(key: DocKey, delete: Boolean) = Unit
    }
}

/**
 * The synced data of one local database (the app database, the program database): its outbox,
 * how its rows become documents and how pulled documents become rows. Pulled documents are
 * applied without enqueueing anything (no ping-pong).
 */
interface LocalSyncStore {
    /** Pending changes, oldest first. */
    suspend fun pending(limit: Int): List<OutgoingDoc>

    suspend fun pendingCount(): Int

    fun observePendingCount(): Flow<Int>

    /** Removes these changes from the outbox, unless the document changed again meanwhile. */
    suspend fun acknowledge(changes: List<PendingChange>)

    /** What to pull, in order (a program before its subcollections). */
    suspend fun pullPlan(): List<PullQuery>

    /**
     * Applies a page of [query] in one transaction, with the conflict policy of
     * [SyncConflicts]. Returns more queries to run now (the subcollections of programs that
     * didn't exist locally, fetched in full).
     */
    suspend fun apply(query: PullQuery, docs: List<IncomingDoc>): List<PullQuery>

    /** Marks every local row as pending (the merge when local data meets an account). */
    suspend fun enqueueAll()
}

/** Firestore, behind an interface: the app's only reads and writes of the cloud. */
interface RemoteUserData {
    /** Writes up to [MAX_BATCH] documents in one batch (updatedAt = server time); throws on failure. */
    suspend fun commit(uid: String, docs: List<OutgoingDoc>)

    /** One page of [query] ordered by (updatedAt, id), from [since] (inclusive) or after [after]. */
    suspend fun fetch(uid: String, query: PullQuery, since: SyncTime?, after: PageCursor?, limit: Int): RemotePage

    /** Creates or updates `users/{uid}` (createdAt set once by the server). */
    suspend fun writeProfile(uid: String, displayName: String?, photoUrl: String?)

    /** Deletes every document under `users/{uid}` (subcollections included) and the profile, in batches. */
    suspend fun deleteAll(uid: String)

    companion object {
        /** Firestore's limit of writes per batch. */
        const val MAX_BATCH = 500
    }
}

/** Sync bookkeeping, per account: whose data is on the device, pull watermarks, the last result. */
interface SyncStateStore {
    /** The account whose data the local database holds (null: guest data, never synced). */
    fun lastSyncedUid(): String?
    fun setLastSyncedUid(uid: String?)

    fun watermark(uid: String, key: String): SyncTime?
    fun setWatermark(uid: String, key: String, time: SyncTime)

    fun lastSuccessAt(uid: String): Long?
    fun setLastSuccessAt(uid: String, millis: Long)

    /** Forgets an account's watermarks and times (its server data was deleted). */
    fun forget(uid: String)

    /**
     * The account whose server data was erased for Delete account while the account itself still
     * exists (its deletion failed): it is never synced, so its local data is not uploaded again,
     * until it is deleted or signs out. Survives a restart.
     */
    fun erasedAccount(): String?
    fun setErasedAccount(uid: String?)
}

/** Whether the device has a network now (the sync is skipped and scheduled when not). */
fun interface NetworkMonitor {
    fun isOnline(): Boolean
}

/** Runs a sync later, when there is a network (WorkManager in the app). */
fun interface SyncScheduler {
    fun scheduleRetry()
}
