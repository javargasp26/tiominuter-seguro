package com.javargasp.tiominutermvi.ui.weeklygoal

import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoalRepository
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyGoalViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Thursday 2026-10-22T10:00:00Z: the current week starts on 19 October
    private val localDateProvider = LocalDateProvider({ 1_792_663_200_000L }, { TimeZone.getTimeZone("UTC") })
    private val repository = FakeWeeklyGoalRepository()

    private fun createViewModel() = WeeklyGoalViewModel(repository, localDateProvider)

    @Test
    fun `the goal in force this week is preselected`() = runTest {
        repository.goals.value = listOf(WeeklyGoal(5, "2026-10-05"), WeeklyGoal(9, "2026-11-02"))

        val state = createViewModel().state.value

        assertFalse(state.isLoading)
        assertEquals(5, state.currentGoal)
        assertEquals(5, state.selectedWorkouts)
    }

    @Test
    fun `without a goal the stepper starts at the default`() = runTest {
        val state = createViewModel().state.value

        assertNull(state.currentGoal)
        assertEquals(WeeklyGoalState.DEFAULT_WORKOUTS, state.selectedWorkouts)
    }

    @Test
    fun `plus and minus move one workout at a time`() = runTest {
        val viewModel = createViewModel()

        viewModel.handleIntent(WeeklyGoalIntent.OnIncrement)
        viewModel.handleIntent(WeeklyGoalIntent.OnIncrement)
        assertEquals(5, viewModel.state.value.selectedWorkouts)

        viewModel.handleIntent(WeeklyGoalIntent.OnDecrement)
        assertEquals(4, viewModel.state.value.selectedWorkouts)
    }

    @Test
    fun `the stepper stops at 1 and at 14`() = runTest {
        val viewModel = createViewModel()

        repeat(10) { viewModel.handleIntent(WeeklyGoalIntent.OnDecrement) }
        assertEquals(1, viewModel.state.value.selectedWorkouts)
        assertFalse(viewModel.state.value.canDecrement)
        assertTrue(viewModel.state.value.canIncrement)

        repeat(20) { viewModel.handleIntent(WeeklyGoalIntent.OnIncrement) }
        assertEquals(14, viewModel.state.value.selectedWorkouts)
        assertTrue(viewModel.state.value.canDecrement)
        assertFalse(viewModel.state.value.canIncrement)
    }

    @Test
    fun `saving stores the choice and goes back`() = runTest {
        repository.goals.value = listOf(WeeklyGoal(3, "2026-10-12"))
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WeeklyGoalIntent.OnIncrement)
        viewModel.handleIntent(WeeklyGoalIntent.OnSave)

        assertEquals(listOf(4), repository.saved)
        assertEquals(4, viewModel.state.value.currentGoal)
        assertFalse(viewModel.state.value.isSaving)
        assertEquals(listOf(WeeklyGoalEffect.NavigateBack), effects)
    }

    @Test
    fun `the limits can be saved`() = runTest {
        val viewModel = createViewModel()
        repeat(20) { viewModel.handleIntent(WeeklyGoalIntent.OnIncrement) }
        viewModel.handleIntent(WeeklyGoalIntent.OnSave)

        val other = createViewModel()
        repeat(20) { other.handleIntent(WeeklyGoalIntent.OnDecrement) }
        other.handleIntent(WeeklyGoalIntent.OnSave)

        assertEquals(listOf(14, 1), repository.saved)
    }

    @Test
    fun `a save failure is shown and the screen stays`() = runTest {
        repository.error = IllegalStateException("disk full")
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WeeklyGoalIntent.OnSave)

        assertTrue(viewModel.state.value.saveFailed)
        assertFalse(viewModel.state.value.isSaving)
        assertTrue(effects.isEmpty())

        viewModel.handleIntent(WeeklyGoalIntent.OnIncrement)
        assertFalse(viewModel.state.value.saveFailed)
    }

    @Test
    fun `back leaves without saving`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(WeeklyGoalIntent.OnIncrement)
        viewModel.handleIntent(WeeklyGoalIntent.OnBackPressed)

        assertTrue(repository.saved.isEmpty())
        assertEquals(listOf(WeeklyGoalEffect.NavigateBack), effects)
    }

    private fun TestScope.collectEffects(viewModel: WeeklyGoalViewModel): List<WeeklyGoalEffect> {
        val effects = mutableListOf<WeeklyGoalEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }
        return effects
    }

    private class FakeWeeklyGoalRepository : WeeklyGoalRepository {
        val goals = MutableStateFlow<List<WeeklyGoal>>(emptyList())
        val saved = mutableListOf<Int>()
        var error: Exception? = null

        override fun observeGoals(): Flow<List<WeeklyGoal>> = goals

        override suspend fun setGoal(workoutsPerWeek: Int) {
            error?.let { throw it }
            saved.add(workoutsPerWeek)
        }
    }
}
