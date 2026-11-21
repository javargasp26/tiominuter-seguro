package com.javargasp.tiominutermvi.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.javargasp.tiominutermvi.data.local.entity.WeeklyGoalEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** In-memory Room tests for [com.javargasp.tiominutermvi.data.local.dao.WeeklyGoalDao]. */
class WeeklyGoalDaoTest {

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

    @Test
    fun emptyAtFirst() = runTest {
        assertTrue(database.weeklyGoalDao().observeAll().first().isEmpty())
    }

    @Test
    fun goalsAreOrderedByWeek() = runTest {
        val dao = database.weeklyGoalDao()
        dao.upsert(WeeklyGoalEntity("2026-10-19", 4, 3_000))
        dao.upsert(WeeklyGoalEntity("2026-09-28", 2, 1_000))
        dao.upsert(WeeklyGoalEntity("2026-10-05", 3, 2_000))

        assertEquals(
            listOf("2026-09-28", "2026-10-05", "2026-10-19"),
            dao.observeAll().first().map { it.effectiveFromWeek }
        )
    }

    @Test
    fun upsertInTheSameWeekReplacesTheRow() = runTest {
        val dao = database.weeklyGoalDao()
        dao.upsert(WeeklyGoalEntity("2026-10-12", 2, 1_000))
        dao.upsert(WeeklyGoalEntity("2026-10-19", 3, 2_000))

        dao.upsert(WeeklyGoalEntity("2026-10-19", 5, 3_000))

        assertEquals(
            listOf(WeeklyGoalEntity("2026-10-12", 2, 1_000), WeeklyGoalEntity("2026-10-19", 5, 3_000)),
            dao.observeAll().first()
        )
    }
}
