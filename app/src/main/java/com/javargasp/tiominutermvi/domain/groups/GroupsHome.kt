package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** My groups' members for Home's banner, with my uid. */
data class HomeGroups(val myUid: String, val groups: List<GroupMembers>)

/** What Home needs from the groups feature (its only dependency on it). */
interface GroupsHome {
    /**
     * Called every time Home is shown: asks for a summary publish (a new week is noticed here) and
     * a throttled reconciliation, and returns my groups' members (a one-shot read cached for 10
     * minutes; no listeners on Home). Null for a guest or when they can't be read.
     */
    suspend fun onHomeShown(): HomeGroups?

    fun trainedTodayDismissedOn(): String?

    fun dismissTrainedToday(today: String)

    /** No groups (tests and previews). */
    object None : GroupsHome {
        override suspend fun onHomeShown(): HomeGroups? = null
        override fun trainedTodayDismissedOn(): String? = null
        override fun dismissTrainedToday(today: String) = Unit
    }
}

class DefaultGroupsHome(
    private val auth: AuthRepository,
    private val repository: GroupRepository,
    private val reconciler: GroupReconciler,
    private val publisher: GroupSummaryPublisher,
    private val cache: GroupMembersCache,
    private val preferences: GroupPreferences,
    private val log: GroupLog,
    private val scope: CoroutineScope
) : GroupsHome {

    override suspend fun onHomeShown(): HomeGroups? {
        val session = auth.session.first() as? AuthSession.SignedIn ?: return null
        val uid = session.uid
        publisher.request()
        scope.launch { reconciler.reconcileIfDue(uid) }
        cache.get(uid)?.let { return HomeGroups(uid, it) }
        return try {
            val groups = repository.memberships(uid).mapNotNull { membership ->
                try {
                    GroupMembers(membership.groupId, repository.members(membership.groupId))
                } catch (e: GroupException) {
                    null // removed, or the group is gone: the reconciliation drops it
                }
            }
            cache.put(uid, groups)
            HomeGroups(uid, groups)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GroupException) {
            log.warn("Groups: Home members not read (${e.error})")
            null
        }
    }

    override fun trainedTodayDismissedOn(): String? = preferences.trainedTodayDismissedOn()

    override fun dismissTrainedToday(today: String) = preferences.setTrainedTodayDismissedOn(today)
}

/**
 * Started by TioMinuterApplication: the summary publisher and the feed publisher, and on every
 * account change the in-memory caches and the banner state are cleared (groups belong to the
 * account) and, when signed in, a throttled reconciliation and a throttled cleanup of expired posts run.
 */
class GroupsCoordinator(
    private val auth: AuthRepository,
    private val publisher: GroupSummaryPublisher,
    private val reconciler: GroupReconciler,
    private val cache: GroupMembersCache,
    private val preferences: GroupPreferences,
    private val scope: CoroutineScope,
    private val feedPublisher: FeedPublisher? = null,
    private val feedCleaner: FeedCleaner? = null
) {
    fun start() {
        publisher.start()
        feedPublisher?.start()
        scope.launch {
            var previousUid: String? = null
            var first = true
            auth.session.collect { session ->
                val uid = (session as? AuthSession.SignedIn)?.uid
                if (!first && uid != previousUid) {
                    cache.clear()
                    preferences.setTrainedTodayDismissedOn(null)
                }
                first = false
                previousUid = uid
                if (uid != null) {
                    try {
                        reconciler.reconcileIfDue(uid)
                        feedCleaner?.cleanUpIfDue(uid)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // reconcileIfDue logs group failures; anything else must not stop the collector
                    }
                }
            }
        }
    }
}
