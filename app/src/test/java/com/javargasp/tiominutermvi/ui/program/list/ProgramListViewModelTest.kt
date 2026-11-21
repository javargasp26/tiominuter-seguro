package com.javargasp.tiominutermvi.ui.program.list

import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.ProgramDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
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
class ProgramListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeProgramRepository()

    @Test
    fun `loading until the first emission`() = runTest {
        val viewModel = ProgramListViewModel(FakeProgramRepository(emit = false))

        assertTrue(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun `no programs shows the empty state`() = runTest {
        val viewModel = ProgramListViewModel(repository)

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun `programs are shown as cards in the repository order`() = runTest {
        val viewModel = ProgramListViewModel(repository)

        repository.programs.value = listOf(summary(2), summary(1))

        assertEquals(listOf(2L, 1L), viewModel.state.value.programs.map { it.id })
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun `delete asks for confirmation and deletes on confirm`() = runTest {
        repository.programs.value = listOf(summary(1), summary(2))
        val viewModel = ProgramListViewModel(repository)

        viewModel.handleIntent(ProgramListIntent.OnDeleteRequested(2))
        assertEquals(2L, viewModel.state.value.pendingDeleteId)
        assertTrue(repository.deleted.isEmpty())

        viewModel.handleIntent(ProgramListIntent.OnConfirmDelete)

        assertEquals(listOf(2L), repository.deleted)
        assertNull(viewModel.state.value.pendingDeleteId)
        assertEquals(listOf(1L), viewModel.state.value.programs.map { it.id })
    }

    @Test
    fun `cancel delete keeps the program`() = runTest {
        repository.programs.value = listOf(summary(1))
        val viewModel = ProgramListViewModel(repository)

        viewModel.handleIntent(ProgramListIntent.OnDeleteRequested(1))
        viewModel.handleIntent(ProgramListIntent.OnDismissDelete)

        assertTrue(repository.deleted.isEmpty())
        assertNull(viewModel.state.value.pendingDeleteId)
        assertEquals(1, viewModel.state.value.programs.size)
    }

    @Test
    fun `tap opens the detail and the FAB opens create`() = runTest {
        val viewModel = ProgramListViewModel(repository)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(ProgramListIntent.OnProgramClicked(7))
        viewModel.handleIntent(ProgramListIntent.OnCreateClicked)
        viewModel.handleIntent(ProgramListIntent.OnBackPressed)

        assertEquals(
            listOf(
                ProgramListEffect.NavigateToDetail(7),
                ProgramListEffect.NavigateToCreate,
                ProgramListEffect.NavigateBack
            ),
            effects
        )
    }

    private fun TestScope.collectEffects(viewModel: ProgramListViewModel): List<ProgramListEffect> {
        val effects = mutableListOf<ProgramListEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        return effects
    }

    private fun summary(id: Long) = ProgramSummary(
        id = id,
        profile = ProgramProfile(30, 70.0, 175, ProgramLevel.BEGINNER, ProgramGoal.GAIN_MUSCLE, ProgramLocation.GYM, 6, 3),
        createdAt = id,
        completedWorkouts = 1,
        totalWorkouts = 18
    )

    private class FakeProgramRepository(emit: Boolean = true) : ProgramRepository {
        val programs = MutableStateFlow<List<ProgramSummary>?>(if (emit) emptyList() else null)
        val deleted = mutableListOf<Long>()

        override fun observePrograms(): Flow<List<ProgramSummary>> =
            flow { programs.collect { if (it != null) emit(it) } }

        override suspend fun deleteProgram(programId: Long) {
            deleted += programId
            programs.value = programs.value?.filterNot { it.id == programId }
        }

        override fun observeProgram(programId: Long): Flow<ProgramDetail?> = flowOf(null)
        override fun observeWorkout(programId: Long, week: Int, dayIndex: Int): Flow<ProgramWorkoutDetail?> = flowOf(null)
        override suspend fun previewProgram(profile: ProgramProfile): GeneratedProgram = GeneratedProgram(emptyList())
        override suspend fun createProgram(profile: ProgramProfile): Long = 1
        override suspend fun markSlotCompleted(programId: Long, week: Int, dayIndex: Int, position: Int) = Unit
        override suspend fun resetWorkout(programId: Long, week: Int, dayIndex: Int) = Unit
        override suspend fun markWorkoutCompleted(programId: Long, week: Int, dayIndex: Int, durationSeconds: Int?) = Unit
        override suspend fun getExercises(): Map<Int, ProgramExercise> = emptyMap()
        override suspend fun getImpactRules(): ImpactRules = ImpactRules(30.0, 60, 60, 15, setOf(87))
    }
}
