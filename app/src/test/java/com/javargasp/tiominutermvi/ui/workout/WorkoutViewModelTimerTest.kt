package com.javargasp.tiominutermvi.ui.workout

import com.javargasp.tiominutermvi.testutil.FakeDailyProgressRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.javargasp.tiominutermvi.testutil.ActiveSessionTestKit

/**
 * Timer of normal (non-rest) TIME slots: nothing runs on load; "Get Ready"
 * (OnStartTimerWithReady) counts 5 → 0, then the timer counts down and, at its
 * natural end, emits sound + haptic and advances. Rest slots, which start on load,
 * are covered by [WorkoutViewModelRestTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTimerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val jumpingJacks = RoutineExercise(exerciseId = 1, type = ExerciseType.TIME, amount = 30)
    private val pushUps = RoutineExercise(exerciseId = 2, type = ExerciseType.REPS, amount = 15)

    @Test
    fun `TIME exercise loads its duration but waits for Get Ready`() = runTest {
        val viewModel = loadedViewModel(jumpingJacks)
        val effects = collectEffects(viewModel)

        assertTimerIdle(viewModel, remaining = 30)
        assertEquals(30, viewModel.state.value.timerDurationSeconds)

        advanceTimeBy(60_000)
        runCurrent()

        assertTimerIdle(viewModel, remaining = 30)
        assertEquals(0, viewModel.state.value.currentExerciseIndex)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `timer does not start for REPS exercise`() = runTest {
        val viewModel = loadedViewModel(pushUps)
        val effects = collectEffects(viewModel)

        advanceTimeBy(60_000)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(0, state.timerDurationSeconds)
        assertEquals(0, state.timerRemainingSeconds)
        assertFalse(state.isTimerRunning)
        assertFalse(state.isReadyCountdown)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `timer countdown updates state correctly`() = runTest {
        val viewModel = loadedViewModel(jumpingJacks.copy(amount = 3))
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)

        // Get Ready: 5 → 0, the main timer stays untouched
        for (expected in 5 downTo 1) {
            assertTrue(viewModel.state.value.isReadyCountdown)
            assertEquals(expected, viewModel.state.value.readyCountdownSeconds)
            assertFalse(viewModel.state.value.isTimerRunning)
            assertEquals(3, viewModel.state.value.timerRemainingSeconds)
            advanceTimeBy(1_000)
            runCurrent()
        }

        // Then the timer: 3 → 1
        for (expected in 3 downTo 1) {
            val state = viewModel.state.value
            assertFalse(state.isReadyCountdown)
            assertEquals(0, state.readyCountdownSeconds)
            assertTrue(state.isTimerRunning)
            assertEquals(3, state.timerDurationSeconds)
            assertEquals(expected, state.timerRemainingSeconds)
            assertTrue(effects.isEmpty())
            advanceTimeBy(1_000)
            runCurrent()
        }

        // Reaching 0 ends the (only) exercise
        assertFalse(viewModel.state.value.isTimerRunning)
        assertTrue(viewModel.state.value.isWorkoutComplete)
        assertEquals(
            listOf(
                WorkoutEffect.PlayCompletionSound,
                WorkoutEffect.TriggerHapticFeedback,
                WorkoutEffect.NavigateToCompletion
            ),
            effects
        )
    }

    @Test
    fun `timer finished intent moves to next exercise`() = runTest {
        val viewModel = loadedViewModel(jumpingJacks.copy(amount = 1), pushUps)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(viewModel.state.value.isTimerRunning)

        // The timer reaches 0 and sends OnTimerFinished itself
        advanceTimeBy(1_000)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(1, state.currentExerciseIndex)
        assertEquals(2, state.currentExercise?.exerciseId)
        assertEquals(pushUps, state.currentRoutineExercise)
        assertTimerIdle(viewModel, remaining = 0)
        assertEquals(
            listOf(WorkoutEffect.PlayCompletionSound, WorkoutEffect.TriggerHapticFeedback),
            effects
        )
    }

    @Test
    fun `timer is cancelled when moving to next exercise`() = runTest {
        val viewModel = loadedViewModel(jumpingJacks, pushUps)
        val effects = collectEffects(viewModel)
        startedTimer(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnNextExercise)

        assertEquals(1, viewModel.state.value.currentExerciseIndex)
        assertTimerIdle(viewModel, remaining = 0)

        // The cancelled timer never fires
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, viewModel.state.value.currentExerciseIndex)
        assertTimerIdle(viewModel, remaining = 0)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `skip during Get Ready cancels the countdown and the timer never starts`() = runTest {
        val viewModel = loadedViewModel(jumpingJacks, jumpingJacks.copy(amount = 20))
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(viewModel.state.value.isReadyCountdown)

        viewModel.handleIntent(WorkoutIntent.OnSkipExercise)
        advanceTimeBy(60_000)
        runCurrent()

        // The next TIME slot waits for its own Get Ready
        assertEquals(1, viewModel.state.value.currentExerciseIndex)
        assertTimerIdle(viewModel, remaining = 20)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `timer is cancelled when finishing workout`() = runTest {
        val viewModel = loadedViewModel(jumpingJacks)
        val effects = collectEffects(viewModel)
        startedTimer(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnFinishWorkout)

        val state = viewModel.state.value
        assertTrue(state.isWorkoutComplete)
        assertFalse(state.isTimerRunning)
        assertEquals(0, state.timerRemainingSeconds)

        advanceTimeBy(60_000)
        runCurrent()
        assertFalse(viewModel.state.value.isTimerRunning)
        assertEquals(listOf(WorkoutEffect.NavigateToCompletion), effects)
    }

    @Test
    fun `back cancels the Get Ready countdown and the timer`() = runTest {
        val viewModel = loadedViewModel(jumpingJacks, pushUps)
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)
        advanceTimeBy(2_000)
        runCurrent()

        viewModel.handleIntent(WorkoutIntent.OnBackPressed)
        advanceTimeBy(60_000)
        runCurrent()

        assertFalse(viewModel.state.value.isReadyCountdown)
        assertFalse(viewModel.state.value.isTimerRunning)
        assertEquals(0, viewModel.state.value.currentExerciseIndex)
        assertEquals(listOf(WorkoutEffect.NavigateBack), effects)
    }

    @Test
    fun `clearing the ViewModel cancels the running timer`() = runTest {
        val store = ViewModelStore()
        val viewModel = loadedViewModel(jumpingJacks, pushUps, store = store)
        val effects = collectEffects(viewModel)
        startedTimer(viewModel)

        store.clear() // calls onCleared()
        advanceTimeBy(60_000)
        runCurrent()

        assertFalse(viewModel.state.value.isTimerRunning)
        assertEquals(0, viewModel.state.value.currentExerciseIndex)
        assertTrue(effects.isEmpty())
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun loadedViewModel(
        vararg slots: RoutineExercise,
        store: ViewModelStore = ViewModelStore()
    ): WorkoutViewModel {
        val repository = FakeWorkoutRepository(
            routine = Routine(routineId = 1, routineDay = "Day 1", exercises = slots.toList()),
            exercises = slots.map { Exercise(it.exerciseId, "Exercise ${it.exerciseId}", "anim.gif") }
        )
        // Created through a ViewModelStore so a test can clear it (onCleared)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WorkoutViewModel(repository, routineId = 1, NoOpTrainingLogRecorder(), timeSource = { 0L }, dailySessions = ActiveSessionTestKit().tracker, dailyProgress = FakeDailyProgressRepository()) as T
        }
        val viewModel = ViewModelProvider(store, factory)[WorkoutViewModel::class.java]
        viewModel.handleIntent(WorkoutIntent.OnLoad)
        assertEquals(slots.first(), viewModel.state.value.currentRoutineExercise)
        return viewModel
    }

    /** Taps "Get Ready" and lets the timer run for 2 s. */
    private fun TestScope.startedTimer(viewModel: WorkoutViewModel) {
        val duration = viewModel.state.value.timerDurationSeconds
        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)
        advanceTimeBy(5_000 + 2_000)
        runCurrent()
        assertTrue(viewModel.state.value.isTimerRunning)
        assertEquals(duration - 2, viewModel.state.value.timerRemainingSeconds)
    }

    private fun assertTimerIdle(viewModel: WorkoutViewModel, remaining: Int) {
        val state = viewModel.state.value
        assertFalse(state.isTimerRunning)
        assertFalse(state.isReadyCountdown)
        assertEquals(remaining, state.timerRemainingSeconds)
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

    private class NoOpTrainingLogRecorder : TrainingLogRecorder {
        override suspend fun record(source: TrainingSource, refId: String?, title: String, durationSeconds: Int?) = 1L
    }
}
