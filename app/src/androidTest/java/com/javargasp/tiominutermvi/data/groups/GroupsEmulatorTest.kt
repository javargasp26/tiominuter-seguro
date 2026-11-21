package com.javargasp.tiominutermvi.data.groups

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthResult
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoalRepository
import com.javargasp.tiominutermvi.domain.groups.GroupAccountCleaner
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.GroupLog
import com.javargasp.tiominutermvi.domain.groups.GroupMembers
import com.javargasp.tiominutermvi.domain.groups.GroupMembersCache
import com.javargasp.tiominutermvi.domain.groups.GroupOperations
import com.javargasp.tiominutermvi.domain.groups.GroupPreferences
import com.javargasp.tiominutermvi.domain.groups.GroupRanking
import com.javargasp.tiominutermvi.domain.groups.GroupReconciler
import com.javargasp.tiominutermvi.domain.groups.GroupSummaryPublisher
import com.javargasp.tiominutermvi.domain.groups.MemberProfiles
import com.javargasp.tiominutermvi.domain.groups.MySummarySource
import com.javargasp.tiominutermvi.domain.groups.PublishOutcome
import com.javargasp.tiominutermvi.domain.groups.TrainedTodayRule
import com.javargasp.tiominutermvi.domain.sync.SyncOutcome
import com.javargasp.tiominutermvi.domain.sync.SyncRepository
import com.javargasp.tiominutermvi.domain.sync.SyncStatus
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Groups end to end against the Firebase EMULATORS (Firestore + Auth on the host, 10.0.2.2), with
 * the real firebase/firestore.rules: several email/password test users (never the real project or a
 * real account) create, join, publish summaries, read the ranking and the banner input, remove,
 * transfer, leave, fill a group to 20 and delete it in chunks, and run the delete-account cleanup.
 * Opt-in: only with the instrumentation argument `groupsEmulator=true` (scripts/test_groups_emulator.sh).
 */
@RunWith(AndroidJUnit4::class)
class GroupsEmulatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val users = mutableListOf<User>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dates = LocalDateProvider({ System.currentTimeMillis() }, { TimeZone.getDefault() })
    private val today = dates.today()

    @Before
    fun setUp() {
        assumeTrue(
            "Opt-in: run scripts/test_groups_emulator.sh",
            InstrumentationRegistry.getArguments().getString("groupsEmulator") == "true"
        )
    }

    @After
    fun tearDown() {
        users.forEach { it.close() }
        scope.cancel()
    }

    /** One person on their own phone: a FirebaseApp with its own Auth and Firestore (both emulators). */
    private inner class User(val name: String) {
        val app: FirebaseApp
        val uid: String
        val repository: FirestoreGroupRepository
        val entries = MutableStateFlow<List<TrainingLogEntry>>(emptyList())
        val goals = MutableStateFlow(listOf(WeeklyGoal(3, dates.mondayOf(dates.previousWeek(today)))))
        val preferences = MemoryPreferences()
        val reconciler: GroupReconciler
        val operations: GroupOperations
        val publisher: GroupSummaryPublisher
        val session: AuthSession.SignedIn

        init {
            // firebase-auth caches its emulator config per API KEY, process-wide, holding the first app
            // that used it: a key shared with a deleted app (an earlier test) fails with "FirebaseApp
            // was deleted". So every app gets its own key and name (the Auth emulator accepts any key).
            val appId = UUID.randomUUID().toString()
            val options = FirebaseOptions.Builder(requireNotNull(FirebaseOptions.fromResource(context)))
                .setProjectId(PROJECT_ID)
                .setApiKey("emulator-key-$appId")
                .build()
            app = FirebaseApp.initializeApp(context, options, "groups-$name-$appId")
            val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
            val firestore = FirebaseFirestore.getInstance(app).apply {
                useEmulator(HOST, FIRESTORE_PORT)
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
            // Email/password on the Auth emulator: a real (non-anonymous) provider, as the rules require
            val email = "$name-${UUID.randomUUID()}@example.test"
            uid = requireNotNull(Tasks.await(auth.createUserWithEmailAndPassword(email, "password-123"), 30, TimeUnit.SECONDS).user).uid
            session = AuthSession.SignedIn(uid, name, null, null)
            repository = FirestoreGroupRepository(firestore)
            val authRepository = object : AuthRepository {
                override val session: Flow<AuthSession> = MutableStateFlow(this@User.session)
                override suspend fun signInWithGoogle(idToken: String) = AuthResult.Success
                override suspend fun signOut() = Unit
                override suspend fun deleteAccount(wipeLocalData: Boolean, reauthIdToken: String) = AuthResult.Success
            }
            val summaries = MySummarySource(MemoryTrainingLog(entries), MemoryGoals(goals), dates)
            val profiles = MemberProfiles { "Member" }
            val log = GroupLog { println("GroupsEmulatorTest: $it") }
            reconciler = GroupReconciler(repository, preferences, { System.currentTimeMillis() }, log)
            publisher = GroupSummaryPublisher(authRepository, summaries, profiles, repository, preferences, { true }, NoSync, log, scope)
            operations = GroupOperations(
                repository, authRepository, summaries, profiles, { true }, reconciler, publisher, GroupMembersCache({ 0L }), dates, log
            )
            users += this
        }

        fun close() {
            FirebaseAuth.getInstance(app).signOut()
            app.delete()
        }
    }

    private suspend fun expectError(error: GroupError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $error")
        } catch (e: GroupException) {
            assertEquals(error, e.error)
        }
    }

    private fun entry(date: String, source: TrainingSource, minutes: Int = 30) = TrainingLogEntry(
        id = 0, uuid = UUID.randomUUID().toString(), dateLocal = date, completedAt = 0L, source = source, refId = null,
        title = "", durationSeconds = minutes * 60, createdAt = 0L, updatedAt = 0L
    )

    @Test
    fun create_join_publish_rank_remove_transfer_leave_and_a_full_group_deleted_in_chunks() = runBlocking {
        val ana = User("ana")
        val bob = User("bob")
        val cara = User("cara")

        // Create → the code → join by code (a non-member previews the name first)
        val created = ana.operations.create("Morning crew")
        assertEquals("Morning crew", bob.operations.findInvite(created.code.lowercase()).groupName)
        bob.operations.join(bob.operations.findInvite(created.code))
        cara.operations.join(cara.operations.findInvite(created.code))
        expectError(GroupError.ALREADY_MEMBER) { cara.operations.join(cara.operations.findInvite(created.code)) }
        expectError(GroupError.INVALID_CODE) { cara.operations.findInvite("ZZZZZZZZ") }

        // Summaries: Bob trained today (gym + activity), Ana once this week, Cara not yet
        bob.entries.value = listOf(entry(today, TrainingSource.GYM), entry(today, TrainingSource.ACTIVITY), entry(dates.mondayOf(today), TrainingSource.DAILY))
        ana.entries.value = listOf(entry(dates.mondayOf(today), TrainingSource.PROGRAM), entry(today, TrainingSource.DAILY, minutes = 5))
        assertEquals(PublishOutcome.PUBLISHED, bob.publisher.publish(bob.session))
        assertEquals(PublishOutcome.PUBLISHED, ana.publisher.publish(ana.session))
        assertEquals(PublishOutcome.UNCHANGED, ana.publisher.publish(ana.session))

        // Ana reads the ranking and the banner input
        val members = ana.repository.members(created.id)
        val ranking = GroupRanking.rank(members, dates.mondayOf(today), ana.uid)
        assertEquals(listOf("bob", "ana", "cara"), ranking.map { it.member.profile.displayName })
        assertEquals(3, ranking.first().workouts)
        val bobSummary = members.single { it.uid == bob.uid }.summary
        assertTrue(bobSummary.daysThisWeek.getValue(dayOfWeek(today)).containsAll(listOf(TrainingSource.GYM, TrainingSource.ACTIVITY)))
        assertEquals(today, bobSummary.lastTrainedLocal)
        val banner = TrainedTodayRule.banner(ana.uid, today, false, null, listOf(GroupMembers(created.id, members)))
        assertEquals(listOf("bob"), banner?.names)

        // Remove Cara: she can no longer read the group; her reconciliation cleans her index
        val group = { requireNotNull(runBlocking { ana.repository.group(created.id) }) }
        ana.operations.removeMember(group(), cara.uid)
        expectError(GroupError.PERMISSION_DENIED) { cara.repository.members(created.id) }
        assertTrue(cara.reconciler.reconcile(cara.uid).isEmpty())
        assertTrue(cara.repository.memberships(cara.uid).isEmpty())

        // Transfer to Bob; Ana is now a plain member and leaves
        ana.operations.transfer(group(), bob.uid)
        expectError(GroupError.PERMISSION_DENIED) { ana.repository.renameGroup(created.id, created.code, "Taken") }
        val asBob = requireNotNull(bob.repository.group(created.id))
        bob.operations.rename(asBob, "Evening crew")
        assertEquals("Evening crew", bob.operations.findInvite(created.code).groupName)
        ana.operations.leave(requireNotNull(bob.repository.group(created.id)))
        assertEquals(1, requireNotNull(bob.repository.group(created.id)).memberCount)

        // A new code: the old one stops working
        val newCode = bob.operations.regenerateCode(requireNotNull(bob.repository.group(created.id)))
        expectError(GroupError.INVALID_CODE) { cara.operations.findInvite(created.code) }

        // Fill it to 20 (Cara again, after her reconciliation, and 18 more); the 21st is refused
        cara.operations.join(cara.operations.findInvite(newCode))
        val others = (1..18).map { User("member$it") }
        others.forEach { it.operations.join(it.operations.findInvite(newCode)) }
        assertEquals(20, requireNotNull(bob.repository.group(created.id)).memberCount)
        expectError(GroupError.GROUP_FULL) { ana.operations.join(ana.operations.findInvite(newCode)) }

        // Delete in chunks: every other member removed one batch at a time, then the group
        bob.operations.delete(requireNotNull(bob.repository.group(created.id)))
        assertNull(cara.repository.findInvite(newCode))
        assertFalse(others.first().repository.ownMemberExists(created.id, others.first().uid))
        assertTrue(others.first().reconciler.reconcile(others.first().uid).isEmpty())
        assertTrue(bob.repository.memberships(bob.uid).isEmpty())
    }

    @Test
    fun delete_account_cleanup_hands_owned_groups_to_the_oldest_member_and_deletes_solo_ones() = runBlocking {
        val dani = User("dani")
        val eli = User("eli")
        val fer = User("fer")
        val shared = dani.operations.create("Shared")
        eli.operations.join(eli.operations.findInvite(shared.code))
        fer.operations.join(fer.operations.findInvite(shared.code))
        val solo = dani.operations.create("Solo")
        val theirs = eli.operations.create("Theirs")
        dani.operations.join(dani.operations.findInvite(theirs.code))

        GroupAccountCleaner(dani.repository, dani.reconciler, GroupMembersCache({ 0L })).leaveAllGroups(dani.uid)

        assertEquals(eli.uid, requireNotNull(eli.repository.group(shared.id)).ownerUid)
        assertEquals(2, requireNotNull(eli.repository.group(shared.id)).memberCount)
        assertNull(eli.repository.findInvite(solo.code))
        assertEquals(1, requireNotNull(eli.repository.group(theirs.id)).memberCount)
        assertTrue(dani.repository.memberships(dani.uid).isEmpty())
    }

    private fun dayOfWeek(date: String): Int = dates.weekDates(date).indexOf(date) + 1

    private class MemoryTrainingLog(private val entries: MutableStateFlow<List<TrainingLogEntry>>) : TrainingLogRepository {
        override fun observeEntries(): Flow<List<TrainingLogEntry>> = entries
        override fun observeEntriesBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntry>> = emptyFlow()
        override suspend fun getEntry(id: Long): TrainingLogEntry? = null
        override suspend fun addActivityEntry(input: ActivityEntryInput): Long = 0
        override suspend fun updateActivityEntry(id: Long, input: ActivityEntryInput) = Unit
        override suspend fun deleteEntry(id: Long) = Unit
        override suspend fun setPhotoPath(id: Long, photoPath: String?): String? = null
        override suspend fun getPhotoPaths(): List<String> = emptyList()
    }

    private class MemoryGoals(private val goals: MutableStateFlow<List<WeeklyGoal>>) : WeeklyGoalRepository {
        override fun observeGoals(): Flow<List<WeeklyGoal>> = goals
        override suspend fun setGoal(workoutsPerWeek: Int) = Unit
    }

    private class MemoryPreferences : GroupPreferences {
        private val values = mutableMapOf<String, Any?>()
        override fun publishedSummaryHash(uid: String) = values["hash$uid"] as String?
        override fun setPublishedSummaryHash(uid: String, hash: String?) { values["hash$uid"] = hash }
        override fun isSummaryDirty(uid: String) = values["dirty$uid"] as Boolean? ?: false
        override fun setSummaryDirty(uid: String, dirty: Boolean) { values["dirty$uid"] = dirty }
        override fun lastReconciledAt(uid: String) = values["rec$uid"] as Long?
        override fun setLastReconciledAt(uid: String, millis: Long) { values["rec$uid"] = millis }
        override fun trainedTodayDismissedOn() = values["dismissed"] as String?
        override fun setTrainedTodayDismissedOn(date: String?) { values["dismissed"] = date }
    }

    private object NoSync : SyncRepository {
        override val status: Flow<SyncStatus> = MutableStateFlow(SyncStatus())
        override suspend fun pendingCount(): Int = 0
        override suspend fun syncNow(): SyncOutcome = SyncOutcome.SKIPPED
    }

    private companion object {
        const val PROJECT_ID = "tiominuter-e43f7"
        const val HOST = "10.0.2.2"
        const val AUTH_PORT = 9099
        const val FIRESTORE_PORT = 8080
    }
}
