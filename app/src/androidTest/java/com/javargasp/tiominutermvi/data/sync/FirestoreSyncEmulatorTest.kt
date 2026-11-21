package com.javargasp.tiominutermvi.data.sync

import android.content.Context
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
import com.javargasp.tiominutermvi.data.content.AssetBundledContent
import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.goal.WeeklyGoalRepositoryImpl
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource
import com.javargasp.tiominutermvi.data.program.repository.ProgramRepositoryImpl
import com.javargasp.tiominutermvi.data.program.sync.ProgramSyncStore
import com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoFileStore
import com.javargasp.tiominutermvi.domain.auth.LocalUserDataWiper
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.kind
import com.javargasp.tiominutermvi.domain.sync.SyncEngine
import com.javargasp.tiominutermvi.domain.sync.SyncOutcome
import com.javargasp.tiominutermvi.domain.sync.SyncStateStore
import com.javargasp.tiominutermvi.domain.sync.SyncTime
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The sync end to end against the Firebase EMULATORS (Firestore + Auth on the host, 10.0.2.2),
 * with the real rules of firebase/firestore.rules: device A writes through the real repositories
 * and pushes; device B (fresh Room databases) pulls and must hold the same data. Opt-in: it runs
 * only with the instrumentation argument `syncEmulator=true` (scripts/test_sync_emulator.sh), and
 * uses its own FirebaseApp, never the real project.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreSyncEmulatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var app: FirebaseApp
    private lateinit var firestore: FirebaseFirestore
    private lateinit var uid: String
    private val devices = mutableListOf<Device>()

    @Before
    fun setUp() {
        assumeTrue(
            "Opt-in: run scripts/test_sync_emulator.sh",
            InstrumentationRegistry.getArguments().getString("syncEmulator") == "true"
        )
        // firebase-auth caches its emulator config per API KEY, process-wide, holding the first app
        // that used it: a key shared with an app deleted by an earlier test fails with "FirebaseApp
        // was deleted". So every app gets its own key and name (the Auth emulator accepts any key).
        val appId = UUID.randomUUID().toString()
        val options = FirebaseOptions.Builder(requireNotNull(FirebaseOptions.fromResource(context)))
            .setProjectId(PROJECT_ID)
            .setApiKey("emulator-key-$appId")
            .build()
        app = FirebaseApp.initializeApp(context, options, "sync-emulator-$appId")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
        firestore = FirebaseFirestore.getInstance(app).apply {
            useEmulator(HOST, FIRESTORE_PORT)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        uid = requireNotNull(Tasks.await(auth.signInAnonymously(), 30, TimeUnit.SECONDS).user).uid
    }

    @After
    fun tearDown() {
        if (!::app.isInitialized) return
        devices.forEach { it.close() }
        FirebaseAuth.getInstance(app).signOut()
        app.delete()
    }

    @Test
    fun push_from_one_device_and_pull_on_another_gives_the_same_data_under_the_real_rules() = runBlocking {
        val a = Device("a")
        val b = Device("b")

        // Device A: a goal, a logged activity, a gym workout, a program with a completed strength
        // workout (a PROGRAM entry) and a deleted entry (a tombstone)
        a.goals.setGoal(4)
        a.log.addActivityEntry(
            ActivityEntryInput(ActivityType.RUNNING, "", 30, Intensity.MODERATE, a.now - 3_600_000L, "Easy run")
        )
        a.log.record(TrainingSource.GYM, "gym:64", "Pull Day", 2_700)
        val programId = a.programs.createProgram(PROFILE)
        val slots = (a.programs.observeWorkout(programId, 1, 0).first()!!.workout as ProgramWorkout.Strength).slots
        slots.forEach {
            a.programs.onStrengthSetCompleted(programId, 1, 0)
            a.programs.markSlotCompleted(programId, 1, 0, it.position)
        }
        val gone = a.log.record(TrainingSource.DAILY, "daily:1", "Day 1", 900)
        a.log.deleteEntry(gone)

        assertEquals(SyncOutcome.SUCCESS, a.engine.sync(uid))
        assertEquals(0, a.engine.pendingNow())

        // Device B, empty: everything arrives
        assertEquals(SyncOutcome.SUCCESS, b.engine.sync(uid))

        assertEquals(a.entries(), b.entries())
        assertEquals(a.goalRows(), b.goalRows())
        assertEquals(a.programRows(), b.programRows())
        assertEquals(a.workoutRows(), b.workoutRows())
        assertEquals(a.completionRows(), b.completionRows())
        assertEquals(4, b.entries().size + 1) // activity, gym, program; the deleted one is not there
        // The PROGRAM entry names B's own local program id
        val bProgram = b.programDb.programSyncDao().getPrograms().single()
        val programEntry = b.appDb.appSyncDao().entryUuids()
            .mapNotNull { b.appDb.appSyncDao().getEntry(it) }
            .single { it.source == "PROGRAM" }
        assertEquals("program:${bProgram.id}:1:0", programEntry.refId)
        assertEquals(0, b.engine.pendingNow())

        // B changes this week's goal and deletes the gym entry; A receives both
        b.goals.setGoal(6)
        val gymOnB = b.appDb.appSyncDao().entryUuids().mapNotNull { b.appDb.appSyncDao().getEntry(it) }.single { it.source == "GYM" }
        b.log.deleteEntry(gymOnB.id)
        assertEquals(SyncOutcome.SUCCESS, b.engine.sync(uid))
        assertEquals(SyncOutcome.SUCCESS, a.engine.sync(uid))

        assertEquals(listOf(6), a.goalRows().map { it.second })
        assertTrue(a.entries().none { it.source == "GYM" })
        assertEquals(a.entries(), b.entries())

        // Delete account: every server document goes
        a.engine.eraseServerData(uid)
        val c = Device("c")
        assertEquals(SyncOutcome.SUCCESS, c.engine.sync(uid))
        assertTrue(c.entries().isEmpty())
        assertTrue(c.programRows().isEmpty())
    }

    /** One phone: its own Room databases, repositories, stores and sync engine. */
    private inner class Device(name: String) {
        val now = System.currentTimeMillis()
        private val clock = WallClock { System.currentTimeMillis() }
        val appDb = Room.inMemoryDatabaseBuilder(context, TioMinuterDatabase::class.java).build()
        val programDb = Room.inMemoryDatabaseBuilder(context, ProgramDatabase::class.java).build()
        private val root = File(context.cacheDir, "sync-emulator-$name").apply { deleteRecursively(); mkdirs() }
        private val dates = LocalDateProvider(clock) { TimeZone.getTimeZone("UTC") }
        val log = TrainingLogRepositoryImpl(
            appDb.trainingLogDao(), clock, dates, TrainingPhotoFileStore(File(root, "files"), File(root, "cache")),
            changes = RoomChangeTracker(appDb, appDb.syncOutboxDao(), clock)
        )
        val goals = WeeklyGoalRepositoryImpl(appDb.weeklyGoalDao(), clock, dates, RoomChangeTracker(appDb, appDb.syncOutboxDao(), clock))
        val programs = ProgramRepositoryImpl(
            programDb, AssetProgramRemoteDataSource(bundledContent()), clock, log, { it.kind.name }, log,
            changes = RoomChangeTracker(programDb, programDb.syncOutboxDao(), clock)
        )
        private val programStore = ProgramSyncStore(programDb, clock)
        val engine = SyncEngine(
            stores = listOf(programStore, AppSyncStore(appDb, programStore, clock)),
            remote = FirestoreRemoteUserData(firestore),
            state = MapSyncStateStore(),
            wiper = LocalUserDataWiper { error("no account switch in this test") },
            clock = clock,
            network = { true },
            scheduler = { },
            // Small pages: the pull paginates
            pageSize = 2
        )

        init {
            devices += this
        }

        /** Everything synced of each entry (not the device id, photo or local updatedAt), refIds by uuid. */
        suspend fun entries() = appDb.appSyncDao().entryUuids().mapNotNull { appDb.appSyncDao().getEntry(it) }
            .map {
                val refId = it.refId?.let { ref -> com.javargasp.tiominutermvi.domain.sync.ProgramRefIds.toRemote(ref, programStore) }
                it.copy(id = 0, photoPath = null, updatedAt = 0, refId = refId)
            }
            .sortedBy { it.uuid }

        suspend fun goalRows() = appDb.appSyncDao().goalWeeks().sorted().map { it to appDb.appSyncDao().getGoal(it)!!.workoutsPerWeek }

        suspend fun programRows() = programDb.programSyncDao().getPrograms().map { it.copy(id = 0) }.sortedBy { it.uuid }

        suspend fun workoutRows() = programDb.programSyncDao().getPrograms().flatMap { program ->
            programDb.programDao().getWorkouts(program.id).map { Triple(program.uuid, "${it.week}-${it.dayIndex}", it.copy(programId = 0)) }
        }.sortedBy { it.first + it.second }

        suspend fun completionRows() = programDb.programSyncDao().getCompletions().map { completion ->
            programStore.uuidOf(completion.programId) to completion.copy(programId = 0)
        }.sortedBy { it.second.week * 10 + it.second.dayIndex }

        fun close() {
            appDb.close()
            programDb.close()
            root.deleteRecursively()
        }
    }

    private class MapSyncStateStore : SyncStateStore {
        private var owner: String? = null
        private val marks = mutableMapOf<String, SyncTime>()
        private val ok = mutableMapOf<String, Long>()
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
        private var erased: String? = null
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

        val PROFILE = ProgramProfile(
            age = 30, weightKg = 70.5, heightCm = 175, level = ProgramLevel.BEGINNER,
            goal = ProgramGoal.GAIN_MUSCLE, location = ProgramLocation.GYM, weeks = 4, daysPerWeek = 3
        )
    }
}
