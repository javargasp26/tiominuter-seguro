package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.sync.NetworkMonitor
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** One of my groups on the list: my place in this week's ranking. */
data class MyGroup(val group: GroupInfo, val myPosition: Int?, val isOwner: Boolean)

/** A group I just created, with its code to share. */
data class CreatedGroup(val id: String, val name: String, val code: String)

/**
 * Every group operation the screens trigger, with the product rules the app checks before writing
 * (the rules check them again): signed in, online, a weekly goal before creating or joining, at most
 * 5 groups, a valid name and code. Failures are [GroupException]s; PERMISSION_DENIED is logged
 * without personal data and never retried in a loop. With [feed], the membership changes follow the
 * feed sequences: leaving deletes my posts first, a removed member's posts are deleted by the owner
 * after the removal, and Delete group deletes every post before the members and the group.
 */
class GroupOperations(
    private val repository: GroupRepository,
    private val auth: AuthRepository,
    private val summaries: MySummarySource,
    private val profiles: MemberProfiles,
    private val network: NetworkMonitor,
    private val reconciler: GroupReconciler,
    private val publisher: GroupSummaryPublisher?,
    private val cache: GroupMembersCache,
    private val dates: LocalDateProvider,
    private val log: GroupLog,
    private val feed: FeedModeration? = null,
    private val newCode: () -> String = { InviteCodes.generate() }
) {

    /** The signed-in account; [GroupError.NOT_SIGNED_IN] for a guest. */
    suspend fun currentUser(): AuthSession.SignedIn =
        auth.session.first() as? AuthSession.SignedIn ?: throw GroupException(GroupError.NOT_SIGNED_IN)

    suspend fun hasWeeklyGoal(): Boolean = summaries.current() != null

    /** My groups (the index reconciled first) with my position this week. */
    suspend fun myGroups(): List<MyGroup> = run("list") {
        val user = currentUser()
        val weekStart = dates.currentWeekStart()
        reconciler.reconcile(user.uid).map { group ->
            val members = try {
                repository.members(group.id)
            } catch (e: GroupException) {
                emptyList()
            }
            val position = GroupRanking.rank(members, weekStart, user.uid).firstOrNull { it.isMe }?.position
            MyGroup(group, position, group.ownerUid == user.uid)
        }
    }

    /** The group a typed code points to; [GroupError.INVALID_CODE] when it's malformed or unused. */
    suspend fun findInvite(rawCode: String): InvitePreview = run("lookup") {
        val code = InviteCodes.normalize(rawCode)
        if (!InviteCodes.isValid(code)) throw GroupException(GroupError.INVALID_CODE)
        currentUser()
        requireOnline()
        repository.findInvite(code) ?: throw GroupException(GroupError.INVALID_CODE)
    }

    suspend fun create(rawName: String): CreatedGroup = run("create") {
        val name = GroupLimits.normalizeName(rawName) ?: throw GroupException(GroupError.INVALID_NAME)
        val user = currentUser()
        requireOnline()
        val summary = summaries.current() ?: throw GroupException(GroupError.GOAL_REQUIRED)
        if (reconciler.reconcile(user.uid).size >= GroupLimits.MAX_GROUPS) throw GroupException(GroupError.GROUP_LIMIT)
        val groupId = repository.newGroupId()
        val code = withFreshCode { code ->
            repository.createGroup(user.uid, groupId, name, code, profiles.of(user), summary)
        }
        afterMembershipChange()
        CreatedGroup(groupId, name, code)
    }

    /** Joins the group of [invite]; returns its id. */
    suspend fun join(invite: InvitePreview): String = run("join") {
        val user = currentUser()
        requireOnline()
        val summary = summaries.current() ?: throw GroupException(GroupError.GOAL_REQUIRED)
        val groups = reconciler.reconcile(user.uid)
        if (groups.any { it.id == invite.groupId }) throw GroupException(GroupError.ALREADY_MEMBER)
        if (groups.size >= GroupLimits.MAX_GROUPS) throw GroupException(GroupError.GROUP_LIMIT)
        try {
            repository.joinGroup(user.uid, invite, profiles.of(user), summary)
        } catch (e: GroupException) {
            if (e.error != GroupError.PERMISSION_DENIED) throw e
            // The code may have been regenerated meanwhile; otherwise the group is full
            val current = repository.findInvite(invite.code)
            throw GroupException(
                if (current?.groupId == invite.groupId) GroupError.GROUP_FULL else GroupError.INVALID_CODE,
                e.causeName
            )
        }
        afterMembershipChange()
        invite.groupId
    }

    /**
     * Leave: a member leaves; an owner alone deletes the group; an owner with other members must
     * name [newOwnerUid] (transfer and leave in one batch).
     */
    suspend fun leave(group: GroupInfo, newOwnerUid: String? = null) = run("leave") {
        val user = currentUser()
        requireOnline()
        when {
            group.ownerUid != user.uid -> {
                feed?.deletePostsBy(group.id, user.uid)
                repository.leaveGroup(user.uid, group.id, null)
            }
            group.memberCount <= 1 -> deleteChunked(user.uid, group)
            newOwnerUid == null || newOwnerUid == user.uid -> throw GroupException(GroupError.PERMISSION_DENIED)
            else -> {
                feed?.deletePostsBy(group.id, user.uid)
                repository.leaveGroup(user.uid, group.id, newOwnerUid)
            }
        }
        afterMembershipChange()
    }

    suspend fun removeMember(group: GroupInfo, memberUid: String) = run("remove") {
        requireOwner(group)
        requireOnline()
        repository.removeMember(group.id, memberUid)
        cache.clear()
        // The removal is done; posts left behind (a failure here) expire or the owner deletes them
        try {
            feed?.deletePostsBy(group.id, memberUid)
        } catch (e: GroupException) {
            log.warn("Groups: posts of a removed member not deleted (${e.error})")
        }
    }

    suspend fun transfer(group: GroupInfo, newOwnerUid: String) = run("transfer") {
        requireOwner(group)
        requireOnline()
        repository.transferOwnership(group.id, newOwnerUid)
    }

    suspend fun rename(group: GroupInfo, rawName: String) = run("rename") {
        val name = GroupLimits.normalizeName(rawName) ?: throw GroupException(GroupError.INVALID_NAME)
        requireOwner(group)
        requireOnline()
        repository.renameGroup(group.id, group.inviteCode, name)
    }

    /** A new invite code (the old one stops working); returns it. */
    suspend fun regenerateCode(group: GroupInfo): String = run("regenerate") {
        requireOwner(group)
        requireOnline()
        withFreshCode { code -> repository.regenerateCode(group.id, group.inviteCode, code, group.name) }
    }

    /** Delete group (owner): every other member removed one batch at a time, then the final batch. */
    suspend fun delete(group: GroupInfo) = run("delete") {
        val user = requireOwner(group)
        requireOnline()
        deleteChunked(user.uid, group)
        afterMembershipChange()
    }

    /**
     * Production limits the document lookups of one batch (docs/firestore-model.md), so the posts go
     * first, one write each (only while the group exists can the owner delete them), then the other
     * members, one per batch (the rules allow one removal per write), and the last batch deletes the
     * group, its code and my own membership.
     */
    internal suspend fun deleteChunked(uid: String, group: GroupInfo) {
        feed?.deleteAllPosts(group.id)
        repository.members(group.id).filter { it.uid != uid }.forEach { member ->
            try {
                repository.removeMember(group.id, member.uid)
            } catch (e: GroupException) {
                // Someone who left meanwhile is fine; anyone still there stops the deletion
                if (e.error != GroupError.PERMISSION_DENIED || repository.members(group.id).any { it.uid == member.uid }) throw e
            }
        }
        repository.deleteGroup(uid, group.id, group.inviteCode)
    }

    private suspend fun requireOwner(group: GroupInfo): AuthSession.SignedIn {
        val user = currentUser()
        if (group.ownerUid != user.uid) throw GroupException(GroupError.PERMISSION_DENIED)
        return user
    }

    private fun requireOnline() {
        if (!network.isOnline()) throw GroupException(GroupError.NETWORK)
    }

    /** A random code; a code already in use is denied by the rules, so up to 3 tries. */
    private suspend fun withFreshCode(write: suspend (String) -> Unit): String {
        repeat(CODE_ATTEMPTS - 1) {
            val code = newCode()
            try {
                write(code)
                return code
            } catch (e: GroupException) {
                if (e.error != GroupError.PERMISSION_DENIED) throw e
            }
        }
        return newCode().also { write(it) }
    }

    private fun afterMembershipChange() {
        cache.clear()
        publisher?.request()
    }

    private suspend fun <T> run(operation: String, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: GroupException) {
            if (e.error == GroupError.PERMISSION_DENIED || e.error == GroupError.FAILED) {
                log.warn("Groups: $operation failed (${e.error}${e.causeName?.let { ", $it" } ?: ""})")
            }
            throw e
        }

    private companion object {
        const val CODE_ATTEMPTS = 3
    }
}
