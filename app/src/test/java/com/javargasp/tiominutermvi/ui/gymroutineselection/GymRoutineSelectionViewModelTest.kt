package com.javargasp.tiominutermvi.ui.gymroutineselection

import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
import com.javargasp.tiominutermvi.testutil.GymSessionTestKit

@OptIn(ExperimentalCoroutinesApi::class)
class GymRoutineSelectionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val gymSessions = GymSessionTestKit()

    private lateinit var routinesFlow: MutableStateFlow<List<GymRoutine>>
    private lateinit var repository: FakeGymRoutineRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        routinesFlow = MutableStateFlow(emptyList())
        repository = FakeGymRoutineRepository(routinesFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `OnResume closes abandoned gym sessions and announces each one once`() = runTest {
        gymSessions.clock.now = 0L
        gymSessions.tracker.onSetCompleted(64)
        gymSessions.clock.now = 65 * 60_000L
        gymSessions.tracker.onSetCompleted(64)
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)
        val effects = mutableListOf<GymRoutineSelectionEffect>()
        val collector = launch(UnconfinedTestDispatcher()) { viewModel.effect.collect { effects.add(it) } }

        gymSessions.clock.now = 65 * 60_000L + 3 * 3_600_000L
        viewModel.handleIntent(GymRoutineSelectionIntent.OnResume)
        viewModel.handleIntent(GymRoutineSelectionIntent.OnResume)

        assertEquals(listOf(GymRoutineSelectionEffect.ShowSessionSaved(65 * 60)), effects)
        assertEquals(1, gymSessions.recorder.entries.size)
        collector.cancel()
    }

    @Test
    fun `a session closed elsewhere (Home at app start) is announced when the gym opens`() = runTest {
        gymSessions.clock.now = 0L
        gymSessions.tracker.onSetCompleted(64)
        gymSessions.clock.now = 5 * 3_600_000L
        gymSessions.tracker.closeInactiveSessions()
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)
        val effects = mutableListOf<GymRoutineSelectionEffect>()
        val collector = launch(UnconfinedTestDispatcher()) { viewModel.effect.collect { effects.add(it) } }

        viewModel.handleIntent(GymRoutineSelectionIntent.OnResume)

        assertEquals(listOf(GymRoutineSelectionEffect.ShowSessionSaved(0)), effects)
        collector.cancel()
    }

    @Test
    fun `routines emitted by the repository appear in state`() = runTest {
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)

        routinesFlow.value = listOf(routine(1), routine(2))

        assertEquals(listOf(routine(1), routine(2)), viewModel.state.value.routines)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `observation starts only once even if OnLoad is sent twice`() = runTest {
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)

        viewModel.handleIntent(GymRoutineSelectionIntent.OnLoad)
        viewModel.handleIntent(GymRoutineSelectionIntent.OnLoad)

        assertEquals(1, repository.observeCalls)
    }

    @Test
    fun `OnLoad after a configuration change does not refresh again`() = runTest {
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)

        viewModel.handleIntent(GymRoutineSelectionIntent.OnLoad)
        viewModel.handleIntent(GymRoutineSelectionIntent.OnLoad)

        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `expanded cards survive OnLoad after a configuration change`() = runTest {
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)
        routinesFlow.value = listOf(routine(1), routine(2))
        viewModel.handleIntent(GymRoutineSelectionIntent.OnLoad)
        viewModel.handleIntent(GymRoutineSelectionIntent.OnToggleExpanded(2))

        viewModel.handleIntent(GymRoutineSelectionIntent.OnLoad)

        assertEquals(setOf(2), viewModel.state.value.expandedRoutineIds)
    }

    @Test
    fun `routine with zero exercises is listed`() = runTest {
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)

        routinesFlow.value = listOf(routine(1))

        assertEquals(listOf(routine(1)), viewModel.state.value.routines)
        assertTrue(viewModel.state.value.routines.single().exercises.isEmpty())
    }

    @Test
    fun `toggle expand and collapse with several cards expanded at once`() = runTest {
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)

        viewModel.handleIntent(GymRoutineSelectionIntent.OnToggleExpanded(1))
        assertEquals(setOf(1), viewModel.state.value.expandedRoutineIds)

        viewModel.handleIntent(GymRoutineSelectionIntent.OnToggleExpanded(2))
        assertEquals(setOf(1, 2), viewModel.state.value.expandedRoutineIds)

        viewModel.handleIntent(GymRoutineSelectionIntent.OnToggleExpanded(1))
        assertEquals(setOf(2), viewModel.state.value.expandedRoutineIds)
    }

    @Test
    fun `OnStartRoutine emits NavigateToGymRoutine`() = runTest {
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)
        val effects = mutableListOf<GymRoutineSelectionEffect>()
        val collector = launch(UnconfinedTestDispatcher()) {
            viewModel.effect.collect { effects.add(it) }
        }

        viewModel.handleIntent(GymRoutineSelectionIntent.OnStartRoutine(7))

        assertEquals(listOf(GymRoutineSelectionEffect.NavigateToGymRoutine(7)), effects)
        collector.cancel()
    }

    @Test
    fun `refresh failure with data emits ShowRefreshError and keeps routines`() = runTest {
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)
        val effects = mutableListOf<GymRoutineSelectionEffect>()
        val collector = launch(UnconfinedTestDispatcher()) {
            viewModel.effect.collect { effects.add(it) }
        }

        routinesFlow.value = listOf(routine(1))
        repository.refreshResult = Result.failure(IllegalStateException("offline"))

        viewModel.handleIntent(GymRoutineSelectionIntent.OnLoad)

        assertTrue(effects.contains(GymRoutineSelectionEffect.ShowRefreshError))
        assertEquals(listOf(routine(1)), viewModel.state.value.routines)
        assertNull(viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isRefreshing)
        collector.cancel()
    }

    @Test
    fun `refresh failure without data sets errorMessage`() = runTest {
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)
        val effects = mutableListOf<GymRoutineSelectionEffect>()
        val collector = launch(UnconfinedTestDispatcher()) {
            viewModel.effect.collect { effects.add(it) }
        }

        repository.refreshResult = Result.failure(IllegalStateException("offline"))

        viewModel.handleIntent(GymRoutineSelectionIntent.OnLoad)

        assertFalse(effects.contains(GymRoutineSelectionEffect.ShowRefreshError))
        assertEquals("offline", viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.routines.isEmpty())
        collector.cancel()
    }

    @Test
    fun `OnRetry refreshes again`() = runTest {
        val viewModel = GymRoutineSelectionViewModel(repository, gymSessions.tracker)
        repository.refreshResult = Result.failure(IllegalStateException("offline"))

        viewModel.handleIntent(GymRoutineSelectionIntent.OnLoad)
        assertEquals(1, repository.refreshCalls)

        repository.refreshResult = Result.success(Unit)
        viewModel.handleIntent(GymRoutineSelectionIntent.OnRetry)

        assertEquals(2, repository.refreshCalls)
        assertNull(viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    private class FakeGymRoutineRepository(
        private val routinesFlow: MutableStateFlow<List<GymRoutine>>
    ) : GymRoutineRepository {

        var observeCalls = 0
        var refreshCalls = 0
        var refreshResult: Result<Unit> = Result.success(Unit)

        override fun observeGymRoutines(): kotlinx.coroutines.flow.Flow<List<GymRoutine>> {
            observeCalls++
            return routinesFlow
        }

        override fun observeGymRoutine(routineId: Int): kotlinx.coroutines.flow.Flow<GymRoutine?> =
            flow { emit(null) }

        override suspend fun refresh(): Result<Unit> {
            refreshCalls++
            return refreshResult
        }
    }

    private fun routine(id: Int) = GymRoutine(
        routineId = id,
        routineName = "Routine $id",
        exercises = emptyList()
    )
}
