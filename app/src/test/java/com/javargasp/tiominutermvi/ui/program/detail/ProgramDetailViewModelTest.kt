package com.javargasp.tiominutermvi.ui.program.detail

import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.ProgramDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.domain.program.model.ProgramWeekDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutKind
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTitle
import com.javargasp.tiominutermvi.domain.program.model.StrengthSlot
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import com.javargasp.tiominutermvi.ui.program.common.formatPercent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val strength = ProgramWorkout.Strength(
        StrengthDayTitle.FULL_BODY_A,
        listOf(StrengthSlot(0, 2, ProgramSlotType.REPS, listOf(10, 10, 10), 90))
    )
    private val run = ProgramWorkout.OutdoorRun(1.5, walk = false)
    private val circuit = ProgramWorkout.HomeCircuit(3, 30, 30, 60, listOf(1, 2, 3))

    private val programFlow = MutableStateFlow<ProgramDetail?>(program())
    private val repository = FakeProgramRepository(programFlow)

    private fun program(completed: Map<Int, Set<Int>> = emptyMap()): ProgramDetail {
        val weeks = (1..6).map { week ->
            val done = completed[week].orEmpty()
            ProgramWeekDetail(
                weekNumber = week,
                isDeload = week == 4,
                workouts = listOf(
                    ProgramWorkoutDetail(1, week, 0, strength, 0 in done, emptySet()),
                    ProgramWorkoutDetail(1, week, 1, run, 1 in done, emptySet()),
                    ProgramWorkoutDetail(1, week, 2, circuit, 2 in done, emptySet())
                )
            )
        }
        return ProgramDetail(
            summary = ProgramSummary(
                1,
                ProgramProfile(30, 70.0, 175, ProgramLevel.BEGINNER, ProgramGoal.LOSE_FAT, ProgramLocation.HOME, 6, 3),
                0,
                completed.values.sumOf { it.size },
                18
            ),
            weeks = weeks
        )
    }

    @Test
    fun `program is shown with every week`() = runTest {
        val viewModel = ProgramDetailViewModel(1, repository)

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isNotFound)
        assertEquals((1..6).toList(), state.program!!.weeks.map { it.weekNumber })
    }

    @Test
    fun `week percentages use one decimal`() = runTest {
        programFlow.value = program(completed = mapOf(1 to setOf(0), 2 to setOf(0, 2), 3 to setOf(0, 1, 2)))
        val viewModel = ProgramDetailViewModel(1, repository)

        val percents = viewModel.state.value.program!!.weeks.map {
            formatPercent(it.completedWorkouts, it.workouts.size, Locale.US)
        }

        assertEquals(listOf("33.3", "66.7", "100.0", "0.0", "0.0", "0.0"), percents)
        assertEquals("33,3", formatPercent(1, 3, Locale.forLanguageTag("es")))
    }

    @Test
    fun `deload week is flagged`() = runTest {
        val viewModel = ProgramDetailViewModel(1, repository)

        assertEquals(listOf(4), viewModel.state.value.program!!.weeks.filter { it.isDeload }.map { it.weekNumber })
    }

    @Test
    fun `completion state follows the repository`() = runTest {
        val viewModel = ProgramDetailViewModel(1, repository)
        assertTrue(viewModel.state.value.program!!.weeks[0].workouts.none { it.isCompleted })

        programFlow.value = program(completed = mapOf(1 to setOf(1)))

        val week1 = viewModel.state.value.program!!.weeks[0]
        assertEquals(listOf(false, true, false), week1.workouts.map { it.isCompleted })
        assertEquals(1, week1.completedWorkouts)
    }

    @Test
    fun `tapping a workout opens the screen of its kind, also when completed`() = runTest {
        programFlow.value = program(completed = mapOf(2 to setOf(0)))
        val viewModel = ProgramDetailViewModel(1, repository)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(ProgramDetailIntent.OnWorkoutClicked(2, 0))
        viewModel.handleIntent(ProgramDetailIntent.OnWorkoutClicked(2, 1))
        viewModel.handleIntent(ProgramDetailIntent.OnWorkoutClicked(2, 2))
        viewModel.handleIntent(ProgramDetailIntent.OnWorkoutClicked(9, 0)) // no such week

        assertEquals(
            listOf(
                ProgramDetailEffect.NavigateToWorkout(ProgramWorkoutKind.STRENGTH, 2, 0),
                ProgramDetailEffect.NavigateToWorkout(ProgramWorkoutKind.OUTDOOR_RUN, 2, 1),
                ProgramDetailEffect.NavigateToWorkout(ProgramWorkoutKind.HOME_CIRCUIT, 2, 2)
            ),
            effects
        )
    }

    @Test
    fun `deleted program shows not found`() = runTest {
        val viewModel = ProgramDetailViewModel(1, repository)

        programFlow.value = null

        assertTrue(viewModel.state.value.isNotFound)
    }

    private fun TestScope.collectEffects(viewModel: ProgramDetailViewModel): List<ProgramDetailEffect> {
        val effects = mutableListOf<ProgramDetailEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        return effects
    }

    private class FakeProgramRepository(private val program: Flow<ProgramDetail?>) : ProgramRepository {
        override fun observeProgram(programId: Long): Flow<ProgramDetail?> = program
        override fun observePrograms(): Flow<List<ProgramSummary>> = flowOf(emptyList())
        override fun observeWorkout(programId: Long, week: Int, dayIndex: Int): Flow<ProgramWorkoutDetail?> = flowOf(null)
        override suspend fun previewProgram(profile: ProgramProfile): GeneratedProgram = GeneratedProgram(emptyList())
        override suspend fun createProgram(profile: ProgramProfile): Long = 1
        override suspend fun deleteProgram(programId: Long) = Unit
        override suspend fun markSlotCompleted(programId: Long, week: Int, dayIndex: Int, position: Int) = Unit
        override suspend fun resetWorkout(programId: Long, week: Int, dayIndex: Int) = Unit
        override suspend fun markWorkoutCompleted(programId: Long, week: Int, dayIndex: Int, durationSeconds: Int?) = Unit
        override suspend fun getExercises(): Map<Int, ProgramExercise> = emptyMap()
        override suspend fun getImpactRules(): ImpactRules = ImpactRules(30.0, 60, 60, 15, setOf(87))
    }
}
