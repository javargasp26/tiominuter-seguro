package com.javargasp.tiominutermvi.data.groups

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.javargasp.tiominutermvi.data.sync.awaitTask
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.GroupInfo
import com.javargasp.tiominutermvi.domain.groups.GroupMember
import com.javargasp.tiominutermvi.domain.groups.GroupRepository
import com.javargasp.tiominutermvi.domain.groups.GroupSnapshot
import com.javargasp.tiominutermvi.domain.groups.InvitePreview
import com.javargasp.tiominutermvi.domain.groups.MemberProfile
import com.javargasp.tiominutermvi.domain.groups.MemberSummary
import com.javargasp.tiominutermvi.domain.groups.Membership
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [GroupRepository] on Cloud Firestore: exactly the documents and batches of docs/firestore-model.md
 * "Groups" (firebase/rules-tests/test/groupBatches.js is the reference). One-shot reads come from
 * the server (`Source.SERVER`), so a removed member is noticed at once. Counter steps are merges with
 * `increment(±1)`, and a joiner raises memberCount with `increment(1)` (they can't read the group).
 */
class FirestoreGroupRepository(
    private val firestore: FirebaseFirestore,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : GroupRepository {

    private fun groupRef(groupId: String) = firestore.document("groups/$groupId")
    private fun memberRef(groupId: String, uid: String) = firestore.document("groups/$groupId/members/$uid")
    private fun inviteRef(code: String) = firestore.document("inviteCodes/$code")
    private fun membershipRef(uid: String, groupId: String) = firestore.document("users/$uid/groups/$groupId")
    private fun counterRef(uid: String) = firestore.document("users/$uid/meta/groups")

    override fun newGroupId(): String = firestore.collection("groups").document().id

    override suspend fun memberships(uid: String): List<Membership> = call {
        firestore.collection("users/$uid/groups").get(Source.SERVER).awaitTask().documents.map {
            Membership(it.id, it.getTimestamp("joinedAt")?.toDate()?.time)
        }
    }

    override suspend fun group(groupId: String): GroupInfo? = call {
        groupRef(groupId).get(Source.SERVER).awaitTask().toGroup()
    }

    override suspend fun members(groupId: String): List<GroupMember> = call {
        firestore.collection("groups/$groupId/members").get(Source.SERVER).awaitTask().documents.mapNotNull { it.toMember() }
    }

    override fun observeGroup(groupId: String): Flow<GroupSnapshot> = callbackFlow {
        var group: GroupInfo? = null
        var members: List<GroupMember>? = null
        fun emitIfReady() {
            val g = group ?: return
            val m = members ?: return
            trySend(GroupSnapshot(g, m))
        }
        fun fail(error: FirebaseFirestoreException?) {
            close(GroupException(error?.let(::errorOf) ?: GroupError.NOT_FOUND, error?.javaClass?.simpleName))
        }
        val registrations = mutableListOf<ListenerRegistration>()
        registrations += groupRef(groupId).addSnapshotListener { snapshot, error ->
            when {
                error != null -> fail(error)
                snapshot == null || !snapshot.exists() -> close(GroupException(GroupError.NOT_FOUND))
                else -> {
                    group = snapshot.toGroup()
                    emitIfReady()
                }
            }
        }
        registrations += firestore.collection("groups/$groupId/members").addSnapshotListener { snapshot, error ->
            when {
                error != null -> fail(error)
                snapshot != null -> {
                    members = snapshot.documents.mapNotNull { it.toMember() }
                    emitIfReady()
                }
            }
        }
        awaitClose { registrations.forEach { it.remove() } }
    }

    override suspend fun ownMemberExists(groupId: String, uid: String): Boolean = call {
        memberRef(groupId, uid).get(Source.SERVER).awaitTask().exists()
    }

    override suspend fun findInvite(code: String): InvitePreview? = call {
        val doc = inviteRef(code).get(Source.SERVER).awaitTask()
        if (!doc.exists()) return@call null
        val groupId = doc.getString("groupId") ?: return@call null
        InvitePreview(code, groupId, doc.getString("groupName").orEmpty())
    }

    override suspend fun createGroup(
        uid: String,
        groupId: String,
        name: String,
        code: String,
        profile: MemberProfile,
        summary: MemberSummary
    ) = commit {
        set(
            groupRef(groupId),
            mapOf(
                "name" to name,
                "ownerUid" to uid,
                "inviteCode" to code,
                "memberCount" to 1,
                "memberChangeUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                SCHEMA_VERSION to 1
            )
        )
        set(memberRef(groupId, uid), newMemberFields(code, profile, summary))
        set(inviteRef(code), inviteFields(groupId, name))
        set(membershipRef(uid, groupId), membershipFields(groupId))
        counterStep(uid, groupId, 1)
    }

    override suspend fun joinGroup(uid: String, invite: InvitePreview, profile: MemberProfile, summary: MemberSummary) = commit {
        update(
            groupRef(invite.groupId),
            mapOf(
                "memberCount" to FieldValue.increment(1),
                "memberChangeUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        set(memberRef(invite.groupId, uid), newMemberFields(invite.code, profile, summary))
        set(membershipRef(uid, invite.groupId), membershipFields(invite.groupId))
        counterStep(uid, invite.groupId, 1)
    }

    override suspend fun leaveGroup(uid: String, groupId: String, newOwnerUid: String?) = commit {
        val fields = mutableMapOf<String, Any>(
            "memberCount" to FieldValue.increment(-1),
            "memberChangeUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (newOwnerUid != null) fields["ownerUid"] = newOwnerUid
        update(groupRef(groupId), fields)
        delete(memberRef(groupId, uid))
        delete(membershipRef(uid, groupId))
        counterStep(uid, groupId, -1)
    }

    override suspend fun removeMember(groupId: String, memberUid: String) = commit {
        update(
            groupRef(groupId),
            mapOf(
                "memberCount" to FieldValue.increment(-1),
                "memberChangeUid" to memberUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        delete(memberRef(groupId, memberUid))
    }

    override suspend fun transferOwnership(groupId: String, newOwnerUid: String) = commit {
        update(groupRef(groupId), mapOf("ownerUid" to newOwnerUid, "updatedAt" to FieldValue.serverTimestamp()))
    }

    override suspend fun renameGroup(groupId: String, code: String, name: String) = commit {
        update(groupRef(groupId), mapOf("name" to name, "updatedAt" to FieldValue.serverTimestamp()))
        update(inviteRef(code), mapOf("groupName" to name))
    }

    override suspend fun regenerateCode(groupId: String, oldCode: String, newCode: String, name: String) = commit {
        update(groupRef(groupId), mapOf("inviteCode" to newCode, "updatedAt" to FieldValue.serverTimestamp()))
        delete(inviteRef(oldCode))
        set(inviteRef(newCode), inviteFields(groupId, name))
    }

    override suspend fun deleteGroup(uid: String, groupId: String, code: String) = commit {
        delete(memberRef(groupId, uid))
        delete(groupRef(groupId))
        delete(inviteRef(code))
        delete(membershipRef(uid, groupId))
        counterStep(uid, groupId, -1)
    }

    override suspend fun removeStaleMembership(uid: String, groupId: String, deleteOwnMemberDoc: Boolean) = commit {
        if (deleteOwnMemberDoc) delete(memberRef(groupId, uid))
        delete(membershipRef(uid, groupId))
        counterStep(uid, groupId, -1)
    }

    override suspend fun updateSummary(groupId: String, uid: String, profile: MemberProfile, summary: MemberSummary) = commit {
        update(memberRef(groupId, uid), memberFields(profile, summary))
    }

    override suspend fun deleteGroupCounter(uid: String) {
        val counter = call { counterRef(uid).get(Source.SERVER).awaitTask() }
        // The rules allow it only at 0; a missing counter has nothing to delete
        if (!counter.exists() || counter.getLong("groupCount") != 0L) return
        call { counterRef(uid).delete().awaitTask() }
    }

    // ---------- Documents ----------

    private fun memberFields(profile: MemberProfile, summary: MemberSummary): Map<String, Any?> = mapOf(
        "displayName" to profile.displayName,
        "photoUrl" to profile.photoUrl,
        "weekStartLocal" to summary.weekStartLocal,
        "workoutsThisWeek" to summary.workoutsThisWeek,
        "weeklyGoal" to summary.weeklyGoal,
        "streakWeeks" to summary.streakWeeks,
        "daysThisWeek" to summary.daysThisWeek.entries.associate { (day, sources) -> day.toString() to sources.map { it.name } },
        "lastTrainedLocal" to summary.lastTrainedLocal,
        "updatedAt" to FieldValue.serverTimestamp(),
        SCHEMA_VERSION to 1
    )

    private fun newMemberFields(code: String, profile: MemberProfile, summary: MemberSummary): Map<String, Any?> =
        memberFields(profile, summary) + mapOf("joinedAt" to FieldValue.serverTimestamp(), "inviteCode" to code)

    private fun inviteFields(groupId: String, name: String): Map<String, Any> = mapOf(
        "groupId" to groupId,
        "groupName" to name,
        "createdAt" to FieldValue.serverTimestamp(),
        SCHEMA_VERSION to 1
    )

    private fun membershipFields(groupId: String): Map<String, Any> =
        mapOf("groupId" to groupId, "joinedAt" to FieldValue.serverTimestamp(), SCHEMA_VERSION to 1)

    /** users/{uid}/meta/groups: groupCount ± 1 naming the group (a merge, so no read is needed). */
    private fun WriteBatch.counterStep(uid: String, groupId: String, step: Long) {
        set(
            counterRef(uid),
            mapOf(
                "groupCount" to FieldValue.increment(step),
                "lastGroupId" to groupId,
                "updatedAt" to FieldValue.serverTimestamp(),
                SCHEMA_VERSION to 1
            ),
            SetOptions.merge()
        )
    }

    private fun DocumentSnapshot.toGroup(): GroupInfo? {
        if (!exists()) return null
        return GroupInfo(
            id = id,
            name = getString("name").orEmpty(),
            ownerUid = getString("ownerUid").orEmpty(),
            inviteCode = getString("inviteCode").orEmpty(),
            memberCount = getLong("memberCount")?.toInt() ?: 0
        )
    }

    private fun DocumentSnapshot.toMember(): GroupMember? {
        if (!exists()) return null
        @Suppress("UNCHECKED_CAST")
        val days = (get("daysThisWeek") as? Map<String, Any?>).orEmpty().mapNotNull { (key, value) ->
            val day = key.toIntOrNull()?.takeIf { it in 1..7 } ?: return@mapNotNull null
            val sources = (value as? List<*>).orEmpty()
                .mapNotNull { name -> TrainingSource.entries.firstOrNull { it.name == name } }
                .distinct()
                .sortedBy { it.ordinal }
            if (sources.isEmpty()) null else day to sources
        }.sortedBy { it.first }.toMap()
        return GroupMember(
            uid = id,
            profile = MemberProfile(getString("displayName").orEmpty(), getString("photoUrl")),
            joinedAtMillis = getTimestamp("joinedAt")?.toDate()?.time,
            summary = MemberSummary(
                weekStartLocal = getString("weekStartLocal").orEmpty(),
                workoutsThisWeek = getLong("workoutsThisWeek")?.toInt() ?: 0,
                weeklyGoal = getLong("weeklyGoal")?.toInt() ?: 1,
                streakWeeks = getLong("streakWeeks")?.toInt() ?: 0,
                daysThisWeek = days,
                lastTrainedLocal = getString("lastTrainedLocal")
            )
        )
    }

    // ---------- Calls ----------

    private suspend fun commit(build: WriteBatch.() -> Unit) {
        call { firestore.batch().apply(build).commit().awaitTask() }
    }

    private suspend fun <T> call(block: suspend () -> T): T = firestoreCall(timeoutMillis, block)

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L
        private const val SCHEMA_VERSION = "schemaVersion"

        internal fun errorOf(e: FirebaseFirestoreException): GroupError = when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> GroupError.PERMISSION_DENIED
            FirebaseFirestoreException.Code.NOT_FOUND -> GroupError.NOT_FOUND
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> GroupError.NETWORK
            else -> GroupError.FAILED
        }
    }
}
