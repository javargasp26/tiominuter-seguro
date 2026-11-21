package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.time.WallClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps my membership index honest. Nobody else can write my `users/{uid}` docs, so when the owner
 * removes me or deletes a group, my index entry and counter go stale until my own app cleans them
 * up: for each entry, my own member doc (always readable by me) and the group. One batch per stale
 * entry (the counter moves by exactly 1 per batch).
 */
class GroupReconciler(
    private val repository: GroupRepository,
    private val preferences: GroupPreferences,
    private val clock: WallClock,
    private val log: GroupLog,
    private val throttleMillis: Long = DEFAULT_THROTTLE_MILLIS
) {
    private val mutex = Mutex()

    /** The groups I'm really in (stale entries removed). Throws when the index can't be read. */
    suspend fun reconcile(uid: String): List<GroupInfo> = mutex.withLock {
        val groups = repository.memberships(uid).mapNotNull { membership ->
            val groupId = membership.groupId
            if (!repository.ownMemberExists(groupId, uid)) {
                removeStale(uid, groupId, deleteOwnMemberDoc = false)
                return@mapNotNull null
            }
            val group = repository.group(groupId)
            if (group == null) removeStale(uid, groupId, deleteOwnMemberDoc = true)
            group
        }
        preferences.setLastReconciledAt(uid, clock.nowMillis())
        groups
    }

    /** At app start and on Home: at most once every [throttleMillis]; failures are only logged. */
    suspend fun reconcileIfDue(uid: String) {
        val last = preferences.lastReconciledAt(uid)
        val now = clock.nowMillis()
        if (last != null && now >= last && now - last < throttleMillis) return
        try {
            reconcile(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GroupException) {
            log.warn("Groups: reconciliation skipped (${e.error})")
        }
    }

    // A stale entry that can't be removed now (offline, a race) is retried at the next reconciliation
    private suspend fun removeStale(uid: String, groupId: String, deleteOwnMemberDoc: Boolean) {
        try {
            repository.removeStaleMembership(uid, groupId, deleteOwnMemberDoc)
        } catch (e: GroupException) {
            log.warn("Groups: stale membership not removed (${e.error})")
        }
    }

    companion object {
        const val DEFAULT_THROTTLE_MILLIS = 6 * 60 * 60 * 1000L
    }
}

/** Group failures, logged without personal data (operation, error, exception class only). */
fun interface GroupLog {
    fun warn(message: String)
}
