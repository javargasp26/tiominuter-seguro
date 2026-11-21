package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.sync.NetworkMonitor
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.RecordedEntry
import com.javargasp.tiominutermvi.domain.traininglog.ShareOptions
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutShareStore
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutSharing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Deleting posts for the membership sequences of docs/firestore-model.md "Feed": one delete per
 * post (an owner's delete looks up the group, and production limits a batch to 20 lookups).
 * Failures throw [GroupException].
 */
class FeedModeration(private val feed: FeedRepository) {

    /** Every post of [authorUid] in the group (mine before leaving; a removed member's, by the owner). */
    suspend fun deletePostsBy(groupId: String, authorUid: String): Int {
        val ids = feed.postIdsBy(groupId, authorUid)
        ids.forEach { feed.deletePost(groupId, it) }
        return ids.size
    }

    /** Every post of the group (Delete group, first step, while the group still exists). */
    suspend fun deleteAllPosts(groupId: String): Int {
        var deleted = 0
        while (true) {
            val ids = feed.postIds(groupId, FeedLimits.DELETE_PAGE)
            if (ids.isEmpty()) return deleted
            ids.forEach { feed.deletePost(groupId, it) }
            deleted += ids.size
        }
    }
}

/**
 * The Spark cleanup (no TTL): posts created more than 8 days ago are deleted by any member, when a
 * feed opens and, throttled to once every [throttleMillis], at app start for each of my groups. The
 * device clock picks the candidates, so the server may refuse one (its clock says it isn't expired
 * yet): each post is deleted on its own and a refusal is skipped silently.
 */
class FeedCleaner(
    private val feed: FeedRepository,
    private val groups: GroupRepository,
    private val preferences: FeedPreferences,
    private val clock: WallClock,
    private val log: GroupLog,
    private val throttleMillis: Long = DEFAULT_THROTTLE_MILLIS
) {

    /** Returns how many expired posts were deleted; never throws a group error. */
    suspend fun cleanUpExpired(groupId: String): Int {
        val ids = try {
            feed.expiredPostIds(groupId, clock.nowMillis() - FeedLimits.POST_LIFETIME_MILLIS, FeedLimits.CLEANUP_LIMIT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GroupException) {
            log.warn("Feed: cleanup skipped (${e.error})")
            return 0
        }
        var deleted = 0
        ids.forEach { id ->
            try {
                feed.deletePost(groupId, id)
                deleted++
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroupException) {
                // Not expired yet on the server's clock (or offline): the next cleanup gets it
            }
        }
        return deleted
    }

    /** At app start: every group of my index, at most once every [throttleMillis]. */
    suspend fun cleanUpIfDue(uid: String) {
        val last = preferences.lastFeedCleanupAt(uid)
        val now = clock.nowMillis()
        if (last != null && now >= last && now - last < throttleMillis) return
        try {
            groups.memberships(uid).forEach { cleanUpExpired(it.groupId) }
            preferences.setLastFeedCleanupAt(uid, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GroupException) {
            log.warn("Feed: cleanup at start skipped (${e.error})")
        }
    }

    companion object {
        const val DEFAULT_THROTTLE_MILLIS = 6 * 60 * 60 * 1000L
    }
}

/**
 * [WorkoutSharing] for the completion screens: the switch shows only when signed in, in at least
 * one group, for a workout the queue holds (counted, recorded while signed in, still recent).
 */
class FeedShareController(
    private val auth: AuthRepository,
    private val trainingLog: TrainingLogRepository,
    private val store: WorkoutShareStore,
    private val groups: GroupRepository,
    private val preferences: FeedPreferences,
    private val network: NetworkMonitor,
    private val publisher: FeedPublisher?
) : WorkoutSharing {

    override suspend fun options(entryId: Long): ShareOptions? {
        val uid = signedInUid() ?: return null
        val entry = trainingLog.getEntry(entryId) ?: return null
        val row = store.get(entry.uuid)?.takeIf { it.ownerUid == uid && !it.entryDeleted } ?: return null
        if (!hasGroups(uid)) return null
        return ShareOptions(share = row.share, needsIntro = !preferences.isFeedIntroSeen(uid))
    }

    override suspend fun isAvailable(): Boolean {
        val uid = signedInUid() ?: return false
        return hasGroups(uid)
    }

    override suspend fun setShare(entryId: Long, share: Boolean) {
        val entry = trainingLog.getEntry(entryId) ?: return
        store.setShare(entry.uuid, share)
    }

    override suspend fun acknowledgeIntro() {
        val uid = signedInUid() ?: return
        preferences.setFeedIntroSeen(uid)
        publisher?.request()
    }

    override fun latestRecorded(source: TrainingSource): Flow<RecordedEntry?> = store.latestRecorded(source)

    private suspend fun signedInUid(): String? = (auth.session.first() as? AuthSession.SignedIn)?.uid

    /** From the server; offline (or on a failure), the count of the last read. */
    private suspend fun hasGroups(uid: String): Boolean =
        if (!network.isOnline()) {
            (preferences.knownGroupCount(uid) ?: 0) > 0
        } else try {
            groups.memberships(uid).size.also { preferences.setKnownGroupCount(uid, it) } > 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: GroupException) {
            (preferences.knownGroupCount(uid) ?: 0) > 0
        }
}
