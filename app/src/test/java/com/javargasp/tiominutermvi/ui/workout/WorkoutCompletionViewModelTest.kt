package com.javargasp.tiominutermvi.ui.workout

import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import com.javargasp.tiominutermvi.testutil.FakeDailyProgressRepository
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutCompletionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val progress = FakeDailyProgressRepository()
    private val repository = object : WorkoutRepository {
        val routines = (1..30).map { Routine(it, "Day $it", exercises = emptyList()) }
        override suspend fun getExercises(): List<Exercise> = emptyList()
        override suspend fun getRoutines(): List<Routine> = routines
        override suspend fun getExercise(exerciseId: Int): Exercise? = null
        override suspend fun getRoutine(routineId: Int): Routine? = null
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `finishing Day 30 offers to start again once the progress is written`() = runTest {
        progress.progress.value = DailyProgress(lastCompletedDay = 29)
        val viewModel = WorkoutCompletionViewModel(30, repository, progress)
        assertFalse(viewModel.state.value.offersStartAgain)

        progress.onDayFinished(30)

        assertTrue(viewModel.state.value.offersStartAgain)
    }

    @Test
    fun `any other day offers nothing`() = runTest {
        progress.progress.value = DailyProgress(lastCompletedDay = 30)

        assertFalse(WorkoutCompletionViewModel(12, repository, progress).state.value.offersStartAgain)
    }

    @Test
    fun `starting again asks first, restarts and opens the days`() = runTest {
        progress.progress.value = DailyProgress(lastCompletedDay = 30)
        val viewModel = WorkoutCompletionViewModel(30, repository, progress)
        val effects = mutableListOf<WorkoutCompletionEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }

        viewModel.handleIntent(WorkoutCompletionIntent.OnStartAgainClicked)
        assertTrue(viewModel.state.value.showRestartDialog)
        assertEquals(0, progress.restarts)
        viewModel.handleIntent(WorkoutCompletionIntent.OnRestartConfirmed)

        assertEquals(1, progress.restarts)
        assertEquals(DailyProgress(0, 1), progress.progress.value)
        assertEquals(listOf(WorkoutCompletionEffect.NavigateToDailySelection), effects)
        assertFalse(viewModel.state.value.offersStartAgain)
    }
}
