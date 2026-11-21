package com.javargasp.tiominutermvi.data.sync

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.javargasp.tiominutermvi.domain.sync.DocKey
import com.javargasp.tiominutermvi.domain.sync.IncomingDoc
import com.javargasp.tiominutermvi.domain.sync.OutgoingDoc
import com.javargasp.tiominutermvi.domain.sync.PageCursor
import com.javargasp.tiominutermvi.domain.sync.PullQuery
import com.javargasp.tiominutermvi.domain.sync.RemotePage
import com.javargasp.tiominutermvi.domain.sync.RemoteUserData
import com.javargasp.tiominutermvi.domain.sync.SyncCollection
import com.javargasp.tiominutermvi.domain.sync.SyncTime
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [RemoteUserData] on Cloud Firestore, writing exactly the documents of docs/firestore-model.md:
 * every write carries `updatedAt = serverTimestamp()`, `deleted` and `schemaVersion` 1 (the daily
 * progress, never tombstoned, has no `deleted`); a tombstone is a `set` (not a merge) of only its
 * id field and those three. Reads always come from
 * the server (`Source.SERVER`), so an offline cache can never move a watermark.
 */
class FirestoreRemoteUserData(
    private val firestore: FirebaseFirestore,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : RemoteUserData {

    override suspend fun commit(uid: String, docs: List<OutgoingDoc>) {
        require(docs.size <= RemoteUserData.MAX_BATCH) { "At most ${RemoteUserData.MAX_BATCH} writes per batch" }
        if (docs.isEmpty()) return
        val batch = firestore.batch()
        docs.forEach { doc ->
            batch.set(firestore.document("users/$uid/${doc.key.path}"), documentOf(doc))
        }
        withTimeout(timeoutMillis) { batch.commit().awaitTask() }
    }

    private fun documentOf(doc: OutgoingDoc): Map<String, Any?> {
        val sync = mapOf("updatedAt" to FieldValue.serverTimestamp(), "schemaVersion" to SCHEMA_VERSION)
        val fields = doc.fields
        return if (fields == null) {
            check(doc.key.collection.canTombstone) { "${doc.key.collection.path} is never tombstoned" }
            mapOf(doc.key.collection.idField to doc.key.id, "deleted" to true) + sync
        } else if (doc.key.collection.canTombstone) {
            fields + mapOf("deleted" to false) + sync
        } else {
            // The daily progress has no deleted field (never tombstoned)
            fields + sync
        }
    }

    override suspend fun fetch(uid: String, query: PullQuery, since: SyncTime?, after: PageCursor?, limit: Int): RemotePage {
        var request: Query = collectionOf(uid, query)
        if (since != null) request = request.whereGreaterThanOrEqualTo(UPDATED_AT, since.toTimestamp())
        request = request.orderBy(UPDATED_AT).orderBy(FieldPath.documentId())
        if (after != null) request = request.startAfter(after.updatedAt.toTimestamp(), after.docId)
        val snapshot = withTimeout(timeoutMillis) { request.limit(limit.toLong()).get(Source.SERVER).awaitTask() }
        val docs = snapshot.documents.mapNotNull { it.toIncoming(query) }
        val last = snapshot.documents.lastOrNull()
        val next = if (snapshot.size() >= limit && last != null) {
            last.getTimestamp(UPDATED_AT)?.let { PageCursor(it.toSyncTime(), last.id) }
        } else {
            null
        }
        return RemotePage(docs, next)
    }

    private fun collectionOf(uid: String, query: PullQuery): CollectionReference =
        if (query.parentId == null) {
            firestore.collection("users/$uid/${query.collection.path}")
        } else {
            firestore.collection("users/$uid/${SyncCollection.PROGRAM.path}/${query.parentId}/${query.collection.path}")
        }

    private fun DocumentSnapshot.toIncoming(query: PullQuery): IncomingDoc? {
        val data = data ?: return null
        val updatedAt = getTimestamp(UPDATED_AT) ?: return null
        return IncomingDoc(
            key = DocKey(query.collection, id, query.parentId),
            fields = data - setOf(UPDATED_AT, "deleted", "schemaVersion"),
            deleted = getBoolean("deleted") == true,
            updatedAt = updatedAt.toSyncTime()
        )
    }

    /**
     * Creates `users/{uid}` with `createdAt` = server time, or updates it keeping `createdAt`
     * (a merge without it), as the rules require.
     */
    override suspend fun writeProfile(uid: String, displayName: String?, photoUrl: String?) {
        val ref = firestore.document("users/$uid")
        val exists = withTimeout(timeoutMillis) { ref.get(Source.SERVER).awaitTask() }.exists()
        val fields = mapOf(
            "displayName" to displayName,
            "photoUrl" to photoUrl,
            "updatedAt" to FieldValue.serverTimestamp(),
            "deleted" to false,
            "schemaVersion" to SCHEMA_VERSION
        )
        withTimeout(timeoutMillis) {
            if (exists) {
                ref.set(fields, SetOptions.merge()).awaitTask()
            } else {
                ref.set(fields + ("createdAt" to FieldValue.serverTimestamp())).awaitTask()
            }
        }
    }

    /** Every program's workouts and completions, the programs, the log, the goals, the daily progress, then the profile. */
    override suspend fun deleteAll(uid: String) {
        val programs = firestore.collection("users/$uid/${SyncCollection.PROGRAM.path}")
        while (true) {
            val page = withTimeout(timeoutMillis) { programs.limit(DELETE_PAGE.toLong()).get(Source.SERVER).awaitTask() }
            if (page.isEmpty) break
            page.documents.forEach { program ->
                deleteCollection(program.reference.collection(SyncCollection.PROGRAM_WORKOUT.path))
                deleteCollection(program.reference.collection(SyncCollection.PROGRAM_COMPLETION.path))
            }
            deleteDocuments(page.documents)
        }
        deleteCollection(firestore.collection("users/$uid/${SyncCollection.TRAINING_LOG.path}"))
        deleteCollection(firestore.collection("users/$uid/${SyncCollection.WEEKLY_GOAL.path}"))
        deleteCollection(firestore.collection("users/$uid/${SyncCollection.DAILY_PROGRESS.path}"))
        withTimeout(timeoutMillis) { firestore.document("users/$uid").delete().awaitTask() }
    }

    private suspend fun deleteCollection(collection: CollectionReference) {
        while (true) {
            val page = withTimeout(timeoutMillis) {
                collection.limit(RemoteUserData.MAX_BATCH.toLong()).get(Source.SERVER).awaitTask()
            }
            if (page.isEmpty) return
            deleteDocuments(page.documents)
        }
    }

    private suspend fun deleteDocuments(documents: List<DocumentSnapshot>) {
        documents.chunked(RemoteUserData.MAX_BATCH).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            withTimeout(timeoutMillis) { batch.commit().awaitTask() }
        }
    }

    private fun SyncTime.toTimestamp() = Timestamp(seconds, nanos)

    private fun Timestamp.toSyncTime() = SyncTime(seconds, nanoseconds)

    companion object {
        const val SCHEMA_VERSION = 1L
        const val UPDATED_AT = "updatedAt"
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
        private const val DELETE_PAGE = 100
    }
}

/** Awaits a Play services [Task] without an extra dependency. */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> continuation.resumeWithException(error)
            task.isCanceled -> continuation.cancel()
            else -> continuation.resume(task.result)
        }
    }
}
