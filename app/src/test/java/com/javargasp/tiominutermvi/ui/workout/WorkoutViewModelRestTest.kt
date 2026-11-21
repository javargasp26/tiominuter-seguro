package com.javargasp.tiominutermvi.ui.workout

import com.javargasp.tiominutermvi.testutil.FakeDailyProgressRepository
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
 * Rest slots ([RoutineExercise.isRest]) start their countdown on load, without
 * "Get Ready"; other TIME slots wait for the user. Decided by the flag, not by exerciseId.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelRestTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Any id: only the flag matters
    private val rest = RoutineExercise(50, ExerciseType.TIME, 3, isRest = true)
    private val timed = RoutineExercise(1, ExerciseType.TIME, 20)
    private val reps = RoutineExercise(2, ExerciseType.REPS, 15)

    @Test
    fun `loading a rest slot starts the countdown immediately without Get Ready`() = runTest {
        val viewModel = loadedViewModel(rest, timed)

        val state = viewModel.state.value
        assertEquals(rest, state.currentRoutineExercise)
        assertFalse(state.isReadyCountdown)
        assertTrue(state.isTimerRunning)
        assertEquals(3, state.timerRemainingSeconds)
    }

    @Test
    fun `rest counts down then emits sound and haptic and moves to the next exercise`() = runTest {
        val viewModel = loadedViewModel(rest, timed)
        val effects = collectEffects(viewModel)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, viewModel.state.value.timerRemainingSeconds)
        assertTrue(effects.isEmpty())

        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(
            listOf(WorkoutEffect.PlayCompletionSound, WorkoutEffect.TriggerHapticFeedback),
            effects
        )
        val state = viewModel.state.value
        assertEquals(1, state.currentExerciseIndex)
        assertEquals(timed, state.currentRoutineExercise)
        // The next (normal) TIME slot waits for "Get Ready"
        assertFalse(state.isTimerRunning)
        assertEquals(20, state.timerRemainingSeconds)
    }

    @Test
    fun `rest as the last slot finishes the workout when it ends`() = runTest {
        val viewModel = loadedViewModel(rest)
        val effects = collectEffects(viewModel)

        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(
            listOf(
                WorkoutEffect.PlayCompletionSound,
                WorkoutEffect.TriggerHapticFeedback,
                WorkoutEffect.NavigateToCompletion
            ),
            effects
        )
        assertTrue(viewModel.state.value.isWorkoutComplete)
    }

    @Test
    fun `skipping during the rest cancels its timer and advances`() = runTest {
        val viewModel = loadedViewModel(rest, timed)
        val effects = collectEffects(viewModel)
        advanceTimeBy(1_000)
        runCurrent()

        viewModel.handleIntent(WorkoutIntent.OnSkipExercise)
        advanceTimeBy(10_000)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(1, state.currentExerciseIndex)
        assertFalse(state.isTimerRunning)
        assertEquals(20, state.timerRemainingSeconds)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `back during the rest cancels its timer`() = runTest {
        val viewModel = loadedViewModel(rest, timed)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnBackPressed)
        advanceTimeBy(10_000)
        runCurrent()

        assertFalse(viewModel.state.value.isTimerRunning)
        assertEquals(0, viewModel.state.value.currentExerciseIndex)
        assertEquals(listOf(WorkoutEffect.NavigateBack), effects)
    }

    @Test
    fun `a normal TIME slot does not auto-start and shows Get Ready first`() = runTest {
        val viewModel = loadedViewModel(timed)

        assertFalse(viewModel.state.value.isTimerRunning)
        assertFalse(viewModel.state.value.isReadyCountdown)
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(viewModel.state.value.isTimerRunning)
        assertEquals(20, viewModel.state.value.timerRemainingSeconds)

        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)

        assertTrue(viewModel.state.value.isReadyCountdown)
        assertEquals(5, viewModel.state.value.readyCountdownSeconds)
        assertFalse(viewModel.state.value.isTimerRunning)

        advanceTimeBy(5_000)
        runCurrent()

        assertFalse(viewModel.state.value.isReadyCountdown)
        assertTrue(viewModel.state.value.isTimerRunning)
        assertEquals(20, viewModel.state.value.timerRemainingSeconds)
    }

    @Test
    fun `a REPS slot does not start a timer`() = runTest {
        val viewModel = loadedViewModel(reps)

        assertFalse(viewModel.state.value.isTimerRunning)
        assertEquals(0, viewModel.state.value.timerRemainingSeconds)
    }

    @Test
    fun `re-sending OnLoad during a rest does not create a second timer`() = runTest {
        val longRest = rest.copy(amount = 30)
        val viewModel = loadedViewModel(longRest, timed)
        val effects = collectEffects(viewModel)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(27, viewModel.state.value.timerRemainingSeconds)

        viewModel.handleIntent(WorkoutIntent.OnLoad)
        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)

        assertEquals(27, viewModel.state.value.timerRemainingSeconds)
        assertTrue(viewModel.state.value.isTimerRunning)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(26, viewModel.state.value.timerRemainingSeconds)

        advanceTimeBy(26_000)
        runCurrent()
        assertEquals(
            listOf(WorkoutEffect.PlayCompletionSound, WorkoutEffect.TriggerHapticFeedback),
            effects
        )
        assertEquals(1, viewModel.state.value.currentExerciseIndex)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun loadedViewModel(vararg slots: RoutineExercise): WorkoutViewModel {
        val repository = FakeWorkoutRepository(
            routine = Routine(routineId = 1, routineDay = "Day 1", exercises = slots.toList()),
            exercises = slots.map { Exercise(it.exerciseId, "Exercise ${it.exerciseId}", "anim.gif") }
        )
        val viewModel = WorkoutViewModel(repository, routineId = 1, NoOpTrainingLogRecorder(), timeSource = { 0L }, dailySessions = ActiveSessionTestKit().tracker, dailyProgress = FakeDailyProgressRepository())
        viewModel.handleIntent(WorkoutIntent.OnLoad)
        assertEquals(slots.first(), viewModel.state.value.currentRoutineExercise)
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
