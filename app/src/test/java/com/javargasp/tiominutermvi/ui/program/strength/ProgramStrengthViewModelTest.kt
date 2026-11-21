package com.javargasp.tiominutermvi.ui.program.strength

import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
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
import com.javargasp.tiominutermvi.domain.traininglog.photo.StagedPhoto
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoException
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramStrengthViewModelTest {

    private val photos = FakeTrainingPhotoRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val workout = ProgramWorkout.Strength(
        StrengthDayTitle.UPPER_A,
        listOf(
            StrengthSlot(0, 116, ProgramSlotType.REPS, listOf(6, 6, 6, 6), 90),
            StrengthSlot(1, 68, ProgramSlotType.REPS, listOf(6, 6, 6, 6), 90),
            StrengthSlot(2, 4, ProgramSlotType.TIME, listOf(40, 40, 40), 60)
        )
    )
    private val repository = FakeProgramRepository(workout)

    private fun createViewModel() = ProgramStrengthViewModel(1, 2, 0, repository, photos)

    @Test
    fun `exercises and progress are shown`() = runTest {
        repository.completeSlots(0)
        val viewModel = createViewModel()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(workout, state.workout)
        assertEquals(1, state.completedCount)
        assertEquals(3, state.totalCount)
        assertEquals(2, state.week)
        assertTrue(state.canReset)
        assertFalse(state.showWorkoutCompleted)
    }

    @Test
    fun `completing the last exercise shows the workout completed dialog`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        repository.completeSlots(0, 1)
        assertFalse(viewModel.state.value.showWorkoutCompleted)
        repository.completeSlots(0, 1, 2) // the repository completes the workout with the last slot

        assertTrue(viewModel.state.value.showWorkoutCompleted)
        assertTrue(viewModel.state.value.isWorkoutCompleted)

        viewModel.handleIntent(ProgramStrengthIntent.OnWorkoutCompletedDismissed)

        assertFalse(viewModel.state.value.showWorkoutCompleted)
        assertEquals(listOf(ProgramStrengthEffect.NavigateBack), effects)
    }

    @Test
    fun `opening an already completed workout shows no dialog`() = runTest {
        repository.completeSlots(0, 1, 2)
        val viewModel = createViewModel()

        assertTrue(viewModel.state.value.allCompleted)
        assertFalse(viewModel.state.value.showWorkoutCompleted)
    }

    @Test
    fun `reset needs confirmation and clears the exercises`() = runTest {
        repository.completeSlots(0, 1)
        val viewModel = createViewModel()

        viewModel.handleIntent(ProgramStrengthIntent.OnResetClicked)
        assertTrue(viewModel.state.value.showResetConfirmation)
        viewModel.handleIntent(ProgramStrengthIntent.OnConfirmReset)

        assertEquals(1, repository.resets)
        assertFalse(viewModel.state.value.showResetConfirmation)
        assertEquals(0, viewModel.state.value.completedCount)
    }

    @Test
    fun `reset is not offered with nothing completed, and dismiss keeps progress`() = runTest {
        val viewModel = createViewModel()

        viewModel.handleIntent(ProgramStrengthIntent.OnResetClicked)
        assertFalse(viewModel.state.value.showResetConfirmation)

        repository.completeSlots(1)
        viewModel.handleIntent(ProgramStrengthIntent.OnResetClicked)
        viewModel.handleIntent(ProgramStrengthIntent.OnDismissReset)
        assertEquals(0, repository.resets)
        assertEquals(1, viewModel.state.value.completedCount)
    }

    @Test
    fun `tapping an exercise opens it, also when done`() = runTest {
        repository.completeSlots(1)
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(ProgramStrengthIntent.OnExerciseClicked(1))
        viewModel.handleIntent(ProgramStrengthIntent.OnExerciseClicked(2))

        assertEquals(
            listOf(ProgramStrengthEffect.NavigateToExercise(1), ProgramStrengthEffect.NavigateToExercise(2)),
            effects
        )
    }

    @Test
    fun `a missing or non-strength workout shows not found`() = runTest {
        val missing = ProgramStrengthViewModel(1, 2, 0, FakeProgramRepository(null), photos)
        val cardio = ProgramStrengthViewModel(1, 2, 0, FakeProgramRepository(ProgramWorkout.OutdoorRun(1.5, false)), photos)

        assertTrue(missing.state.value.isNotFound)
        assertTrue(cardio.state.value.isNotFound)
    }

    private fun TestScope.collectEffects(viewModel: ProgramStrengthViewModel): List<ProgramStrengthEffect> {
        val effects = mutableListOf<ProgramStrengthEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        return effects
    }

    /** Emits the workout with the completed positions; completing every slot completes the workout. */
    // ------------------------------------------------------------------
    // Completion photo
    // ------------------------------------------------------------------

    private val gallery = TrainingPhotoSource("content://picker/1", fromCamera = false)

    @Test
    fun `an entry recorded before this visit is not offered for a photo`() = runTest {
        repository.completionEntryId.value = 5
        val viewModel = createViewModel()

        assertNull(viewModel.state.value.completionPhoto.entryId)
    }

    @Test
    fun `the workout completed dialog attaches a photo to the entry just recorded`() = runTest {
        repository.completionEntryId.value = 5 // an earlier completion
        val viewModel = createViewModel()
        repository.completeSlots(0, 1, 2)
        repository.completionEntryId.value = null // the new completion starts
        repository.completionEntryId.value = 9 // and is recorded

        viewModel.handleIntent(ProgramStrengthIntent.OnCompletionPhotoSelected(gallery))

        assertTrue(viewModel.state.value.showWorkoutCompleted)
        assertEquals(listOf(9L to "content://picker/1"), photos.attached)
        assertEquals(
            CompletionPhotoState(entryId = 9, photoPath = "training_photos/entry9-1.jpg"),
            viewModel.state.value.completionPhoto
        )
    }

    @Test
    fun `a photo failure is a message and the dialog stays`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)
        repository.completeSlots(0, 1, 2)
        repository.completionEntryId.value = 9
        photos.error = TrainingPhotoError.NO_SPACE

        viewModel.handleIntent(ProgramStrengthIntent.OnCompletionPhotoSelected(gallery))

        assertEquals(listOf<ProgramStrengthEffect>(ProgramStrengthEffect.ShowPhotoError(TrainingPhotoError.NO_SPACE)), effects)
        assertEquals(CompletionPhotoState(entryId = 9), viewModel.state.value.completionPhoto)
        assertTrue(viewModel.state.value.showWorkoutCompleted)
    }

    @Test
    fun `no photo can be added before the entry is known`() = runTest {
        val viewModel = createViewModel()
        repository.completeSlots(0, 1, 2)

        viewModel.handleIntent(ProgramStrengthIntent.OnCompletionPhotoSelected(gallery))

        assertTrue(photos.attached.isEmpty())
    }

    private class FakeProgramRepository(private val workout: ProgramWorkout?) : ProgramRepository {
        private val completed = MutableStateFlow<Set<Int>>(emptySet())
        var resets = 0

        /** Entry id of the latest recorded completion, as ProgramRepositoryImpl exposes it. */
        val completionEntryId = MutableStateFlow<Long?>(null)

        override fun observeCompletionEntryId(programId: Long, week: Int, dayIndex: Int): Flow<Long?> = completionEntryId

        fun completeSlots(vararg positions: Int) {
            completed.value = positions.toSet()
        }

        override fun observeWorkout(programId: Long, week: Int, dayIndex: Int): Flow<ProgramWorkoutDetail?> =
            if (workout == null) {
                flowOf(null)
            } else {
                flow {
                    completed.collect { done ->
                        val all = (workout as? ProgramWorkout.Strength)?.slots?.all { it.position in done } == true
                        emit(ProgramWorkoutDetail(programId, week, dayIndex, workout, all, done))
                    }
                }
            }

        override suspend fun resetWorkout(programId: Long, week: Int, dayIndex: Int) {
            resets++
            completed.value = emptySet()
        }

        override suspend fun getExercises(): Map<Int, ProgramExercise> = emptyMap()
        override fun observePrograms(): Flow<List<ProgramSummary>> = flowOf(emptyList())
        override fun observeProgram(programId: Long): Flow<ProgramDetail?> = flowOf(null)
        override suspend fun previewProgram(profile: ProgramProfile): GeneratedProgram = GeneratedProgram(emptyList())
        override suspend fun createProgram(profile: ProgramProfile): Long = 1
        override suspend fun deleteProgram(programId: Long) = Unit
        override suspend fun markSlotCompleted(programId: Long, week: Int, dayIndex: Int, position: Int) = Unit
        override suspend fun markWorkoutCompleted(programId: Long, week: Int, dayIndex: Int, durationSeconds: Int?) = Unit
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
