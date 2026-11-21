package com.javargasp.tiominutermvi.testutil

import com.javargasp.tiominutermvi.data.local.dao.FeedShareDao
import com.javargasp.tiominutermvi.data.local.entity.FeedShareEntity
import com.javargasp.tiominutermvi.domain.groups.FeedLimits
import com.javargasp.tiominutermvi.domain.groups.FeedPage
import com.javargasp.tiominutermvi.domain.groups.FeedPost
import com.javargasp.tiominutermvi.domain.groups.FeedPreferences
import com.javargasp.tiominutermvi.domain.groups.FeedRepository
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.NewPost
import com.javargasp.tiominutermvi.domain.groups.Reaction
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutTitleKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** [FeedShareDao] in memory, with the SQL semantics of its queries (version bumps, dirty only if unchanged). */
class FakeFeedShareDao : FeedShareDao {
    val rows = MutableStateFlow<Map<String, FeedShareEntity>>(emptyMap())

    override suspend fun put(row: FeedShareEntity) {
        rows.value = rows.value + (row.entryUuid to row)
    }

    override suspend fun get(uuid: String): FeedShareEntity? = rows.value[uuid]

    override suspend fun dirty(): List<FeedShareEntity> = rows.value.values.filter { it.dirty }.sortedBy { it.createdAt }

    override fun observeDirtyCount(): Flow<Int> = rows.map { map -> map.values.count { it.dirty } }

    private fun change(uuid: String, block: (FeedShareEntity) -> FeedShareEntity): Int {
        val row = rows.value[uuid] ?: return 0
        rows.value = rows.value + (uuid to block(row))
        return 1
    }

    override suspend fun setShare(uuid: String, share: Boolean): Int =
        change(uuid) { it.copy(share = share, version = it.version + 1, dirty = true) }

    override suspend fun markDeleted(uuid: String): Int =
        change(uuid) { it.copy(entryDeleted = true, version = it.version + 1, dirty = true) }

    override suspend fun touch(uuid: String): Int = change(uuid) { it.copy(version = it.version + 1, dirty = true) }

    override suspend fun saveProgress(
        uuid: String,
        targetGroupIds: String?,
        postedGroupIds: String,
        refusedGroupIds: String,
        postedPhotoPath: String?,
        version: Long,
        stillDirty: Boolean
    ) {
        change(uuid) {
            it.copy(
                targetGroupIds = targetGroupIds,
                postedGroupIds = postedGroupIds,
                refusedGroupIds = refusedGroupIds,
                postedPhotoPath = postedPhotoPath,
                dirty = stillDirty || it.version != version
            )
        }
    }

    override suspend fun delete(uuid: String) {
        rows.value = rows.value - uuid
    }

    override suspend fun deleteDoneBefore(millis: Long) {
        rows.value = rows.value.filterValues { !(it.createdAt < millis && !it.dirty) }
    }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
}

class InMemoryFeedPreferences : FeedPreferences {
    val introSeen = mutableSetOf<String>()
    val cleanups = mutableMapOf<String, Long>()
    val hidden = mutableMapOf<String, MutableSet<String>>()
    val groupCounts = mutableMapOf<String, Int>()
    val tabs = mutableMapOf<String, Int>()

    override fun isFeedIntroSeen(uid: String): Boolean = uid in introSeen
    override fun setFeedIntroSeen(uid: String) {
        introSeen += uid
    }
    override fun lastFeedCleanupAt(uid: String): Long? = cleanups[uid]
    override fun setLastFeedCleanupAt(uid: String, millis: Long) {
        cleanups[uid] = millis
    }
    override fun hiddenPosts(uid: String): Set<String> = hidden[uid].orEmpty()
    override fun hidePost(uid: String, key: String, nowMillis: Long) {
        hidden.getOrPut(uid) { mutableSetOf() } += key
    }
    override fun knownGroupCount(uid: String): Int? = groupCounts[uid]
    override fun setKnownGroupCount(uid: String, count: Int) {
        groupCounts[uid] = count
    }
    override fun detailTab(groupId: String): Int? = tabs[groupId]
    override fun setDetailTab(groupId: String, tab: Int) {
        tabs[groupId] = tab
    }
}

/**
 * The feed in memory with the semantics of firebase/firestore.rules "Feed": members only, a
 * second create of an id refused, only the author changes the thumbnail, deletes by the author,
 * the owner (while the group exists), any member after 8 days, a no-op delete of a missing post.
 * [uid] is the signed-in caller; membership and ownership come from [groups]. [failOn] scripts errors.
 */
class FakeFeedRepository(private val groups: FakeGroupRepository, var now: () -> Long) : FeedRepository {

    var uid = "me"

    /** When set, a reaction write waits for it (to see the optimistic state before the answer). */
    var reactionGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val posts = mutableMapOf<String, LinkedHashMap<String, FeedPost>>()
    val calls = mutableListOf<String>()
    private val errors = mutableMapOf<String, ArrayDeque<GroupError>>()
    private val version = MutableStateFlow(0)

    fun failOn(operation: String, error: GroupError, times: Int = 1) {
        errors.getOrPut(operation) { ArrayDeque() }.apply { repeat(times) { addLast(error) } }
    }

    private fun op(name: String, detail: String) {
        calls += "$name:$detail"
        errors[name]?.removeFirstOrNull()?.let { throw GroupException(it) }
    }

    private fun denied(): Nothing = throw GroupException(GroupError.PERMISSION_DENIED)

    private fun isMember(groupId: String) = groupId in groups.groups && groups.members[groupId]?.containsKey(uid) == true

    private fun changed() {
        version.value++
    }

    fun post(groupId: String, postId: String): FeedPost? = posts[groupId]?.get(postId)

    /** A post seeded directly (e.g. one created [ageMillis] ago by [author]). */
    fun seed(groupId: String, post: FeedPost) {
        posts.getOrPut(groupId) { linkedMapOf() }[post.id] = post
        changed()
    }

    override suspend fun createPost(groupId: String, post: NewPost) {
        op("createPost", "$groupId:${post.postId}")
        if (!isMember(groupId) || post.authorUid != uid) denied()
        if (post.thumb != null && post.thumb.size > FeedLimits.THUMB_MAX_BYTES) denied()
        // As the rules: optional, but only a valid key of the post's own source
        if (post.titleKey != null && WorkoutTitleKey.parse(post.titleKey, post.source) == null) denied()
        val existing = posts[groupId]?.get(post.postId)
        if (existing != null) denied()
        seed(
            groupId,
            FeedPost(
                id = post.postId,
                authorUid = post.authorUid,
                authorName = post.profile.displayName,
                authorPhotoUrl = post.profile.photoUrl,
                source = post.source,
                title = post.title,
                activityType = post.activityType,
                durationSeconds = post.durationSeconds,
                dateLocal = post.dateLocal,
                weekStartLocal = post.weekStartLocal,
                createdAtMillis = now(),
                reactions = emptyMap(),
                thumb = post.thumb,
                titleKey = post.titleKey
            )
        )
    }

    override suspend fun isMyPost(groupId: String, postId: String, authorUid: String): Boolean {
        op("isMyPost", "$groupId:$postId")
        if (!isMember(groupId)) denied()
        return posts[groupId]?.get(postId)?.authorUid == authorUid
    }

    override suspend fun setThumbnail(groupId: String, postId: String, thumb: ByteArray?) {
        op("setThumbnail", "$groupId:$postId:${thumb?.size ?: "none"}")
        val post = posts[groupId]?.get(postId) ?: throw GroupException(GroupError.NOT_FOUND)
        if (!isMember(groupId) || post.authorUid != uid) denied()
        if (thumb != null && thumb.size > FeedLimits.THUMB_MAX_BYTES) denied()
        seed(groupId, post.copy(thumb = thumb))
    }

    override suspend fun deletePost(groupId: String, postId: String) {
        op("deletePost", "$groupId:$postId")
        val post = posts[groupId]?.get(postId)
        val allowed = when {
            post == null -> isMember(groupId)
            post.authorUid == uid -> true
            groups.groups[groupId]?.ownerUid == uid -> true
            else -> isMember(groupId) && now() > (post.createdAtMillis ?: 0L) + FeedLimits.POST_LIFETIME_MILLIS
        }
        if (!allowed) denied()
        if (post != null) {
            posts.getValue(groupId).remove(postId)
            changed()
        }
    }

    override fun observeWeek(groupId: String, weekStartLocal: String, limit: Int): Flow<FeedPage> = flow {
        calls += "observeWeek:$groupId:$limit"
        version.collect {
            if (!isMember(groupId)) throw GroupException(GroupError.PERMISSION_DENIED)
            val week = posts[groupId].orEmpty().values
                .filter { it.weekStartLocal == weekStartLocal }
                .sortedByDescending { it.createdAtMillis ?: Long.MAX_VALUE }
            emit(FeedPage(week.take(limit), hasMore = week.size >= limit))
        }
    }

    override suspend fun setReaction(groupId: String, postId: String, uid: String, reaction: Reaction?) {
        reactionGate?.await()
        op("setReaction", "$groupId:$postId:${reaction?.code ?: "none"}")
        if (!isMember(groupId) || uid != this.uid) denied()
        val post = posts[groupId]?.get(postId) ?: throw GroupException(GroupError.NOT_FOUND)
        seed(groupId, post.copy(reactions = if (reaction == null) post.reactions - uid else post.reactions + (uid to reaction)))
    }

    override suspend fun postIdsBy(groupId: String, authorUid: String): List<String> {
        op("postIdsBy", "$groupId:$authorUid")
        if (!isMember(groupId)) denied()
        return posts[groupId].orEmpty().values.filter { it.authorUid == authorUid }.map { it.id }
    }

    override suspend fun postIds(groupId: String, limit: Int): List<String> {
        op("postIds", groupId)
        if (!isMember(groupId)) denied()
        return posts[groupId].orEmpty().keys.take(limit)
    }

    override suspend fun expiredPostIds(groupId: String, createdBeforeMillis: Long, limit: Int): List<String> {
        op("expiredPostIds", groupId)
        if (!isMember(groupId)) denied()
        return posts[groupId].orEmpty().values
            .filter { (it.createdAtMillis ?: Long.MAX_VALUE) < createdBeforeMillis }
            .sortedBy { it.createdAtMillis }
            .take(limit)
            .map { it.id }
    }
}

/** A post as the fake stores it, for seeding. */
fun feedPost(
    id: String,
    author: String,
    createdAt: Long,
    week: String = "2026-10-19",
    reactions: Map<String, Reaction> = emptyMap(),
    source: com.javargasp.tiominutermvi.domain.traininglog.TrainingSource =
        com.javargasp.tiominutermvi.domain.traininglog.TrainingSource.GYM,
    title: String = "Pull Day",
    titleKey: String? = null
) = FeedPost(
    id = id,
    authorUid = author,
    authorName = author.replaceFirstChar { it.uppercase() },
    authorPhotoUrl = null,
    source = source,
    title = title,
    activityType = null,
    durationSeconds = 1800,
    dateLocal = week,
    weekStartLocal = week,
    createdAtMillis = createdAt,
    reactions = reactions,
    thumb = null,
    titleKey = titleKey
)

/** The training history in memory (newest first), for the real repository. */
class InMemoryTrainingLogDao : com.javargasp.tiominutermvi.data.local.dao.TrainingLogDao {
    val rows = MutableStateFlow<List<com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity>>(emptyList())

    override suspend fun insert(entry: com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1
        rows.value = rows.value + entry.copy(id = id)
        return id
    }

    override fun observeAll() = rows.map { list -> list.sortedWith(compareByDescending<com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity> { it.completedAt }.thenByDescending { it.id }) }
    override fun observeBetween(fromDate: String, toDate: String) = observeAll().map { list -> list.filter { it.dateLocal in fromDate..toDate } }
    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun getByUuid(uuid: String) = rows.value.firstOrNull { it.uuid == uuid }
    override suspend fun update(entry: com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity): Int {
        if (rows.value.none { it.id == entry.id }) return 0
        rows.value = rows.value.map { if (it.id == entry.id) entry else it }
        return 1
    }
    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
    override suspend fun updatePhotoPath(id: Long, photoPath: String?, updatedAt: Long): Int {
        val before = rows.value
        rows.value = before.map { if (it.id == id) it.copy(photoPath = photoPath, updatedAt = updatedAt) else it }
        return before.count { it.id == id }
    }
    override suspend fun getPhotoPaths(): List<String> = rows.value.mapNotNull { it.photoPath }
}

/** No photo files (the training history only deletes them). */
object NoPhotoFiles : com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoFiles {
    override fun fileOf(relativePath: String) = java.io.File(relativePath)
    override fun newStagedPhoto() = throw UnsupportedOperationException()
    override fun captureFile() = throw UnsupportedOperationException()
    override fun deleteCapture() = Unit
    override fun deletePhoto(photoPath: String) = Unit
    override fun storedFiles(): List<String> = emptyList()
    override fun deleteStoredFile(relativePath: String) = Unit
    override fun deleteStaleTemporaryFiles(olderThanMillis: Long) = Unit
}

/**
 * The feed wired over fakes on top of [GroupsTestKit]: the real training history and the real
 * Room-shaped queue (in memory), so recording, deleting and photo changes reach the publisher as
 * in the app. [signedInUid] is who the queue sees as signed in (null: a guest).
 */
class FeedTestKit(scope: kotlinx.coroutines.CoroutineScope? = null) {
    val groups = GroupsTestKit(scope)
    var signedInUid: String? = "me"
    val shareDao = FakeFeedShareDao()
    val store = com.javargasp.tiominutermvi.data.traininglog.RoomWorkoutShareStore(shareDao, { groups.now }) { signedInUid }
    val logDao = InMemoryTrainingLogDao()
    val trainingLog = com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl(
        logDao, { groups.now }, groups.dates, NoPhotoFiles, shares = store
    )
    val feed = FakeFeedRepository(groups.repository) { groups.now }
    val preferences = InMemoryFeedPreferences()

    /** Thumbnails by photo path (none: the photo can't be read). */
    val thumbnails = mutableMapOf<String, ByteArray>()

    /** The program workouts the program feature knows, by refId (for the posts' title key). */
    val programTitles = FakeProgramTitleCodes()
    val publisher = com.javargasp.tiominutermvi.domain.groups.FeedPublisher(
        auth = groups.auth,
        store = store,
        trainingLog = trainingLog,
        groups = groups.repository,
        feed = feed,
        profiles = groups.profiles,
        thumbnails = { path -> thumbnails[path] },
        preferences = preferences,
        network = groups.network,
        sync = groups.sync,
        dates = groups.dates,
        clock = { groups.now },
        log = groups.log,
        scope = scope ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        programTitles = programTitles
    )
    val moderation = com.javargasp.tiominutermvi.domain.groups.FeedModeration(feed)
    val cleaner = com.javargasp.tiominutermvi.domain.groups.FeedCleaner(feed, groups.repository, preferences, { groups.now }, groups.log)
    val operations = com.javargasp.tiominutermvi.domain.groups.GroupOperations(
        groups.repository, groups.auth, groups.summaries, groups.profiles, groups.network, groups.reconciler,
        null, groups.cache, groups.dates, groups.log, moderation
    ) { groups.codes.removeFirst() }
    val sharing = com.javargasp.tiominutermvi.domain.groups.FeedShareController(
        groups.auth, trainingLog, store, groups.repository, preferences, groups.network, publisher
    )

    val me get() = GroupsTestKit.ME

    /** Records a workout now (as the app's recorder does); returns its id. */
    suspend fun record(
        minutes: Int? = 30,
        source: com.javargasp.tiominutermvi.domain.traininglog.TrainingSource = com.javargasp.tiominutermvi.domain.traininglog.TrainingSource.GYM,
        title: String = "Pull Day",
        refId: String = "gym:64"
    ): Long = trainingLog.record(source, refId, title, minutes?.let { it * 60 })

    suspend fun uuidOf(id: Long): String = trainingLog.getEntry(id)!!.uuid
}
