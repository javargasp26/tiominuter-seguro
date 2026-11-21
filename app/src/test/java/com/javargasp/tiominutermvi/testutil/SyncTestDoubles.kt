package com.javargasp.tiominutermvi.testutil

import com.javargasp.tiominutermvi.domain.sync.DocKey
import com.javargasp.tiominutermvi.domain.sync.IncomingDoc
import com.javargasp.tiominutermvi.domain.sync.LocalSyncStore
import com.javargasp.tiominutermvi.domain.sync.NetworkMonitor
import com.javargasp.tiominutermvi.domain.sync.OutgoingDoc
import com.javargasp.tiominutermvi.domain.sync.PageCursor
import com.javargasp.tiominutermvi.domain.sync.PendingChange
import com.javargasp.tiominutermvi.domain.sync.PullQuery
import com.javargasp.tiominutermvi.domain.sync.RemotePage
import com.javargasp.tiominutermvi.domain.sync.RemoteUserData
import com.javargasp.tiominutermvi.domain.sync.SyncCollection
import com.javargasp.tiominutermvi.domain.sync.SyncConflicts
import com.javargasp.tiominutermvi.domain.sync.SyncOutcome
import com.javargasp.tiominutermvi.domain.sync.SyncRepository
import com.javargasp.tiominutermvi.domain.sync.SyncScheduler
import com.javargasp.tiominutermvi.domain.sync.SyncStateStore
import com.javargasp.tiominutermvi.domain.sync.SyncStatus
import com.javargasp.tiominutermvi.domain.sync.SyncTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An in-memory Firestore: documents per account and path. Like Firestore, every document of one
 * batch gets the SAME server time (the hard case for pagination), and time grows per batch.
 */
class FakeRemoteUserData : RemoteUserData {
    data class StoredDoc(val key: DocKey, val fields: Map<String, Any?>, val deleted: Boolean, val updatedAt: SyncTime)

    /** uid → path → document. */
    val docs = mutableMapOf<String, MutableMap<String, StoredDoc>>()
    val profiles = mutableMapOf<String, Pair<String?, String?>>()
    val batchSizes = mutableListOf<Int>()
    val fetches = mutableListOf<PullQuery>()
    var commitError: Exception? = null
    var deleteError: Exception? = null
    var fetchError: Exception? = null
    private var serverSeconds = 1_000L

    fun docsOf(uid: String): Map<String, StoredDoc> = docs[uid].orEmpty()

    /** A document written by another device (its own batch, a new server time). */
    fun put(uid: String, key: DocKey, fields: Map<String, Any?>, deleted: Boolean = false) {
        val time = SyncTime(++serverSeconds, 0)
        docs.getOrPut(uid) { mutableMapOf() }[key.path] = StoredDoc(key, fields, deleted, time)
    }

    override suspend fun commit(uid: String, docs: List<OutgoingDoc>) {
        check(docs.size <= RemoteUserData.MAX_BATCH)
        commitError?.let { throw it }
        val time = SyncTime(++serverSeconds, 0)
        val account = this.docs.getOrPut(uid) { mutableMapOf() }
        docs.forEach { doc ->
            val fields = doc.fields ?: mapOf(doc.key.collection.idField to doc.key.id)
            account[doc.key.path] = StoredDoc(doc.key, fields, deleted = doc.fields == null, updatedAt = time)
        }
        batchSizes += docs.size
    }

    override suspend fun fetch(uid: String, query: PullQuery, since: SyncTime?, after: PageCursor?, limit: Int): RemotePage {
        fetchError?.let { throw it }
        fetches += query
        val sorted = docsOf(uid).values
            .filter { it.key.collection == query.collection && it.key.parentId == query.parentId }
            .filter { since == null || it.updatedAt >= since }
            .sortedWith(compareBy<StoredDoc> { it.updatedAt }.thenBy { it.key.id })
            .filter { after == null || it.updatedAt > after.updatedAt || (it.updatedAt == after.updatedAt && it.key.id > after.docId) }
        val page = sorted.take(limit)
        val next = if (sorted.size > limit) page.last().let { PageCursor(it.updatedAt, it.key.id) } else null
        return RemotePage(page.map { IncomingDoc(it.key, it.fields, it.deleted, it.updatedAt) }, next)
    }

    override suspend fun writeProfile(uid: String, displayName: String?, photoUrl: String?) {
        profiles[uid] = displayName to photoUrl
    }

    override suspend fun deleteAll(uid: String) {
        deleteError?.let { throw it }
        docs.remove(uid)
        profiles.remove(uid)
    }
}

/**
 * An in-memory local database for the engine: rows by key (fields + the local updatedAt) and an
 * outbox, with the real conflict policy ([SyncConflicts]). Applying never enqueues.
 */
class InMemorySyncStore(private val collections: List<SyncCollection>) : LocalSyncStore {
    data class Row(val fields: Map<String, Any?>, val updatedAt: Long)

    val rows = mutableMapOf<DocKey, Row>()
    val outbox = linkedMapOf<DocKey, PendingChange>()
    private val count = MutableStateFlow(0)
    private var localClock = 0L

    /** A local write, as a repository does it: the row and its change together. */
    fun write(key: DocKey, fields: Map<String, Any?>, updatedAt: Long = ++localClock) {
        rows[key] = Row(fields, updatedAt)
        enqueue(key, delete = false)
    }

    fun delete(key: DocKey) {
        rows.remove(key)
        enqueue(key, delete = true)
    }

    /** A wipe (the data of another account goes). */
    fun clear() {
        rows.clear()
        outbox.clear()
        count.value = 0
    }

    private fun enqueue(key: DocKey, delete: Boolean) {
        val version = (outbox[key]?.version ?: 0L) + 1
        outbox.remove(key)
        outbox[key] = PendingChange(key, delete, version)
        count.value = outbox.size
    }

    override suspend fun pending(limit: Int): List<OutgoingDoc> =
        outbox.values.take(limit).map { OutgoingDoc(it, if (it.delete) null else rows[it.key]?.fields) }

    override suspend fun pendingCount(): Int = outbox.size

    override fun observePendingCount(): Flow<Int> = count

    override suspend fun acknowledge(changes: List<PendingChange>) {
        changes.forEach { if (outbox[it.key]?.version == it.version) outbox.remove(it.key) }
        count.value = outbox.size
    }

    override suspend fun pullPlan(): List<PullQuery> = collections.map { PullQuery(it) }

    override suspend fun apply(query: PullQuery, docs: List<IncomingDoc>): List<PullQuery> {
        docs.forEach { doc ->
            val pending = doc.key in outbox
            val decision = SyncConflicts.decide(doc, pending)
            if (decision == SyncConflicts.Decision.KEEP_LOCAL) return@forEach
            outbox.remove(doc.key)
            if (doc.deleted) rows.remove(doc.key) else rows[doc.key] = Row(doc.fields, doc.updatedAt.millis)
        }
        count.value = outbox.size
        return emptyList()
    }

    override suspend fun enqueueAll() {
        rows.keys.toList().forEach { enqueue(it, delete = false) }
    }
}

class FakeSyncStateStore : SyncStateStore {
    var owner: String? = null
    val watermarks = mutableMapOf<Pair<String, String>, SyncTime>()
    val successes = mutableMapOf<String, Long>()

    override fun lastSyncedUid(): String? = owner
    override fun setLastSyncedUid(uid: String?) {
        owner = uid
    }
    override fun watermark(uid: String, key: String): SyncTime? = watermarks[uid to key]
    override fun setWatermark(uid: String, key: String, time: SyncTime) {
        watermarks[uid to key] = time
    }
    override fun lastSuccessAt(uid: String): Long? = successes[uid]
    override fun setLastSuccessAt(uid: String, millis: Long) {
        successes[uid] = millis
    }
    override fun forget(uid: String) {
        watermarks.keys.removeAll { it.first == uid }
        successes.remove(uid)
    }

    var erased: String? = null
    override fun erasedAccount(): String? = erased
    override fun setErasedAccount(uid: String?) {
        erased = uid
    }
}

class FakeNetwork(var online: Boolean = true) : NetworkMonitor {
    override fun isOnline(): Boolean = online
}

class RecordingScheduler : SyncScheduler {
    var retries = 0
    override fun scheduleRetry() {
        retries++
    }
}

/** [SyncRepository] for ViewModel tests: a scripted outcome and pending count. */
class FakeSyncRepository(var pending: Int = 0, var outcome: SyncOutcome = SyncOutcome.SUCCESS) : SyncRepository {
    override val status = MutableStateFlow(SyncStatus())
    var syncCalls = 0

    override suspend fun pendingCount(): Int = pending

    override suspend fun syncNow(): SyncOutcome {
        syncCalls++
        if (outcome == SyncOutcome.SUCCESS) pending = 0
        return outcome
    }
}
