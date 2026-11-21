package com.javargasp.tiominutermvi.domain.groups

import kotlinx.coroutines.flow.Flow

/**
 * Firestore, behind an interface: the groups feature's only reads and writes of the cloud. Every
 * write is ONE batch of docs/firestore-model.md "Batched operations" (the same batches as
 * firebase/rules-tests/test/groupBatches.js). Failures throw [GroupException].
 */
interface GroupRepository {

    /** A new random group id (a Firestore auto id). */
    fun newGroupId(): String

    /** My membership index (`users/{uid}/groups`). */
    suspend fun memberships(uid: String): List<Membership>

    /** The group, or null when it no longer exists (I must still have a member doc to read it). */
    suspend fun group(groupId: String): GroupInfo?

    /** Every member doc of a group I'm in. */
    suspend fun members(groupId: String): List<GroupMember>

    /**
     * The group and its members, live, while collected (the detail screen only, while visible).
     * Ends with [GroupError.NOT_FOUND] when the group is deleted or I'm no longer a member.
     */
    fun observeGroup(groupId: String): Flow<GroupSnapshot>

    /** Whether my own member doc exists (always readable by me: "no" means I was removed or it's gone). */
    suspend fun ownMemberExists(groupId: String, uid: String): Boolean

    /** `inviteCodes/{code}`, or null when no group uses that code. */
    suspend fun findInvite(code: String): InvitePreview?

    /** Create: group, my member doc, the invite code, my index entry, counter + 1. */
    suspend fun createGroup(uid: String, groupId: String, name: String, code: String, profile: MemberProfile, summary: MemberSummary)

    /** Join with an invite looked up first: memberCount + 1, my member doc, index, counter + 1. */
    suspend fun joinGroup(uid: String, invite: InvitePreview, profile: MemberProfile, summary: MemberSummary)

    /** Leave: memberCount − 1, my member doc and index entry, counter − 1; an owner hands the group to [newOwnerUid]. */
    suspend fun leaveGroup(uid: String, groupId: String, newOwnerUid: String?)

    /** Remove member (owner): memberCount − 1 naming them, their member doc. One member per batch. */
    suspend fun removeMember(groupId: String, memberUid: String)

    suspend fun transferOwnership(groupId: String, newOwnerUid: String)

    /** Rename (owner): the group and its invite code doc. */
    suspend fun renameGroup(groupId: String, code: String, name: String)

    /** New code (owner): old invite doc deleted, new one created, group.inviteCode. */
    suspend fun regenerateCode(groupId: String, oldCode: String, newCode: String, name: String)

    /**
     * The final batch of Delete group (owner, every other member already removed): the group, its
     * invite code, my member doc, my index entry, counter − 1.
     */
    suspend fun deleteGroup(uid: String, groupId: String, code: String)

    /**
     * Reconciliation of a stale index entry: the entry and counter − 1, plus my own member doc when
     * [deleteOwnMemberDoc] (a doc left behind by a deleted group).
     */
    suspend fun removeStaleMembership(uid: String, groupId: String, deleteOwnMemberDoc: Boolean)

    /** My summary (and name and photo) in one group: an update, so a removed member gets NOT_FOUND. */
    suspend fun updateSummary(groupId: String, uid: String, profile: MemberProfile, summary: MemberSummary)

    /** Delete account, last group step: the counter, when it exists and is at 0. */
    suspend fun deleteGroupCounter(uid: String)
}

/** Small local state of the groups feature. Keyed by account where it matters. */
interface GroupPreferences {
    /** A hash of the summary last written to every group, or null. */
    fun publishedSummaryHash(uid: String): String?

    fun setPublishedSummaryHash(uid: String, hash: String?)

    /** True when the last publish didn't reach every group (offline, failures): publish again next time. */
    fun isSummaryDirty(uid: String): Boolean

    fun setSummaryDirty(uid: String, dirty: Boolean)

    /** When the index was last reconciled (WallClock millis), or null. */
    fun lastReconciledAt(uid: String): Long?

    fun setLastReconciledAt(uid: String, millis: Long)

    /** The local date on which the "already trained today" banner was dismissed, or null. */
    fun trainedTodayDismissedOn(): String?

    fun setTrainedTodayDismissedOn(date: String?)
}

/** Text the domain needs from resources: the name of a member whose account has none. */
fun interface GroupTexts {
    fun memberFallbackName(): String
}
