package com.javargasp.tiominutermvi.ui.program.treadmill

import com.javargasp.tiominutermvi.domain.feedback.WorkoutFeedback
import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.ProgramDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegment
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegmentLabel
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
import kotlinx.coroutines.flow.MutableStateFlow
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
class ProgramTreadmillViewModelTest {

    private val photos = FakeTrainingPhotoRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val feedback = FakeWorkoutFeedback()
    private val timeSource = MonotonicTimeSource { mainDispatcherRule.dispatcher.scheduler.currentTime }
    private val segments = listOf(
        TreadmillSegment(300, 5.0, 1.0, TreadmillSegmentLabel.WARM_UP),
        TreadmillSegment(60, 7.0, 2.0, TreadmillSegmentLabel.JOG),
        TreadmillSegment(120, 5.0, 1.0, TreadmillSegmentLabel.WALK),
        TreadmillSegment(300, 5.0, 0.0, TreadmillSegmentLabel.COOL_DOWN)
    )
    private val repository = FakeProgramRepository(ProgramWorkout.Treadmill(segments))

    private val sessions = ActiveSessionTestKit()

    private fun createViewModel() = ProgramTreadmillViewModel(1, 2, 1, repository, feedback, timeSource, photos, sessions.tracker)

    private fun TestScope.advanceSeconds(seconds: Int) {
        advanceTimeBy(seconds * 1000L)
        runCurrent()
    }

    @Test
    fun `loads segments, not started`() = runTest {
        val state = createViewModel().state.value

        assertEquals(segments, state.segments)
        assertEquals(0, state.currentIndex)
        assertEquals(300, state.remainingSeconds)
        assertEquals(780, state.totalRemainingSeconds)
        assertFalse(state.isStarted)
        assertEquals(segments[1], state.nextSegment)
    }

    @Test
    fun `segments progress with feedback at every change`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)

        advanceSeconds(100)
        assertEquals(200, viewModel.state.value.remainingSeconds)
        assertEquals(680, viewModel.state.value.totalRemainingSeconds)

        advanceSeconds(200)
        assertEquals(1, viewModel.state.value.currentIndex)
        assertEquals(60, viewModel.state.value.remainingSeconds)
        assertEquals(1, feedback.sounds)
        assertEquals(1, feedback.vibrations)

        advanceSeconds(60)
        assertEquals(2, viewModel.state.value.currentIndex)
        assertEquals(2, feedback.sounds)
    }

    @Test
    fun `pause stops the countdown and resume continues it`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)
        advanceSeconds(30)

        viewModel.handleIntent(ProgramTreadmillIntent.OnPause)
        assertFalse(viewModel.state.value.isRunning)
        advanceSeconds(500)
        assertEquals(270, viewModel.state.value.remainingSeconds)
        assertEquals(0, viewModel.state.value.currentIndex)

        viewModel.handleIntent(ProgramTreadmillIntent.OnResume)
        advanceSeconds(70)
        assertTrue(viewModel.state.value.isRunning)
        assertEquals(200, viewModel.state.value.remainingSeconds)
    }

    @Test
    fun `skip moves to the next segment with feedback, and stays paused when paused`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)

        viewModel.handleIntent(ProgramTreadmillIntent.OnSkipSegment)
        assertEquals(1, viewModel.state.value.currentIndex)
        assertEquals(60, viewModel.state.value.remainingSeconds)
        assertEquals(1, feedback.sounds)

        viewModel.handleIntent(ProgramTreadmillIntent.OnPause)
        viewModel.handleIntent(ProgramTreadmillIntent.OnSkipSegment)
        advanceSeconds(200)
        assertEquals(2, viewModel.state.value.currentIndex)
        assertEquals(120, viewModel.state.value.remainingSeconds)
        assertFalse(viewModel.state.value.isRunning)
    }

    @Test
    fun `the end completes the workout`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)

        advanceSeconds(780)

        assertTrue(viewModel.state.value.isFinished)
        assertEquals(0, viewModel.state.value.totalRemainingSeconds)
        assertEquals(listOf(Triple(1L, 2, 1)), repository.completed)
        // The training history gets the time since Start
        assertEquals(listOf<Int?>(780), repository.durations)
        assertEquals(4, feedback.sounds)
    }

    @Test
    fun `skipping every segment also completes it`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)

        repeat(4) { viewModel.handleIntent(ProgramTreadmillIntent.OnSkipSegment) }
        viewModel.handleIntent(ProgramTreadmillIntent.OnDone)

        assertTrue(viewModel.state.value.isFinished)
        assertEquals(1, repository.completed.size)
        assertEquals(listOf(ProgramTreadmillEffect.NavigateBack), effects)
    }

    @Test
    fun `the finished view attaches a photo to the entry of this completion`() = runTest {
        repository.completionEntryId.value = 7 // an earlier run of this workout
        val viewModel = createViewModel()
        assertNull(viewModel.state.value.completionPhoto.entryId)
        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)
        repeat(4) { viewModel.handleIntent(ProgramTreadmillIntent.OnSkipSegment) }

        viewModel.handleIntent(ProgramTreadmillIntent.OnCompletionPhotoSelected(TrainingPhotoSource("content://p/1", false)))

        assertTrue(viewModel.state.value.isFinished)
        assertEquals(listOf(101L to "content://p/1"), photos.attached)
        assertEquals("training_photos/entry101-1.jpg", viewModel.state.value.completionPhoto.photoPath)
    }

    @Test
    fun `a photo failure on the finished view is a message`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)
        repeat(4) { viewModel.handleIntent(ProgramTreadmillIntent.OnSkipSegment) }
        photos.error = TrainingPhotoError.FAILED

        viewModel.handleIntent(ProgramTreadmillIntent.OnCompletionPhotoSelected(TrainingPhotoSource("content://p/1", false)))

        assertEquals(listOf<ProgramTreadmillEffect>(ProgramTreadmillEffect.ShowPhotoError(TrainingPhotoError.FAILED)), effects)
        assertFalse(viewModel.state.value.completionPhoto.isProcessing)
        assertTrue(viewModel.state.value.isFinished)
    }

    @Test
    fun `back while running asks to confirm`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)
        viewModel.handleIntent(ProgramTreadmillIntent.OnBackPressed)
        assertTrue(viewModel.state.value.showExitConfirmation)

        viewModel.handleIntent(ProgramTreadmillIntent.OnConfirmExit)
        advanceSeconds(1000)

        assertEquals(listOf(ProgramTreadmillEffect.NavigateBack), effects)
        assertTrue(repository.completed.isEmpty())
        assertEquals(0, feedback.sounds)
    }

    @Test
    fun `back before start leaves directly`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(ProgramTreadmillIntent.OnBackPressed)

        assertEquals(listOf(ProgramTreadmillEffect.NavigateBack), effects)
    }

    // --- Active time (session) ---

    @Test
    fun `the running countdown counts, in the background too, an explicit pause doesn't`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)
        advanceSeconds(60)
        viewModel.handleIntent(ProgramTreadmillIntent.OnPause)
        advanceSeconds(300)
        viewModel.handleIntent(ProgramTreadmillIntent.OnResume)
        // 11 minutes more with the screen locked: the countdown keeps going and so does the count
        advanceSeconds(660)

        repeat(4) { if (!viewModel.state.value.isFinished) viewModel.handleIntent(ProgramTreadmillIntent.OnSkipSegment) }

        assertTrue(viewModel.state.value.isFinished)
        assertEquals(listOf<Int?>(720), repository.durations)
        // Recorded once, by the finish; the session is gone
        assertNull(sessions.store.session)
        sessions.clock.now += 5 * 3_600_000L
        sessions.tracker.closeInactive()
        assertTrue(sessions.store.abandoned.isEmpty())
    }

    @Test
    fun `after a process death the time up to the last save counts`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)
        advanceSeconds(22)

        // Saved every 5 s: the process dies here, the stored session has 20 s
        assertEquals(20, sessions.store.session?.activeSeconds)
        sessions.clock.now += 2 * 3_600_000L + 1
        sessions.tracker.closeInactive()
        assertEquals(listOf(20), sessions.store.abandoned.map { it.activeSeconds })

        // A running workout must be stopped before the test drains virtual time
        viewModel.handleIntent(ProgramTreadmillIntent.OnPause)
    }

    @Test
    fun `leaving unfinished keeps the active time, recorded after 2 hours of inactivity`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)
        advanceSeconds(90)
        viewModel.handleIntent(ProgramTreadmillIntent.OnBackPressed)
        viewModel.handleIntent(ProgramTreadmillIntent.OnConfirmExit)
        runCurrent()

        assertEquals(90, sessions.store.session?.activeSeconds)
        assertTrue(repository.durations.isEmpty())

        sessions.clock.now += 2 * 3_600_000L + 1
        sessions.tracker.closeInactive()
        assertEquals(listOf("program:1:2:1" to 90), sessions.store.abandoned.map { it.key to it.activeSeconds })
        assertTrue(repository.completed.isEmpty())
    }

    @Test
    fun `starting records an unfinished cardio workout first and says so`() = runTest {
        sessions.tracker.start("program:9:1:0")
        sessions.tracker.update("program:9:1:0", 500)
        val viewModel = createViewModel()
        val effects = mutableListOf<ProgramTreadmillEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects.add(it) } }

        viewModel.handleIntent(ProgramTreadmillIntent.OnStart)

        assertEquals(listOf<ProgramTreadmillEffect>(ProgramTreadmillEffect.ShowSessionSaved(500)), effects)
        assertEquals(listOf(500), sessions.store.abandoned.map { it.activeSeconds })
        assertEquals("program:1:2:1", sessions.store.session?.key)
    }

    @Test
    fun `a non-treadmill workout shows not found`() = runTest {
        val viewModel = ProgramTreadmillViewModel(
            1, 2, 1, FakeProgramRepository(ProgramWorkout.OutdoorRun(2.0, false)), feedback, timeSource, photos, sessions.tracker
        )

        assertTrue(viewModel.state.value.isNotFound)
    }

    private fun TestScope.collectEffects(viewModel: ProgramTreadmillViewModel): List<ProgramTreadmillEffect> {
        val effects = mutableListOf<ProgramTreadmillEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        return effects
    }

    private class FakeWorkoutFeedback : WorkoutFeedback {
        var sounds = 0
        var vibrations = 0
        override fun playCompletionSound() { sounds++ }
        override fun vibrate() { vibrations++ }
    }

    private class FakeProgramRepository(private val workout: ProgramWorkout) : ProgramRepository {
        val completed = mutableListOf<Triple<Long, Int, Int>>()
        val durations = mutableListOf<Int?>()

        override fun observeWorkout(programId: Long, week: Int, dayIndex: Int): Flow<ProgramWorkoutDetail?> =
            flowOf(ProgramWorkoutDetail(programId, week, dayIndex, workout, false, emptySet()))

        override suspend fun markWorkoutCompleted(programId: Long, week: Int, dayIndex: Int, durationSeconds: Int?) {
            completed += Triple(programId, week, dayIndex)
            durations += durationSeconds
            // Like ProgramRepositoryImpl: the completion's training-history entry
            completionEntryId.value = 100L + completed.size
        }

        val completionEntryId = MutableStateFlow<Long?>(null)

        override fun observeCompletionEntryId(programId: Long, week: Int, dayIndex: Int): Flow<Long?> = completionEntryId

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
