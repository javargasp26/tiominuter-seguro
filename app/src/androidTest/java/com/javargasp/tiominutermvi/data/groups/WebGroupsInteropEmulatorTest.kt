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
import com.javargasp.tiominutermvi.domain.groups.GroupLog
import com.javargasp.tiominutermvi.domain.groups.GroupMembersCache
import com.javargasp.tiominutermvi.domain.groups.GroupOperations
import com.javargasp.tiominutermvi.domain.groups.GroupPreferences
import com.javargasp.tiominutermvi.domain.groups.GroupRanking
import com.javargasp.tiominutermvi.domain.groups.GroupReconciler
import com.javargasp.tiominutermvi.domain.groups.MemberProfiles
import com.javargasp.tiominutermvi.domain.groups.MySummarySource
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Groups across clients, phase 2 of scripts/test_web_groups_emulator.sh (the Firestore + Auth
 * emulators on the host, 10.0.2.2, with the real rules): the web created a group with the run's
 * code (web/test/emulator/web_groups_cross_test.dart); this phone's user joins it with Android's
 * real GroupOperations and FirestoreGroupRepository, sees the web member, and creates a group the
 * web then joins (phase 3). Opt-in (`webGroups=true`); its own FirebaseApp, never the real project.
 */
@RunWith(AndroidJUnit4::class)
class WebGroupsInteropEmulatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var app: FirebaseApp
    private val dates = LocalDateProvider({ System.currentTimeMillis() }, { TimeZone.getDefault() })

    @Before
    fun setUp() {
        assumeTrue(
            "Opt-in: run scripts/test_web_groups_emulator.sh",
            InstrumentationRegistry.getArguments().getString("webGroups") == "true"
        )
    }

    @After
    fun tearDown() {
        if (!::app.isInitialized) return
        FirebaseAuth.getInstance(app).signOut()
        app.delete()
    }

    @Test
    fun an_android_user_joins_the_web_group_and_creates_one_the_web_joins() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val run = requireNotNull(arguments.getString("crossRun"))
        val webCode = requireNotNull(arguments.getString("webGroupCode"))
        val androidCode = requireNotNull(arguments.getString("androidGroupCode"))
        val appId = UUID.randomUUID().toString()
        val options = FirebaseOptions.Builder(requireNotNull(FirebaseOptions.fromResource(context)))
            .setProjectId(PROJECT_ID)
            .setApiKey("emulator-key-$appId")
            .build()
        app = FirebaseApp.initializeApp(context, options, "web-groups-$appId")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
        val firestore = FirebaseFirestore.getInstance(app).apply {
            useEmulator(HOST, FIRESTORE_PORT)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        val uid = requireNotNull(
            Tasks.await(auth.createUserWithEmailAndPassword("cross-$run-droid@example.test", "password-123"), 30, TimeUnit.SECONDS).user
        ).uid
        val session = AuthSession.SignedIn(uid, "Droid", null, null)
        val authRepository = object : AuthRepository {
            override val session: Flow<AuthSession> = MutableStateFlow(session)
            override suspend fun signInWithGoogle(idToken: String) = AuthResult.Success
            override suspend fun signOut() = Unit
            override suspend fun deleteAccount(wipeLocalData: Boolean, reauthIdToken: String) = AuthResult.Success
        }
        val repository = FirestoreGroupRepository(firestore)
        val goals = MutableStateFlow(listOf(WeeklyGoal(3, dates.currentWeekStart())))
        val summaries = MySummarySource(EmptyLog, MemoryGoals(goals), dates)
        val log = GroupLog { println("WebGroupsInteropEmulatorTest: $it") }
        val reconciler = GroupReconciler(repository, MemoryPreferences(), { System.currentTimeMillis() }, log)
        val operations = GroupOperations(
            repository, authRepository, summaries, MemberProfiles { "Member" }, { true }, reconciler, null,
            GroupMembersCache({ 0L }), dates, log, newCode = { androidCode }
        )

        // The web's group, by its code: joined with Android's batch
        val invite = operations.findInvite(webCode)
        assertEquals("Web crew $run", invite.groupName)
        val webGroupId = operations.join(invite)
        val members = repository.members(webGroupId)
        assertEquals(2, members.size)
        val web = members.single { it.uid != uid }
        assertEquals("Web Ana", web.profile.displayName)
        assertEquals(dates.currentWeekStart(), web.summary.weekStartLocal)
        assertEquals(2, GroupRanking.rank(members, dates.currentWeekStart(), uid).size)
        assertTrue(operations.myGroups().any { it.group.id == webGroupId && !it.isOwner })

        // A group of Android's for the web to join (phase 3)
        val created = operations.create("Android crew $run")
        assertEquals(androidCode, created.code)
    }

    private object EmptyLog : TrainingLogRepository {
        override fun observeEntries(): Flow<List<TrainingLogEntry>> = MutableStateFlow(emptyList())
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

    private companion object {
        const val PROJECT_ID = "tiominuter-e43f7"
        const val HOST = "10.0.2.2"
        const val AUTH_PORT = 9099
        const val FIRESTORE_PORT = 8080
    }
}
