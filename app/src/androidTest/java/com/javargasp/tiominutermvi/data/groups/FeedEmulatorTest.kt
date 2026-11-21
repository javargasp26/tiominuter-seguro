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
import com.javargasp.tiominutermvi.domain.groups.FeedCleaner
import com.javargasp.tiominutermvi.domain.groups.FeedModeration
import com.javargasp.tiominutermvi.domain.groups.FeedPreferences
import com.javargasp.tiominutermvi.domain.groups.GroupAccountCleaner
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.GroupLog
import com.javargasp.tiominutermvi.domain.groups.GroupMembersCache
import com.javargasp.tiominutermvi.domain.groups.GroupOperations
import com.javargasp.tiominutermvi.domain.groups.GroupPreferences
import com.javargasp.tiominutermvi.domain.groups.GroupReconciler
import com.javargasp.tiominutermvi.domain.groups.MemberProfile
import com.javargasp.tiominutermvi.domain.groups.MemberProfiles
import com.javargasp.tiominutermvi.domain.groups.MySummarySource
import com.javargasp.tiominutermvi.domain.groups.NewPost
import com.javargasp.tiominutermvi.domain.groups.Reaction
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.GymRoutineNames
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.ProgramTitleCode
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalog
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The group feed end to end against the Firebase EMULATORS (Firestore + Auth on the host, 10.0.2.2)
 * with the real firebase/firestore.rules and the app's Firestore repositories: two email/password
 * test users in a group post with a thumbnail, read the week, react / change / remove, the owner
 * deletes a post, an expired post is cleaned up by a member, a member leaves with posts, and the
 * delete-account cleanup removes my posts everywhere; a post's titleKey travels, the reader resolves it
 * (and falls back to the title without one), a bad or foreign key is refused and it never changes.
 * Never the real project.
 * Opt-in: only with the instrumentation argument `feedEmulator=true` (scripts/test_feed_emulator.sh).
 */
@RunWith(AndroidJUnit4::class)
class FeedEmulatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val users = mutableListOf<User>()
    private val dates = LocalDateProvider({ System.currentTimeMillis() }, { TimeZone.getDefault() })
    private val today = dates.today()
    private val week = dates.mondayOf(today)

    @Before
    fun setUp() {
        assumeTrue(
            "Opt-in: run scripts/test_feed_emulator.sh",
            InstrumentationRegistry.getArguments().getString("feedEmulator") == "true"
        )
    }

    @After
    fun tearDown() {
        users.forEach { it.close() }
    }

    /** One person on their own phone: a FirebaseApp with its own Auth and Firestore (both emulators). */
    private inner class User(val name: String) {
        val app: FirebaseApp
        val uid: String
        val groups: FirestoreGroupRepository
        val feed: FirestoreFeedRepository
        val operations: GroupOperations
        val reconciler: GroupReconciler
        val moderation: FeedModeration
        val cleaner: FeedCleaner

        init {
            // firebase-auth caches its emulator config per API key, process-wide: one key per app
            val appId = UUID.randomUUID().toString()
            val options = FirebaseOptions.Builder(requireNotNull(FirebaseOptions.fromResource(context)))
                .setProjectId(PROJECT_ID)
                .setApiKey("emulator-key-$appId")
                .build()
            app = FirebaseApp.initializeApp(context, options, "feed-$name-$appId")
            val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
            val firestore = FirebaseFirestore.getInstance(app).apply {
                useEmulator(HOST, FIRESTORE_PORT)
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
            val email = "$name-$appId@example.test"
            uid = requireNotNull(Tasks.await(auth.createUserWithEmailAndPassword(email, "password-123"), 30, TimeUnit.SECONDS).user).uid
            val session = AuthSession.SignedIn(uid, name, null, null)
            groups = FirestoreGroupRepository(firestore)
            feed = FirestoreFeedRepository(firestore)
            val authRepository = object : AuthRepository {
                override val session: Flow<AuthSession> = MutableStateFlow(session)
                override suspend fun signInWithGoogle(idToken: String) = AuthResult.Success
                override suspend fun signOut() = Unit
                override suspend fun deleteAccount(wipeLocalData: Boolean, reauthIdToken: String) = AuthResult.Success
            }
            val summaries = MySummarySource(NoEntries, OneGoal(dates.mondayOf(dates.previousWeek(today))), dates)
            val log = GroupLog { println("FeedEmulatorTest: $it") }
            reconciler = GroupReconciler(groups, MemoryPreferences(), { System.currentTimeMillis() }, log)
            moderation = FeedModeration(feed)
            cleaner = FeedCleaner(feed, groups, MemoryPreferences(), { System.currentTimeMillis() }, log)
            operations = GroupOperations(
                groups, authRepository, summaries, MemberProfiles { "Member" }, { true }, reconciler, null,
                GroupMembersCache({ 0L }), dates, log, moderation
            )
            users += this
        }

        fun post(
            groupId: String,
            postId: String = UUID.randomUUID().toString(),
            thumb: ByteArray? = null,
            source: TrainingSource = TrainingSource.GYM,
            title: String = "Pull Day",
            titleKey: String? = null
        ) = NewPost(
            postId = postId,
            authorUid = uid,
            profile = MemberProfile(name, null),
            source = source,
            title = title,
            activityType = null,
            durationSeconds = 2700,
            dateLocal = today,
            weekStartLocal = week,
            thumb = thumb,
            titleKey = titleKey
        ).also { runBlocking { feed.createPost(groupId, it) } }

        /** A raw update, as a modified client could send it. */
        fun rawUpdate(groupId: String, postId: String, fields: Map<String, Any?>) {
            Tasks.await(FirebaseFirestore.getInstance(app).document("groups/$groupId/posts/$postId").update(fields), 30, TimeUnit.SECONDS)
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

    @Test
    fun post_read_react_moderate_clean_up_and_leave_through_the_real_rules() = runBlocking {
        val ana = User("ana")
        val bob = User("bob")
        val created = ana.operations.create("Feed crew")
        bob.operations.join(bob.operations.findInvite(created.code))
        val g = created.id

        // Bob posts with a 60 KB thumbnail; Ana reads it in this week's feed
        val post = bob.post(g, thumb = ByteArray(60 * 1024) { 1 })
        val page = ana.feed.observeWeek(g, week, 20).first()
        val read = page.posts.single()
        assertEquals(post.postId, read.id)
        assertEquals(60 * 1024, read.thumb?.size)
        assertEquals("bob", read.authorName)
        // The same entry can't be posted twice; a thumbnail over the cap is refused
        expectError(GroupError.PERMISSION_DENIED) { bob.feed.createPost(g, post) }
        expectError(GroupError.PERMISSION_DENIED) { bob.post(g, thumb = ByteArray(65_537)) }

        // Reactions: set, change, remove; never someone else's key
        ana.feed.setReaction(g, post.postId, ana.uid, Reaction.FIRE)
        bob.feed.setReaction(g, post.postId, bob.uid, Reaction.STRONG)
        assertEquals(mapOf(ana.uid to Reaction.FIRE, bob.uid to Reaction.STRONG), bob.feed.observeWeek(g, week, 20).first().posts.single().reactions)
        ana.feed.setReaction(g, post.postId, ana.uid, Reaction.CLAP)
        ana.feed.setReaction(g, post.postId, ana.uid, null)
        expectError(GroupError.PERMISSION_DENIED) { ana.feed.setReaction(g, post.postId, bob.uid, null) }
        assertEquals(mapOf(bob.uid to Reaction.STRONG), ana.feed.observeWeek(g, week, 20).first().posts.single().reactions)

        // The author changes only the thumbnail; Ana can't
        bob.feed.setThumbnail(g, post.postId, null)
        expectError(GroupError.PERMISSION_DENIED) { ana.feed.setThumbnail(g, post.postId, ByteArray(10)) }

        // The owner deletes any post; a member can't delete someone else's
        val anasPost = ana.post(g)
        expectError(GroupError.PERMISSION_DENIED) { bob.feed.deletePost(g, anasPost.postId) }
        ana.feed.deletePost(g, post.postId)
        assertEquals(listOf(anasPost.postId), bob.feed.observeWeek(g, week, 20).first().posts.map { it.id })

        // An expired post (server time 9 days ago) is cleaned up by a plain member; a fresh one stays
        val expiredId = UUID.randomUUID().toString()
        seedPostCreatedDaysAgo(g, expiredId, ana.uid, days = 9)
        assertEquals(1, bob.cleaner.cleanUpExpired(g))
        assertTrue(expiredId !in bob.feed.postIds(g, 100))
        assertTrue(anasPost.postId in bob.feed.postIds(g, 100))

        // Bob posts twice, then leaves: his posts go first
        bob.post(g)
        bob.post(g)
        bob.operations.leave(requireNotNull(bob.groups.group(g)))
        assertEquals(emptyList<String>(), ana.feed.postIdsBy(g, bob.uid))
        expectError(GroupError.PERMISSION_DENIED) { bob.post(g) }
        // A missing post is a no-op delete for a member
        ana.feed.deletePost(g, UUID.randomUUID().toString())
    }

    @Test
    fun title_keys_travel_and_each_reader_resolves_them_but_never_changes_them() = runBlocking {
        val ana = User("ana")
        val bob = User("bob")
        val created = ana.operations.create("Names crew")
        bob.operations.join(bob.operations.findInvite(created.code))
        val g = created.id

        // Bob's app is in Spanish: titles in Spanish, keys locale-free; one post of an older app has none
        val gym = bob.post(g, title = "Día de tirón", titleKey = "gym/64")
        val daily = bob.post(g, source = TrainingSource.DAILY, title = "Día 5", titleKey = "daily/5")
        val program = bob.post(g, source = TrainingSource.PROGRAM, title = "Tren superior A", titleKey = "program/UPPER_A")
        val old = bob.post(g, title = "Pull Day")
        // A key of another source, a bad format or over 80 characters is refused
        expectError(GroupError.PERMISSION_DENIED) { bob.post(g, source = TrainingSource.DAILY, titleKey = "gym/64") }
        expectError(GroupError.PERMISSION_DENIED) { bob.post(g, titleKey = "gym/064") }
        expectError(GroupError.PERMISSION_DENIED) { bob.post(g, source = TrainingSource.PROGRAM, titleKey = "program/${"A".repeat(73)}") }

        // Ana reads them and names them from her own catalogs (English here)
        val catalog = WorkoutNameCatalog(gymRoutines = mapOf(64 to GymRoutineNames("Pull Day", "Día de tirón")))
        val posts = ana.feed.observeWeek(g, week, 20).first().posts.associateBy { it.id }
        fun nameOf(id: String) = posts.getValue(id).let { WorkoutNameResolver.nameOfPost(it.titleKey, it.source, it.activityType, it.title, catalog) }
        assertEquals(4, posts.size)
        assertEquals("gym/64", posts.getValue(gym.postId).titleKey)
        assertEquals(WorkoutName.Gym("Pull Day", "Día de tirón"), nameOf(gym.postId))
        assertEquals(WorkoutName.Daily(5), nameOf(daily.postId))
        assertEquals(WorkoutName.Program(ProgramTitleCode.UPPER_A), nameOf(program.postId))
        assertEquals("the post of an older app shows its title", WorkoutName.Stored("Pull Day"), nameOf(old.postId))

        // The key never changes: not by the author, not with a thumbnail, not by a member reacting
        expectError(GroupError.PERMISSION_DENIED) { runCatchingFirestore { bob.rawUpdate(g, gym.postId, mapOf("titleKey" to "gym/65")) } }
        expectError(GroupError.PERMISSION_DENIED) { runCatchingFirestore { bob.rawUpdate(g, old.postId, mapOf("titleKey" to "gym/64")) } }
        expectError(GroupError.PERMISSION_DENIED) {
            runCatchingFirestore { ana.rawUpdate(g, gym.postId, mapOf("reactions.${ana.uid}" to "fire", "titleKey" to "gym/65")) }
        }
        bob.feed.setThumbnail(g, gym.postId, ByteArray(100) { 2 })
        ana.feed.setReaction(g, gym.postId, ana.uid, Reaction.CLAP)
        assertEquals("gym/64", ana.feed.observeWeek(g, week, 20).first().posts.single { it.id == gym.postId }.titleKey)
    }

    /** A raw write's refusal as the repositories report it. */
    private fun runCatchingFirestore(block: () -> Unit) {
        try {
            block()
        } catch (e: java.util.concurrent.ExecutionException) {
            val cause = e.cause as? com.google.firebase.firestore.FirebaseFirestoreException ?: throw e
            if (cause.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw GroupException(GroupError.PERMISSION_DENIED)
            }
            throw e
        }
    }

    @Test
    fun remove_member_and_delete_group_delete_their_posts() = runBlocking {
        val ana = User("ana2")
        val bob = User("bob2")
        val cara = User("cara2")
        val created = ana.operations.create("Owners")
        bob.operations.join(bob.operations.findInvite(created.code))
        cara.operations.join(cara.operations.findInvite(created.code))
        bob.post(created.id)
        bob.post(created.id)
        cara.post(created.id)

        ana.operations.removeMember(requireNotNull(ana.groups.group(created.id)), bob.uid)
        assertEquals(emptyList<String>(), ana.feed.postIdsBy(created.id, bob.uid))
        assertEquals(1, ana.feed.postIdsBy(created.id, cara.uid).size)

        ana.operations.delete(requireNotNull(ana.groups.group(created.id)))
        assertNull(cara.groups.findInvite(created.code))
    }

    @Test
    fun delete_account_removes_my_posts_in_every_group_before_leaving_them() = runBlocking {
        val dani = User("dani")
        val eli = User("eli")
        val shared = dani.operations.create("Shared")
        eli.operations.join(eli.operations.findInvite(shared.code))
        val solo = dani.operations.create("Solo")
        val theirs = eli.operations.create("Theirs")
        dani.operations.join(dani.operations.findInvite(theirs.code))
        listOf(shared.id, solo.id, theirs.id).forEach { dani.post(it) }
        eli.post(shared.id)
        eli.post(theirs.id)

        GroupAccountCleaner(dani.groups, dani.reconciler, GroupMembersCache({ 0L }), dani.moderation).leaveAllGroups(dani.uid)

        assertEquals(emptyList<String>(), eli.feed.postIdsBy(shared.id, dani.uid))
        assertEquals(emptyList<String>(), eli.feed.postIdsBy(theirs.id, dani.uid))
        assertEquals(1, eli.feed.postIdsBy(shared.id, eli.uid).size)
        assertEquals(eli.uid, requireNotNull(eli.groups.group(shared.id)).ownerUid)
        assertNull(eli.groups.findInvite(solo.code))
    }

    /** A post written with the rules off (the emulator's `owner` token), created [days] ago on the server's clock. */
    private fun seedPostCreatedDaysAgo(groupId: String, postId: String, authorUid: String, days: Int) {
        val createdAt = Date(System.currentTimeMillis() - days * 24L * 60 * 60 * 1000)
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(createdAt)
        val body = """
            {"fields": {
              "authorUid": {"stringValue": "$authorUid"},
              "authorName": {"stringValue": "Old"},
              "source": {"stringValue": "GYM"},
              "title": {"stringValue": ""},
              "dateLocal": {"stringValue": "$week"},
              "weekStartLocal": {"stringValue": "$week"},
              "createdAt": {"timestampValue": "$time"},
              "reactions": {"mapValue": {}},
              "schemaVersion": {"integerValue": "1"}
            }}
        """.trimIndent()
        val url = URL("http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT_ID/databases/(default)/documents/groups/$groupId/posts?documentId=$postId")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
            assertEquals(200, connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }

    private object NoEntries : TrainingLogRepository {
        override fun observeEntries(): Flow<List<TrainingLogEntry>> = MutableStateFlow(emptyList())
        override fun observeEntriesBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntry>> = emptyFlow()
        override suspend fun getEntry(id: Long): TrainingLogEntry? = null
        override suspend fun addActivityEntry(input: ActivityEntryInput): Long = 0
        override suspend fun updateActivityEntry(id: Long, input: ActivityEntryInput) = Unit
        override suspend fun deleteEntry(id: Long) = Unit
        override suspend fun setPhotoPath(id: Long, photoPath: String?): String? = null
        override suspend fun getPhotoPaths(): List<String> = emptyList()
    }

    private class OneGoal(week: String) : WeeklyGoalRepository {
        private val goals = MutableStateFlow(listOf(WeeklyGoal(3, week)))
        override fun observeGoals(): Flow<List<WeeklyGoal>> = goals
        override suspend fun setGoal(workoutsPerWeek: Int) = Unit
    }

    private class MemoryPreferences : GroupPreferences, FeedPreferences {
        private val values = mutableMapOf<String, Any?>()
        override fun publishedSummaryHash(uid: String) = values["hash$uid"] as String?
        override fun setPublishedSummaryHash(uid: String, hash: String?) { values["hash$uid"] = hash }
        override fun isSummaryDirty(uid: String) = values["dirty$uid"] as Boolean? ?: false
        override fun setSummaryDirty(uid: String, dirty: Boolean) { values["dirty$uid"] = dirty }
        override fun lastReconciledAt(uid: String) = values["rec$uid"] as Long?
        override fun setLastReconciledAt(uid: String, millis: Long) { values["rec$uid"] = millis }
        override fun trainedTodayDismissedOn() = values["dismissed"] as String?
        override fun setTrainedTodayDismissedOn(date: String?) { values["dismissed"] = date }
        override fun isFeedIntroSeen(uid: String) = true
        override fun setFeedIntroSeen(uid: String) = Unit
        override fun lastFeedCleanupAt(uid: String) = values["clean$uid"] as Long?
        override fun setLastFeedCleanupAt(uid: String, millis: Long) { values["clean$uid"] = millis }
        override fun hiddenPosts(uid: String): Set<String> = emptySet()
        override fun hidePost(uid: String, key: String, nowMillis: Long) = Unit
        override fun knownGroupCount(uid: String): Int? = null
        override fun setKnownGroupCount(uid: String, count: Int) = Unit
        override fun detailTab(groupId: String): Int? = null
        override fun setDetailTab(groupId: String, tab: Int) = Unit
    }

    private companion object {
        const val PROJECT_ID = "tiominuter-e43f7"
        const val HOST = "10.0.2.2"
        const val AUTH_PORT = 9099
        const val FIRESTORE_PORT = 8080
    }
}
