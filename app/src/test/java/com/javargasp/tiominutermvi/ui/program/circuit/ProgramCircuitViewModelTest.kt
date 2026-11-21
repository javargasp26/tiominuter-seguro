package com.javargasp.tiominutermvi.ui.program.circuit

import com.javargasp.tiominutermvi.domain.feedback.WorkoutFeedback
import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.ProgramDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.StagedPhoto
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoException
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import com.javargasp.tiominutermvi.testutil.ActiveSessionTestKit

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramCircuitViewModelTest {

    private val photos = FakeTrainingPhotoRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val feedback = FakeWorkoutFeedback()
    private val timeSource = MonotonicTimeSource { mainDispatcherRule.dispatcher.scheduler.currentTime }

    /** 2 rounds of 3 exercises: 30 s work, 15 s rest, 60 s between rounds. */
    private val circuit = ProgramWorkout.HomeCircuit(
        rounds = 2,
        workSeconds = 30,
        restSeconds = 15,
        restBetweenRoundsSeconds = 60,
        exerciseIds = listOf(1, 2, 3)
    )
    private val repository = FakeProgramRepository(circuit)

    private val sessions = ActiveSessionTestKit()

    private fun createViewModel() = ProgramCircuitViewModel(1, 1, 1, repository, feedback, timeSource, photos, sessions.tracker)

    private fun TestScope.advanceSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1000L)
        runCurrent()
    }

    @Test
    fun `not started until Start`() = runTest {
        val viewModel = createViewModel()

        assertNull(viewModel.state.value.phase)
        assertEquals(1, viewModel.state.value.currentRound)
        assertEquals(2, viewModel.state.value.totalRounds)
        assertEquals(1, viewModel.state.value.shownExerciseId)
    }

    @Test
    fun `work, rest with the next exercise, then work again`() = runTest {
        val viewModel = createViewModel()

        viewModel.handleIntent(ProgramCircuitIntent.OnStart)
        assertEquals(CircuitPhase.Work(1, 0, 30, 30), viewModel.state.value.phase)

        advanceSeconds(10)
        assertEquals(CircuitPhase.Work(1, 0, 20, 30), viewModel.state.value.phase)

        advanceSeconds(20)
        assertEquals(CircuitPhase.Rest(1, 1, 15, 15), viewModel.state.value.phase)
        assertEquals(2, viewModel.state.value.shownExerciseId)
        assertEquals(1, feedback.sounds)

        advanceSeconds(15)
        assertEquals(CircuitPhase.Work(1, 1, 30, 30), viewModel.state.value.phase)
        assertEquals(2, feedback.sounds)
    }

    @Test
    fun `rest between rounds after the last exercise of a round`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramCircuitIntent.OnStart)

        advanceSeconds(30 + 15 + 30 + 15 + 30) // three exercises of round 1

        assertEquals(CircuitPhase.RoundRest(2, 60, 60), viewModel.state.value.phase)
        assertEquals(2, viewModel.state.value.currentRound)
        assertEquals(1, viewModel.state.value.shownExerciseId)

        advanceSeconds(60)
        assertEquals(CircuitPhase.Work(2, 0, 30, 30), viewModel.state.value.phase)
    }

    @Test
    fun `the last exercise of the last round completes the workout`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(ProgramCircuitIntent.OnStart)

        advanceSeconds(2 * (3 * 30 + 2 * 15) + 60)

        assertTrue(viewModel.state.value.isFinished)
        assertNull(viewModel.state.value.phase)
        assertEquals(listOf(Triple(1L, 1, 1)), repository.completed)
        // The training history gets the time since Start
        assertEquals(listOf<Int?>(300), repository.durations)
        // 5 work→rest/round-rest changes + 5 rest→work changes + the end
        assertEquals(11, feedback.sounds)
        viewModel.handleIntent(ProgramCircuitIntent.OnDone)
        assertEquals(listOf(ProgramCircuitEffect.NavigateBack), effects)
    }

    @Test
    fun `skip ends the current phase early`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramCircuitIntent.OnStart)

        viewModel.handleIntent(ProgramCircuitIntent.OnSkip)
        assertEquals(CircuitPhase.Rest(1, 1, 15, 15), viewModel.state.value.phase)

        viewModel.handleIntent(ProgramCircuitIntent.OnSkip)
        assertEquals(CircuitPhase.Work(1, 1, 30, 30), viewModel.state.value.phase)
        assertEquals(2, feedback.sounds)

        advanceSeconds(29)
        assertEquals(CircuitPhase.Work(1, 1, 1, 30), viewModel.state.value.phase) // one timer only
    }

    @Test
    fun `back after starting asks to confirm`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(ProgramCircuitIntent.OnStart)

        viewModel.handleIntent(ProgramCircuitIntent.OnBackPressed)
        assertTrue(viewModel.state.value.showExitConfirmation)
        viewModel.handleIntent(ProgramCircuitIntent.OnDismissExit)
        assertFalse(viewModel.state.value.showExitConfirmation)

        viewModel.handleIntent(ProgramCircuitIntent.OnBackPressed)
        viewModel.handleIntent(ProgramCircuitIntent.OnConfirmExit)
        advanceSeconds(1000)

        assertEquals(listOf(ProgramCircuitEffect.NavigateBack), effects)
        assertTrue(repository.completed.isEmpty())
    }

    // --- Active time (session) ---

    @Test
    fun `the whole run counts, rests and the background included (the countdown never stops)`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramCircuitIntent.OnStart)
        // Work, rest and the phone locked: the countdown keeps going
        advanceSeconds(130)

        repeat(20) { if (!viewModel.state.value.isFinished) viewModel.handleIntent(ProgramCircuitIntent.OnSkip) }

        assertTrue(viewModel.state.value.isFinished)
        assertEquals(listOf<Int?>(130), repository.durations)
        assertNull(sessions.store.session)
    }

    @Test
    fun `leaving stops the count`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramCircuitIntent.OnStart)
        advanceSeconds(40)
        viewModel.handleIntent(ProgramCircuitIntent.OnBackPressed)
        viewModel.handleIntent(ProgramCircuitIntent.OnConfirmExit)
        runCurrent()
        advanceSeconds(600)

        assertEquals(40, sessions.store.session?.activeSeconds)
        assertTrue(repository.durations.isEmpty())
    }

    @Test
    fun `a non-circuit workout shows not found`() = runTest {
        val viewModel = ProgramCircuitViewModel(
            1, 1, 1, FakeProgramRepository(ProgramWorkout.OutdoorRun(2.0, false)), feedback, timeSource, photos, sessions.tracker
        )

        assertTrue(viewModel.state.value.isNotFound)
    }

    private fun TestScope.collectEffects(viewModel: ProgramCircuitViewModel): List<ProgramCircuitEffect> {
        val effects = mutableListOf<ProgramCircuitEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        return effects
    }

    private class FakeWorkoutFeedback : WorkoutFeedback {
        var sounds = 0
        override fun playCompletionSound() { sounds++ }
        override fun vibrate() = Unit
    }

    private class FakeProgramRepository(private val workout: ProgramWorkout) : ProgramRepository {
        val completed = mutableListOf<Triple<Long, Int, Int>>()
        val durations = mutableListOf<Int?>()

        override fun observeWorkout(programId: Long, week: Int, dayIndex: Int): Flow<ProgramWorkoutDetail?> =
            flowOf(ProgramWorkoutDetail(programId, week, dayIndex, workout, false, emptySet()))

        override suspend fun markWorkoutCompleted(programId: Long, week: Int, dayIndex: Int, durationSeconds: Int?) {
            completed += Triple(programId, week, dayIndex)
            durations += durationSeconds
        }

        override fun observePrograms(): Flow<List<ProgramSummary>> = flowOf(emptyList())
        override fun observeProgram(programId: Long): Flow<ProgramDetail?> = flowOf(null)
        override suspend fun previewProgram(profile: ProgramProfile): GeneratedProgram = GeneratedProgram(emptyList())
        override suspend fun createProgram(profile: ProgramProfile): Long = 1
        override suspend fun deleteProgram(programId: Long) = Unit
        override suspend fun markSlotCompleted(programId: Long, week: Int, dayIndex: Int, position: Int) = Unit
        override suspend fun resetWorkout(programId: Long, week: Int, dayIndex: Int) = Unit
        override suspend fun getExercises(): Map<Int, ProgramExercise> = emptyMap()
        override suspend fun getImpactRules(): ImpactRules = ImpactRules(30.0, 60, 60, 15, setOf(87))
    }

    /** Photo repository fake: records every call; [error] makes the next actions fail. */
    private class FakeTrainingPhotoRepository : TrainingPhotoRepository {
        var error: TrainingPhotoError? = null
        val attached = mutableListOf<Pair<Long, String>>()
        val committed = mutableListOf<Pair<Long, StagedPhoto>>()
        val discarded = mutableListOf<StagedPhoto>()
        val removed = mutableListOf<Long>()
        private var staged = 0

        override suspend fun stage(source: TrainingPhotoSource): StagedPhoto {
            error?.let { throw TrainingPhotoException(it) }
            staged++
            return StagedPhoto(File("staged-$staged.jpg"), File("staged-${staged}_thumb.jpg"))
        }

        override suspend fun commit(entryId: Long, staged: StagedPhoto): String {
            error?.let { throw TrainingPhotoException(it) }
            committed += entryId to staged
            return "training_photos/entry$entryId-${committed.size}.jpg"
        }

        override fun discard(staged: StagedPhoto) {
            discarded += staged
        }

        override suspend fun attach(entryId: Long, source: TrainingPhotoSource): String {
            error?.let { throw TrainingPhotoException(it) }
            attached += entryId to source.uri
            return "training_photos/entry$entryId-${attached.size}.jpg"
        }

        override suspend fun remove(entryId: Long) {
            error?.let { throw TrainingPhotoException(it) }
            removed += entryId
        }

        override suspend fun cleanUp(): Int = 0
    }
}
