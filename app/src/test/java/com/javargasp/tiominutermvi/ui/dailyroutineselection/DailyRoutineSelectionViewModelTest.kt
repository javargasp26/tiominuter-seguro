package com.javargasp.tiominutermvi.ui.dailyroutineselection

import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.testutil.ActiveSessionTestKit
import com.javargasp.tiominutermvi.testutil.FakeDailyProgressRepository
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import com.javargasp.tiominutermvi.testutil.StringResources
import com.javargasp.tiominutermvi.testutil.XmlWorkoutNameTexts
import com.javargasp.tiominutermvi.ui.components.text
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class DailyRoutineSelectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val progress = FakeDailyProgressRepository()
    private val routines = (1..30).map { Routine(it, "Day $it", exercises = emptyList()) }

    private fun TestScope.loaded(): DailyRoutineSelectionViewModel {
        val viewModel = DailyRoutineSelectionViewModel(FakeWorkoutRepository(routines), ActiveSessionTestKit().tracker, progress)
        viewModel.handleIntent(DailyRoutineSelectionIntent.OnLoad)
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: DailyRoutineSelectionViewModel): List<DailyRoutineSelectionEffect> {
        val effects = mutableListOf<DailyRoutineSelectionEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        return effects
    }

    @Test
    fun `a new person sees Day 1 available and every other day locked`() = runTest {
        val state = loaded().state.value

        assertEquals(DailyDayStatus.AVAILABLE, state.statusOf(1))
        assertTrue((2..30).all { state.statusOf(it) == DailyDayStatus.LOCKED })
        assertEquals(1, state.nextDay)
        assertFalse(state.canRestart)
        assertFalse(state.isCycleComplete)
    }

    @Test
    fun `finished days are completed, the next is available, the rest locked`() = runTest {
        progress.progress.value = DailyProgress(lastCompletedDay = 3)
        val state = loaded().state.value

        assertEquals(listOf(DailyDayStatus.COMPLETED), (1..3).map { state.statusOf(it) }.distinct())
        assertEquals(DailyDayStatus.AVAILABLE, state.statusOf(4))
        assertEquals(DailyDayStatus.LOCKED, state.statusOf(5))
        assertEquals(4, state.nextDay)
        assertTrue(state.canRestart)
    }

    @Test
    fun `a locked day can't be started, unlocked and completed days can`() = runTest {
        progress.progress.value = DailyProgress(lastCompletedDay = 2)
        val viewModel = loaded()
        val effects = effectsOf(viewModel)

        viewModel.handleIntent(DailyRoutineSelectionIntent.OnRoutineSelected(9))
        viewModel.handleIntent(DailyRoutineSelectionIntent.OnRoutineSelected(1))
        viewModel.handleIntent(DailyRoutineSelectionIntent.OnRoutineSelected(3))

        assertEquals(
            listOf(DailyRoutineSelectionEffect.NavigateToWorkout(1), DailyRoutineSelectionEffect.NavigateToWorkout(3)),
            effects
        )
    }

    @Test
    fun `restart asks first, cancelling changes nothing`() = runTest {
        progress.progress.value = DailyProgress(lastCompletedDay = 5)
        val viewModel = loaded()

        viewModel.handleIntent(DailyRoutineSelectionIntent.OnRestartClicked)
        assertTrue(viewModel.state.value.showRestartDialog)
        viewModel.handleIntent(DailyRoutineSelectionIntent.OnRestartDismissed)

        assertFalse(viewModel.state.value.showRestartDialog)
        assertEquals(0, progress.restarts)
        assertEquals(DailyDayStatus.COMPLETED, viewModel.state.value.statusOf(5))
    }

    @Test
    fun `confirming the restart locks every day again from Day 1`() = runTest {
        progress.progress.value = DailyProgress(lastCompletedDay = 12, cycle = 1)
        val viewModel = loaded()

        viewModel.handleIntent(DailyRoutineSelectionIntent.OnRestartClicked)
        viewModel.handleIntent(DailyRoutineSelectionIntent.OnRestartConfirmed)

        val state = viewModel.state.value
        assertFalse(state.showRestartDialog)
        assertEquals(DailyProgress(lastCompletedDay = 0, cycle = 2), state.progress)
        assertEquals(DailyDayStatus.AVAILABLE, state.statusOf(1))
        assertEquals(DailyDayStatus.LOCKED, state.statusOf(2))
    }

    @Test
    fun `after the last day the cycle is complete and starting again is the same restart`() = runTest {
        progress.progress.value = DailyProgress(lastCompletedDay = 30)
        val viewModel = loaded()

        assertTrue(viewModel.state.value.isCycleComplete)
        viewModel.handleIntent(DailyRoutineSelectionIntent.OnRestartClicked)
        viewModel.handleIntent(DailyRoutineSelectionIntent.OnRestartConfirmed)

        assertFalse(viewModel.state.value.isCycleComplete)
        assertEquals(1, progress.restarts)
        assertEquals(1, viewModel.state.value.nextDay)
    }

    @Test
    fun `progress arriving later (a pull) updates the list`() = runTest {
        val viewModel = loaded()

        progress.progress.value = DailyProgress(lastCompletedDay = 7)

        assertEquals(DailyDayStatus.AVAILABLE, viewModel.state.value.statusOf(8))
    }

    @Test
    fun `the lock line and the restart texts in both languages`() = runTest {
        val state = loaded().state.value
        val day2 = state.dayName(2)

        assertEquals(WorkoutName.Daily(2), day2)
        assertEquals("Complete Day 2 to unlock", StringResources.ENGLISH.get(R.string.daily_locked_hint, day2.text(XmlWorkoutNameTexts.EN)))
        assertEquals("Completa el Día 2 para desbloquearlo", StringResources.SPANISH.get(R.string.daily_locked_hint, day2.text(XmlWorkoutNameTexts.ES)))
        val day1 = state.dayName(1)
        assertEquals("Start again from Day 1", StringResources.ENGLISH.get(R.string.daily_start_again, day1.text(XmlWorkoutNameTexts.EN)))
        assertEquals("Empezar de nuevo desde el Día 1", StringResources.SPANISH.get(R.string.daily_start_again, day1.text(XmlWorkoutNameTexts.ES)))
        val message = StringResources.SPANISH.get(R.string.daily_restart_message, day1.text(XmlWorkoutNameTexts.ES))
        assertTrue(message, message.contains("historial") && message.contains("meta semanal") && message.contains("grupos"))
    }

    @Test
    fun `before the progress is read nothing can be started`() = runTest {
        val state = DailyRoutineSelectionState(routines = routines)

        assertNull(state.nextDay)
        assertEquals(DailyDayStatus.LOCKED, state.statusOf(1))
    }

    private class FakeWorkoutRepository(private val routines: List<Routine>) : WorkoutRepository {
        override suspend fun getExercises(): List<Exercise> = emptyList()
        override suspend fun getRoutines(): List<Routine> = routines
        override suspend fun getExercise(exerciseId: Int): Exercise? = null
        override suspend fun getRoutine(routineId: Int): Routine? = routines.firstOrNull { it.routineId == routineId }
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }
}
