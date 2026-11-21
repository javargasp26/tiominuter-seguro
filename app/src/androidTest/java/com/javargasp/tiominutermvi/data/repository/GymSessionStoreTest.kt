package com.javargasp.tiominutermvi.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.local.entity.ExerciseEntity
import com.javargasp.tiominutermvi.data.local.entity.GymExerciseProgressEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineExerciseEntity
import com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoFileStore
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionTracker
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.TimeZone

/**
 * The gym session on real Room (in memory) with the real training log: closing writes one entry
 * with the real duration and the session's own date, and resets the progress, in one transaction.
 */
@RunWith(AndroidJUnit4::class)
class GymSessionStoreTest {

    private lateinit var database: TioMinuterDatabase
    private lateinit var trainingLog: TrainingLogRepositoryImpl
    private var now = 0L
    private val clock = WallClock { now }

    // 2026-10-19 23:30 UTC: a session started late in the evening
    private val start = 1_792_452_600_000L
    private val minute = 60_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TioMinuterDatabase::class.java).build()
        val photoFiles = TrainingPhotoFileStore(File(context.cacheDir, "session-files"), File(context.cacheDir, "session-cache"))
        trainingLog = TrainingLogRepositoryImpl(
            database.trainingLogDao(), clock, LocalDateProvider(clock) { TimeZone.getTimeZone("UTC") }, photoFiles
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedRoutine() {
        database.exerciseDao().upsertAll(listOf(ExerciseEntity(18, "Pull-ups", "18_pull_ups.gif", "Dominadas")))
        database.gymRoutineDao().replaceCatalog(
            listOf(GymRoutineEntity(64, "Pull Day")),
            mapOf(64 to List(3) { GymRoutineExerciseEntity(64, it, 18, "REPS", listOf(10, 10), 60) })
        )
    }

    private fun tracker(recorder: SessionEntryRecorder = trainingLog) =
        GymSessionTracker(RoomGymSessionStore(database), recorder, clock)

    @Test
    fun an_abandoned_session_becomes_one_entry_dated_on_its_start_and_the_progress_resets() = runTest {
        seedRoutine()
        val tracker = tracker()
        for (offset in listOf(0L, 10 * minute, 40 * minute)) {
            now = start + offset
            tracker.onSetCompleted(64)
        }
        database.gymProgressDao().markCompleted(GymExerciseProgressEntity(64, 0, start + 40 * minute))

        now = start + 40 * minute + 2 * 60 * minute + 1
        tracker.closeInactiveSessions()
        tracker.closeInactiveSessions()

        val entry = trainingLog.observeEntries().first().single()
        assertEquals("gym:64", entry.refId)
        assertEquals("Pull Day", entry.title)
        assertEquals(40 * 60, entry.durationSeconds)
        // Started on the 19th (UTC) even though the last set and the close were on the 20th
        assertEquals("2026-10-19", entry.dateLocal)
        assertEquals(start + 40 * minute, entry.completedAt)
        assertTrue(database.gymProgressDao().getCompletionTimes(64).isEmpty())
        assertTrue(database.gymSessionDao().getAll().isEmpty())
    }

    @Test
    fun a_failed_close_rolls_back_the_entry_and_keeps_progress_and_session() = runTest {
        seedRoutine()
        // Inserts the entry, then fails: the transaction must undo the insert too
        val failingAfterInsert = SessionEntryRecorder { source, refId, title, duration, completedAt, dateOf ->
            trainingLog.recordSession(source, refId, title, duration, completedAt, dateOf)
            error("disk full")
        }
        now = start
        tracker().onSetCompleted(64)
        database.gymProgressDao().markCompleted(GymExerciseProgressEntity(64, 0, start))

        now = start + 5 * 60 * minute
        tracker(failingAfterInsert).closeInactiveSessions()

        assertTrue(trainingLog.observeEntries().first().isEmpty())
        assertEquals(listOf(start), database.gymProgressDao().getCompletionTimes(64))
        assertEquals(1, database.gymSessionDao().getAll().size)
    }
}
