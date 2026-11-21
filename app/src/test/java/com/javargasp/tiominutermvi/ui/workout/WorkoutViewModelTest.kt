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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.javargasp.tiominutermvi.testutil.ActiveSessionTestKit

/**
 * Exercise progression of the daily workout: skip, REPS completion, finishing and
 * completion navigation. Timer details live in [WorkoutViewModelTimerTest], rest
 * slots in [WorkoutViewModelRestTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pushUps = Exercise(exerciseId = 1, name = "Push-ups", animation = "push_ups.gif")
    private val squats = Exercise(exerciseId = 2, name = "Squats", animation = "squats.gif")
    private val plank = Exercise(exerciseId = 3, name = "Plank", animation = "plank.gif")

    private val pushUpsSlot = RoutineExercise(exerciseId = 1, type = ExerciseType.REPS, amount = 10)
    private val squatsSlot = RoutineExercise(exerciseId = 2, type = ExerciseType.REPS, amount = 15)
    private val plankSlot = RoutineExercise(exerciseId = 3, type = ExerciseType.TIME, amount = 30)

    @Test
    fun `skip exercise moves to next exercise`() = runTest {
        val viewModel = loadedViewModel(pushUpsSlot, squatsSlot)
        val effects = collectEffects(viewModel)
        assertEquals(0, viewModel.state.value.currentExerciseIndex)

        viewModel.handleIntent(WorkoutIntent.OnSkipExercise)

        val state = viewModel.state.value
        assertEquals(1, state.currentExerciseIndex)
        assertEquals(squats, state.currentExercise)
        assertEquals(squatsSlot, state.currentRoutineExercise)
        assertFalse(state.isWorkoutComplete)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `skip last exercise completes workout`() = runTest {
        val viewModel = loadedViewModel(pushUpsSlot)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnSkipExercise)

        val state = viewModel.state.value
        assertTrue(state.isWorkoutComplete)
        assertTrue(state.hasNavigatedToCompletion)
        assertNull(state.currentExercise)
        assertNull(state.currentRoutineExercise)
        assertEquals(listOf(WorkoutEffect.NavigateToCompletion), effects)
    }

    @Test
    fun `complete REPS exercise updates completed reps and moves to next`() = runTest {
        val viewModel = loadedViewModel(pushUpsSlot, squatsSlot)
        val effects = collectEffects(viewModel)
        assertEquals(0, viewModel.state.value.completedReps)

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)

        // Auto-advances; the new exercise starts with its reps reset
        val state = viewModel.state.value
        assertEquals(1, state.currentExerciseIndex)
        assertEquals(squats, state.currentExercise)
        assertEquals(0, state.completedReps)
        assertFalse(state.isWorkoutComplete)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `complete last REPS exercise completes workout`() = runTest {
        val viewModel = loadedViewModel(pushUpsSlot)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)

        val state = viewModel.state.value
        assertTrue(state.isWorkoutComplete)
        assertTrue(state.hasNavigatedToCompletion)
        assertEquals(10, state.completedReps) // Target reps completed
        assertEquals(listOf(WorkoutEffect.NavigateToCompletion), effects)
    }

    @Test
    fun `prevent duplicate completion navigation`() = runTest {
        val viewModel = loadedViewModel(pushUpsSlot)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)
        assertEquals(listOf(WorkoutEffect.NavigateToCompletion), effects)

        // Any further attempt to finish is ignored
        viewModel.handleIntent(WorkoutIntent.OnFinishWorkout)
        viewModel.handleIntent(WorkoutIntent.OnSkipExercise)
        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)
        runCurrent()

        assertEquals(listOf(WorkoutEffect.NavigateToCompletion), effects)
    }

    @Test
    fun `TIME exercise completion emits sound and haptic effects`() = runTest {
        val viewModel = loadedViewModel(plankSlot)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnStartTimerWithReady)
        advanceTimeBy(5_000 + 29_000)
        runCurrent()
        assertTrue(effects.isEmpty())

        // Natural end of the timer
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(
            listOf(
                WorkoutEffect.PlayCompletionSound,
                WorkoutEffect.TriggerHapticFeedback,
                WorkoutEffect.NavigateToCompletion
            ),
            effects
        )
        assertFalse(viewModel.state.value.isTimerRunning)
    }

    @Test
    fun `REPS exercise completion does not emit sound effects`() = runTest {
        val viewModel = loadedViewModel(pushUpsSlot, squatsSlot)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)
        viewModel.handleIntent(WorkoutIntent.OnCompleteReps)

        assertEquals(listOf(WorkoutEffect.NavigateToCompletion), effects)
    }

    @Test
    fun `finish workout cleans up timer and state`() = runTest {
        val viewModel = loadedViewModel(pushUpsSlot)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WorkoutIntent.OnFinishWorkout)

        val state = viewModel.state.value
        assertTrue(state.isWorkoutComplete)
        assertTrue(state.hasNavigatedToCompletion)
        assertNull(state.currentExercise)
        assertNull(state.currentRoutineExercise)
        assertFalse(state.isTimerRunning)
        assertEquals(0, state.timerRemainingSeconds)
        assertFalse(state.isReadyCountdown)
        assertEquals(listOf(WorkoutEffect.NavigateToCompletion), effects)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun loadedViewModel(vararg slots: RoutineExercise): WorkoutViewModel {
        val repository = FakeWorkoutRepository(
            routine = Routine(routineId = 1, routineDay = "Day 1", exercises = slots.toList()),
            exercises = listOf(pushUps, squats, plank)
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
