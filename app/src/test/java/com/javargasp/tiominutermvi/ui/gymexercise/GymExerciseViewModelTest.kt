package com.javargasp.tiominutermvi.ui.gymexercise

import com.javargasp.tiominutermvi.domain.feedback.WorkoutFeedback
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.model.GymRoutineExercise
import com.javargasp.tiominutermvi.domain.repository.GymProgressRepository
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.testutil.GymSessionTestKit
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
import com.javargasp.tiominutermvi.domain.gymsession.GymSession

@OptIn(ExperimentalCoroutinesApi::class)
class GymExerciseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gymRoutineRepository = FakeGymRoutineRepository()
    private val gymProgressRepository = FakeGymProgressRepository()
    private val workoutFeedback = FakeWorkoutFeedback()
    private val sessions = GymSessionTestKit()

    /** Extra time added on top of virtual time, to simulate time passing without ticks. */
    private var timeOffsetMillis = 0L
    private val timeSource = MonotonicTimeSource {
        mainDispatcherRule.dispatcher.scheduler.currentTime + timeOffsetMillis
    }

    // ------------------------------------------------------------------
    // REPS
    // ------------------------------------------------------------------

    @Test
    fun `REPS set done starts rest and rest end moves to next set`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 30))
        assertEquals(GymExercisePhase.PerformingReps(0), viewModel.state.value.phase)
        assertEquals(0, viewModel.state.value.completedSets)

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        assertEquals(GymExercisePhase.Resting(nextSetIndex = 1, remainingSeconds = 30, totalSeconds = 30), viewModel.state.value.phase)
        assertEquals(1, viewModel.state.value.completedSets)

        advanceSeconds(1)
        assertEquals(GymExercisePhase.Resting(1, 29, 30), viewModel.state.value.phase)

        advanceSeconds(29)
        assertEquals(GymExercisePhase.PerformingReps(1), viewModel.state.value.phase)
    }

    @Test
    fun `skip rest moves to next set without feedback`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 30))

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        advanceSeconds(5)
        viewModel.handleIntent(GymExerciseIntent.OnSkipRest)

        assertEquals(GymExercisePhase.PerformingReps(1), viewModel.state.value.phase)
        assertEquals(0, workoutFeedback.sounds)
        assertEquals(0, workoutFeedback.vibrations)
    }

    @Test
    fun `rest 0 goes straight to next set`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10), restSeconds = 0))

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)

        assertEquals(GymExercisePhase.PerformingReps(1), viewModel.state.value.phase)
        assertEquals(0, workoutFeedback.sounds)
    }

    @Test
    fun `rest end plays sound and vibration`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10), restSeconds = 10))

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        advanceSeconds(9)
        assertEquals(0, workoutFeedback.sounds)
        assertEquals(0, workoutFeedback.vibrations)

        advanceSeconds(1)
        assertEquals(1, workoutFeedback.sounds)
        assertEquals(1, workoutFeedback.vibrations)
        assertEquals(GymExercisePhase.PerformingReps(1), viewModel.state.value.phase)
    }

    // ------------------------------------------------------------------
    // Last set
    // ------------------------------------------------------------------

    @Test
    fun `last set marks completed, plays feedback and navigates back without rest`() = runTest {
        val viewModel = createLoadedViewModel(
            exercise = repsExercise(reps = listOf(10, 10), restSeconds = 30),
            routineId = 7,
            position = 2
        )
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        viewModel.handleIntent(GymExerciseIntent.OnSkipRest)
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)

        assertEquals(listOf(7 to 2), gymProgressRepository.markCompletedCalls)
        assertEquals(1, workoutFeedback.sounds)
        assertEquals(1, workoutFeedback.vibrations)
        assertEquals(listOf(GymExerciseEffect.NavigateBack), effects)
        assertFalse(viewModel.state.value.phase is GymExercisePhase.Resting)

        // No rest timer was started after the last set.
        advanceSeconds(60)
        assertEquals(1, workoutFeedback.sounds)
        assertEquals(listOf(GymExerciseEffect.NavigateBack), effects)
    }

    @Test
    fun `last set done twice marks completed only once`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10), restSeconds = 30))
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)

        assertEquals(1, gymProgressRepository.markCompletedCalls.size)
        assertEquals(listOf(GymExerciseEffect.NavigateBack), effects)
    }

    // ------------------------------------------------------------------
    // TIME
    // ------------------------------------------------------------------

    @Test
    fun `TIME set runs get ready, countdown, feedback at the end, then rest`() = runTest {
        val viewModel = createLoadedViewModel(timeExercise(durations = listOf(20, 20), restSeconds = 15))
        assertEquals(GymExercisePhase.ReadyToStartTimed(0), viewModel.state.value.phase)

        viewModel.handleIntent(GymExerciseIntent.OnStartTimedSet)
        assertEquals(GymExercisePhase.GetReady(setIndex = 0, remainingSeconds = 5), viewModel.state.value.phase)

        advanceSeconds(1)
        assertEquals(GymExercisePhase.GetReady(0, 4), viewModel.state.value.phase)

        advanceSeconds(4)
        assertEquals(GymExercisePhase.PerformingTimed(setIndex = 0, remainingSeconds = 20, totalSeconds = 20), viewModel.state.value.phase)
        assertEquals(0, workoutFeedback.sounds)

        advanceSeconds(19)
        assertEquals(GymExercisePhase.PerformingTimed(0, 1, 20), viewModel.state.value.phase)
        assertEquals(0, workoutFeedback.sounds)

        advanceSeconds(1)
        assertEquals(1, workoutFeedback.sounds)
        assertEquals(1, workoutFeedback.vibrations)
        assertEquals(GymExercisePhase.Resting(nextSetIndex = 1, remainingSeconds = 15, totalSeconds = 15), viewModel.state.value.phase)
    }

    @Test
    fun `last TIME set plays feedback once and completes`() = runTest {
        val viewModel = createLoadedViewModel(
            exercise = timeExercise(durations = listOf(10), restSeconds = 15),
            routineId = 3,
            position = 0
        )
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(GymExerciseIntent.OnStartTimedSet)
        advanceSeconds(5 + 10)

        assertEquals(listOf(3 to 0), gymProgressRepository.markCompletedCalls)
        assertEquals(1, workoutFeedback.sounds)
        assertEquals(1, workoutFeedback.vibrations)
        assertEquals(listOf(GymExerciseEffect.NavigateBack), effects)
    }

    @Test
    fun `countdown is computed from the time source, not from the number of ticks`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10), restSeconds = 60))

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        advanceSeconds(1)
        assertEquals(GymExercisePhase.Resting(1, 59, 60), viewModel.state.value.phase)

        // 40 s pass while no tick runs (e.g. the app was in the background).
        timeOffsetMillis += 40_000
        advanceSeconds(1)

        assertEquals(GymExercisePhase.Resting(1, 18, 60), viewModel.state.value.phase)
    }

    // ------------------------------------------------------------------
    // Back / exit confirmation
    // ------------------------------------------------------------------

    @Test
    fun `back with no sets done navigates back`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10), restSeconds = 30))
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(GymExerciseIntent.OnBackPressed)

        assertEquals(listOf(GymExerciseEffect.NavigateBack), effects)
        assertFalse(viewModel.state.value.showExitConfirmation)
    }

    @Test
    fun `back with sets done shows confirmation instead of navigating`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 30))
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        viewModel.handleIntent(GymExerciseIntent.OnBackPressed)

        assertTrue(viewModel.state.value.showExitConfirmation)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `confirm exit navigates back without marking completed`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 30))
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        viewModel.handleIntent(GymExerciseIntent.OnBackPressed)
        viewModel.handleIntent(GymExerciseIntent.OnConfirmExit)

        assertEquals(listOf(GymExerciseEffect.NavigateBack), effects)
        assertTrue(gymProgressRepository.markCompletedCalls.isEmpty())
        assertFalse(viewModel.state.value.showExitConfirmation)

        // The rest timer was cancelled: no feedback when it would have ended.
        advanceSeconds(60)
        assertEquals(0, workoutFeedback.sounds)
    }

    @Test
    fun `dismiss exit hides confirmation and keeps the phase`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 0))
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        viewModel.handleIntent(GymExerciseIntent.OnBackPressed)
        viewModel.handleIntent(GymExerciseIntent.OnDismissExit)

        assertFalse(viewModel.state.value.showExitConfirmation)
        assertEquals(GymExercisePhase.PerformingReps(1), viewModel.state.value.phase)
        assertTrue(effects.isEmpty())
    }

    // ------------------------------------------------------------------
    // Single timer
    // ------------------------------------------------------------------

    @Test
    fun `only one timer runs at a time after skipping rest`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 30))
        val restingSeconds = mutableListOf<Int>()

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        advanceSeconds(10)
        viewModel.handleIntent(GymExerciseIntent.OnSkipRest)
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { state ->
                (state.phase as? GymExercisePhase.Resting)?.let { restingSeconds.add(it.remainingSeconds) }
            }
        }

        // The skipped rest would have ended 20 s from now; it must not interfere.
        advanceSeconds(25)

        assertEquals((30 downTo 5).toList(), restingSeconds)
        assertEquals(GymExercisePhase.Resting(2, 5, 30), viewModel.state.value.phase)
        assertEquals(0, workoutFeedback.sounds)
    }

    // ------------------------------------------------------------------
    // Loading / not found
    // ------------------------------------------------------------------

    @Test
    fun `loaded state exposes routine name, exercise and position info`() = runTest {
        val exercise = repsExercise(reps = listOf(10, 10), restSeconds = 30)
        val viewModel = createLoadedViewModel(exercise = exercise, position = 1, exerciseCount = 4)

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isNotFound)
        assertEquals("Routine", state.routineName)
        assertEquals("Rutina", state.routineNameEs)
        assertEquals(exercise.copy(position = 1), state.exercise)
        assertEquals(1, state.position)
        assertEquals(2, state.exerciseNumber)
        assertEquals(4, state.totalExercises)
    }

    @Test
    fun `OnLoad twice loads the routine only once`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10), restSeconds = 30))
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)

        viewModel.handleIntent(GymExerciseIntent.OnLoad)

        assertEquals(1, gymRoutineRepository.observeCalls)
        assertEquals(1, viewModel.state.value.completedSets)
    }

    @Test
    fun `routine not found sets isNotFound`() = runTest {
        gymRoutineRepository.routine = null
        val viewModel = createViewModel(routineId = 1, position = 0)

        viewModel.handleIntent(GymExerciseIntent.OnLoad)

        assertTrue(viewModel.state.value.isNotFound)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.phase)
    }

    @Test
    fun `position not found sets isNotFound`() = runTest {
        gymRoutineRepository.routine = routine(
            routineId = 1,
            exercise = repsExercise(reps = listOf(10), restSeconds = 30),
            position = 0,
            exerciseCount = 2
        )
        val viewModel = createViewModel(routineId = 1, position = 5)

        viewModel.handleIntent(GymExerciseIntent.OnLoad)

        assertTrue(viewModel.state.value.isNotFound)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.exercise)
    }

    // ------------------------------------------------------------------
    // Edge cases
    // ------------------------------------------------------------------

    @Test
    fun `routine removed during a rest shows not found and stops the timer`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 30))
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        advanceSeconds(5)

        gymRoutineRepository.routine = null

        assertTrue(viewModel.state.value.isNotFound)
        assertNull(viewModel.state.value.phase)
        advanceSeconds(60)
        assertEquals(0, workoutFeedback.sounds)
        assertTrue(gymProgressRepository.markCompletedCalls.isEmpty())
    }

    @Test
    fun `slot removed while open shows not found`() = runTest {
        val viewModel = createLoadedViewModel(
            exercise = repsExercise(reps = listOf(10, 10), restSeconds = 30),
            position = 2,
            exerciseCount = 3
        )

        gymRoutineRepository.routine = gymRoutineRepository.routine!!.let { routine ->
            routine.copy(exercises = routine.exercises.filter { it.position != 2 })
        }

        assertTrue(viewModel.state.value.isNotFound)
        assertNull(viewModel.state.value.phase)
    }

    @Test
    fun `back from not found navigates back`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 30))
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        gymRoutineRepository.routine = null

        viewModel.handleIntent(GymExerciseIntent.OnBackPressed)

        assertEquals(listOf(GymExerciseEffect.NavigateBack), effects)
        assertFalse(viewModel.state.value.showExitConfirmation)
    }

    @Test
    fun `markCompleted failure shows not found instead of crashing`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10), restSeconds = 30))
        val effects = collectEffects(viewModel)
        gymProgressRepository.markCompletedError = IllegalStateException("FOREIGN KEY constraint failed")

        viewModel.handleIntent(GymExerciseIntent.OnSetDone)

        assertTrue(viewModel.state.value.isNotFound)
        assertEquals(0, workoutFeedback.sounds)
        assertTrue(effects.isEmpty())

        viewModel.handleIntent(GymExerciseIntent.OnBackPressed)
        assertEquals(listOf(GymExerciseEffect.NavigateBack), effects)
    }

    @Test
    fun `routine update while open keeps the exercise in progress`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 30))
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        advanceSeconds(5)

        gymRoutineRepository.routine = gymRoutineRepository.routine!!.copy(routineName = "Renamed", routineNameEs = "Renombrada")

        assertEquals("Renamed", viewModel.state.value.routineName)
        assertEquals("Renombrada", viewModel.state.value.routineNameEs)
        assertEquals(GymExercisePhase.Resting(1, 25, 30), viewModel.state.value.phase)
        assertFalse(viewModel.state.value.isNotFound)
    }

    @Test
    fun `exercise missing from the catalog still loads`() = runTest {
        val viewModel = createLoadedViewModel(
            repsExercise(reps = listOf(10, 10), restSeconds = 30).copy(exercise = null)
        )

        assertFalse(viewModel.state.value.isNotFound)
        assertNull(viewModel.state.value.exercise?.exercise)
        assertEquals(GymExercisePhase.PerformingReps(0), viewModel.state.value.phase)
    }

    @Test
    fun `rest countdown continues unchanged after OnLoad from a configuration change`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 30))
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        advanceSeconds(10)
        val restingSeconds = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { state ->
                (state.phase as? GymExercisePhase.Resting)?.let { restingSeconds.add(it.remainingSeconds) }
            }
        }

        viewModel.handleIntent(GymExerciseIntent.OnLoad)
        advanceSeconds(5)

        assertEquals((20 downTo 15).toList(), restingSeconds)
        assertEquals(1, gymRoutineRepository.observeCalls)
    }

    // ------------------------------------------------------------------
    // Gym session
    // ------------------------------------------------------------------

    @Test
    fun `every completed set is noted in the gym session`() = runTest {
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 0), routineId = 7)

        sessions.clock.now = 1_000_000L
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        runCurrent()
        sessions.clock.now = 1_300_000L
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        runCurrent()

        assertEquals(GymSession(7, sessionStartedAt = 1_000_000L, lastSetAt = 1_300_000L), sessions.store.sessions[7])
        assertTrue(gymProgressRepository.markCompletedCalls.isEmpty())
    }

    @Test
    fun `the last set is noted before the exercise is marked completed`() = runTest {
        var lastSetAtWhenMarked: Long? = null
        gymProgressRepository.onMarkCompleted = { lastSetAtWhenMarked = sessions.store.sessions[1]?.lastSetAt }
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10), restSeconds = 0))

        sessions.clock.now = 1_000L
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        runCurrent()
        sessions.clock.now = 90_000L
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        runCurrent()

        assertEquals(90_000L, lastSetAtWhenMarked)
        assertEquals(listOf(1 to 0), gymProgressRepository.markCompletedCalls)
    }

    @Test
    fun `a set long after the last one saves the abandoned session first`() = runTest {
        sessions.store.sessions[1] = GymSession(1, sessionStartedAt = 0L, lastSetAt = 20 * 60_000L)
        val viewModel = createLoadedViewModel(repsExercise(reps = listOf(10, 10, 10), restSeconds = 0))

        sessions.clock.now = 20 * 60_000L + 3 * 3_600_000L
        viewModel.handleIntent(GymExerciseIntent.OnSetDone)
        runCurrent()

        assertEquals(listOf(20 * 60), sessions.recorder.entries.map { it.durationSeconds })
        assertEquals(listOf(1), sessions.store.clearedProgress)
        assertEquals(GymSession(1, sessions.clock.now, sessions.clock.now), sessions.store.sessions[1])
        // The exercise goes on as usual
        assertEquals(GymExercisePhase.PerformingReps(1), viewModel.state.value.phase)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun createViewModel(routineId: Int, position: Int) = GymExerciseViewModel(
        routineId = routineId,
        position = position,
        gymRoutineRepository = gymRoutineRepository,
        gymProgressRepository = gymProgressRepository,
        workoutFeedback = workoutFeedback,
        timeSource = timeSource,
        gymSessions = sessions.tracker
    )

    /**
     * Creates a ViewModel for a routine whose slot at [position] is [exercise]
     * (other slots are filler) and sends OnLoad.
     */
    private fun createLoadedViewModel(
        exercise: GymRoutineExercise,
        routineId: Int = 1,
        position: Int = 0,
        exerciseCount: Int = maxOf(3, position + 1)
    ): GymExerciseViewModel {
        gymRoutineRepository.routine = routine(routineId, exercise, position, exerciseCount)
        return createViewModel(routineId, position).also {
            it.handleIntent(GymExerciseIntent.OnLoad)
        }
    }

    private fun TestScope.collectEffects(viewModel: GymExerciseViewModel): List<GymExerciseEffect> {
        val effects = mutableListOf<GymExerciseEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }
        return effects
    }

    /** Advances virtual time and runs the tasks scheduled exactly at the new time. */
    private fun TestScope.advanceSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1000L)
        runCurrent()
    }

    private fun routine(
        routineId: Int,
        exercise: GymRoutineExercise,
        position: Int,
        exerciseCount: Int
    ) = GymRoutine(
        routineId = routineId,
        routineName = "Routine",
        routineNameEs = "Rutina",
        exercises = List(exerciseCount) { index ->
            if (index == position) {
                exercise.copy(position = index)
            } else {
                repsExercise(reps = listOf(8), restSeconds = 60).copy(position = index, exerciseId = 100 + index)
            }
        }
    )

    private fun repsExercise(reps: List<Int>, restSeconds: Int) = GymRoutineExercise(
        position = 0,
        exerciseId = 18,
        exercise = Exercise(exerciseId = 18, name = "Pull-ups", animation = "18_pull_ups.gif"),
        type = ExerciseType.REPS,
        reps = reps,
        restSeconds = restSeconds
    )

    private fun timeExercise(durations: List<Int>, restSeconds: Int) = GymRoutineExercise(
        position = 0,
        exerciseId = 4,
        exercise = Exercise(exerciseId = 4, name = "Plank", animation = "4_plank.gif"),
        type = ExerciseType.TIME,
        reps = durations,
        restSeconds = restSeconds
    )

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    private class FakeGymRoutineRepository : GymRoutineRepository {

        private val routineFlow = MutableStateFlow<GymRoutine?>(null)

        /** Current catalog content for the routine; setting it emits to observers. */
        var routine: GymRoutine?
            get() = routineFlow.value
            set(value) {
                routineFlow.value = value
            }
        var observeCalls = 0

        override fun observeGymRoutines(): Flow<List<GymRoutine>> = routineFlow.map { listOfNotNull(it) }

        override fun observeGymRoutine(routineId: Int): Flow<GymRoutine?> {
            observeCalls++
            return routineFlow.map { routine -> routine?.takeIf { it.routineId == routineId } }
        }

        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }

    private class FakeGymProgressRepository : GymProgressRepository {

        val markCompletedCalls = mutableListOf<Pair<Int, Int>>()

        /** When set, markCompleted throws it (e.g. the routine row is gone). */
        var markCompletedError: Exception? = null

        /** Runs when markCompleted is called (to see what was stored before it). */
        var onMarkCompleted: () -> Unit = {}

        override fun observeCompletedPositions(routineId: Int): Flow<Set<Int>> = flowOf(emptySet())

        override suspend fun getCompletionTimes(routineId: Int): List<Long> = emptyList()

        override suspend fun markCompleted(routineId: Int, position: Int) {
            onMarkCompleted()
            markCompletedError?.let { throw it }
            markCompletedCalls.add(routineId to position)
        }

        override suspend fun resetRoutine(routineId: Int) = Unit
    }

    private class FakeWorkoutFeedback : WorkoutFeedback {

        var sounds = 0
        var vibrations = 0

        override fun playCompletionSound() {
            sounds++
        }

        override fun vibrate() {
            vibrations++
        }
    }
}
