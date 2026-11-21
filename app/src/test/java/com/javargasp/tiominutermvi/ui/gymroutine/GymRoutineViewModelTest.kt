package com.javargasp.tiominutermvi.ui.gymroutine

import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.model.GymRoutineExercise
import com.javargasp.tiominutermvi.domain.repository.GymProgressRepository
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.StagedPhoto
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoException
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.javargasp.tiominutermvi.testutil.InMemoryGymSessionStore
import com.javargasp.tiominutermvi.testutil.FakeWallClock
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionTracker

@OptIn(ExperimentalCoroutinesApi::class)
class GymRoutineViewModelTest {

    private val photos = FakeTrainingPhotoRepository()

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var routineFlow: MutableStateFlow<GymRoutine?>
    private lateinit var completedFlow: MutableStateFlow<Set<Int>>
    private lateinit var gymRoutineRepository: FakeGymRoutineRepository
    private lateinit var gymProgressRepository: FakeGymProgressRepository
    private lateinit var trainingLogRecorder: FakeTrainingLogRecorder
    private lateinit var clock: FakeWallClock
    private lateinit var sessionStore: InMemoryGymSessionStore
    private lateinit var gymSessions: GymSessionTracker

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        routineFlow = MutableStateFlow(null)
        completedFlow = MutableStateFlow(emptySet())
        gymRoutineRepository = FakeGymRoutineRepository(routineFlow)
        gymProgressRepository = FakeGymProgressRepository(completedFlow)
        trainingLogRecorder = FakeTrainingLogRecorder()
        clock = FakeWallClock()
        // Closing a session resets the routine's progress, as Room does
        sessionStore = InMemoryGymSessionStore(onClearProgress = { completedFlow.value = emptySet() })
        gymSessions = GymSessionTracker(sessionStore, trainingLogRecorder, clock)
    }

    /** Sets of [routineId] completed at these wall-clock times (the exercise screen reports them). */
    private suspend fun setsAt(vararg millis: Long, routineId: Int = 1) {
        for (time in millis) {
            clock.now = time
            gymSessions.onSetCompleted(routineId)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(routineId: Int = 1) =
        GymRoutineViewModel(routineId, gymRoutineRepository, gymProgressRepository, gymSessions, photos)

    @Test
    fun `state reflects routine and completed positions in order`() = runTest {
        val viewModel = createViewModel()

        routineFlow.value = routine(1, exerciseCount = 3)
        completedFlow.value = setOf(2, 0)

        assertEquals(routine(1, exerciseCount = 3), viewModel.state.value.routine)
        assertEquals(setOf(0, 2), viewModel.state.value.validCompletedPositions)
        assertEquals(2, viewModel.state.value.completedCount)
        assertEquals(3, viewModel.state.value.totalCount)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isNotFound)
    }

    @Test
    fun `observation starts only once even if OnLoad is sent twice`() = runTest {
        val viewModel = createViewModel()

        viewModel.handleIntent(GymRoutineIntent.OnLoad)
        viewModel.handleIntent(GymRoutineIntent.OnLoad)

        assertEquals(1, gymRoutineRepository.observeCalls)
        assertEquals(1, gymProgressRepository.observeCalls)
    }

    @Test
    fun `completed positions outside the routine are ignored in the count`() = runTest {
        val viewModel = createViewModel()

        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = setOf(0, 5, 9)

        assertEquals(setOf(0), viewModel.state.value.validCompletedPositions)
        assertEquals(1, viewModel.state.value.completedCount)
        assertEquals(2, viewModel.state.value.totalCount)
    }

    @Test
    fun `all completed shows dialog but does not reset until dismissed`() = runTest {
        val viewModel = createViewModel()

        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = setOf(0, 1)

        assertTrue(viewModel.state.value.isAllCompleted)
        assertTrue(viewModel.state.value.showRoutineCompleted)
        assertEquals(0, gymProgressRepository.resetCalls)

        viewModel.handleIntent(GymRoutineIntent.OnRoutineCompletedDismissed)

        assertEquals(1, gymProgressRepository.resetCalls)
        assertFalse(viewModel.state.value.showRoutineCompleted)
    }

    @Test
    fun `manual reset confirmation shown only when canReset`() = runTest {
        val viewModel = createViewModel()

        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = emptySet()

        viewModel.handleIntent(GymRoutineIntent.OnResetClicked)
        assertFalse(viewModel.state.value.showResetConfirmation)

        completedFlow.value = setOf(0)
        viewModel.handleIntent(GymRoutineIntent.OnResetClicked)
        assertTrue(viewModel.state.value.showResetConfirmation)
    }

    @Test
    fun `confirm reset calls resetRoutine and hides dialog`() = runTest {
        val viewModel = createViewModel()

        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = setOf(0)
        viewModel.handleIntent(GymRoutineIntent.OnResetClicked)
        assertTrue(viewModel.state.value.showResetConfirmation)

        viewModel.handleIntent(GymRoutineIntent.OnConfirmReset)

        assertEquals(1, gymProgressRepository.resetCalls)
        assertFalse(viewModel.state.value.showResetConfirmation)
    }

    @Test
    fun `dismiss reset does not call resetRoutine`() = runTest {
        val viewModel = createViewModel()

        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = setOf(0)
        viewModel.handleIntent(GymRoutineIntent.OnResetClicked)
        assertTrue(viewModel.state.value.showResetConfirmation)

        viewModel.handleIntent(GymRoutineIntent.OnDismissReset)

        assertEquals(0, gymProgressRepository.resetCalls)
        assertFalse(viewModel.state.value.showResetConfirmation)
    }

    @Test
    fun `OnExerciseClicked emits NavigateToExercise also for completed exercise`() = runTest {
        val viewModel = createViewModel()
        val effects = mutableListOf<GymRoutineEffect>()
        val collector = launch(UnconfinedTestDispatcher()) {
            viewModel.effect.collect { effects.add(it) }
        }

        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = setOf(0)

        viewModel.handleIntent(GymRoutineIntent.OnExerciseClicked(0))
        viewModel.handleIntent(GymRoutineIntent.OnExerciseClicked(1))

        assertEquals(
            listOf(
                GymRoutineEffect.NavigateToExercise(1, 0),
                GymRoutineEffect.NavigateToExercise(1, 1)
            ),
            effects
        )
        collector.cancel()
    }

    @Test
    fun `routine not found sets isNotFound`() = runTest {
        val viewModel = createViewModel()

        routineFlow.value = null

        assertTrue(viewModel.state.value.isNotFound)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.routine)
    }

    @Test
    fun `routine removed while open shows not found and closes dialogs`() = runTest {
        val viewModel = createViewModel()
        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = setOf(0, 1)
        assertTrue(viewModel.state.value.showRoutineCompleted)

        routineFlow.value = null

        assertTrue(viewModel.state.value.isNotFound)
        assertNull(viewModel.state.value.routine)
        assertFalse(viewModel.state.value.showRoutineCompleted)
        assertFalse(viewModel.state.value.showResetConfirmation)
        assertEquals(0, viewModel.state.value.totalCount)
    }

    @Test
    fun `routine with zero exercises never shows the completed dialog`() = runTest {
        val viewModel = createViewModel()

        routineFlow.value = routine(1, exerciseCount = 0)
        completedFlow.value = setOf(0)

        assertFalse(viewModel.state.value.isNotFound)
        assertEquals(0, viewModel.state.value.totalCount)
        assertEquals(0, viewModel.state.value.completedCount)
        assertFalse(viewModel.state.value.isAllCompleted)
        assertFalse(viewModel.state.value.showRoutineCompleted)
        assertFalse(viewModel.state.value.canReset)
    }

    @Test
    fun `state survives OnLoad after a configuration change`() = runTest {
        val viewModel = createViewModel()
        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = setOf(0)
        viewModel.handleIntent(GymRoutineIntent.OnResetClicked)

        viewModel.handleIntent(GymRoutineIntent.OnLoad)

        assertTrue(viewModel.state.value.showResetConfirmation)
        assertEquals(setOf(0), viewModel.state.value.validCompletedPositions)
        assertEquals(1, gymRoutineRepository.observeCalls)
    }

    @Test
    fun `OnBackPressed emits NavigateBack`() = runTest {
        val viewModel = createViewModel()
        val effects = mutableListOf<GymRoutineEffect>()
        val collector = launch(UnconfinedTestDispatcher()) {
            viewModel.effect.collect { effects.add(it) }
        }

        viewModel.handleIntent(GymRoutineIntent.OnBackPressed)

        assertEquals(listOf(GymRoutineEffect.NavigateBack), effects)
        collector.cancel()
    }

    // ------------------------------------------------------------------
    // Training history
    // ------------------------------------------------------------------

    @Test
    fun `completing the routine records one GYM entry with the session's duration, first to last set`() = runTest {
        setsAt(1_000L, 31_000L, 61_000L, routineId = 7)
        val viewModel = createViewModel(routineId = 7)
        routineFlow.value = routine(7, exerciseCount = 3)

        completedFlow.value = setOf(0, 1, 2)

        assertTrue(viewModel.state.value.showRoutineCompleted)
        assertEquals(
            listOf(FakeTrainingLogRecorder.Recorded(TrainingSource.GYM, "gym:7", "Routine 7", 60)),
            trainingLogRecorder.recorded
        )
        // Recorded before the reset, which only happens on dismiss
        assertEquals(0, gymProgressRepository.resetCalls)
    }

    @Test
    fun `a routine of a single set records 0 seconds`() = runTest {
        setsAt(5_000L)
        createViewModel()
        routineFlow.value = routine(1, exerciseCount = 1)

        completedFlow.value = setOf(0)

        assertEquals(0, trainingLogRecorder.recorded.single().durationSeconds)
    }

    @Test
    fun `the dialog shown again after the entry was recorded records nothing`() = runTest {
        // E.g. the process was killed while the completion dialog was open: no session is left
        val viewModel = createViewModel()
        routineFlow.value = routine(1, exerciseCount = 2)

        completedFlow.value = setOf(0, 1)

        assertTrue(viewModel.state.value.showRoutineCompleted)
        assertTrue(trainingLogRecorder.recorded.isEmpty())
        assertFalse(viewModel.state.value.completionPhoto.canAddPhoto)
    }

    @Test
    fun `re-sent OnLoad and later emissions do not record again`() = runTest {
        setsAt(0L)
        val viewModel = createViewModel()
        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = setOf(0, 1)

        viewModel.handleIntent(GymRoutineIntent.OnLoad)
        routineFlow.value = routine(1, exerciseCount = 2).copy(routineName = "Renamed")
        completedFlow.value = setOf(0, 1, 5)

        assertTrue(viewModel.state.value.showRoutineCompleted)
        assertEquals(1, trainingLogRecorder.recorded.size)
    }

    @Test
    fun `completing the routine again after the dismiss records a new entry`() = runTest {
        setsAt(0L)
        val viewModel = createViewModel()
        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = setOf(0, 1)
        viewModel.handleIntent(GymRoutineIntent.OnRoutineCompletedDismissed)
        assertEquals(1, trainingLogRecorder.recorded.size)

        setsAt(600_000L)
        completedFlow.value = setOf(0)
        assertEquals(1, trainingLogRecorder.recorded.size)
        setsAt(900_000L)
        completedFlow.value = setOf(0, 1)

        assertEquals(2, trainingLogRecorder.recorded.size)
    }

    @Test
    fun `an empty routine or a partial completion records nothing`() = runTest {
        setsAt(0L)
        createViewModel()
        routineFlow.value = routine(1, exerciseCount = 0)
        completedFlow.value = setOf(0)
        routineFlow.value = routine(1, exerciseCount = 3)
        completedFlow.value = setOf(0, 1)

        assertTrue(trainingLogRecorder.recorded.isEmpty())
    }

    @Test
    fun `a recording failure does not change the completion flow`() = runTest {
        setsAt(0L)
        trainingLogRecorder.error = IllegalStateException("disk full")
        val viewModel = createViewModel()
        routineFlow.value = routine(1, exerciseCount = 2)

        completedFlow.value = setOf(0, 1)

        assertTrue(viewModel.state.value.showRoutineCompleted)
        viewModel.handleIntent(GymRoutineIntent.OnRoutineCompletedDismissed)
        assertFalse(viewModel.state.value.showRoutineCompleted)
        assertEquals(1, gymProgressRepository.resetCalls)
        assertTrue(viewModel.state.value.completedPositions.isEmpty())
    }

    // ------------------------------------------------------------------
    // Gym session: reset by hand, inactivity close
    // ------------------------------------------------------------------

    @Test
    fun `a reset by hand discards the session, so it is never saved`() = runTest {
        setsAt(0L, 600_000L)
        val viewModel = createViewModel()
        routineFlow.value = routine(1, exerciseCount = 3)
        completedFlow.value = setOf(0)

        viewModel.handleIntent(GymRoutineIntent.OnResetClicked)
        viewModel.handleIntent(GymRoutineIntent.OnConfirmReset)
        clock.now = 10 * HOUR
        viewModel.handleIntent(GymRoutineIntent.OnResume)

        assertTrue(sessionStore.sessions.isEmpty())
        assertTrue(trainingLogRecorder.recorded.isEmpty())
    }

    @Test
    fun `opening the screen after 2 hours without a set saves the workout, resets it and says so`() = runTest {
        setsAt(0L, 25 * MINUTE, 65 * MINUTE)
        val viewModel = createViewModel()
        val effects = mutableListOf<GymRoutineEffect>()
        val collector = launch(UnconfinedTestDispatcher()) { viewModel.effect.collect { effects.add(it) } }
        routineFlow.value = routine(1, exerciseCount = 3)
        completedFlow.value = setOf(0, 1)

        clock.now = 65 * MINUTE + 2 * HOUR + 1
        viewModel.handleIntent(GymRoutineIntent.OnResume)

        assertEquals(
            listOf(FakeTrainingLogRecorder.Recorded(TrainingSource.GYM, "gym:1", "Routine 1", 65 * 60)),
            trainingLogRecorder.recorded
        )
        assertTrue(viewModel.state.value.completedPositions.isEmpty())
        assertFalse(viewModel.state.value.showRoutineCompleted)
        assertEquals(listOf<GymRoutineEffect>(GymRoutineEffect.ShowSessionSaved(65 * 60)), effects)

        // Resumed again: nothing new to save or say
        viewModel.handleIntent(GymRoutineIntent.OnResume)
        assertEquals(1, trainingLogRecorder.recorded.size)
        assertEquals(1, effects.size)
        collector.cancel()
    }

    @Test
    fun `resuming within 2 hours keeps the progress`() = runTest {
        setsAt(0L, 30 * MINUTE)
        val viewModel = createViewModel()
        routineFlow.value = routine(1, exerciseCount = 3)
        completedFlow.value = setOf(0)

        clock.now = 30 * MINUTE + 2 * HOUR
        viewModel.handleIntent(GymRoutineIntent.OnResume)

        assertEquals(setOf(0), viewModel.state.value.completedPositions)
        assertTrue(trainingLogRecorder.recorded.isEmpty())
    }

    // ------------------------------------------------------------------
    // Completion photo
    // ------------------------------------------------------------------

    private val gallery = TrainingPhotoSource("content://picker/1", fromCamera = false)

    private suspend fun completedViewModel(): GymRoutineViewModel {
        setsAt(0L, 60_000L)
        val viewModel = createViewModel()
        routineFlow.value = routine(1, exerciseCount = 2)
        completedFlow.value = setOf(0, 1)
        return viewModel
    }

    @Test
    fun `the completion dialog knows the entry just recorded`() = runTest {
        trainingLogRecorder.nextEntryId = 42

        val viewModel = completedViewModel()

        assertEquals(CompletionPhotoState(entryId = 42), viewModel.state.value.completionPhoto)
        assertTrue(viewModel.state.value.completionPhoto.canAddPhoto)
    }

    @Test
    fun `a photo is attached to that entry`() = runTest {
        trainingLogRecorder.nextEntryId = 42
        val viewModel = completedViewModel()

        viewModel.handleIntent(GymRoutineIntent.OnCompletionPhotoSelected(gallery))

        assertEquals(listOf(42L to "content://picker/1"), photos.attached)
        assertEquals(
            CompletionPhotoState(entryId = 42, photoPath = "training_photos/entry42-1.jpg"),
            viewModel.state.value.completionPhoto
        )
    }

    @Test
    fun `a second photo replaces the first`() = runTest {
        trainingLogRecorder.nextEntryId = 42
        val viewModel = completedViewModel()
        viewModel.handleIntent(GymRoutineIntent.OnCompletionPhotoSelected(gallery))

        viewModel.handleIntent(GymRoutineIntent.OnCompletionPhotoSelected(gallery.copy(uri = "content://picker/2")))

        assertEquals(listOf(42L, 42L), photos.attached.map { it.first })
        assertEquals("training_photos/entry42-2.jpg", viewModel.state.value.completionPhoto.photoPath)
    }

    @Test
    fun `a photo failure shows a message and keeps the completion`() = runTest {
        val viewModel = completedViewModel()
        val effects = mutableListOf<GymRoutineEffect>()
        val collector = launch(UnconfinedTestDispatcher()) { viewModel.effect.collect { effects.add(it) } }
        photos.error = TrainingPhotoError.UNREADABLE_IMAGE

        viewModel.handleIntent(GymRoutineIntent.OnCompletionPhotoSelected(gallery))

        assertEquals(listOf<GymRoutineEffect>(GymRoutineEffect.ShowPhotoError(TrainingPhotoError.UNREADABLE_IMAGE)), effects)
        assertEquals(CompletionPhotoState(entryId = 1), viewModel.state.value.completionPhoto)
        assertTrue(viewModel.state.value.showRoutineCompleted)
        assertEquals(1, trainingLogRecorder.recorded.size)
        collector.cancel()
    }

    @Test
    fun `without a recorded entry there is no photo to add`() = runTest {
        trainingLogRecorder.error = IllegalStateException("disk full")
        val viewModel = completedViewModel()

        viewModel.handleIntent(GymRoutineIntent.OnCompletionPhotoSelected(gallery))

        assertNull(viewModel.state.value.completionPhoto.entryId)
        assertFalse(viewModel.state.value.completionPhoto.canAddPhoto)
        assertTrue(photos.attached.isEmpty())
    }

    @Test
    fun `a new completion starts without the previous photo`() = runTest {
        trainingLogRecorder.nextEntryId = 42
        val viewModel = completedViewModel()
        viewModel.handleIntent(GymRoutineIntent.OnCompletionPhotoSelected(gallery))
        viewModel.handleIntent(GymRoutineIntent.OnRoutineCompletedDismissed)

        setsAt(600_000L)
        completedFlow.value = setOf(0, 1)

        assertEquals(CompletionPhotoState(entryId = 43), viewModel.state.value.completionPhoto)
    }

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    private class FakeGymRoutineRepository(
        private val routineFlow: MutableStateFlow<GymRoutine?>
    ) : GymRoutineRepository {

        var observeCalls = 0

        override fun observeGymRoutines(): Flow<List<GymRoutine>> {
            observeCalls++
            return MutableStateFlow(emptyList())
        }

        override fun observeGymRoutine(routineId: Int): Flow<GymRoutine?> {
            observeCalls++
            return routineFlow
        }

        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }

    private class FakeGymProgressRepository(
        private val completedFlow: MutableStateFlow<Set<Int>>
    ) : GymProgressRepository {

        var observeCalls = 0
        var resetCalls = 0
        var completionTimes: List<Long> = emptyList()

        override fun observeCompletedPositions(routineId: Int): Flow<Set<Int>> {
            observeCalls++
            return completedFlow
        }

        override suspend fun getCompletionTimes(routineId: Int): List<Long> = completionTimes

        override suspend fun markCompleted(routineId: Int, position: Int) {
            completedFlow.value = completedFlow.value + position
        }

        override suspend fun resetRoutine(routineId: Int) {
            resetCalls++
            completedFlow.value = emptySet()
        }
    }

    private class FakeTrainingLogRecorder : SessionEntryRecorder {
        /** Id returned for the next recorded entry. */
        var nextEntryId = 1L

        data class Recorded(val source: TrainingSource, val refId: String?, val title: String, val durationSeconds: Int?)

        val recorded = mutableListOf<Recorded>()

        /** When set, record throws it. */
        var error: Exception? = null

        override suspend fun recordSession(
            source: TrainingSource,
            refId: String?,
            title: String,
            durationSeconds: Int,
            completedAt: Long,
            dateOfMillis: Long
        ): Long {
            error?.let { throw it }
            recorded.add(Recorded(source, refId, title, durationSeconds))
            return nextEntryId++
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun routine(id: Int, exerciseCount: Int): GymRoutine = GymRoutine(
        routineId = id,
        routineName = "Routine $id",
        exercises = List(exerciseCount) { index ->
            GymRoutineExercise(
                position = index,
                exerciseId = index + 1,
                exercise = Exercise(
                    exerciseId = index + 1,
                    name = "Exercise ${index + 1}",
                    animation = "anim${index + 1}.gif"
                ),
                type = ExerciseType.REPS,
                reps = listOf(10, 10, 10),
                restSeconds = 60
            )
        }
    )

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

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
