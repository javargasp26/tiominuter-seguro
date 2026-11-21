package com.javargasp.tiominutermvi.ui.program.strengthexercise

import com.javargasp.tiominutermvi.domain.feedback.WorkoutFeedback
import com.javargasp.tiominutermvi.domain.program.model.Equipment
import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.Impact
import com.javargasp.tiominutermvi.domain.program.model.Mechanic
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.ProgramDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTitle
import com.javargasp.tiominutermvi.domain.program.model.StrengthSlot
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramStrengthExerciseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val feedback = FakeWorkoutFeedback()
    private val timeSource = MonotonicTimeSource { mainDispatcherRule.dispatcher.scheduler.currentTime }

    private val squat = StrengthSlot(0, 2, ProgramSlotType.REPS, listOf(10, 10, 10), 90)
    private val plank = StrengthSlot(1, 4, ProgramSlotType.TIME, listOf(30, 30), 60)
    private val noRest = StrengthSlot(2, 3, ProgramSlotType.REPS, listOf(8, 8), 0)
    private val sidePlank = StrengthSlot(3, 120, ProgramSlotType.TIME, listOf(20, 20), 45, perSide = true)
    private val lunge = StrengthSlot(4, 20, ProgramSlotType.REPS, listOf(10, 10), 60, perSide = true)
    private val workout = ProgramWorkout.Strength(
        StrengthDayTitle.FULL_BODY_A,
        listOf(squat, plank, noRest, sidePlank, lunge)
    )
    private val repository = FakeProgramRepository(workout)

    private fun createViewModel(position: Int) =
        ProgramStrengthExerciseViewModel(1, 1, 0, position, repository, feedback, timeSource)

    private fun TestScope.advanceSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1000L)
        runCurrent()
    }

    private fun TestScope.collectEffects(viewModel: ProgramStrengthExerciseViewModel): List<ProgramStrengthExerciseEffect> {
        val effects = mutableListOf<ProgramStrengthExerciseEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        return effects
    }

    @Test
    fun `loads the slot and starts set 1`() = runTest {
        val viewModel = createViewModel(0)

        val state = viewModel.state.value
        assertEquals(squat, state.slot)
        assertEquals("Squats", state.exercise?.name)
        assertEquals(1, state.exerciseNumber)
        assertEquals(5, state.totalExercises)
        assertEquals(ProgramExercisePhase.PerformingReps(0), state.phase)
    }

    @Test
    fun `reps set done rests, then the next set starts with feedback`() = runTest {
        val viewModel = createViewModel(0)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)
        assertEquals(ProgramExercisePhase.Resting(1, 90, 90), viewModel.state.value.phase)
        assertEquals(1, viewModel.state.value.completedSets)

        advanceSeconds(1)
        assertEquals(ProgramExercisePhase.Resting(1, 89, 90), viewModel.state.value.phase)

        advanceSeconds(89)
        assertEquals(ProgramExercisePhase.PerformingReps(1), viewModel.state.value.phase)
        assertEquals(1, feedback.sounds)
        assertEquals(1, feedback.vibrations)
    }

    @Test
    fun `skip rest goes to the next set without feedback`() = runTest {
        val viewModel = createViewModel(0)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)
        advanceSeconds(10)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSkipRest)
        advanceSeconds(100)

        assertEquals(ProgramExercisePhase.PerformingReps(1), viewModel.state.value.phase)
        assertEquals(0, feedback.sounds)
    }

    @Test
    fun `rest 0 goes straight to the next set`() = runTest {
        val viewModel = createViewModel(2)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)

        assertEquals(ProgramExercisePhase.PerformingReps(1), viewModel.state.value.phase)
    }

    @Test
    fun `last set marks the slot, gives feedback once and goes back, with no rest`() = runTest {
        val viewModel = createViewModel(0)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSkipRest)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSkipRest)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)

        assertEquals(listOf(Mark(programId = 1, week = 1, dayIndex = 0, position = 0)), repository.marked)
        assertEquals(1, feedback.sounds)
        assertEquals(1, feedback.vibrations)
        assertEquals(listOf(ProgramStrengthExerciseEffect.NavigateBack), effects)
        assertFalse(viewModel.state.value.phase is ProgramExercisePhase.Resting)
    }

    @Test
    fun `every set is noted in the workout's session, the last one before the slot is marked`() = runTest {
        val viewModel = createViewModel(0)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSkipRest)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSkipRest)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)
        runCurrent()

        assertEquals(listOf("set", "set", "set", "mark"), repository.calls)
    }

    @Test
    fun `TIME set has a 5 second get ready, counts down and rests`() = runTest {
        val viewModel = createViewModel(1)
        assertEquals(ProgramExercisePhase.ReadyToStartTimed(0), viewModel.state.value.phase)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnStartTimedSet)
        assertEquals(ProgramExercisePhase.GetReady(0, 5), viewModel.state.value.phase)

        advanceSeconds(5)
        assertEquals(ProgramExercisePhase.PerformingTimed(0, 30, 30), viewModel.state.value.phase)

        advanceSeconds(10)
        assertEquals(ProgramExercisePhase.PerformingTimed(0, 20, 30), viewModel.state.value.phase)

        advanceSeconds(20)
        assertEquals(ProgramExercisePhase.Resting(1, 60, 60), viewModel.state.value.phase)
        assertEquals(1, feedback.sounds)
    }

    @Test
    fun `last TIME set completes the slot`() = runTest {
        val viewModel = createViewModel(1)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnStartTimedSet)
        advanceSeconds(35)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSkipRest)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnStartTimedSet)

        advanceSeconds(35)

        assertEquals(listOf(1), repository.marked.map { it.position })
        assertEquals(2, feedback.sounds) // end of set 1 + completion
    }

    @Test
    fun `back with sets done asks to confirm and leaves without marking`() = runTest {
        val viewModel = createViewModel(0)
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnBackPressed)
        assertTrue(viewModel.state.value.showExitConfirmation)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnDismissExit)
        assertFalse(viewModel.state.value.showExitConfirmation)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnBackPressed)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnConfirmExit)

        assertEquals(listOf(ProgramStrengthExerciseEffect.NavigateBack), effects)
        assertTrue(repository.marked.isEmpty())
        advanceSeconds(200)
        assertEquals(0, feedback.sounds) // rest timer stopped
    }

    @Test
    fun `back with no set done leaves directly`() = runTest {
        val viewModel = createViewModel(0)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnBackPressed)

        assertFalse(viewModel.state.value.showExitConfirmation)
        assertEquals(listOf(ProgramStrengthExerciseEffect.NavigateBack), effects)
    }

    @Test
    fun `a missing slot shows not found`() = runTest {
        val viewModel = createViewModel(9)

        assertTrue(viewModel.state.value.isNotFound)
        assertNull(viewModel.state.value.phase)
    }

    @Test
    fun `workout removed while open stops the timer and shows not found`() = runTest {
        val viewModel = createViewModel(0)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)

        repository.workout.value = null
        advanceSeconds(100)

        assertTrue(viewModel.state.value.isNotFound)
        assertEquals(0, feedback.sounds)
    }

    @Test
    fun `marking failure shows not found instead of crashing`() = runTest {
        repository.failMark = true
        val viewModel = createViewModel(2)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)

        assertTrue(viewModel.state.value.isNotFound)
        assertTrue(effects.isEmpty())
    }

    // ------------------------------------------------------------------
    // Per-side slots
    // ------------------------------------------------------------------

    @Test
    fun `per-side TIME set runs left, switch, right, then rests`() = runTest {
        val viewModel = createViewModel(3)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnStartTimedSet)
        advanceSeconds(5) // get ready

        assertEquals(ProgramExercisePhase.PerformingTimed(0, 20, 20, ProgramSide.LEFT), viewModel.state.value.phase)
        advanceSeconds(19)
        assertEquals(ProgramExercisePhase.PerformingTimed(0, 1, 20, ProgramSide.LEFT), viewModel.state.value.phase)
        assertEquals(0, feedback.sounds)

        advanceSeconds(1)
        assertEquals(ProgramExercisePhase.SwitchingSides(0, 5), viewModel.state.value.phase)
        // Sound + vibration at the switch
        assertEquals(1, feedback.sounds)
        assertEquals(1, feedback.vibrations)
        assertEquals(0, viewModel.state.value.completedSets)

        advanceSeconds(5)
        assertEquals(ProgramExercisePhase.PerformingTimed(0, 20, 20, ProgramSide.RIGHT), viewModel.state.value.phase)
        assertEquals(1, feedback.sounds)

        advanceSeconds(20)
        assertEquals(ProgramExercisePhase.Resting(1, 45, 45), viewModel.state.value.phase)
        assertEquals(2, feedback.sounds)
        assertEquals(1, viewModel.state.value.completedSets)
    }

    @Test
    fun `per-side TIME set lasts both sides plus the switch`() = runTest {
        val viewModel = createViewModel(3)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnStartTimedSet)
        advanceSeconds(5) // get ready

        // 20 s left + 5 s switch + 20 s right = 45 s
        advanceSeconds(44)
        assertEquals(ProgramExercisePhase.PerformingTimed(0, 1, 20, ProgramSide.RIGHT), viewModel.state.value.phase)

        advanceSeconds(1)
        assertEquals(ProgramExercisePhase.Resting(1, 45, 45), viewModel.state.value.phase)
    }

    @Test
    fun `last per-side TIME set completes after the right side, with no rest`() = runTest {
        val viewModel = createViewModel(3)
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnStartTimedSet)
        advanceSeconds(50)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSkipRest)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnStartTimedSet)
        advanceSeconds(5 + 20 + 5)
        assertEquals(ProgramExercisePhase.PerformingTimed(1, 20, 20, ProgramSide.RIGHT), viewModel.state.value.phase)
        assertTrue(repository.marked.isEmpty())

        advanceSeconds(20)

        assertEquals(listOf(3), repository.marked.map { it.position })
        // Set 1: switch + end; set 2: switch + completion
        assertEquals(4, feedback.sounds)
        assertEquals(listOf(ProgramStrengthExerciseEffect.NavigateBack), effects)
    }

    @Test
    fun `back during the switch of set 1 leaves and stops the timer`() = runTest {
        val viewModel = createViewModel(3)
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnStartTimedSet)
        advanceSeconds(26)
        assertEquals(ProgramExercisePhase.SwitchingSides(0, 4), viewModel.state.value.phase)

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnBackPressed)
        advanceSeconds(100)

        assertEquals(listOf(ProgramStrengthExerciseEffect.NavigateBack), effects)
        assertEquals(1, feedback.sounds) // only the switch cue
    }

    @Test
    fun `per-side REPS set is one tap and keeps the per-side reps`() = runTest {
        val viewModel = createViewModel(4)
        val state = viewModel.state.value
        assertTrue(state.slot!!.perSide)
        assertEquals(ProgramExercisePhase.PerformingReps(0), state.phase)
        assertEquals(10, state.slot!!.reps[0])

        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnSetDone)

        assertEquals(ProgramExercisePhase.Resting(1, 60, 60), viewModel.state.value.phase)
        assertEquals(0, feedback.sounds)
    }

    @Test
    fun `non per-side TIME set has no side`() = runTest {
        val viewModel = createViewModel(1)
        viewModel.handleIntent(ProgramStrengthExerciseIntent.OnStartTimedSet)
        advanceSeconds(5)

        assertNull((viewModel.state.value.phase as ProgramExercisePhase.PerformingTimed).side)
    }

    private data class Mark(val programId: Long, val week: Int, val dayIndex: Int, val position: Int)

    private class FakeWorkoutFeedback : WorkoutFeedback {
        var sounds = 0
        var vibrations = 0
        override fun playCompletionSound() { sounds++ }
        override fun vibrate() { vibrations++ }
    }

    private class FakeProgramRepository(workout: ProgramWorkout?) : ProgramRepository {
        val workout = MutableStateFlow(workout)
        val marked = mutableListOf<Mark>()
        var failMark = false

        /** Every call in order: "set" for a noted set, "mark" for a marked slot. */
        val calls = mutableListOf<String>()

        override suspend fun onStrengthSetCompleted(programId: Long, week: Int, dayIndex: Int) {
            calls += "set"
        }

        override fun observeWorkout(programId: Long, week: Int, dayIndex: Int): Flow<ProgramWorkoutDetail?> =
            flow {
                workout.collect { w -> emit(w?.let { ProgramWorkoutDetail(programId, week, dayIndex, it, false, emptySet()) }) }
            }

        override suspend fun markSlotCompleted(programId: Long, week: Int, dayIndex: Int, position: Int) {
            if (failMark) throw IllegalStateException("FOREIGN KEY constraint failed")
            marked += Mark(programId, week, dayIndex, position)
            calls += "mark"
        }

        override suspend fun getExercises(): Map<Int, ProgramExercise> = mapOf(
            2 to ProgramExercise(
                2, "Squats", "Sentadillas", "2_squat.gif",
                emptyList(),
                Equipment.BODYWEIGHT,
                Mechanic.COMPOUND,
                Impact.LOW,
                emptySet()
            )
        )

        override fun observePrograms(): Flow<List<ProgramSummary>> = flowOf(emptyList())
        override fun observeProgram(programId: Long): Flow<ProgramDetail?> = flowOf(null)
        override suspend fun previewProgram(profile: ProgramProfile): GeneratedProgram = GeneratedProgram(emptyList())
        override suspend fun createProgram(profile: ProgramProfile): Long = 1
        override suspend fun deleteProgram(programId: Long) = Unit
        override suspend fun resetWorkout(programId: Long, week: Int, dayIndex: Int) = Unit
        override suspend fun markWorkoutCompleted(programId: Long, week: Int, dayIndex: Int, durationSeconds: Int?) = Unit
        override suspend fun getImpactRules(): ImpactRules = ImpactRules(30.0, 60, 60, 15, setOf(87))
    }
}
