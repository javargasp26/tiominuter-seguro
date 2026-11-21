package com.javargasp.tiominutermvi.data.auth

import com.javargasp.tiominutermvi.testutil.FakeFeedShareDao
import com.javargasp.tiominutermvi.testutil.FakeDailyProgressDao
import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import com.javargasp.tiominutermvi.data.local.entity.FeedShareEntity
import com.javargasp.tiominutermvi.data.content.ContentStore
import com.javargasp.tiominutermvi.data.local.dao.GymProgressDao
import com.javargasp.tiominutermvi.data.local.dao.GymSessionDao
import com.javargasp.tiominutermvi.data.local.dao.TrainingLogDao
import com.javargasp.tiominutermvi.data.local.dao.WeeklyGoalDao
import com.javargasp.tiominutermvi.data.local.entity.GymExerciseProgressEntity
import com.javargasp.tiominutermvi.data.local.entity.GymSessionEntity
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.data.local.entity.WeeklyGoalEntity
import com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoFileStore
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoRepositoryImpl
import com.javargasp.tiominutermvi.domain.program.repository.ProgramDataCleaner
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoProcessor
import com.javargasp.tiominutermvi.testutil.CONTENT_DIR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.TimeZone
import org.junit.Assert.assertNull
import com.javargasp.tiominutermvi.data.local.entity.DailySessionEntity
import com.javargasp.tiominutermvi.data.local.dao.DailySessionDao
import io.mockk.mockk
import io.mockk.coVerify
import com.javargasp.tiominutermvi.data.sync.SyncOutboxDao

/**
 * The local wipe with fake DAOs, the real training photo repository and file store on a temporary
 * `filesDir`, and a downloaded-content cache next to the photos: user data goes, content stays.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLocalUserDataWiperTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val filesDir by lazy { folder.newFolder("files") }
    private val cacheDir by lazy { folder.newFolder("cache") }

    private val trainingLogDao = FakeTrainingLogDao()
    private val weeklyGoalDao = FakeWeeklyGoalDao()
    private val gymProgressDao = FakeGymProgressDao()
    private val gymSessionDao = FakeGymSessionDao()
    private val dailySessionDao = FakeDailySessionDao()
    private val syncOutboxDao = mockk<SyncOutboxDao>(relaxed = true)
    private val feedShareDao = FakeFeedShareDao()
    private val dailyProgressDao = FakeDailyProgressDao()
    private var programsCleared = false
    private val transactions = mutableListOf<String>()

    private fun wiper(): DefaultLocalUserDataWiper {
        val photoFiles = TrainingPhotoFileStore(filesDir, cacheDir)
        val clock = { 1_000L }
        val trainingLog = TrainingLogRepositoryImpl(
            trainingLogDao,
            clock,
            LocalDateProvider(clock) { TimeZone.getTimeZone("UTC") },
            photoFiles
        )
        val photos = TrainingPhotoRepositoryImpl(trainingLog, NoProcessor, photoFiles, clock, dispatcher)
        return DefaultLocalUserDataWiper(
            inTransaction = { block ->
                transactions += "begin"
                block()
                transactions += "end"
            },
            trainingLogDao = trainingLogDao,
            weeklyGoalDao = weeklyGoalDao,
            gymProgressDao = gymProgressDao,
            gymSessionDao = gymSessionDao,
            dailySessionDao = dailySessionDao,
            syncOutboxDao = syncOutboxDao,
            feedShareDao = feedShareDao,
            dailyProgressDao = dailyProgressDao,
            programs = ProgramDataCleaner { programsCleared = true },
            photos = photos
        )
    }

    private fun write(relative: String, text: String = relative): File =
        File(filesDir, relative).apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    @Test
    fun `user data is removed, the content cache and bundled content are untouched`() = runTest(dispatcher) {
        trainingLogDao.rows.value = listOf(entry(1, photoPath = "training_photos/uuid-1-100.jpg"), entry(2, photoPath = null))
        weeklyGoalDao.rows.value = listOf(WeeklyGoalEntity("2026-10-19", 4, 1_000L))
        gymProgressDao.rows = 3
        gymSessionDao.rows = mutableListOf(GymSessionEntity(64, 1_000L, 2_000L))
        dailySessionDao.row = DailySessionEntity(3, 1_000L, 600, 2_000L)
        feedShareDao.put(FeedShareEntity("uuid-1", "me", true, false, null, "", "", null, 1_000L, 1, true))
        dailyProgressDao.row.value = DailyProgressEntity(lastCompletedDay = 7, cycle = 2, updatedAt = 1_000L)
        val photo = write("training_photos/uuid-1-100.jpg")
        val thumbnail = write("training_photos/uuid-1-100_thumb.jpg")
        val orphan = write("training_photos/uuid-9-50.jpg")
        val activeManifest = write("${ContentStore.DIRECTORY}/active.json", """{"contentVersion":"v2"}""")
        val cachedObject = write("${ContentStore.DIRECTORY}/objects/${"a".repeat(64)}", "cached routines")
        val bundled = File(CONTENT_DIR, "exercises.json")
        val bundledBefore = bundled.readBytes()

        wiper().wipe()

        assertTrue(trainingLogDao.rows.value.isEmpty())
        assertTrue(weeklyGoalDao.rows.value.isEmpty())
        assertEquals(0, gymProgressDao.rows)
        assertTrue(gymSessionDao.rows.isEmpty())
        assertNull(dailySessionDao.row)
        coVerify { syncOutboxDao.deleteAll() }
        assertTrue("the feed queue goes with the account's data", feedShareDao.rows.value.isEmpty())
        assertNull("the daily unlock progress goes too", dailyProgressDao.row.value)
        assertTrue(programsCleared)
        assertEquals(listOf("begin", "end"), transactions)
        assertFalse(photo.exists())
        assertFalse(thumbnail.exists())
        assertFalse(orphan.exists())

        assertEquals("""{"contentVersion":"v2"}""", activeManifest.readText())
        assertEquals("cached routines", cachedObject.readText())
        assertTrue(bundled.readBytes().contentEquals(bundledBefore))
    }

    @Test
    fun `wiping with no user data does nothing harmful`() = runTest(dispatcher) {
        val cachedObject = write("content/objects/${"b".repeat(64)}", "cached")

        wiper().wipe()

        assertTrue(trainingLogDao.rows.value.isEmpty())
        assertTrue(programsCleared)
        assertEquals("cached", cachedObject.readText())
    }

    private fun entry(id: Long, photoPath: String?) = TrainingLogEntity(
        id = id,
        uuid = "uuid-$id",
        dateLocal = "2026-10-20",
        completedAt = 1_000L,
        source = "DAILY",
        refId = "daily:1",
        title = "Day 1",
        durationSeconds = 600,
        activityType = null,
        intensity = null,
        note = null,
        photoPath = photoPath,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    private object NoProcessor : TrainingPhotoProcessor {
        override suspend fun process(sourceUri: String, photoFile: File, thumbnailFile: File) =
            error("not used by the wipe")
    }

    private class FakeTrainingLogDao : TrainingLogDao {
        val rows = MutableStateFlow<List<TrainingLogEntity>>(emptyList())
        override suspend fun insert(entry: TrainingLogEntity): Long = error("not used")
        override fun observeAll(): Flow<List<TrainingLogEntity>> = rows
        override fun observeBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntity>> =
            rows.map { list -> list.filter { it.dateLocal in fromDate..toDate } }
        override suspend fun getById(id: Long): TrainingLogEntity? = rows.value.firstOrNull { it.id == id }
        override suspend fun getByUuid(uuid: String): TrainingLogEntity? = rows.value.firstOrNull { it.uuid == uuid }
        override suspend fun update(entry: TrainingLogEntity): Int = error("not used")
        override suspend fun delete(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }
        override suspend fun deleteAll() {
            rows.value = emptyList()
        }
        override suspend fun updatePhotoPath(id: Long, photoPath: String?, updatedAt: Long): Int = error("not used")
        override suspend fun getPhotoPaths(): List<String> = rows.value.mapNotNull { it.photoPath }
    }

    private class FakeWeeklyGoalDao : WeeklyGoalDao {
        val rows = MutableStateFlow<List<WeeklyGoalEntity>>(emptyList())
        override suspend fun upsert(goal: WeeklyGoalEntity) = error("not used")
        override fun observeAll(): Flow<List<WeeklyGoalEntity>> = rows
        override suspend fun deleteAll() {
            rows.value = emptyList()
        }
    }

    private class FakeDailySessionDao : DailySessionDao {
        var row: DailySessionEntity? = null
        override suspend fun get(): DailySessionEntity? = row
        override suspend fun upsert(session: DailySessionEntity) = error("not used")
        override suspend fun deleteAll() {
            row = null
        }
    }

    private class FakeGymSessionDao : GymSessionDao {
        var rows = mutableListOf<GymSessionEntity>()
        override suspend fun get(routineId: Int): GymSessionEntity? = rows.firstOrNull { it.routineId == routineId }
        override suspend fun getAll(): List<GymSessionEntity> = rows
        override suspend fun upsert(session: GymSessionEntity) = error("not used")
        override suspend fun delete(routineId: Int) = error("not used")
        override suspend fun deleteAll() {
            rows = mutableListOf()
        }
    }

    private class FakeGymProgressDao : GymProgressDao {
        var rows = 0
        override fun observeProgress(routineId: Int): Flow<List<GymExerciseProgressEntity>> = emptyFlow()
        override suspend fun getCompletionTimes(routineId: Int): List<Long> = emptyList()
        override suspend fun markCompleted(entity: GymExerciseProgressEntity) = error("not used")
        override suspend fun clearRoutine(routineId: Int) = error("not used")
        override suspend fun deleteAll() {
            rows = 0
        }
    }
}
