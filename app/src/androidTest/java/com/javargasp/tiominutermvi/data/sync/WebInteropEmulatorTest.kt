package com.javargasp.tiominutermvi.data.sync

import com.javargasp.tiominutermvi.data.daily.DailyProgressRepositoryImpl
import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules
import org.junit.Assert.assertFalse
import android.content.Context
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
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
import com.google.firebase.firestore.Source
import com.javargasp.tiominutermvi.data.content.AssetBundledContent
import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.goal.WeeklyGoalRepositoryImpl
import com.javargasp.tiominutermvi.data.groups.FirestoreFeedRepository
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.data.program.mapper.toPayload
import com.javargasp.tiominutermvi.data.program.mapper.toProfile
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource
import com.javargasp.tiominutermvi.data.program.repository.ProgramRepositoryImpl
import com.javargasp.tiominutermvi.data.program.sync.ProgramSyncStore
import com.javargasp.tiominutermvi.data.remote.AssetGymRemoteDataSource
import com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoFileStore
import com.javargasp.tiominutermvi.domain.auth.LocalUserDataWiper
import com.javargasp.tiominutermvi.domain.goal.WeeklyProgressCalculator
import com.javargasp.tiominutermvi.domain.groups.FeedLimits
import com.javargasp.tiominutermvi.domain.groups.GroupSummaryCalculator
import com.javargasp.tiominutermvi.domain.groups.ThumbnailFitter
import com.javargasp.tiominutermvi.domain.program.model.kind
import com.javargasp.tiominutermvi.domain.sync.SyncEngine
import com.javargasp.tiominutermvi.domain.sync.SyncOutcome
import com.javargasp.tiominutermvi.domain.sync.SyncStateStore
import com.javargasp.tiominutermvi.domain.sync.SyncTime
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.CalendarDates
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutTitleKey
import com.javargasp.tiominutermvi.domain.traininglog.gymRefId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The web as a writer, phase 2 of scripts/test_web_train_emulator.sh (Firestore + Auth emulators on
 * the host, 10.0.2.2, with the real rules): the web wrote training with its real code
 * (web/test/emulator/web_train_cross_test.dart); this phone pulls it into fresh Room databases with
 * the real sync and checks it against Android's own rules — the program against ProgramGenerator,
 * my member summary against GroupSummaryCalculator, the posts' title keys — then pushes its own
 * changes, which the web reads back (phase 3). Opt-in (`webInterop=true`); its own FirebaseApp,
 * never the real project.
 */
@RunWith(AndroidJUnit4::class)
class WebInteropEmulatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var app: FirebaseApp
    private lateinit var firestore: FirebaseFirestore
    private lateinit var uid: String
    private val clock = WallClock { System.currentTimeMillis() }
    private lateinit var appDb: TioMinuterDatabase
    private lateinit var programDb: ProgramDatabase
    private lateinit var root: File

    private lateinit var run: String

    /** null: the main phase (after the web's writes); "after-web-restart": the last step (the web restarted the days). */
    private var crossStep: String? = null

    @Before
    fun setUp() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in: run scripts/test_web_train_emulator.sh", arguments.getString("webInterop") == "true")
        run = requireNotNull(arguments.getString("crossRun")) { "crossRun names the web's test user" }
        crossStep = arguments.getString("crossStep")
        val appId = UUID.randomUUID().toString()
        val options = FirebaseOptions.Builder(requireNotNull(FirebaseOptions.fromResource(context)))
            .setProjectId(PROJECT_ID)
            .setApiKey("emulator-key-$appId")
            .build()
        app = FirebaseApp.initializeApp(context, options, "web-interop-$appId")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
        firestore = FirebaseFirestore.getInstance(app).apply {
            useEmulator(HOST, FIRESTORE_PORT)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        // The user the web created in phase 1
        uid = requireNotNull(
            Tasks.await(auth.signInWithEmailAndPassword("cross-$run-me@example.test", "password-123"), 30, TimeUnit.SECONDS).user
        ).uid
        appDb = Room.inMemoryDatabaseBuilder(context, TioMinuterDatabase::class.java).build()
        programDb = Room.inMemoryDatabaseBuilder(context, ProgramDatabase::class.java).build()
        root = File(context.cacheDir, "web-interop").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        if (!::app.isInitialized) return
        if (::appDb.isInitialized) appDb.close()
        if (::programDb.isInitialized) programDb.close()
        if (::root.isInitialized) root.deleteRecursively()
        FirebaseAuth.getInstance(app).signOut()
        app.delete()
    }

    /**
     * The daily unlock, phone → web (step 2; the web checks it in step 3): a new account of its own
     * (no history) finishes Day 1 here, through the real repository and sync.
     */
    @Test
    fun day_one_finished_on_the_phone_is_pushed_for_the_web() = runBlocking {
        assumeTrue("the main phase only", crossStep == null)
        val auth = FirebaseAuth.getInstance(app)
        auth.signOut()
        val dailyUid = requireNotNull(
            Tasks.await(auth.createUserWithEmailAndPassword("cross-$run-daily@example.test", "password-123"), 30, TimeUnit.SECONDS).user
        ).uid
        val progress = DailyProgressRepositoryImpl(appDb.dailyProgressDao(), clock, RoomChangeTracker(appDb, appDb.syncOutboxDao(), clock))
        assertEquals(DailyProgress(), progress.observeProgress().first())

        progress.onDayFinished(1)
        assertEquals(SyncOutcome.SUCCESS, dailyEngine().sync(dailyUid))

        val doc = Tasks.await(firestore.document("users/$dailyUid/dailyProgress/state").get(Source.SERVER)).data!!
        assertEquals(setOf("lastCompletedDay", "cycle", "updatedAt", "schemaVersion"), doc.keys)
        assertEquals(1L to 0L, doc["lastCompletedDay"] to doc["cycle"])
    }

    /**
     * The daily unlock, web → phone (step 4, after the web restarted the days in step 3): this phone
     * still has the old cycle with Day 1 done, synced before; the pull brings the restart (a newer
     * cycle wins over a further day of an older one), so only Day 1 is unlocked again.
     */
    @Test
    fun a_restart_on_the_web_locks_the_days_again_on_the_phone() = runBlocking {
        assumeTrue("the step after the web's restart", crossStep == "after-web-restart")
        val auth = FirebaseAuth.getInstance(app)
        auth.signOut()
        val dailyUid = requireNotNull(
            Tasks.await(auth.signInWithEmailAndPassword("cross-$run-daily@example.test", "password-123"), 30, TimeUnit.SECONDS).user
        ).uid
        appDb.dailyProgressDao().upsert(DailyProgressEntity(lastCompletedDay = 1, cycle = 0, updatedAt = 1L))
        val progress = DailyProgressRepositoryImpl(appDb.dailyProgressDao(), clock, RoomChangeTracker(appDb, appDb.syncOutboxDao(), clock))

        assertEquals(SyncOutcome.SUCCESS, dailyEngine(owner = dailyUid).sync(dailyUid))

        val pulled = progress.observeProgress().first()
        assertEquals(DailyProgress(lastCompletedDay = 0, cycle = 1), pulled)
        assertTrue(DailyUnlockRules.isUnlocked(1, pulled))
        assertFalse(DailyUnlockRules.isUnlocked(2, pulled))
        assertEquals(1, DailyUnlockRules.nextAvailableDay(pulled, 30))
        assertEquals("nothing to push back", 0, appDb.syncOutboxDao().count())
    }

    /** The app database's sync only ([owner]: the account whose data this phone already holds). */
    private fun dailyEngine(owner: String? = null): SyncEngine {
        val programStore = ProgramSyncStore(programDb, clock)
        return SyncEngine(
            stores = listOf(programStore, AppSyncStore(appDb, programStore, clock)),
            remote = FirestoreRemoteUserData(firestore),
            state = MapSyncStateStore().apply { setLastSyncedUid(owner) },
            wiper = LocalUserDataWiper { error("no account switch in this test") },
            clock = clock,
            network = { true },
            scheduler = { }
        )
    }

    @Test
    fun what_the_web_writes_reaches_a_fresh_phone_and_what_the_phone_pushes_reaches_the_web() = runBlocking {
        assumeTrue("the main phase only", crossStep == null)
        val dates = LocalDateProvider(clock) { TimeZone.getDefault() }
        val log = TrainingLogRepositoryImpl(
            appDb.trainingLogDao(), clock, dates, TrainingPhotoFileStore(File(root, "files"), File(root, "cache")),
            changes = RoomChangeTracker(appDb, appDb.syncOutboxDao(), clock)
        )
        val goals = WeeklyGoalRepositoryImpl(appDb.weeklyGoalDao(), clock, dates, RoomChangeTracker(appDb, appDb.syncOutboxDao(), clock))
        val programs = ProgramRepositoryImpl(
            programDb, AssetProgramRemoteDataSource(bundledContent()), clock, log, { it.kind.name }, log,
            changes = RoomChangeTracker(programDb, programDb.syncOutboxDao(), clock)
        )
        val programStore = ProgramSyncStore(programDb, clock)
        val engine = SyncEngine(
            stores = listOf(programStore, AppSyncStore(appDb, programStore, clock)),
            remote = FirestoreRemoteUserData(firestore),
            state = MapSyncStateStore(),
            wiper = LocalUserDataWiper { error("no account switch in this test") },
            clock = clock,
            network = { true },
            scheduler = { },
            pageSize = 3
        )

        // ---------- The web's writes, pulled by the real sync ----------
        assertEquals(SyncOutcome.SUCCESS, engine.sync(uid))
        val entries = appDb.appSyncDao().entryUuids().mapNotNull { appDb.appSyncDao().getEntry(it) }
        val daily = entries.single { it.source == "DAILY" }
        assertEquals("daily:3", daily.refId)
        assertEquals("Day 3", daily.title)
        assertEquals(900, daily.durationSeconds)
        val activity = entries.single { it.source == "ACTIVITY" }
        assertEquals("RUNNING", activity.activityType)
        assertEquals("MODERATE", activity.intensity)
        assertEquals(1800, activity.durationSeconds)
        assertEquals("Easy run", activity.note)
        assertTrue("the deleted entry never arrives", entries.none { it.refId == "daily:1" })

        // The web's gym routine: what GymSessionTracker.completeSession records here (the routine's
        // refId, its English catalog name, the session's first → last set)
        val gym = entries.single { it.source == "GYM" }
        val pushDay = AssetGymRemoteDataSource(bundledContent()).fetchGymRoutines().single { it.routineId == 65 }
        assertEquals(gymRefId(65), gym.refId)
        assertEquals(pushDay.routineName, gym.title)
        assertEquals(1800, gym.durationSeconds)
        assertNull(gym.activityType)
        assertEquals(daily.dateLocal, gym.dateLocal)

        // The program: the web's Dart generator gave exactly what Android's generator gives
        val program = programDb.programSyncDao().getPrograms().single()
        val expected = programs.previewProgram(program.toProfile())
        val stored = programDb.programDao().getWorkouts(program.id)
        assertEquals(expected.weeks.sumOf { it.workouts.size }, stored.size)
        expected.weeks.forEach { week ->
            week.workouts.forEachIndexed { day, workout ->
                val row = stored.single { it.week == week.weekNumber && it.dayIndex == day }
                assertEquals("week ${week.weekNumber} day $day", workout.toPayload(), row.payload)
                assertEquals(week.isDeload, row.isDeload)
            }
        }
        val completion = programDb.programSyncDao().getCompletions().single()
        assertEquals(1 to 0, completion.week to completion.dayIndex)
        // The PROGRAM entry names this phone's own program id
        assertEquals("program:${program.id}:1:0", entries.single { it.source == "PROGRAM" }.refId)

        // This week's goal (the web's Monday: the date of its workout)
        val webToday = daily.dateLocal
        val webMonday = CalendarDates.mondayOf(webToday)
        assertEquals(4, appDb.appSyncDao().getGoal(webMonday)?.workoutsPerWeek)

        // My member summary: what GroupSummaryCalculator gives over the same data
        val groupId = Tasks.await(firestore.collection("users/$uid/groups").get(Source.SERVER)).documents.single().id
        val member = Tasks.await(firestore.document("groups/$groupId/members/$uid").get(Source.SERVER)).data!!
        val summary = GroupSummaryCalculator.summary(webToday, log.observeEntries().first(), goals.observeGoals().first())!!
        assertEquals(summary.weekStartLocal, member["weekStartLocal"])
        assertEquals(summary.workoutsThisWeek.toLong(), member["workoutsThisWeek"])
        assertEquals(summary.weeklyGoal.toLong(), member["weeklyGoal"])
        assertEquals(summary.streakWeeks.toLong(), member["streakWeeks"])
        assertEquals(summary.lastTrainedLocal, member["lastTrainedLocal"])
        assertEquals(
            summary.daysThisWeek.mapKeys { it.key.toString() }.mapValues { (_, sources) -> sources.map { it.name } },
            member["daysThisWeek"]
        )
        assertEquals("Cross Me", member["displayName"])

        // The posts: the daily one by its title key, the activity by its type; the short ones none
        val dailyPost = Tasks.await(firestore.document("groups/$groupId/posts/${daily.uuid}").get(Source.SERVER))
        assertEquals("daily/3", dailyPost.getString("titleKey"))
        assertEquals(uid, dailyPost.getString("authorUid"))
        assertEquals("DAILY", dailyPost.getString("source"))
        val activityPost = Tasks.await(firestore.document("groups/$groupId/posts/${activity.uuid}").get(Source.SERVER))
        assertEquals("RUNNING", activityPost.getString("activityType"))
        assertNull(activityPost.getString("titleKey"))
        val gymPost = Tasks.await(firestore.document("groups/$groupId/posts/${gym.uuid}").get(Source.SERVER))
        assertEquals(WorkoutTitleKey.Gym(65), WorkoutTitleKey.parse(gymPost.getString("titleKey"), TrainingSource.GYM))
        assertEquals(pushDay.routineName, gymPost.getString("title"))
        assertNotNull(WeeklyProgressCalculator.goalForWeek(goals.observeGoals().first(), webMonday))

        // The web's photos reach the feed as thumbnails, read with Android's own feed code: a JPEG
        // this phone decodes, ≤ the rules' cap and ≤ 400 px, without Exif (no location)
        val feed = FirestoreFeedRepository(firestore).observeWeek(groupId, webMonday, 20).first().posts
        for (entry in listOf(daily, activity)) {
            val thumb = requireNotNull(feed.single { it.id == entry.uuid }.thumb) { "the post of ${entry.source} has a thumbnail" }
            assertTrue(thumb.size <= FeedLimits.THUMB_MAX_BYTES)
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(thumb, 0, thumb.size)) { "a JPEG Android decodes" }
            assertTrue(maxOf(bitmap.width, bitmap.height) <= ThumbnailFitter.MAX_SIDE)
            val exif = ExifInterface(ByteArrayInputStream(thumb))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        }

        // ---------- This phone pushes: its own gym workout, a new goal, the activity deleted ----------
        log.record(TrainingSource.GYM, "gym:64", "Pull Day", 2_700)
        goals.setGoal(5)
        log.deleteEntry(activity.id)
        assertEquals(SyncOutcome.SUCCESS, engine.sync(uid))
        assertEquals(0, engine.pendingNow())
    }

    private class MapSyncStateStore : SyncStateStore {
        private var owner: String? = null
        private val marks = mutableMapOf<String, SyncTime>()
        private val ok = mutableMapOf<String, Long>()
        private var erased: String? = null
        override fun lastSyncedUid() = owner
        override fun setLastSyncedUid(uid: String?) {
            owner = uid
        }
        override fun watermark(uid: String, key: String) = marks["$uid|$key"]
        override fun setWatermark(uid: String, key: String, time: SyncTime) {
            marks["$uid|$key"] = time
        }
        override fun lastSuccessAt(uid: String) = ok[uid]
        override fun setLastSuccessAt(uid: String, millis: Long) {
            ok[uid] = millis
        }
        override fun forget(uid: String) {
            marks.keys.removeAll { it.startsWith("$uid|") }
            ok.remove(uid)
        }
        override fun erasedAccount() = erased
        override fun setErasedAccount(uid: String?) {
            erased = uid
        }
    }

    private fun bundledContent(): ContentSource = object : ContentSource {
        private val bundled = AssetBundledContent(context.assets)
        override suspend fun readText(path: String): String =
            checkNotNull(bundled.open(path)) { "No asset $path" }.use { it.bufferedReader().readText() }
    }

    private companion object {
        const val PROJECT_ID = "tiominuter-e43f7"
        const val HOST = "10.0.2.2"
        const val FIRESTORE_PORT = 8080
        const val AUTH_PORT = 9099
    }
}
