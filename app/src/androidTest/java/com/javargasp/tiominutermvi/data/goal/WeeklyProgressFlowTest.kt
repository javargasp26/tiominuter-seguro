package com.javargasp.tiominutermvi.data.goal

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoFileStore
import com.javargasp.tiominutermvi.domain.goal.CurrentWeekSummary
import com.javargasp.tiominutermvi.domain.goal.ObserveCurrentWeek
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * The current-week Flow over a real in-memory database: recording a workout (or
 * changing the goal) makes it emit an updated summary.
 */
class WeeklyProgressFlowTest {

    private lateinit var database: TioMinuterDatabase
    private lateinit var trainingLog: TrainingLogRepositoryImpl
    private lateinit var goals: WeeklyGoalRepositoryImpl
    private lateinit var observeCurrentWeek: ObserveCurrentWeek

    // Thursday 2026-10-22T10:00:00Z
    private var now = 1_792_663_200_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TioMinuterDatabase::class.java
        ).build()
        val dates = LocalDateProvider({ now }, { TimeZone.getTimeZone("UTC") })
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val photoFiles = TrainingPhotoFileStore(context.filesDir, context.cacheDir)
        trainingLog = TrainingLogRepositoryImpl(database.trainingLogDao(), { now }, dates, photoFiles)
        goals = WeeklyGoalRepositoryImpl(database.weeklyGoalDao(), { now }, dates)
        observeCurrentWeek = ObserveCurrentWeek(goals, trainingLog, dates)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun progressUpdatesWhenAWorkoutIsRecorded() = runTest {
        goals.setGoal(3)
        val updates = Channel<CurrentWeekSummary>(Channel.UNLIMITED)
        val job = launch { observeCurrentWeek().collect { updates.send(it) } }

        val initial = awaitUpdate(updates) { true }
        assertEquals(3, initial.progress.goal)
        assertEquals(0, initial.progress.workoutCount)

        trainingLog.record(TrainingSource.DAILY, "daily:1", "Day 1", 600)
        assertEquals(1, awaitUpdate(updates) { it.progress.workoutCount == 1 }.progress.workoutCount)

        // A second workout the same day counts too; a 5-minute one doesn't
        trainingLog.record(TrainingSource.GYM, "gym:1", "Pull Day", 1_200)
        trainingLog.record(TrainingSource.DAILY, "daily:2", "Day 2", 300)
        now += DAY // Friday
        trainingLog.record(TrainingSource.PROGRAM, "program:1:1:0", "Full body A", null)
        val met = awaitUpdate(updates) { it.progress.workoutCount == 3 }
        assertEquals(mapOf("2026-10-22" to 2, "2026-10-23" to 1), met.progress.workoutsPerDay)
        assertTrue(met.progress.isGoalMet)
        assertEquals(1, met.streak)

        job.cancel()
    }

    @Test
    fun progressUpdatesWhenTheGoalChanges() = runTest {
        trainingLog.record(TrainingSource.DAILY, "daily:1", "Day 1", 600)
        val updates = Channel<CurrentWeekSummary>(Channel.UNLIMITED)
        val job = launch { observeCurrentWeek().collect { updates.send(it) } }
        assertEquals(null, awaitUpdate(updates) { true }.progress.goal)

        goals.setGoal(3)
        val withGoal = awaitUpdate(updates) { it.progress.goal == 3 }
        assertFalse(withGoal.progress.isGoalMet)

        goals.setGoal(1)
        assertTrue(awaitUpdate(updates) { it.progress.goal == 1 }.progress.isGoalMet)

        job.cancel()
    }

    /** Next emission matching [condition], waiting in real time (Room emits on its own threads). */
    private suspend fun awaitUpdate(
        updates: Channel<CurrentWeekSummary>,
        condition: (CurrentWeekSummary) -> Boolean
    ): CurrentWeekSummary = withContext(Dispatchers.Default) {
        withTimeout(5_000) {
            var next = updates.receive()
            while (!condition(next)) next = updates.receive()
            next
        }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
