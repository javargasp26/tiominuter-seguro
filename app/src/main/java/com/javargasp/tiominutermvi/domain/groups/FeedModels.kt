package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.flow.Flow

/**
 * The group feed (Phase 4): one post per counted workout and group, `groups/{groupId}/posts/{entryUuid}`
 * (docs/firestore-model.md "Feed"; firebase/rules-tests/test/feedOps.js is the reference).
 */

/** The four reactions; [code] is what the post stores. */
enum class Reaction(val code: String, val emoji: String) {
    STRONG("strong", "💪"),
    FIRE("fire", "🔥"),
    CLAP("clap", "👏"),
    RAISED("raised", "🙌");

    companion object {
        fun fromCode(code: String?): Reaction? = entries.firstOrNull { it.code == code }
    }
}

/** A post as members read it. [createdAtMillis] is the server time (estimated while pending). */
data class FeedPost(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val source: TrainingSource,
    val title: String,
    val activityType: ActivityType?,
    val durationSeconds: Int?,
    val dateLocal: String,
    val weekStartLocal: String,
    val createdAtMillis: Long?,
    val reactions: Map<String, Reaction>,
    val thumb: ByteArray?,
    /** The locale-free name (`WorkoutTitleKey`), absent on older posts; readers resolve it. */
    val titleKey: String? = null
) {
    /** How many members reacted with each reaction (absent: none). */
    val reactionCounts: Map<Reaction, Int> get() = reactions.values.groupingBy { it }.eachCount()

    // ByteArray has identity equality: compare the thumbnail by content
    override fun equals(other: Any?): Boolean =
        other is FeedPost && sameFields(other) && thumb.contentEqualsNullable(other.thumb)

    override fun hashCode(): Int = listOf(
        id, authorUid, authorName, authorPhotoUrl, source, title, activityType, durationSeconds,
        dateLocal, weekStartLocal, createdAtMillis, reactions, thumb?.contentHashCode(), titleKey
    ).hashCode()

    private fun sameFields(other: FeedPost) = id == other.id && authorUid == other.authorUid &&
        authorName == other.authorName && authorPhotoUrl == other.authorPhotoUrl && source == other.source &&
        title == other.title && activityType == other.activityType && durationSeconds == other.durationSeconds &&
        dateLocal == other.dateLocal && weekStartLocal == other.weekStartLocal &&
        createdAtMillis == other.createdAtMillis && reactions == other.reactions && titleKey == other.titleKey
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null) other == null else other != null && contentEquals(other)

/** One page of this week's feed; [hasMore] when the page is full (a larger limit may show more). */
data class FeedPage(val posts: List<FeedPost>, val hasMore: Boolean)

/** What a new post carries: only these fields (the rules accept nothing else). */
data class NewPost(
    val postId: String,
    val authorUid: String,
    val profile: MemberProfile,
    val source: TrainingSource,
    val title: String,
    val activityType: ActivityType?,
    val durationSeconds: Int?,
    val dateLocal: String,
    val weekStartLocal: String,
    val thumb: ByteArray?,
    /** `gym/64`, `daily/3`, `program/UPPER_A` (WorkoutNameResolver.titleKeyOf); null: left out. */
    val titleKey: String? = null
)

object FeedLimits {
    /** The rules' hard cap of a thumbnail. */
    const val THUMB_MAX_BYTES = 65_536

    /** What the app aims for (a margin under the cap). */
    const val THUMB_TARGET_BYTES = 60 * 1024
    const val TITLE_MAX_LENGTH = 500

    /** A post lives 8 days after its server creation time; then any member may delete it. */
    const val POST_LIFETIME_MILLIS = 8 * 24 * 60 * 60 * 1000L

    /** The queue keeps a row this long (a little more than a post lives). */
    const val SHARE_RETENTION_MILLIS = 9 * 24 * 60 * 60 * 1000L
    const val PAGE_SIZE = 20

    /** Expired posts deleted per cleanup of one group. */
    const val CLEANUP_LIMIT = 20

    /** Post ids read per query when deleting all of a group's posts. */
    const val DELETE_PAGE = 100
}

/** Reactions: one per member and post. */
object ReactionRules {
    /** Tapping [tapped]: the same reaction again removes it, another one replaces it. */
    fun toggle(current: Reaction?, tapped: Reaction): Reaction? = if (current == tapped) null else tapped

    /** The post's reactions with mine set to [mine] (null: removed). */
    fun withMine(reactions: Map<String, Reaction>, myUid: String, mine: Reaction?): Map<String, Reaction> =
        if (mine == null) reactions - myUid else reactions + (myUid to mine)
}

/**
 * The feed in Firestore. Failures throw [GroupException] (NETWORK and FAILED may be retried;
 * PERMISSION_DENIED and NOT_FOUND never are).
 */
interface FeedRepository {

    /** `set` of a new post (the rules refuse a second create of the same id). */
    suspend fun createPost(groupId: String, post: NewPost)

    /** Whether [postId] exists in the group and is [authorUid]'s (a create whose answer was lost). */
    suspend fun isMyPost(groupId: String, postId: String, authorUid: String): Boolean

    /** The author's thumbnail: bytes set or replace it, null removes the field. */
    suspend fun setThumbnail(groupId: String, postId: String, thumb: ByteArray?)

    /** Deletes one post; deleting a missing post is allowed for members. */
    suspend fun deletePost(groupId: String, postId: String)

    /** This week's posts, newest first, at most [limit], live while collected (the Feed tab only). */
    fun observeWeek(groupId: String, weekStartLocal: String, limit: Int): Flow<FeedPage>

    /** My reaction (a field-path update, never the whole map); null removes it. */
    suspend fun setReaction(groupId: String, postId: String, uid: String, reaction: Reaction?)

    suspend fun postIdsBy(groupId: String, authorUid: String): List<String>

    /** Up to [limit] post ids of the group (to delete them all). */
    suspend fun postIds(groupId: String, limit: Int): List<String>

    /** Up to [limit] posts created before [createdBeforeMillis] (device clock), oldest first. */
    suspend fun expiredPostIds(groupId: String, createdBeforeMillis: Long, limit: Int): List<String>
}

/** The JPEG thumbnail posted for a local photo, always ≤ [FeedLimits.THUMB_MAX_BYTES]; null when it can't be made. */
fun interface FeedThumbnailSource {
    suspend fun thumbnailOf(photoPath: String): ByteArray?
}

/**
 * Fits a thumbnail under [FeedLimits.THUMB_TARGET_BYTES]: the quality steps down first, then the
 * size, until it fits; null when even the smallest step doesn't (never more than the cap).
 */
object ThumbnailFitter {
    const val MAX_SIDE = 400
    const val MIN_SIDE = 64
    val QUALITIES = listOf(60, 50, 40, 30)
    private const val SCALE_STEP = 0.75

    /** [encode] returns the JPEG of the image scaled to a longest side of `side` at `quality`, or null. */
    fun fit(longestSide: Int, encode: (side: Int, quality: Int) -> ByteArray?): ByteArray? {
        var side = minOf(longestSide, MAX_SIDE)
        while (side >= MIN_SIDE) {
            for (quality in QUALITIES) {
                val bytes = encode(side, quality) ?: return null
                if (bytes.size <= FeedLimits.THUMB_TARGET_BYTES) return bytes
            }
            side = (side * SCALE_STEP).toInt()
        }
        return null
    }
}

/** Local state of the feed (per account where it matters). */
interface FeedPreferences {
    /** The one-time explanation of what group members see was shown. */
    fun isFeedIntroSeen(uid: String): Boolean

    fun setFeedIntroSeen(uid: String)

    /** When expired posts were last cleaned up at app start (WallClock millis), or null. */
    fun lastFeedCleanupAt(uid: String): Long?

    fun setLastFeedCleanupAt(uid: String, millis: Long)

    /** Posts hidden for me ("groupId/postId"). */
    fun hiddenPosts(uid: String): Set<String>

    /** Hides a post for me; entries older than a post's life are forgotten. */
    fun hidePost(uid: String, key: String, nowMillis: Long)

    /** How many groups I was in the last time the index was read (offline fallback), or null. */
    fun knownGroupCount(uid: String): Int?

    fun setKnownGroupCount(uid: String, count: Int)

    /** The last tab of a group's detail (0 Ranking, 1 Feed), or null. */
    fun detailTab(groupId: String): Int?

    fun setDetailTab(groupId: String, tab: Int)
}

/** The key of a hidden post. */
fun hiddenKey(groupId: String, postId: String) = "$groupId/$postId"
