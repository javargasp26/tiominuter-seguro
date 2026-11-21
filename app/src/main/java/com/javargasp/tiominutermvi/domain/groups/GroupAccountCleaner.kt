package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.auth.AccountGroupsCleaner

/**
 * Delete account, before the server data is erased: first my posts in every group (in a group I
 * own alone, every post), then it leaves every group, one batch per group. Stale entries are
 * reconciled first; a group I own with other members goes to the oldest other member by joinedAt
 * (transfer and leave in one batch); a group I own alone is deleted; last, the counter. Throws on
 * any failure, so the account deletion stops before erasing anything.
 */
class GroupAccountCleaner(
    private val repository: GroupRepository,
    private val reconciler: GroupReconciler,
    private val cache: GroupMembersCache,
    private val feed: FeedModeration? = null
) : AccountGroupsCleaner {

    override suspend fun leaveAllGroups(uid: String) {
        val groups = reconciler.reconcile(uid)
        // Posts first, in every group, before any membership changes
        if (feed != null) {
            groups.forEach { group ->
                if (group.ownerUid == uid && group.memberCount <= 1) feed.deleteAllPosts(group.id) else feed.deletePostsBy(group.id, uid)
            }
        }
        groups.forEach { group ->
            when {
                group.ownerUid != uid -> repository.leaveGroup(uid, group.id, null)
                else -> {
                    val heir = oldestOtherMember(uid, repository.members(group.id))
                    if (heir == null) {
                        repository.deleteGroup(uid, group.id, group.inviteCode)
                    } else {
                        repository.leaveGroup(uid, group.id, heir.uid)
                    }
                }
            }
        }
        repository.deleteGroupCounter(uid)
        cache.clear()
    }

    companion object {
        /** The oldest other member by joinedAt (a pending time last), then by uid. */
        fun oldestOtherMember(uid: String, members: List<GroupMember>): GroupMember? =
            members.filter { it.uid != uid }
                .sortedWith(compareBy<GroupMember> { it.joinedAtMillis ?: Long.MAX_VALUE }.thenBy { it.uid })
                .firstOrNull()
    }
}
