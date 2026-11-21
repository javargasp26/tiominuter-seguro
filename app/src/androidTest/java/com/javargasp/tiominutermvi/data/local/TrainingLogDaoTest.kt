package com.javargasp.tiominutermvi.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** In-memory Room tests for [com.javargasp.tiominutermvi.data.local.dao.TrainingLogDao]. */
class TrainingLogDaoTest {

    private lateinit var database: TioMinuterDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TioMinuterDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entry(uuid: String, date: String, completedAt: Long, title: String = uuid) = TrainingLogEntity(
        uuid = uuid,
        dateLocal = date,
        completedAt = completedAt,
        source = "DAILY",
        refId = "daily:1",
        title = title,
        durationSeconds = 600,
        activityType = null,
        intensity = null,
        note = null,
        photoPath = null,
        createdAt = completedAt,
        updatedAt = completedAt
    )

    @Test
    fun insertReturnsTheIdAndKeepsEveryField() = runTest {
        val dao = database.trainingLogDao()
        val stored = entry("a", "2026-10-19", 1_000).copy(
            refId = null, durationSeconds = null, activityType = "run", intensity = "low", note = "n", photoPath = "p"
        )

        val id = dao.insert(stored)

        assertEquals(listOf(stored.copy(id = id)), dao.observeAll().first())
    }

    @Test
    fun observeAllIsNewestFirst() = runTest {
        val dao = database.trainingLogDao()
        dao.insert(entry("old", "2026-10-17", 1_000))
        dao.insert(entry("new", "2026-10-19", 3_000))
        dao.insert(entry("mid", "2026-10-18", 2_000))
        dao.insert(entry("tie", "2026-10-19", 3_000)) // same instant: newer id first

        assertEquals(listOf("tie", "new", "mid", "old"), dao.observeAll().first().map { it.uuid })
    }

    @Test
    fun observeBetweenIsInclusiveOnLocalDates() = runTest {
        val dao = database.trainingLogDao()
        dao.insert(entry("before", "2026-10-11", 1_000))
        dao.insert(entry("first", "2026-10-12", 2_000))
        dao.insert(entry("inside", "2026-10-15", 3_000))
        dao.insert(entry("last", "2026-10-18", 4_000))
        dao.insert(entry("after", "2026-10-19", 5_000))

        val week = dao.observeBetween("2026-10-12", "2026-10-18").first()

        assertEquals(listOf("last", "inside", "first"), week.map { it.uuid })
    }

    @Test
    fun getByIdReturnsTheEntryOrNull() = runTest {
        val dao = database.trainingLogDao()
        val id = dao.insert(entry("a", "2026-10-19", 1_000))

        assertEquals("a", dao.getById(id)?.uuid)
        assertEquals(null, dao.getById(id + 1))
    }

    @Test
    fun updateReplacesTheRowWithTheSameId() = runTest {
        val dao = database.trainingLogDao()
        val id = dao.insert(entry("a", "2026-10-19", 1_000).copy(source = "ACTIVITY", activityType = "RUNNING", intensity = "EASY"))
        val stored = dao.getById(id)!!

        val changed = dao.update(stored.copy(dateLocal = "2026-10-18", activityType = "YOGA", note = "n", updatedAt = 5_000))

        assertEquals(1, changed)
        val updated = dao.getById(id)!!
        assertEquals("a", updated.uuid)
        assertEquals(stored.createdAt, updated.createdAt)
        assertEquals("2026-10-18", updated.dateLocal)
        assertEquals("YOGA", updated.activityType)
        assertEquals("n", updated.note)
        assertEquals(5_000L, updated.updatedAt)
        assertEquals(0, dao.update(stored.copy(id = id + 10)))
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun deleteRemovesOnlyThatEntry() = runTest {
        val dao = database.trainingLogDao()
        val keep = dao.insert(entry("keep", "2026-10-19", 1_000))
        val remove = dao.insert(entry("remove", "2026-10-19", 2_000))

        dao.delete(remove)
        dao.delete(999) // unknown id: no-op

        assertEquals(listOf(keep), dao.observeAll().first().map { it.id })
    }

    @Test
    fun aDuplicateUuidIsRejected() = runTest {
        val dao = database.trainingLogDao()
        dao.insert(entry("same", "2026-10-19", 1_000))

        try {
            dao.insert(entry("same", "2026-10-20", 2_000, title = "other"))
            fail("duplicate uuid accepted")
        } catch (e: SQLiteConstraintException) {
            // expected
        }
        val rows = dao.observeAll().first()
        assertEquals(1, rows.size)
        assertTrue(rows.single().title == "same")
    }
}
