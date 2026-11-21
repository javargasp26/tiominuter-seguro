package com.javargasp.tiominutermvi.ui.workout

import com.javargasp.tiominutermvi.testutil.FakeDailyProgressRepository
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.model.RoutineExercise
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.javargasp.tiominutermvi.testutil.ActiveSessionTestKit
import org.junit.Assert.assertNull

/**
 * The finished daily workout is recorded once in the training history (DAILY,
 * "daily:<routineId>", the routine's day/title), with the time from the first OnLoad
 * measured on the test scheduler's virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTrainingLogTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pushUps = RoutineExercise(exerciseId = 1, type = ExerciseType.REPS, amount = 10)
    private val squats = RoutineExercise(exerciseId = 2, type = ExerciseType.REPS, amount = 15)
    private val plank = RoutineExercise(exerciseId = 3, type = ExerciseType.TIME, amount = 30)

    private val recorder = FakeTrainingLogRecorder()
    private val sessions = ActiveSessionTestKit()
    private val progress = FakeDailyProgressRepository()

    @Test
    fun `finishing the workout records one DAILY entry with the time since OnLoad`() = runTest {
        val viewModel = loadedViewModel(routine(4, pushUps, squats))

        advanceTimeBy(40_000)
        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)
        assertTrue(recorder.recorded.isEmpty())
        advanceTimeBy(50_000)
        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)

        assertEquals(listOf(Recorded(TrainingSource.DAILY, "daily:4", "Day 4", 90)), recorder.recorded)
    }

    @Test
    fun `the natural end of the last timer records the timer time, not the wait for Get Ready`() = runTest {
        val viewModel = loadedViewModel(routine(1, plank))
        val effects = collectEffects(viewModel)

        // Waiting for "Get Ready": no timer runs, not counted
        advanceTimeBy(10_000)
        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)
        advanceTimeBy(5_000 + 30_000)
        runCurrent()

        assertTrue(effects.contains(WorkoutEffect.NavigateToCompletion))
        assertEquals(listOf(Recorded(TrainingSource.DAILY, "daily:1", "Day 1", 35)), recorder.recorded)
    }

    @Test
    fun `skipping every exercise still records the workout`() = runTest {
        val viewModel = loadedViewModel(routine(2, pushUps, plank))

        viewModel.handleIntent(WorkoutIntent.OnSkipExercise)
        viewModel.handleIntent(WorkoutIntent.OnSkipExercise)

        assertTrue(viewModel.state.value.isWorkoutComplete)
        assertEquals(1, recorder.recorded.size)
        assertEquals("daily:2", recorder.recorded.single().refId)
    }

    @Test
    fun `it records once even if finishing is requested again`() = runTest {
        val viewModel = loadedViewModel(routine(1, pushUps))

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)
        viewModel.handleIntent(WorkoutIntent.OnFinishWorkout)
        viewModel.handleIntent(WorkoutIntent.OnSkipExercise)

        assertEquals(1, recorder.recorded.size)
    }

    @Test
    fun `a re-sent OnLoad does not restart the duration`() = runTest {
        val viewModel = loadedViewModel(routine(1, pushUps))

        advanceTimeBy(60_000)
        viewModel.handleIntent(WorkoutIntent.OnLoad) // rotation
        advanceTimeBy(30_000)
        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)

        assertEquals(90, recorder.recorded.single().durationSeconds)
    }

    @Test
    fun `the title adds the routine title when there is one`() = runTest {
        val viewModel = loadedViewModel(routine(2, pushUps).copy(title = "Legs"))

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)

        assertEquals("Day 2 · Legs", recorder.recorded.single().title)
    }

    @Test
    fun `leaving before the end records nothing`() = runTest {
        val viewModel = loadedViewModel(routine(1, pushUps, squats))

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)
        viewModel.handleIntent(WorkoutIntent.OnBackPressed)

        assertTrue(recorder.recorded.isEmpty())
    }

    @Test
    fun `a recording failure does not change the completion`() = runTest {
        recorder.error = IllegalStateException("disk full")
        val viewModel = loadedViewModel(routine(1, pushUps))
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)

        assertTrue(viewModel.state.value.isWorkoutComplete)
        assertTrue(viewModel.state.value.hasNavigatedToCompletion)
        assertEquals(listOf(WorkoutEffect.NavigateToCompletion), effects)
    }

    // ------------------------------------------------------------------
    // Daily unlock (DailyProgressRepository)
    // ------------------------------------------------------------------

    @Test
    fun `finishing the newest unlocked day unlocks the next one`() = runTest {
        val viewModel = loadedViewModel(routine(1, pushUps))

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)
        runCurrent()

        assertEquals(listOf(1), progress.finishedDays)
        assertEquals(DailyProgress(lastCompletedDay = 1), progress.progress.value)
    }

    @Test
    fun `repeating a completed day changes nothing`() = runTest {
        progress.progress.value = DailyProgress(lastCompletedDay = 5, cycle = 1)
        val viewModel = loadedViewModel(routine(2, pushUps))

        viewModel.handleIntent(WorkoutIntent.OnSkipExercise)
        runCurrent()

        assertEquals(listOf(2), progress.finishedDays)
        assertEquals(DailyProgress(lastCompletedDay = 5, cycle = 1), progress.progress.value)
    }

    @Test
    fun `leaving before the end, or a session closed for inactivity, unlocks nothing`() = runTest {
        val viewModel = loadedViewModel(routine(1, pushUps, squats))
        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)
        viewModel.handleIntent(WorkoutIntent.OnBackPressed)
        runCurrent()

        sessions.clock.now += 2 * 60 * 60_000L + 1
        sessions.tracker.closeInactive()

        assertEquals(1, sessions.store.abandoned.size)
        assertTrue(progress.finishedDays.isEmpty())
        assertEquals(DailyProgress(), progress.progress.value)
    }

    // ------------------------------------------------------------------
    // Active time (session)
    // ------------------------------------------------------------------

    @Test
    fun `a REPS exercise in the background is not counted (no timer runs)`() = runTest {
        val viewModel = loadedViewModel(routine(1, pushUps))

        advanceTimeBy(30_000)
        viewModel.handleIntent(WorkoutIntent.OnScreenHidden)
        advanceTimeBy(10 * 60_000)
        viewModel.handleIntent(WorkoutIntent.OnScreenVisible)
        advanceTimeBy(30_000)
        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)

        assertEquals(60, recorder.recorded.single().durationSeconds)
    }

    @Test
    fun `a running timer counts in the background (screen locked)`() = runTest {
        val viewModel = loadedViewModel(routine(1, plank))

        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)
        viewModel.handleIntent(WorkoutIntent.OnScreenHidden)
        advanceTimeBy(5_000 + 30_000)
        runCurrent()

        assertEquals(35, recorder.recorded.single().durationSeconds)
    }

    @Test
    fun `waiting for Get Ready in the background is not counted (the timer is stopped)`() = runTest {
        val viewModel = loadedViewModel(routine(1, pushUps, plank))
        advanceTimeBy(20_000)
        viewModel.handleIntent(WorkoutIntent.OnCompleteReps) // 20 s of reps, then the TIME exercise waits

        viewModel.handleIntent(WorkoutIntent.OnScreenHidden)
        advanceTimeBy(10 * 60_000)
        viewModel.handleIntent(WorkoutIntent.OnScreenVisible)
        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)
        advanceTimeBy(5_000 + 30_000)
        runCurrent()

        assertEquals(55, recorder.recorded.single().durationSeconds)
    }

    @Test
    fun `after a process death the time up to the last save counts`() = runTest {
        val viewModel = loadedViewModel(routine(3, plank.copy(amount = 300)))
        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)
        advanceTimeBy(12_000)
        runCurrent()

        // Saved every 5 s from the first action: the process dies here with 10 s stored
        assertEquals(10, sessions.store.session?.activeSeconds)
        sessions.clock.now += 2 * 60 * 60_000L + 1
        sessions.tracker.closeInactive()
        assertEquals(listOf("daily:3" to 10), sessions.store.abandoned.map { it.key to it.activeSeconds })

        // A running workout must be stopped before the test drains virtual time
        viewModel.handleIntent(WorkoutIntent.OnBackPressed)
    }

    @Test
    fun `opening and leaving without doing anything stores nothing`() = runTest {
        val viewModel = loadedViewModel(routine(1, pushUps, squats))

        advanceTimeBy(20_000)
        viewModel.handleIntent(WorkoutIntent.OnBackPressed)
        runCurrent()

        assertNull(sessions.store.session)
    }

    @Test
    fun `a workout left unfinished keeps its active time and is recorded after 2 hours`() = runTest {
        val viewModel = loadedViewModel(routine(4, pushUps, squats))
        advanceTimeBy(40_000)
        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)
        advanceTimeBy(20_000)

        viewModel.handleIntent(WorkoutIntent.OnBackPressed)
        runCurrent()

        assertEquals(60, sessions.store.session?.activeSeconds)
        assertTrue(recorder.recorded.isEmpty())

        // The process may die meanwhile: the stored session is enough
        sessions.clock.now += 2 * 60 * 60_000L + 1
        sessions.tracker.closeInactive()
        assertEquals(listOf("daily:4" to 60), sessions.store.abandoned.map { it.key to it.activeSeconds })
    }

    @Test
    fun `a new daily workout records the unfinished one first and says so`() = runTest {
        sessions.tracker.start("daily:9")
        sessions.tracker.update("daily:9", 300)
        val viewModel = loadedViewModel(routine(1, pushUps, squats))
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)

        assertEquals(listOf(300), sessions.store.abandoned.map { it.activeSeconds })
        assertTrue(effects.contains(WorkoutEffect.ShowSessionSaved(300)))
        assertEquals("daily:1", sessions.store.session?.key)
        // A running session saves every few seconds: stop it before the test drains virtual time
        viewModel.handleIntent(WorkoutIntent.OnBackPressed)
    }

    @Test
    fun `a normal finish records once and ends the session`() = runTest {
        val viewModel = loadedViewModel(routine(1, pushUps))
        advanceTimeBy(45_000)

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)
        viewModel.handleIntent(WorkoutIntent.OnFinishWorkout)
        runCurrent()

        assertEquals(listOf(45), recorder.recorded.map { it.durationSeconds })
        assertNull(sessions.store.session)
        sessions.clock.now += 5 * 60 * 60_000L
        sessions.tracker.closeInactive()
        assertTrue(sessions.store.abandoned.isEmpty())
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun routine(id: Int, vararg slots: RoutineExercise) =
        Routine(routineId = id, routineDay = "Day $id", exercises = slots.toList())

    private fun TestScope.loadedViewModel(routine: Routine): WorkoutViewModel {
        val repository = FakeWorkoutRepository(
            routine = routine,
            exercises = routine.exercises.map { Exercise(it.exerciseId, "Exercise ${it.exerciseId}", "anim.gif") }
        )
        val viewModel = WorkoutViewModel(
            repository = repository,
            routineId = routine.routineId,
            trainingLogRecorder = recorder,
            timeSource = { testScheduler.currentTime },
            dailySessions = sessions.tracker,
            dailyProgress = progress
        )
        viewModel.handleIntent(WorkoutIntent.OnLoad)
        assertEquals(routine.exercises.first(), viewModel.state.value.currentRoutineExercise)
        return viewModel
    }

    private fun TestScope.collectEffects(viewModel: WorkoutViewModel): List<WorkoutEffect> {
        val effects = mutableListOf<WorkoutEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }
        return effects
    }

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    private data class Recorded(val source: TrainingSource, val refId: String?, val title: String, val durationSeconds: Int?)

    private class FakeTrainingLogRecorder : TrainingLogRecorder {
        /** Id returned for the next recorded entry. */
        var nextEntryId = 1L
        val recorded = mutableListOf<Recorded>()
        var error: Exception? = null

        override suspend fun record(source: TrainingSource, refId: String?, title: String, durationSeconds: Int?): Long {
            error?.let { throw it }
            recorded.add(Recorded(source, refId, title, durationSeconds))
            return nextEntryId++
        }
    }

    private class FakeWorkoutRepository(
        private val routine: Routine,
        private val exercises: List<Exercise>
    ) : WorkoutRepository {
        override suspend fun getExercises(): List<Exercise> = exercises
        override suspend fun getRoutines(): List<Routine> = listOf(routine)
        override suspend fun getExercise(exerciseId: Int): Exercise? =
            exercises.firstOrNull { it.exerciseId == exerciseId }
        override suspend fun getRoutine(routineId: Int): Routine? =
            routine.takeIf { it.routineId == routineId }
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }
}
