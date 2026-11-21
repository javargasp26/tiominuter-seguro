package com.javargasp.tiominutermvi.domain.sync

import com.javargasp.tiominutermvi.domain.auth.LocalUserDataWiper
import com.javargasp.tiominutermvi.domain.time.WallClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What to do with a pulled document whose local copy may have a pending change. */
object SyncConflicts {

    enum class Decision { APPLY, KEEP_LOCAL }

    /**
     * The same policy for every document, weekly goals included, and no device clock involved:
     * without a pending local change the remote document is applied; with one, the local version
     * wins (it is pushed next and gets a newer server time), except a tombstone of a random-id
     * document (entry, program): deletion wins (docs/firestore-model.md).
     */
    fun decide(doc: IncomingDoc, pendingLocally: Boolean): Decision = when {
        !pendingLocally -> Decision.APPLY
        doc.deleted && doc.key.collection.hasRandomIds -> Decision.APPLY
        else -> Decision.KEEP_LOCAL
    }
}

/** SKIPPED: nothing to do (a guest, or an account whose server data was erased for Delete account). */
enum class SyncOutcome { SUCCESS, OFFLINE, FAILED, SKIPPED }

/** Live state of the engine for the Account screen. */
data class SyncRunState(val running: Boolean = false, val failing: Boolean = false, val lastSuccessAt: Long? = null)

/**
 * One sync of an account: the account check, then pull, then push. Only one runs at a time
 * ([mutex]); a failure keeps every unacknowledged change for the next run and schedules a retry.
 *
 * Account check (on the account's first sync on this device):
 * - local data of another account ([SyncStateStore.lastSyncedUid] is another uid): wipe it first
 *   ([wiper]), then download the new account's data in full;
 * - guest data (never synced): mark every local row pending, then pull in full: the union is
 *   uploaded and downloaded (a merge by id; weekly goals by last write).
 *
 * Pull: per store, every query of [LocalSyncStore.pullPlan] from its watermark (the server time of
 * the newest document applied; inclusive, so re-applying is harmless), page by page. Watermarks
 * are read once and saved only after a store's pull completes: a crash pulls again.
 * Push: batches of up to [RemoteUserData.MAX_BATCH]; a change leaves the outbox only after its
 * batch commits.
 */
class SyncEngine(
    private val stores: List<LocalSyncStore>,
    private val remote: RemoteUserData,
    private val state: SyncStateStore,
    private val wiper: LocalUserDataWiper,
    private val clock: WallClock,
    private val network: NetworkMonitor,
    private val scheduler: SyncScheduler,
    private val pageSize: Int = DEFAULT_PAGE_SIZE
) {
    private val mutex = Mutex()
    private val runState = MutableStateFlow(SyncRunState())

    val status: StateFlow<SyncRunState> = runState

    /** Changes waiting to be uploaded, in every store. */
    val pendingCount: Flow<Int> =
        if (stores.isEmpty()) flowOf(0) else combine(stores.map { it.observePendingCount() }) { it.sum() }

    suspend fun pendingNow(): Int = stores.sumOf { it.pendingCount() }

    /** Shows the last success of [uid] (or none) until the next sync. */
    fun showAccount(uid: String?) {
        runState.update { it.copy(lastSuccessAt = uid?.let(state::lastSuccessAt), failing = false) }
    }

    suspend fun sync(uid: String): SyncOutcome = mutex.withLock {
        // Its server data was erased for Delete account: never upload its local data again
        if (state.erasedAccount() == uid) return@withLock SyncOutcome.SKIPPED
        if (!network.isOnline()) {
            scheduler.scheduleRetry()
            return@withLock SyncOutcome.OFFLINE
        }
        runState.update { it.copy(running = true) }
        try {
            checkAccount(uid)
            stores.forEach { pull(uid, it) }
            stores.forEach { push(uid, it) }
            val now = clock.nowMillis()
            state.setLastSuccessAt(uid, now)
            runState.value = SyncRunState(running = false, failing = false, lastSuccessAt = now)
            SyncOutcome.SUCCESS
        } catch (e: CancellationException) {
            runState.update { it.copy(running = false) }
            throw e
        } catch (e: Exception) {
            // No personal data in the log: the exception's class and message only
            println("Sync: failed (${e.javaClass.simpleName}): ${e.message}")
            runState.update { it.copy(running = false, failing = true) }
            scheduler.scheduleRetry()
            SyncOutcome.FAILED
        }
    }

    /**
     * Deletes every server document of [uid] (Delete account), under the sync lock so no sync runs
     * meanwhile; throws on failure (nothing is marked, so a later sync restores what was already
     * deleted). Once everything is deleted the account is marked erased: none of its syncs run
     * until [clearErasedAccount] (the account was deleted, or signed out). The device then holds
     * guest data again: a later, explicit sign-in merges.
     */
    suspend fun eraseServerData(uid: String) = mutex.withLock {
        remote.deleteAll(uid)
        state.setErasedAccount(uid)
        state.forget(uid)
        if (state.lastSyncedUid() == uid) state.setLastSyncedUid(null)
        runState.value = SyncRunState()
    }

    /** The erased account is gone or signed out: syncs are allowed again (for the next account). */
    fun clearErasedAccount() {
        state.setErasedAccount(null)
    }

    private suspend fun checkAccount(uid: String) {
        val owner = state.lastSyncedUid()
        when {
            owner == uid -> Unit
            owner != null -> {
                // Another account's data: it stays safe in its own cloud
                wiper.wipe()
                state.forget(uid)
                state.setLastSyncedUid(uid)
            }
            else -> {
                stores.forEach { it.enqueueAll() }
                state.forget(uid)
                state.setLastSyncedUid(uid)
            }
        }
    }

    private suspend fun pull(uid: String, store: LocalSyncStore) {
        val queue = ArrayDeque(store.pullPlan())
        val newWatermarks = mutableMapOf<String, SyncTime>()
        while (queue.isNotEmpty()) {
            val query = queue.removeFirst()
            val key = query.collection.path
            val since = if (query.fullFetch) null else state.watermark(uid, key)
            var cursor: PageCursor? = null
            do {
                val page = remote.fetch(uid, query, since, cursor, pageSize)
                queue.addAll(store.apply(query, page.docs))
                page.docs.maxOfOrNull { it.updatedAt }?.let { newest ->
                    newWatermarks[key] = maxOf(newest, newWatermarks[key] ?: newest)
                }
                cursor = page.next
            } while (cursor != null)
        }
        newWatermarks.forEach { (key, time) ->
            val current = state.watermark(uid, key)
            if (current == null || time > current) state.setWatermark(uid, key, time)
        }
    }

    private suspend fun push(uid: String, store: LocalSyncStore) {
        repeat(MAX_PUSH_ROUNDS) {
            val batch = store.pending(RemoteUserData.MAX_BATCH)
            if (batch.isEmpty()) return
            remote.commit(uid, batch)
            store.acknowledge(batch.map { it.change })
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 200

        /** A run stops pushing after this many batches (more is left for the next run). */
        private const val MAX_PUSH_ROUNDS = 40
    }
}
