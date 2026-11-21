package com.javargasp.tiominutermvi.data.traininglog

import com.javargasp.tiominutermvi.data.local.dao.TrainingLogDao
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryError
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity
import com.javargasp.tiominutermvi.domain.traininglog.InvalidActivityEntryException
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoFiles
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.TimeZone

/** Recording and reading with a fake DAO (Room itself is covered by TrainingLogDaoTest). */
class TrainingLogRepositoryImplTest {

    private val photoFiles = FakePhotoFiles()

    // 2026-10-19T23:30:00Z: 20 October in Madrid
    private var now = 1_792_452_600_000L
    private val dao = FakeTrainingLogDao()
    private val repository = TrainingLogRepositoryImpl(
        dao = dao,
        clock = { now },
        localDateProvider = LocalDateProvider({ now }, { TimeZone.getTimeZone("Europe/Madrid") }),
        photoFiles = photoFiles,
        newUuid = { "uuid-${dao.rows.value.size + 1}" }
    )

    @Test
    fun `record stores a new entry completed now with its local date and a uuid`() = runTest {
        repository.record(TrainingSource.GYM, "gym:64", "Pull Day", 1_800)

        assertEquals(
            TrainingLogEntity(
                id = 1,
                uuid = "uuid-1",
                dateLocal = "2026-10-20",
                completedAt = now,
                source = "GYM",
                refId = "gym:64",
                title = "Pull Day",
                durationSeconds = 1_800,
                activityType = null,
                intensity = null,
                note = null,
                photoPath = null,
                createdAt = now,
                updatedAt = now
            ),
            dao.rows.value.single()
        )
    }

    @Test
    fun `record returns the new entry's id`() = runTest {
        val first = repository.record(TrainingSource.GYM, "gym:64", "Pull Day", 1_800)
        val second = repository.record(TrainingSource.DAILY, "daily:1", "Day 1", null)

        assertEquals(listOf(1L, 2L), listOf(first, second))
    }

    @Test
    fun `setPhotoPath stores the photo, updates updatedAt and returns the previous one`() = runTest {
        val id = repository.record(TrainingSource.GYM, "gym:64", "Pull Day", 1_800)
        val recordedAt = now
        now += 60_000

        assertNull(repository.setPhotoPath(id, "training_photos/uuid-1-1.jpg"))
        assertEquals("training_photos/uuid-1-1.jpg", repository.setPhotoPath(id, "training_photos/uuid-1-2.jpg"))

        val row = dao.rows.value.single()
        assertEquals("training_photos/uuid-1-2.jpg", row.photoPath)
        assertEquals(recordedAt, row.createdAt)
        assertEquals(now, row.updatedAt)
        assertEquals(listOf("training_photos/uuid-1-2.jpg"), repository.getPhotoPaths())

        assertEquals("training_photos/uuid-1-2.jpg", repository.setPhotoPath(id, null))
        assertTrue(repository.getPhotoPaths().isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun `setPhotoPath on a missing entry throws`() = runTest {
        repository.setPhotoPath(99, "training_photos/x-1.jpg")
    }

    @Test
    fun `deleting an entry deletes its photo files, an entry without photo deletes none`() = runTest {
        val withPhoto = repository.record(TrainingSource.GYM, "gym:64", "Pull Day", 1_800)
        val withoutPhoto = repository.record(TrainingSource.DAILY, "daily:1", "Day 1", null)
        repository.setPhotoPath(withPhoto, "training_photos/uuid-1-1.jpg")

        repository.deleteEntry(withoutPhoto)
        assertTrue(photoFiles.deletedPhotos.isEmpty())

        repository.deleteEntry(withPhoto)
        assertEquals(listOf("training_photos/uuid-1-1.jpg"), photoFiles.deletedPhotos)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `editing an activity keeps its photo`() = runTest {
        val id = repository.addActivityEntry(activity())
        repository.setPhotoPath(id, "training_photos/uuid-1-1.jpg")

        repository.updateActivityEntry(id, activity(note = "changed"))

        assertEquals("training_photos/uuid-1-1.jpg", repository.getEntry(id)!!.photoPath)
    }

    @Test
    fun `entries are mapped and entries with an unknown source are skipped`() = runTest {
        repository.record(TrainingSource.DAILY, "daily:3", "Day 3", null)
        dao.rows.value = dao.rows.value + dao.rows.value.single().copy(id = 2, uuid = "x", source = "YOGA")

        val entries = repository.observeEntries().first()

        assertEquals(listOf("Day 3"), entries.map { it.title })
        assertEquals(TrainingSource.DAILY, entries.single().source)
    }

    // ------------------------------------------------------------------
    // Logged activities (now: Tuesday 20 October, 01:30 in Madrid)
    // ------------------------------------------------------------------

    private fun activity(
        completedAt: Long = now - 60 * 60 * 1000L,
        type: ActivityType? = ActivityType.RUNNING,
        name: String = "",
        minutes: Int? = 45,
        intensity: Intensity? = Intensity.MODERATE,
        note: String = "  Easy loop  "
    ) = ActivityEntryInput(type, name, minutes, intensity, completedAt, note)

    @Test
    fun `addActivityEntry stores an ACTIVITY entry with codes, seconds and the local date of completedAt`() = runTest {
        // Monday 19 October, 20:00 in Madrid (18:00Z)
        val completedAt = 1_792_432_800_000L
        val id = repository.addActivityEntry(activity(completedAt = completedAt))

        val stored = dao.rows.value.single()
        assertEquals(id, stored.id)
        assertEquals("ACTIVITY", stored.source)
        assertEquals("RUNNING", stored.activityType)
        assertEquals("MODERATE", stored.intensity)
        assertEquals(45 * 60, stored.durationSeconds)
        assertEquals("", stored.title)
        assertEquals("Easy loop", stored.note)
        assertEquals(completedAt, stored.completedAt)
        assertEquals("2026-10-19", stored.dateLocal)
        assertEquals(now, stored.createdAt)
        assertEquals(now, stored.updatedAt)
        assertEquals(null, stored.refId)
        assertEquals(null, stored.photoPath)
    }

    @Test
    fun `OTHER keeps its trimmed custom name as title and a blank note is null`() = runTest {
        repository.addActivityEntry(activity(type = ActivityType.OTHER, name = "  Climbing ", note = "   "))

        val stored = dao.rows.value.single()
        assertEquals("OTHER", stored.activityType)
        assertEquals("Climbing", stored.title)
        assertEquals(null, stored.note)
    }

    @Test
    fun `an invalid activity is rejected and nothing is stored`() = runTest {
        try {
            repository.addActivityEntry(activity(minutes = 0, completedAt = now + 60_000))
            fail("invalid activity accepted")
        } catch (e: InvalidActivityEntryException) {
            assertEquals(setOf(ActivityEntryError.DURATION_OUT_OF_RANGE, ActivityEntryError.DATE_IN_FUTURE), e.errors)
        }
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `updateActivityEntry keeps uuid and createdAt and sets updatedAt`() = runTest {
        val id = repository.addActivityEntry(activity())
        val created = dao.rows.value.single()
        now += 5 * 60 * 1000L

        repository.updateActivityEntry(id, activity(type = ActivityType.YOGA, minutes = 30, intensity = Intensity.EASY, note = ""))

        val updated = dao.rows.value.single()
        assertEquals(created.uuid, updated.uuid)
        assertEquals(created.createdAt, updated.createdAt)
        assertEquals(now, updated.updatedAt)
        assertEquals("YOGA", updated.activityType)
        assertEquals("EASY", updated.intensity)
        assertEquals(30 * 60, updated.durationSeconds)
        assertEquals(null, updated.note)
        assertEquals(TrainingSource.ACTIVITY, repository.getEntry(id)!!.source)
    }

    @Test
    fun `editing keeps the entry's own day allowed after it leaves the window`() = runTest {
        val completedAt = now - 2 * DAY
        val id = repository.addActivityEntry(activity(completedAt = completedAt))
        now += 6 * DAY // the entry is now 8 days old

        repository.updateActivityEntry(id, activity(completedAt = completedAt, minutes = 50))
        assertEquals(50 * 60, dao.rows.value.single().durationSeconds)

        // A day before it is still rejected
        try {
            repository.updateActivityEntry(id, activity(completedAt = completedAt - DAY))
            fail("too early accepted")
        } catch (e: InvalidActivityEntryException) {
            assertEquals(setOf(ActivityEntryError.DATE_TOO_EARLY), e.errors)
        }
    }

    @Test
    fun `only ACTIVITY entries can be updated`() = runTest {
        repository.record(TrainingSource.GYM, "gym:1", "Pull Day", 1_800)

        try {
            repository.updateActivityEntry(1, activity())
            fail("automatic entry updated")
        } catch (e: IllegalStateException) {
            // expected
        }
        try {
            repository.updateActivityEntry(99, activity())
            fail("missing entry updated")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals("GYM", dao.rows.value.single().source)
    }

    @Test
    fun `getEntry returns the entry or null`() = runTest {
        val id = repository.addActivityEntry(activity())

        assertEquals(ActivityType.RUNNING, repository.getEntry(id)!!.activity)
        assertEquals(Intensity.MODERATE, repository.getEntry(id)!!.intensityLevel)
        assertEquals(null, repository.getEntry(42))
    }

    @Test
    fun `delete removes the entry`() = runTest {
        repository.record(TrainingSource.DAILY, "daily:1", "Day 1", 60)
        repository.record(TrainingSource.DAILY, "daily:2", "Day 2", 60)

        repository.deleteEntry(1)

        assertEquals(listOf(2L), dao.rows.value.map { it.id })
    }

    private class FakeTrainingLogDao : TrainingLogDao {
        val rows = MutableStateFlow<List<TrainingLogEntity>>(emptyList())

        override suspend fun insert(entry: TrainingLogEntity): Long {
            val id = rows.value.size + 1L
            rows.value = rows.value + entry.copy(id = id)
            return id
        }

        override fun observeAll(): Flow<List<TrainingLogEntity>> =
            rows.map { list -> list.sortedWith(compareByDescending<TrainingLogEntity> { it.completedAt }.thenByDescending { it.id }) }

        override fun observeBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntity>> =
            observeAll().map { list -> list.filter { it.dateLocal in fromDate..toDate } }

        override suspend fun getById(id: Long): TrainingLogEntity? = rows.value.firstOrNull { it.id == id }
        override suspend fun getByUuid(uuid: String): TrainingLogEntity? = rows.value.firstOrNull { it.uuid == uuid }

        override suspend fun update(entry: TrainingLogEntity): Int {
            if (rows.value.none { it.id == entry.id }) return 0
            rows.value = rows.value.map { if (it.id == entry.id) entry else it }
            return 1
        }

        override suspend fun delete(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }

        override suspend fun deleteAll() {
            rows.value = emptyList()
        }

        override suspend fun updatePhotoPath(id: Long, photoPath: String?, updatedAt: Long): Int {
            val before = rows.value
            rows.value = before.map { if (it.id == id) it.copy(photoPath = photoPath, updatedAt = updatedAt) else it }
            return before.count { it.id == id }
        }

        override suspend fun getPhotoPaths(): List<String> = rows.value.mapNotNull { it.photoPath }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }

    /** Records the photos deleted through [TrainingPhotoFiles.deletePhoto]; nothing else is used. */
    private class FakePhotoFiles : TrainingPhotoFiles {
        val deletedPhotos = mutableListOf<String>()
        override fun fileOf(relativePath: String) = File(relativePath)
        override fun newStagedPhoto() = throw UnsupportedOperationException()
        override fun captureFile() = throw UnsupportedOperationException()
        override fun deleteCapture() = Unit
        override fun deletePhoto(photoPath: String) {
            deletedPhotos += photoPath
        }
        override fun storedFiles(): List<String> = emptyList()
        override fun deleteStoredFile(relativePath: String) = Unit
        override fun deleteStaleTemporaryFiles(olderThanMillis: Long) = Unit
    }
}
