package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.sync.NetworkMonitor
import com.javargasp.tiominutermvi.domain.sync.SyncRepository
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.ProgramWorkoutTitleCodes
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutShare
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutShareStore
import com.javargasp.tiominutermvi.domain.traininglog.titleKeyOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a flush did. */
enum class FlushOutcome {
    /** Guest, or nothing to do. */
    IDLE,

    /** Offline or a failure: the rows stay in the queue for the next trigger. */
    PENDING,
    DONE
}

/**
 * Posts my shared workouts to my groups from the queue ([WorkoutShareStore], filled by the training
 * history in each entry's transaction). One write per group, never one batch, so a group that
 * refuses (I was removed, the group is gone) doesn't block the others; a refusal is remembered and
 * never retried. Triggers, debounced by [debounceMillis] into one flush: app start, any change of
 * the queue (a workout recorded, the switch turned off, an entry deleted, a photo changed),
 * [request] (the explanation acknowledged), and a successful sync run.
 *
 * Nothing is posted until the one-time explanation was shown ([FeedPreferences.isFeedIntroSeen]);
 * a workout is posted to the groups I'm in at its first post (never to a group joined later) and
 * only while its week is the current one (the feed shows nothing else).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FeedPublisher(
    private val auth: AuthRepository,
    private val store: WorkoutShareStore,
    private val trainingLog: TrainingLogRepository,
    private val groups: GroupRepository,
    private val feed: FeedRepository,
    private val profiles: MemberProfiles,
    private val thumbnails: FeedThumbnailSource,
    private val preferences: FeedPreferences,
    private val network: NetworkMonitor,
    private val sync: SyncRepository,
    private val dates: LocalDateProvider,
    private val clock: WallClock,
    private val log: GroupLog,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    /** The program workout of a PROGRAM entry, for its post's `program/<CODE>` title key. */
    private val programTitles: ProgramWorkoutTitleCodes = ProgramWorkoutTitleCodes.None
) {
    private val requests = MutableStateFlow(0)
    private val mutex = Mutex()

    fun start() {
        auth.session
            .distinctUntilChanged()
            .flatMapLatest { session ->
                if (session is AuthSession.SignedIn) {
                    combine(store.observeDirtyCount(), requests) { _, _ -> session }
                } else {
                    emptyFlow()
                }
            }
            .debounce(debounceMillis)
            .onEach { session -> flush(session) }
            .launchIn(scope)

        // After a successful sync run (back online), whatever is left goes out
        sync.status
            .map { it.lastSuccessAt }
            .distinctUntilChanged()
            .drop(1)
            .onEach { request() }
            .launchIn(scope)
    }

    fun request() {
        requests.value++
    }

    /** Works through the queue; never throws (failures leave the rows for the next trigger). */
    suspend fun flush(session: AuthSession.SignedIn): FlushOutcome = mutex.withLock {
        val rows = store.dirty()
        store.removeDoneBefore(clock.nowMillis() - FeedLimits.SHARE_RETENTION_MILLIS)
        if (rows.isEmpty()) return@withLock FlushOutcome.IDLE
        if (!network.isOnline()) return@withLock FlushOutcome.PENDING
        val myGroups = try {
            groups.memberships(session.uid).map { it.groupId }.also { preferences.setKnownGroupCount(session.uid, it.size) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GroupException) {
            log.warn("Feed: groups not read (${e.error})")
            return@withLock FlushOutcome.PENDING
        }
        var pending = false
        for (row in rows) {
            if (row.ownerUid != session.uid) {
                // Another account's workout (an account switch): never posted as me
                store.remove(row.entryUuid)
                continue
            }
            val stillDirty = try {
                process(session, row, myGroups)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Feed: share not processed (${e.javaClass.simpleName})")
                true
            }
            if (stillDirty) pending = true
        }
        if (pending) FlushOutcome.PENDING else FlushOutcome.DONE
    }

    /** One row; true when something is left for the next flush. */
    private suspend fun process(session: AuthSession.SignedIn, row: WorkoutShare, myGroups: List<String>): Boolean {
        val entry = trainingLog.getEntryByUuid(row.entryUuid)
        return when {
            row.entryDeleted || entry == null -> deleteEverywhere(row, myGroups)
            !row.share -> unshare(row)
            else -> share(session, row, entry, myGroups)
        }
    }

    /** The entry is gone: its post in every group of my index (deleting a missing post is allowed). */
    private suspend fun deleteEverywhere(row: WorkoutShare, myGroups: List<String>): Boolean {
        var failed = false
        (myGroups + row.postedGroupIds).distinct().forEach { groupId ->
            when (attempt { feed.deletePost(groupId, row.entryUuid) }) {
                Attempt.RETRY -> failed = true
                else -> Unit // done, or not allowed any more (I left): nothing more to do there
            }
        }
        if (failed) store.saveProgress(row, stillDirty = true) else store.remove(row.entryUuid)
        return failed
    }

    /** The switch was turned off: the posts already made are deleted. */
    private suspend fun unshare(row: WorkoutShare): Boolean {
        val remaining = row.postedGroupIds.filter { groupId ->
            attempt { feed.deletePost(groupId, row.entryUuid) } == Attempt.RETRY
        }
        val failed = remaining.isNotEmpty()
        store.saveProgress(row.copy(postedGroupIds = remaining), stillDirty = failed)
        return failed
    }

    private suspend fun share(
        session: AuthSession.SignedIn,
        row: WorkoutShare,
        entry: TrainingLogEntry,
        myGroups: List<String>
    ): Boolean {
        val weekStart = dates.mondayOf(entry.dateLocal)
        val targets = row.targetGroupIds ?: myGroups
        if (row.postedGroupIds.isEmpty() && weekStart != dates.currentWeekStart()) {
            // A workout of a past week shows in no feed: nothing to post
            store.saveProgress(row.copy(targetGroupIds = targets), stillDirty = false)
            return false
        }
        if (!preferences.isFeedIntroSeen(session.uid)) {
            // Waits (dirty) until the explanation is acknowledged on a completion screen
            return true
        }
        val posted = row.postedGroupIds.toMutableList()
        val refused = row.refusedGroupIds.toMutableList()
        var postedPhoto = row.postedPhotoPath
        var failed = false
        val desiredPhoto = entry.photoPath
        val thumb = ThumbHolder()

        // 1. The posts already made follow the entry's photo
        if (posted.isNotEmpty() && desiredPhoto != postedPhoto) {
            val bytes = thumb.load(desiredPhoto)
            posted.toList().forEach { groupId ->
                when (attempt { feed.setThumbnail(groupId, row.entryUuid, bytes) }) {
                    Attempt.DONE -> Unit
                    Attempt.REFUSED -> {
                        posted -= groupId
                        refused += groupId
                    }
                    Attempt.RETRY -> failed = true
                }
            }
            if (!failed) postedPhoto = desiredPhoto
        }

        // 2. New posts, with the current thumbnail
        val missing = targets.filter { it !in posted && it !in refused }
        if (missing.isNotEmpty()) {
            val post = newPost(session, entry, weekStart, thumb.load(desiredPhoto), programTitles.titleKeyOf(entry))
            missing.forEach { groupId ->
                when (attempt { feed.createPost(groupId, post) }) {
                    Attempt.DONE -> posted += groupId
                    Attempt.REFUSED ->
                        // A create whose answer was lost is refused when sent again (the post exists)
                        if (isMine(groupId, row.entryUuid, session.uid)) posted += groupId else refused += groupId
                    Attempt.RETRY -> failed = true
                }
            }
            if (row.postedGroupIds.isEmpty()) postedPhoto = desiredPhoto
        }
        store.saveProgress(
            row.copy(targetGroupIds = targets, postedGroupIds = posted, refusedGroupIds = refused, postedPhotoPath = postedPhoto),
            stillDirty = failed
        )
        return failed
    }

    private suspend fun isMine(groupId: String, postId: String, uid: String): Boolean =
        try {
            feed.isMyPost(groupId, postId, uid)
        } catch (e: GroupException) {
            false
        }

    private fun newPost(
        session: AuthSession.SignedIn,
        entry: TrainingLogEntry,
        weekStart: String,
        thumb: ByteArray?,
        titleKey: String?
    ) = NewPost(
        postId = entry.uuid,
        authorUid = session.uid,
        profile = profiles.of(session),
        source = entry.source,
        title = entry.title.take(FeedLimits.TITLE_MAX_LENGTH),
        activityType = if (entry.source == TrainingSource.ACTIVITY) entry.activity else null,
        durationSeconds = entry.durationSeconds?.coerceAtLeast(0),
        dateLocal = entry.dateLocal,
        weekStartLocal = weekStart,
        thumb = thumb,
        titleKey = titleKey
    )

    /** The thumbnail is made at most once per row and flush. */
    private inner class ThumbHolder {
        private var loaded = false
        private var bytes: ByteArray? = null

        suspend fun load(photoPath: String?): ByteArray? {
            if (!loaded) {
                bytes = photoPath?.let { path ->
                    try {
                        thumbnails.thumbnailOf(path)?.takeIf { it.size <= FeedLimits.THUMB_MAX_BYTES }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.warn("Feed: thumbnail not made (${e.javaClass.simpleName})")
                        null
                    }
                }
                loaded = true
            }
            return bytes
        }
    }

    private enum class Attempt { DONE, REFUSED, RETRY }

    private suspend fun attempt(write: suspend () -> Unit): Attempt =
        try {
            write()
            Attempt.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: GroupException) {
            when (e.error) {
                // Removed, or the group is gone: never retried in a loop
                GroupError.PERMISSION_DENIED, GroupError.NOT_FOUND -> {
                    log.warn("Feed: write refused by one group (${e.error})")
                    Attempt.REFUSED
                }
                else -> Attempt.RETRY
            }
        }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 5_000L
    }
}
