package com.javargasp.tiominutermvi.data.groups

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.javargasp.tiominutermvi.data.sync.awaitTask
import com.javargasp.tiominutermvi.domain.groups.FeedPage
import com.javargasp.tiominutermvi.domain.groups.FeedPost
import com.javargasp.tiominutermvi.domain.groups.FeedRepository
import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.NewPost
import com.javargasp.tiominutermvi.domain.groups.Reaction
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date

/**
 * [FeedRepository] on Cloud Firestore: the documents and queries of docs/firestore-model.md "Feed"
 * (firebase/rules-tests/test/feedOps.js is the reference). The week query uses the composite index
 * `posts (weekStartLocal ASC, createdAt DESC)` of firebase/firestore.indexes.json; the other
 * queries use single-field indexes. One-shot reads come from the server.
 */
class FirestoreFeedRepository(
    private val firestore: FirebaseFirestore,
    private val timeoutMillis: Long = FirestoreGroupRepository.DEFAULT_TIMEOUT_MILLIS
) : FeedRepository {

    private fun posts(groupId: String) = firestore.collection("groups/$groupId/posts")
    private fun postRef(groupId: String, postId: String) = firestore.document("groups/$groupId/posts/$postId")

    override suspend fun createPost(groupId: String, post: NewPost) = call {
        postRef(groupId, post.postId).set(postFields(post)).awaitTask()
        Unit
    }

    override suspend fun isMyPost(groupId: String, postId: String, authorUid: String): Boolean = call {
        val doc = postRef(groupId, postId).get(Source.SERVER).awaitTask()
        doc.exists() && doc.getString("authorUid") == authorUid
    }

    override suspend fun setThumbnail(groupId: String, postId: String, thumb: ByteArray?) = call {
        postRef(groupId, postId).update(THUMB, thumb?.let(Blob::fromBytes) ?: FieldValue.delete()).awaitTask()
        Unit
    }

    override suspend fun deletePost(groupId: String, postId: String) = call {
        postRef(groupId, postId).delete().awaitTask()
        Unit
    }

    override fun observeWeek(groupId: String, weekStartLocal: String, limit: Int): Flow<FeedPage> = callbackFlow {
        val registration = posts(groupId)
            .whereEqualTo("weekStartLocal", weekStartLocal)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(GroupException(FirestoreGroupRepository.errorOf(error), error.javaClass.simpleName))
                    snapshot != null -> {
                        val posts = snapshot.documents.mapNotNull { it.toPost() }
                        trySend(FeedPage(posts, hasMore = snapshot.size() >= limit))
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun setReaction(groupId: String, postId: String, uid: String, reaction: Reaction?) = call {
        // Only my key, never the whole map: concurrent reactions of others are kept
        postRef(groupId, postId).update(FieldPath.of(REACTIONS, uid), reaction?.code ?: FieldValue.delete()).awaitTask()
        Unit
    }

    override suspend fun postIdsBy(groupId: String, authorUid: String): List<String> = call {
        posts(groupId).whereEqualTo("authorUid", authorUid).get(Source.SERVER).awaitTask().documents.map { it.id }
    }

    override suspend fun postIds(groupId: String, limit: Int): List<String> = call {
        posts(groupId).limit(limit.toLong()).get(Source.SERVER).awaitTask().documents.map { it.id }
    }

    override suspend fun expiredPostIds(groupId: String, createdBeforeMillis: Long, limit: Int): List<String> = call {
        posts(groupId)
            .whereLessThan("createdAt", Timestamp(Date(createdBeforeMillis)))
            .orderBy("createdAt")
            .limit(limit.toLong())
            .get(Source.SERVER)
            .awaitTask()
            .documents
            .map { it.id }
    }

    private suspend fun <T> call(block: suspend () -> T): T = firestoreCall(timeoutMillis, block)

    companion object {
        private const val THUMB = "thumb"
        private const val REACTIONS = "reactions"
        private const val TITLE_KEY = "titleKey"

        /** Exactly the fields the rules accept; optional ones are left out when empty. */
        internal fun postFields(post: NewPost): Map<String, Any?> = buildMap {
            put("authorUid", post.authorUid)
            put("authorName", post.profile.displayName)
            put("authorPhotoUrl", post.profile.photoUrl)
            put("source", post.source.name)
            put("title", post.title)
            put("durationSeconds", post.durationSeconds)
            put("dateLocal", post.dateLocal)
            put("weekStartLocal", post.weekStartLocal)
            put("createdAt", FieldValue.serverTimestamp())
            put(REACTIONS, emptyMap<String, String>())
            put("schemaVersion", 1)
            post.activityType?.let { put("activityType", it.code) }
            post.titleKey?.let { put(TITLE_KEY, it) }
            post.thumb?.let { put(THUMB, Blob.fromBytes(it)) }
        }

        internal fun DocumentSnapshot.toPost(): FeedPost? {
            if (!exists()) return null
            val source = TrainingSource.entries.firstOrNull { it.name == getString("source") } ?: return null
            @Suppress("UNCHECKED_CAST")
            val reactions = (get(REACTIONS) as? Map<String, Any?>).orEmpty()
                .mapNotNull { (uid, code) -> Reaction.fromCode(code as? String)?.let { uid to it } }
                .toMap()
            return FeedPost(
                id = id,
                authorUid = getString("authorUid").orEmpty(),
                authorName = getString("authorName").orEmpty(),
                authorPhotoUrl = getString("authorPhotoUrl"),
                source = source,
                title = getString("title").orEmpty(),
                activityType = getString("activityType")?.let(ActivityType::fromCode),
                durationSeconds = getLong("durationSeconds")?.toInt(),
                dateLocal = getString("dateLocal").orEmpty(),
                weekStartLocal = getString("weekStartLocal").orEmpty(),
                // A post of mine still pending shows the estimated time instead of nothing
                createdAtMillis = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
                reactions = reactions,
                thumb = getBlob(THUMB)?.toBytes(),
                titleKey = getString(TITLE_KEY)
            )
        }
    }
}

