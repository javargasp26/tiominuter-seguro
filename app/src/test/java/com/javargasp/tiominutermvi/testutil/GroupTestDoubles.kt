package com.javargasp.tiominutermvi.testutil

import com.javargasp.tiominutermvi.data.auth.FakeAuthRepository
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoalRepository
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.GroupInfo
import com.javargasp.tiominutermvi.domain.groups.GroupLimits
import com.javargasp.tiominutermvi.domain.groups.GroupLog
import com.javargasp.tiominutermvi.domain.groups.GroupMember
import com.javargasp.tiominutermvi.domain.groups.GroupMembersCache
import com.javargasp.tiominutermvi.domain.groups.GroupOperations
import com.javargasp.tiominutermvi.domain.groups.GroupPreferences
import com.javargasp.tiominutermvi.domain.groups.GroupReconciler
import com.javargasp.tiominutermvi.domain.groups.GroupRepository
import com.javargasp.tiominutermvi.domain.groups.GroupSnapshot
import com.javargasp.tiominutermvi.domain.groups.GroupSummaryPublisher
import com.javargasp.tiominutermvi.domain.groups.InvitePreview
import com.javargasp.tiominutermvi.domain.groups.MemberProfile
import com.javargasp.tiominutermvi.domain.groups.MemberProfiles
import com.javargasp.tiominutermvi.domain.groups.MemberSummary
import com.javargasp.tiominutermvi.domain.groups.Membership
import com.javargasp.tiominutermvi.domain.groups.MySummarySource
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import java.util.TimeZone

/**
 * An in-memory Firestore for groups that behaves like firebase/firestore.rules for the batches of
 * [GroupRepository]: the 20-member and 5-group limits, codes that must match, a taken code, owner
 * leaves, stale index entries, and member docs left behind by a deleted group. [failOn] scripts errors.
 */
class FakeGroupRepository : GroupRepository {

    val groups = linkedMapOf<String, GroupInfo>()
    val members = mutableMapOf<String, LinkedHashMap<String, GroupMember>>()
    val invites = mutableMapOf<String, Pair<String, String>>()
    val index = mutableMapOf<String, LinkedHashMap<String, Membership>>()
    val counters = mutableMapOf<String, Int>()

    /** Every operation by name, in order (e.g. "removeMember:g1:bob"). */
    val calls = mutableListOf<String>()
    private val errors = mutableMapOf<String, ArrayDeque<GroupError>>()
    private val version = MutableStateFlow(0)
    private var ids = 0
    var clock = 1_000L

    /** The next [times] calls of [operation] (e.g. "joinGroup") throw [error]. */
    fun failOn(operation: String, error: GroupError, times: Int = 1) {
        errors.getOrPut(operation) { ArrayDeque() }.apply { repeat(times) { addLast(error) } }
    }

    /** Runs before every operation with its call name (to simulate other people acting meanwhile). */
    var beforeOp: (String) -> Unit = {}

    private fun op(name: String, detail: String = "") {
        val call = if (detail.isEmpty()) name else "$name:$detail"
        calls += call
        beforeOp(call)
        errors[name]?.removeFirstOrNull()?.let { throw GroupException(it) }
    }

    private fun denied(): Nothing = throw GroupException(GroupError.PERMISSION_DENIED)

    private fun changed() {
        version.value++
    }

    /** A group seeded directly: [memberUids] with [summaries] (default: an empty week), owner first. */
    fun seedGroup(
        id: String,
        name: String,
        code: String,
        memberUids: List<String>,
        summaries: Map<String, MemberSummary> = emptyMap(),
        joinedAt: Map<String, Long> = emptyMap()
    ) {
        groups[id] = GroupInfo(id, name, memberUids.first(), code, memberUids.size)
        invites[code] = id to name
        members[id] = linkedMapOf()
        memberUids.forEachIndexed { i, uid ->
            members.getValue(id)[uid] = GroupMember(uid, MemberProfile(uid.replaceFirstChar { it.uppercase() }, null),
                joinedAt[uid] ?: (i.toLong() + 1), summaries[uid] ?: summary())
            index.getOrPut(uid) { linkedMapOf() }[id] = Membership(id, i.toLong() + 1)
            counters[uid] = (counters[uid] ?: 0) + 1
        }
        changed()
    }

    override fun newGroupId(): String = "group${++ids}"

    override suspend fun memberships(uid: String): List<Membership> {
        op("memberships")
        return index[uid].orEmpty().values.toList()
    }

    override suspend fun group(groupId: String): GroupInfo? {
        op("group", groupId)
        return groups[groupId]
    }

    override suspend fun members(groupId: String): List<GroupMember> {
        op("members", groupId)
        if (groupId !in groups) denied()
        return members[groupId].orEmpty().values.toList()
    }

    override fun observeGroup(groupId: String): Flow<GroupSnapshot> = flow {
        version.collect {
            val group = groups[groupId] ?: throw GroupException(GroupError.NOT_FOUND)
            emit(GroupSnapshot(group, members[groupId].orEmpty().values.toList()))
        }
    }

    override suspend fun ownMemberExists(groupId: String, uid: String): Boolean {
        op("ownMemberExists", groupId)
        return members[groupId]?.containsKey(uid) == true
    }

    override suspend fun findInvite(code: String): InvitePreview? {
        op("findInvite", code)
        return invites[code]?.let { (groupId, name) -> InvitePreview(code, groupId, name) }
    }

    override suspend fun createGroup(uid: String, groupId: String, name: String, code: String, profile: MemberProfile, summary: MemberSummary) {
        op("createGroup", code)
        if (code in invites || groupId in groups || (counters[uid] ?: 0) >= GroupLimits.MAX_GROUPS) denied()
        groups[groupId] = GroupInfo(groupId, name, uid, code, 1)
        invites[code] = groupId to name
        members[groupId] = linkedMapOf(uid to GroupMember(uid, profile, clock, summary))
        addIndex(uid, groupId)
    }

    override suspend fun joinGroup(uid: String, invite: InvitePreview, profile: MemberProfile, summary: MemberSummary) {
        op("joinGroup", invite.groupId)
        val group = groups[invite.groupId] ?: denied()
        if (group.inviteCode != invite.code || invites[invite.code]?.first != group.id) denied()
        if (group.memberCount >= GroupLimits.MAX_MEMBERS || members.getValue(group.id).containsKey(uid)) denied()
        if ((counters[uid] ?: 0) >= GroupLimits.MAX_GROUPS || index[uid]?.containsKey(group.id) == true) denied()
        groups[group.id] = group.copy(memberCount = group.memberCount + 1)
        members.getValue(group.id)[uid] = GroupMember(uid, profile, ++clock, summary)
        addIndex(uid, group.id)
    }

    override suspend fun leaveGroup(uid: String, groupId: String, newOwnerUid: String?) {
        op("leaveGroup", "$groupId:${newOwnerUid ?: "-"}")
        val group = groups[groupId] ?: denied()
        val groupMembers = members.getValue(groupId)
        if (uid !in groupMembers || group.memberCount <= 1) denied()
        val owner = when {
            group.ownerUid != uid -> if (newOwnerUid == null) group.ownerUid else denied()
            newOwnerUid == null || newOwnerUid == uid || newOwnerUid !in groupMembers -> denied()
            else -> newOwnerUid
        }
        groupMembers.remove(uid)
        groups[groupId] = group.copy(ownerUid = owner, memberCount = group.memberCount - 1)
        removeIndex(uid, groupId)
    }

    override suspend fun removeMember(groupId: String, memberUid: String) {
        op("removeMember", "$groupId:$memberUid")
        val group = groups[groupId] ?: denied()
        if (memberUid == group.ownerUid || members.getValue(groupId).remove(memberUid) == null) denied()
        groups[groupId] = group.copy(memberCount = group.memberCount - 1)
        changed()
    }

    override suspend fun transferOwnership(groupId: String, newOwnerUid: String) {
        op("transferOwnership", "$groupId:$newOwnerUid")
        val group = groups[groupId] ?: denied()
        if (newOwnerUid !in members.getValue(groupId)) denied()
        groups[groupId] = group.copy(ownerUid = newOwnerUid)
        changed()
    }

    override suspend fun renameGroup(groupId: String, code: String, name: String) {
        op("renameGroup", "$groupId:$name")
        val group = groups[groupId] ?: denied()
        groups[groupId] = group.copy(name = name)
        invites[code] = groupId to name
        changed()
    }

    override suspend fun regenerateCode(groupId: String, oldCode: String, newCode: String, name: String) {
        op("regenerateCode", newCode)
        val group = groups[groupId] ?: denied()
        if (newCode in invites) denied()
        invites.remove(oldCode)
        invites[newCode] = groupId to name
        groups[groupId] = group.copy(inviteCode = newCode)
        changed()
    }

    override suspend fun deleteGroup(uid: String, groupId: String, code: String) {
        op("deleteGroup", groupId)
        val group = groups[groupId] ?: denied()
        if (group.ownerUid != uid) denied()
        groups.remove(groupId)
        invites.remove(code)
        members[groupId]?.remove(uid)
        removeIndex(uid, groupId)
    }

    override suspend fun removeStaleMembership(uid: String, groupId: String, deleteOwnMemberDoc: Boolean) {
        op("removeStaleMembership", groupId)
        if (index[uid]?.containsKey(groupId) != true) denied()
        if (deleteOwnMemberDoc) members[groupId]?.remove(uid)
        if (members[groupId]?.containsKey(uid) == true) denied()
        removeIndex(uid, groupId)
    }

    override suspend fun updateSummary(groupId: String, uid: String, profile: MemberProfile, summary: MemberSummary) {
        op("updateSummary", groupId)
        val current = members[groupId]?.get(uid) ?: throw GroupException(GroupError.NOT_FOUND)
        if (groupId !in groups) denied()
        members.getValue(groupId)[uid] = current.copy(profile = profile, summary = summary)
        changed()
    }

    override suspend fun deleteGroupCounter(uid: String) {
        op("deleteGroupCounter")
        when (counters[uid]) {
            null -> Unit
            0 -> counters.remove(uid)
            else -> denied()
        }
    }

    private fun addIndex(uid: String, groupId: String) {
        index.getOrPut(uid) { linkedMapOf() }[groupId] = Membership(groupId, clock)
        counters[uid] = (counters[uid] ?: 0) + 1
        changed()
    }

    private fun removeIndex(uid: String, groupId: String) {
        index[uid]?.remove(groupId)
        counters[uid] = (counters[uid] ?: 1) - 1
        changed()
    }

    companion object {
        fun summary(
            week: String = "2026-10-19",
            workouts: Int = 0,
            goal: Int = 3,
            streak: Int = 0,
            days: Map<Int, List<TrainingSource>> = emptyMap(),
            lastTrained: String? = null
        ) = MemberSummary(week, workouts, goal, streak, days, lastTrained)
    }
}

class InMemoryGroupPreferences : GroupPreferences {
    val hashes = mutableMapOf<String, String?>()
    val dirty = mutableMapOf<String, Boolean>()
    val reconciled = mutableMapOf<String, Long>()
    var dismissedOn: String? = null

    override fun publishedSummaryHash(uid: String): String? = hashes[uid]
    override fun setPublishedSummaryHash(uid: String, hash: String?) {
        hashes[uid] = hash
    }
    override fun isSummaryDirty(uid: String): Boolean = dirty[uid] ?: false
    override fun setSummaryDirty(uid: String, dirty: Boolean) {
        this.dirty[uid] = dirty
    }
    override fun lastReconciledAt(uid: String): Long? = reconciled[uid]
    override fun setLastReconciledAt(uid: String, millis: Long) {
        reconciled[uid] = millis
    }
    override fun trainedTodayDismissedOn(): String? = dismissedOn
    override fun setTrainedTodayDismissedOn(date: String?) {
        dismissedOn = date
    }
}

class RecordingGroupLog : GroupLog {
    val messages = mutableListOf<String>()
    override fun warn(message: String) {
        messages += message
    }
}

class FakeTrainingLog(val entries: MutableStateFlow<List<TrainingLogEntry>> = MutableStateFlow(emptyList())) : TrainingLogRepository {
    override fun observeEntries(): Flow<List<TrainingLogEntry>> = entries
    override fun observeEntriesBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntry>> = emptyFlow()
    override suspend fun deleteEntry(id: Long) = Unit
    override suspend fun getEntry(id: Long): TrainingLogEntry? = null
    override suspend fun addActivityEntry(input: ActivityEntryInput): Long = throw UnsupportedOperationException()
    override suspend fun updateActivityEntry(id: Long, input: ActivityEntryInput) = throw UnsupportedOperationException()
    override suspend fun setPhotoPath(id: Long, photoPath: String?): String? = throw UnsupportedOperationException()
    override suspend fun getPhotoPaths(): List<String> = emptyList()
}

class FakeGoals(val goals: MutableStateFlow<List<WeeklyGoal>> = MutableStateFlow(emptyList())) : WeeklyGoalRepository {
    override fun observeGoals(): Flow<List<WeeklyGoal>> = goals
    override suspend fun setGoal(workoutsPerWeek: Int) {
        goals.value = goals.value + WeeklyGoal(workoutsPerWeek, "2026-10-19")
    }
}

/** A training entry on [date] lasting [minutes] (null: unknown duration). */
fun trainingEntry(date: String, source: TrainingSource = TrainingSource.GYM, minutes: Int? = 30, id: Long = 0) = TrainingLogEntry(
    id = id,
    uuid = "uuid-$date-$source-$id",
    dateLocal = date,
    completedAt = 0L,
    source = source,
    refId = null,
    title = "",
    durationSeconds = minutes?.let { it * 60 },
    createdAt = 0L,
    updatedAt = 0L
)

/**
 * The groups domain wired over fakes, as groupsModule wires it. Today is Thursday 2026-10-22 (UTC)
 * unless [now] moves; the signed-in user is "me" (Alex Rivera) unless the session changes.
 */
class GroupsTestKit(scope: CoroutineScope? = null) {
    // 2026-10-22T10:00:00Z
    var now = 1_792_663_200_000L
    val dates = LocalDateProvider({ now }, { TimeZone.getTimeZone("UTC") })
    val repository = FakeGroupRepository()
    val preferences = InMemoryGroupPreferences()
    val log = RecordingGroupLog()
    val network = FakeNetwork()
    val sync = FakeSyncRepository()
    val auth = FakeAuthRepository(initialSession = ME)
    val trainingLog = FakeTrainingLog()
    val goals = FakeGoals(MutableStateFlow(listOf(WeeklyGoal(3, "2026-10-12"))))
    val summaries = MySummarySource(trainingLog, goals, dates)
    val profiles = MemberProfiles { "Member" }
    val cache = GroupMembersCache({ now })
    val reconciler = GroupReconciler(repository, preferences, { now }, log)
    val publisher: GroupSummaryPublisher? = scope?.let {
        GroupSummaryPublisher(auth, summaries, profiles, repository, preferences, network, sync, log, it)
    }
    var codes = ArrayDeque(listOf("AAAAAAAA", "BBBBBBBB", "CCCCCCCC", "DDDDDDDD", "EEEEEEEE", "FFFFFFFF", "GGGGGGGG"))
    val operations = GroupOperations(
        repository, auth, summaries, profiles, network, reconciler, publisher, cache, dates, log
    ) { codes.removeFirst() }

    companion object {
        val ME = com.javargasp.tiominutermvi.domain.auth.AuthSession.SignedIn("me", "Alex Rivera", "a@example.com", "https://photo")
    }
}
