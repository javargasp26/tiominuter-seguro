package com.javargasp.tiominutermvi.ui.program.outdoorrun

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.javargasp.tiominutermvi.testutil.ActiveSessionTestKit
import org.junit.Assert.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramOutdoorRunViewModelTest {

    private val photos = FakeTrainingPhotoRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Extra time on top of virtual time, to simulate time passing without ticks (background). */
    private var offsetMillis = 0L
    private val timeSource = MonotonicTimeSource { mainDispatcherRule.dispatcher.scheduler.currentTime + offsetMillis }
    private val repository = FakeProgramRepository(ProgramWorkout.OutdoorRun(3.3, walk = false))

    private val sessions = ActiveSessionTestKit()

    private fun createViewModel() = ProgramOutdoorRunViewModel(1, 3, 1, repository, timeSource, photos, sessions.tracker)

    private fun TestScope.advanceSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1000L)
        runCurrent()
    }

    @Test
    fun `shows the target distance`() = runTest {
        val state = createViewModel().state.value

        assertFalse(state.isLoading)
        assertEquals(3.3, state.distanceKm, 0.0)
        assertFalse(state.walk)
        assertFalse(state.isStarted)
    }

    @Test
    fun `stopwatch counts, pauses and resumes`() = runTest {
        val viewModel = createViewModel()

        viewModel.handleIntent(ProgramOutdoorRunIntent.OnStart)
        advanceSeconds(65)
        assertEquals(65, viewModel.state.value.elapsedSeconds)

        viewModel.handleIntent(ProgramOutdoorRunIntent.OnPause)
        advanceSeconds(30)
        assertEquals(65, viewModel.state.value.elapsedSeconds)

        viewModel.handleIntent(ProgramOutdoorRunIntent.OnResume)
        advanceSeconds(10)
        assertEquals(75, viewModel.state.value.elapsedSeconds)

        viewModel.handleIntent(ProgramOutdoorRunIntent.OnPause)
    }

    @Test
    fun `time spent in the background is counted`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnStart)

        offsetMillis = 120_000L
        advanceSeconds(1)

        assertEquals(121, viewModel.state.value.elapsedSeconds)
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnPause)
    }

    @Test
    fun `finish asks to confirm, then marks the workout completed`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnStart)
        advanceSeconds(600)

        viewModel.handleIntent(ProgramOutdoorRunIntent.OnFinishClicked)
        assertTrue(viewModel.state.value.showFinishConfirmation)
        assertTrue(repository.completed.isEmpty())

        viewModel.handleIntent(ProgramOutdoorRunIntent.OnConfirmFinish)

        assertTrue(viewModel.state.value.isFinished)
        assertFalse(viewModel.state.value.isRunning)
        assertEquals(listOf(Triple(1L, 3, 1)), repository.completed)
        // The training history gets the stopwatch time
        assertEquals(listOf<Int?>(600), repository.durations)
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnDone)
        assertEquals(listOf(ProgramOutdoorRunEffect.NavigateBack), effects)
    }

    @Test
    fun `dismissing finish keeps running, finish before start does nothing`() = runTest {
        val viewModel = createViewModel()

        viewModel.handleIntent(ProgramOutdoorRunIntent.OnFinishClicked)
        assertFalse(viewModel.state.value.showFinishConfirmation)

        viewModel.handleIntent(ProgramOutdoorRunIntent.OnStart)
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnFinishClicked)
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnDismissFinish)
        advanceSeconds(5)

        assertTrue(viewModel.state.value.isRunning)
        assertEquals(5, viewModel.state.value.elapsedSeconds)
        assertTrue(repository.completed.isEmpty())
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnPause)
    }

    @Test
    fun `back after starting asks to confirm and leaves without completing`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnStart)

        viewModel.handleIntent(ProgramOutdoorRunIntent.OnBackPressed)
        assertTrue(viewModel.state.value.showExitConfirmation)
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnConfirmExit)

        assertEquals(listOf(ProgramOutdoorRunEffect.NavigateBack), effects)
        assertTrue(repository.completed.isEmpty())
    }

    // --- Active time (session) ---

    @Test
    fun `a run counts in the background and is saved meanwhile, pauses excluded`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnStart)
        advanceSeconds(60)
        advanceSeconds(600)
        // Saved every few seconds while running, also with the phone in a pocket
        assertTrue((sessions.store.session?.activeSeconds ?: 0) >= 655)
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnPause)
        advanceSeconds(300)

        viewModel.handleIntent(ProgramOutdoorRunIntent.OnFinishClicked)
        viewModel.handleIntent(ProgramOutdoorRunIntent.OnConfirmFinish)

        assertEquals(listOf<Int?>(660), repository.durations)
        assertEquals(660, viewModel.state.value.elapsedSeconds)
        assertNull(sessions.store.session)
    }

    @Test
    fun `brisk walk flag comes from the workout`() = runTest {
        val viewModel = ProgramOutdoorRunViewModel(
            1, 1, 1, FakeProgramRepository(ProgramWorkout.OutdoorRun(2.0, walk = true)), timeSource, photos, sessions.tracker
        )

        assertTrue(viewModel.state.value.walk)
    }

    private fun TestScope.collectEffects(viewModel: ProgramOutdoorRunViewModel): List<ProgramOutdoorRunEffect> {
        val effects = mutableListOf<ProgramOutdoorRunEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        return effects
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
