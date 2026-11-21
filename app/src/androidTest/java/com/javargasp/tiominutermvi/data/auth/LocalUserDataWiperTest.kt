package com.javargasp.tiominutermvi.data.auth

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javargasp.tiominutermvi.data.content.AssetBundledContent
import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.local.entity.ExerciseEntity
import com.javargasp.tiominutermvi.data.local.entity.GymExerciseProgressEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.GymSessionEntity
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.data.local.entity.WeeklyGoalEntity
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramEntity
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource
import com.javargasp.tiominutermvi.data.program.remote.ProgramRemoteDataSource
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramExerciseDto
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramTemplatesDto
import com.javargasp.tiominutermvi.data.program.repository.ProgramRepositoryImpl
import com.javargasp.tiominutermvi.data.remote.AssetDailyRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.AssetGymRemoteDataSource
import com.javargasp.tiominutermvi.data.repository.GymRoutineRepositoryImpl
import com.javargasp.tiominutermvi.data.repository.WorkoutRepositoryImpl
import com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoFileStore
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoRepositoryImpl
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder

/**
 * The local wipe on real Room databases (in memory) and real photo files: user tables and photos
 * are emptied; the catalog tables and the downloaded content folder stay. The second test runs the
 * whole thing in one process with the real repositories, as after a deletion without a restart.
 */
@RunWith(AndroidJUnit4::class)
class LocalUserDataWiperTest {

    private lateinit var database: TioMinuterDatabase
    private lateinit var programDatabase: ProgramDatabase
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TioMinuterDatabase::class.java).build()
        programDatabase = Room.inMemoryDatabaseBuilder(context, ProgramDatabase::class.java).build()
        root = File(context.cacheDir, "wiper-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        programDatabase.close()
        root.deleteRecursively()
    }

    @Test
    fun wipe_empties_user_tables_and_photos_and_keeps_content() = runTest {
        val filesDir = File(root, "files")
        val cacheDir = File(root, "cache")
        val photoFiles = TrainingPhotoFileStore(filesDir, cacheDir)
        val clock = { 1_000L }
        val trainingLog = TrainingLogRepositoryImpl(database.trainingLogDao(), clock, LocalDateProvider(clock), photoFiles)
        val photos = TrainingPhotoRepositoryImpl(trainingLog, NoProcessor, photoFiles, clock)
        val programs = ProgramRepositoryImpl(programDatabase, NoRemote, clock, NoRecorder, { "" }, NoSessionRecorder)
        val wiper = DefaultLocalUserDataWiper(
            inTransaction = { block -> database.withTransaction { block() } },
            trainingLogDao = database.trainingLogDao(),
            weeklyGoalDao = database.weeklyGoalDao(),
            gymProgressDao = database.gymProgressDao(),
            gymSessionDao = database.gymSessionDao(),
            dailySessionDao = database.dailySessionDao(),
            syncOutboxDao = database.syncOutboxDao(),
            feedShareDao = database.feedShareDao(),
            dailyProgressDao = database.dailyProgressDao(),
            programs = programs,
            photos = photos
        )

        // Content (catalog) and user data
        database.exerciseDao().upsertAll(listOf(ExerciseEntity(2, "Squat", "2_squat.gif", "Sentadilla")))
        database.gymRoutineDao().replaceCatalog(listOf(GymRoutineEntity(64, "Pull Day")), emptyMap())
        database.gymProgressDao().markCompleted(GymExerciseProgressEntity(routineId = 64, position = 0, completedAt = 1L))
        database.gymSessionDao().upsert(GymSessionEntity(routineId = 64, sessionStartedAt = 1L, lastSetAt = 2L))
        database.weeklyGoalDao().upsert(WeeklyGoalEntity("2026-10-19", 4, 1L))
        database.trainingLogDao().insert(entry(photoPath = "training_photos/uuid-1-100.jpg"))
        programDatabase.programDao().insertProgram(
            ProgramEntity(goal = "GAIN_MUSCLE", level = "BEGINNER", location = "GYM", weeks = 4, daysPerWeek = 2,
                age = 30, weightKg = 70.0, heightCm = 175, createdAt = 1L)
        )
        val photo = File(filesDir, "training_photos/uuid-1-100.jpg").apply { parentFile?.mkdirs(); writeText("jpg") }
        val thumbnail = File(filesDir, "training_photos/uuid-1-100_thumb.jpg").apply { writeText("jpg") }
        val cached = File(filesDir, "content/objects/${"a".repeat(64)}").apply { parentFile?.mkdirs(); writeText("cached") }

        wiper.wipe()

        assertTrue(database.trainingLogDao().observeAll().first().isEmpty())
        assertTrue(database.weeklyGoalDao().observeAll().first().isEmpty())
        assertTrue(database.gymProgressDao().getCompletionTimes(64).isEmpty())
        assertTrue(database.gymSessionDao().getAll().isEmpty())
        assertTrue(programDatabase.programDao().observeProgramSummaries().first().isEmpty())
        assertFalse(photo.exists())
        assertFalse(thumbnail.exists())

        assertEquals(listOf(2), database.exerciseDao().observeAll().first().map { it.exerciseId })
        assertEquals(listOf(64), database.gymRoutineDao().observeRoutinesWithExercises().first().map { it.routine.routineId })
        assertEquals("cached", cached.readText())
    }

    @Test
    fun after_the_wipe_the_same_repositories_still_serve_content_and_create_programs() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val content = bundledContent(context)
        val filesDir = File(root, "files")
        val photoFiles = TrainingPhotoFileStore(filesDir, File(root, "cache"))
        val clock = { 1_000L }
        val trainingLog = TrainingLogRepositoryImpl(database.trainingLogDao(), clock, LocalDateProvider(clock), photoFiles)
        val photos = TrainingPhotoRepositoryImpl(trainingLog, NoProcessor, photoFiles, clock)
        // One instance of each for the whole test, like the Koin singles of one process
        val daily = WorkoutRepositoryImpl(database, AssetDailyRemoteDataSource(content))
        val gym = GymRoutineRepositoryImpl(database, AssetGymRemoteDataSource(content))
        val programs = ProgramRepositoryImpl(programDatabase, AssetProgramRemoteDataSource(content), clock, trainingLog, { "" }, trainingLog)
        val wiper = DefaultLocalUserDataWiper(
            inTransaction = { block -> database.withTransaction { block() } },
            trainingLogDao = database.trainingLogDao(),
            weeklyGoalDao = database.weeklyGoalDao(),
            gymProgressDao = database.gymProgressDao(),
            gymSessionDao = database.gymSessionDao(),
            dailySessionDao = database.dailySessionDao(),
            syncOutboxDao = database.syncOutboxDao(),
            feedShareDao = database.feedShareDao(),
            dailyProgressDao = database.dailyProgressDao(),
            programs = programs,
            photos = photos
        )

        // Content: the daily catalog refreshes on its first read (once per process), gym on refresh(),
        // the program catalog and templates on first use (cached in memory, never in programs.db)
        val dailyBefore = daily.getRoutines()
        assertTrue(gym.refresh().isSuccess)
        val gymBefore = gym.observeGymRoutines().first()
        val programCatalogBefore = programs.getExercises()
        assertTrue(dailyBefore.isNotEmpty() && gymBefore.isNotEmpty() && programCatalogBefore.isNotEmpty())

        // User data
        val programId = programs.createProgram(profile)
        database.gymProgressDao().markCompleted(
            GymExerciseProgressEntity(routineId = gymBefore.first().routineId, position = 0, completedAt = 1L)
        )
        database.weeklyGoalDao().upsert(WeeklyGoalEntity("2026-10-19", 4, 1L))
        trainingLog.record(TrainingSource.DAILY, "daily:1", "Day 1", 600)

        wiper.wipe()

        assertTrue(database.trainingLogDao().observeAll().first().isEmpty())
        assertTrue(database.weeklyGoalDao().observeAll().first().isEmpty())
        assertTrue(database.gymProgressDao().getCompletionTimes(gymBefore.first().routineId).isEmpty())
        assertTrue(programs.observePrograms().first().isEmpty())
        assertNull(programs.observeProgram(programId).first())

        // Same instances, no restart: every catalog is still there
        assertEquals(dailyBefore.map { it.routineId }, daily.getRoutines().map { it.routineId })
        assertTrue(daily.getExercises().isNotEmpty())
        assertEquals(gymBefore.map { it.routineId }, gym.observeGymRoutines().first().map { it.routineId })
        assertEquals(programCatalogBefore.keys, programs.getExercises().keys)
        assertTrue(programs.previewProgram(profile).weeks.isNotEmpty())

        // And a new program can be created
        val newId = programs.createProgram(profile)
        assertEquals(listOf(newId), programs.observePrograms().first().map { it.id })
        assertTrue(programs.observeProgram(newId).first()!!.weeks.isNotEmpty())
    }

    private val profile = ProgramProfile(
        age = 30, weightKg = 70.0, heightCm = 175, level = ProgramLevel.BEGINNER,
        goal = ProgramGoal.GAIN_MUSCLE, location = ProgramLocation.HOME, weeks = 4, daysPerWeek = 3
    )

    /** The bundled content only (the APK assets), as the app reads it with no download. */
    private fun bundledContent(context: Context): ContentSource = object : ContentSource {
        private val bundled = AssetBundledContent(context.assets)
        override suspend fun readText(path: String): String =
            checkNotNull(bundled.open(path)) { "No asset $path" }.use { it.bufferedReader().readText() }
    }

    private fun entry(photoPath: String?) = TrainingLogEntity(
        uuid = "uuid-1", dateLocal = "2026-10-20", completedAt = 1_000L, source = "DAILY", refId = "daily:1",
        title = "Day 1", durationSeconds = 600, activityType = null, intensity = null, note = null,
        photoPath = photoPath, createdAt = 1_000L, updatedAt = 1_000L
    )

    private object NoProcessor : TrainingPhotoProcessor {
        override suspend fun process(sourceUri: String, photoFile: File, thumbnailFile: File) = error("not used")
    }

    private object NoRemote : ProgramRemoteDataSource {
        override suspend fun fetchExercises(): List<ProgramExerciseDto> = error("not used")
        override suspend fun fetchTemplates(): ProgramTemplatesDto = error("not used")
    }

    private val NoSessionRecorder = SessionEntryRecorder { _, _, _, _, _, _ -> error("not used") }

    private object NoRecorder : TrainingLogRecorder {
        override suspend fun record(source: TrainingSource, refId: String?, title: String, durationSeconds: Int?): Long =
            error("not used")
    }
}
