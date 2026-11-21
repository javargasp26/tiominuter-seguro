package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.sync.NetworkMonitor
import com.javargasp.tiominutermvi.domain.sync.SyncRepository
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
import java.security.MessageDigest

/** What a publish did. */
enum class PublishOutcome {
    /** Guest, or no goal this week. */
    SKIPPED,

    /** The same summary (and name and photo) is already in every group. */
    UNCHANGED,

    /** Offline or a failure: marked dirty, published at the next trigger. */
    PENDING,
    PUBLISHED
}

/**
 * Publishes my weekly summary to my member doc in every group of my index. Triggers, all debounced
 * by [debounceMillis] into one publish: app start (the first emission), any change of the history
 * or the goals (a counted entry created or deleted, a goal change, pulled data), [request] (Home
 * shown, so a new week is noticed lazily; a join or a creation), and a successful sync run while
 * dirty. Writes only when the summary differs from the last one published (a hash in
 * [GroupPreferences]); offline or after a failure the summary is marked dirty and goes out at the
 * next trigger. No background work of its own. A rejected write for one group doesn't block the others.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GroupSummaryPublisher(
    private val auth: AuthRepository,
    private val summaries: MySummarySource,
    private val profiles: MemberProfiles,
    private val repository: GroupRepository,
    private val preferences: GroupPreferences,
    private val network: NetworkMonitor,
    private val sync: SyncRepository,
    private val log: GroupLog,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS
) {
    private val requests = MutableStateFlow(0)
    private val mutex = Mutex()

    fun start() {
        auth.session
            .distinctUntilChanged()
            .flatMapLatest { session ->
                if (session is AuthSession.SignedIn) {
                    combine(summaries.changes(), requests) { _, _ -> session }
                } else {
                    emptyFlow()
                }
            }
            .debounce(debounceMillis)
            .onEach { session -> publish(session) }
            .launchIn(scope)

        // After a successful sync run, a summary left dirty (offline) goes out
        sync.status
            .map { it.lastSuccessAt }
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                val session = auth.session.first()
                if (session is AuthSession.SignedIn && preferences.isSummaryDirty(session.uid)) request()
            }
            .launchIn(scope)
    }

    /** Asks for a publish (debounced with every other trigger). */
    fun request() {
        requests.value++
    }

    /** Publishes now if something changed; never throws (failures mark the summary dirty). */
    suspend fun publish(session: AuthSession.SignedIn): PublishOutcome = mutex.withLock {
        val uid = session.uid
        val summary = summaries.current() ?: return@withLock PublishOutcome.SKIPPED
        val profile = profiles.of(session)
        val hash = hashOf(summary, profile)
        if (!preferences.isSummaryDirty(uid) && preferences.publishedSummaryHash(uid) == hash) {
            return@withLock PublishOutcome.UNCHANGED
        }
        if (!network.isOnline()) {
            preferences.setSummaryDirty(uid, true)
            return@withLock PublishOutcome.PENDING
        }
        var failed = false
        try {
            repository.memberships(uid).forEach { membership ->
                try {
                    repository.updateSummary(membership.groupId, uid, profile, summary)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GroupException) {
                    when (e.error) {
                        // Removed, or the group is gone: the reconciliation drops the entry
                        GroupError.NOT_FOUND, GroupError.PERMISSION_DENIED ->
                            log.warn("Groups: summary not accepted by one group (${e.error})")
                        else -> failed = true
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GroupException) {
            failed = true
        }
        if (failed) {
            preferences.setSummaryDirty(uid, true)
            log.warn("Groups: summary publish pending")
            PublishOutcome.PENDING
        } else {
            preferences.setPublishedSummaryHash(uid, hash)
            preferences.setSummaryDirty(uid, false)
            PublishOutcome.PUBLISHED
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 5_000L

        /** SHA-256 of what a member doc carries, stable across launches (data classes print their fields in order). */
        fun hashOf(summary: MemberSummary, profile: MemberProfile): String =
            MessageDigest.getInstance("SHA-256")
                .digest("$summary|$profile".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
