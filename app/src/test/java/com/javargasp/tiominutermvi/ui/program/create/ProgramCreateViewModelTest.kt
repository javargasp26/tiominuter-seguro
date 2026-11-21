package com.javargasp.tiominutermvi.ui.program.create

import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.ProgramDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.domain.program.model.ProgramWeek
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTitle
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class ProgramCreateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeProgramRepository()

    private fun filledViewModel(age: String = "30", weight: String = "70", height: String = "175") =
        ProgramCreateViewModel(repository).apply {
            handleIntent(ProgramCreateIntent.OnAgeChanged(age))
            handleIntent(ProgramCreateIntent.OnWeightChanged(weight))
            handleIntent(ProgramCreateIntent.OnHeightChanged(height))
        }

    @Test
    fun `defaults are 6 weeks and 3 days, no errors shown`() = runTest {
        val state = ProgramCreateViewModel(repository).state.value

        assertEquals(6, state.weeks)
        assertEquals(3, state.daysPerWeek)
        assertEquals(ProgramFocus.BALANCED, state.focus)
        assertNull(state.ageError)
        assertNull(state.profile)
    }

    @Test
    fun `create with an empty form shows required errors and creates nothing`() = runTest {
        val viewModel = ProgramCreateViewModel(repository)

        viewModel.handleIntent(ProgramCreateIntent.OnCreateClicked)

        val state = viewModel.state.value
        assertEquals(ProgramFieldError.REQUIRED, state.ageError)
        assertEquals(ProgramFieldError.REQUIRED, state.weightError)
        assertEquals(ProgramFieldError.REQUIRED, state.heightError)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `out of range values are flagged inline`() = runTest {
        val viewModel = filledViewModel(age = "15", weight = "251", height = "119")

        val state = viewModel.state.value
        assertEquals(ProgramFieldError.OUT_OF_RANGE, state.ageError)
        assertEquals(ProgramFieldError.OUT_OF_RANGE, state.weightError)
        assertEquals(ProgramFieldError.OUT_OF_RANGE, state.heightError)

        viewModel.handleIntent(ProgramCreateIntent.OnAgeChanged("16"))
        viewModel.handleIntent(ProgramCreateIntent.OnWeightChanged("30"))
        viewModel.handleIntent(ProgramCreateIntent.OnHeightChanged("230"))
        assertNull(viewModel.state.value.ageError)
        assertNull(viewModel.state.value.weightError)
        assertNull(viewModel.state.value.heightError)
    }

    @Test
    fun `fields keep only digits and weight accepts a decimal comma`() = runTest {
        val viewModel = filledViewModel(age = "3a0", weight = "72,5,3", height = "17x5")

        val state = viewModel.state.value
        assertEquals("30", state.ageText)
        assertEquals("72,53", state.weightText)
        assertEquals("175", state.heightText)

        viewModel.handleIntent(ProgramCreateIntent.OnWeightChanged("72,5"))
        assertEquals(72.5, viewModel.state.value.weightKg!!, 0.0)
    }

    @Test
    fun `bmi from weight and height`() = runTest {
        val viewModel = filledViewModel(weight = "70", height = "175")

        assertEquals(22.86, viewModel.state.value.bmi!!, 0.01)
        assertFalse(viewModel.state.value.isLowImpact)
    }

    @Test
    fun `low impact note for bmi 30 or more and for age 60 or more`() = runTest {
        assertTrue(filledViewModel(weight = "100", height = "170").state.value.isLowImpact)
        assertTrue(filledViewModel(age = "65").state.value.isLowImpact)
        assertFalse(filledViewModel(age = "59").state.value.isLowImpact)
    }

    @Test
    fun `underweight with lose fat suggests gain muscle`() = runTest {
        val viewModel = filledViewModel(weight = "50", height = "180") // BMI 15.4

        assertFalse(viewModel.state.value.showUnderweightWarning)
        viewModel.handleIntent(ProgramCreateIntent.OnGoalSelected(ProgramGoal.LOSE_FAT))
        assertTrue(viewModel.state.value.showUnderweightWarning)
    }

    @Test
    fun `weeks and days stay in range`() = runTest {
        val viewModel = ProgramCreateViewModel(repository)

        viewModel.handleIntent(ProgramCreateIntent.OnWeeksChanged(13))
        viewModel.handleIntent(ProgramCreateIntent.OnDaysPerWeekChanged(1))

        assertEquals(12, viewModel.state.value.weeks)
        assertEquals(2, viewModel.state.value.daysPerWeek)
    }

    @Test
    fun `selecting a focus updates the state and the profile`() = runTest {
        val viewModel = filledViewModel()

        viewModel.handleIntent(ProgramCreateIntent.OnFocusSelected(ProgramFocus.CORE))

        assertEquals(ProgramFocus.CORE, viewModel.state.value.focus)
        assertEquals(ProgramFocus.CORE, viewModel.state.value.profile!!.focus)
    }

    @Test
    fun `preview is generated with the selected focus`() = runTest {
        val viewModel = filledViewModel()

        viewModel.handleIntent(ProgramCreateIntent.OnPreviewClicked)
        assertEquals(listOf(previewWorkout), viewModel.state.value.preview)

        viewModel.handleIntent(ProgramCreateIntent.OnDismissPreview)
        viewModel.handleIntent(ProgramCreateIntent.OnFocusSelected(ProgramFocus.LOWER_BODY))
        viewModel.handleIntent(ProgramCreateIntent.OnPreviewClicked)

        assertEquals(listOf(lowerBodyPreviewWorkout), viewModel.state.value.preview)
        assertEquals(listOf(ProgramFocus.BALANCED, ProgramFocus.LOWER_BODY), repository.previewed.map { it.focus })
    }

    @Test
    fun `preview shows week 1 and closes`() = runTest {
        val viewModel = filledViewModel()

        viewModel.handleIntent(ProgramCreateIntent.OnPreviewClicked)

        assertEquals(listOf(previewWorkout), viewModel.state.value.preview)
        assertTrue(repository.created.isEmpty())
        viewModel.handleIntent(ProgramCreateIntent.OnDismissPreview)
        assertNull(viewModel.state.value.preview)
    }

    @Test
    fun `create stores the profile and navigates to the detail`() = runTest {
        val viewModel = filledViewModel(age = "40", weight = "82.5", height = "180")
        viewModel.handleIntent(ProgramCreateIntent.OnLevelSelected(ProgramLevel.ADVANCED))
        viewModel.handleIntent(ProgramCreateIntent.OnGoalSelected(ProgramGoal.LOSE_FAT))
        viewModel.handleIntent(ProgramCreateIntent.OnFocusSelected(ProgramFocus.UPPER_BODY))
        viewModel.handleIntent(ProgramCreateIntent.OnLocationSelected(ProgramLocation.GYM))
        viewModel.handleIntent(ProgramCreateIntent.OnWeeksChanged(8))
        viewModel.handleIntent(ProgramCreateIntent.OnDaysPerWeekChanged(4))
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(ProgramCreateIntent.OnCreateClicked)

        assertEquals(
            listOf(
                ProgramProfile(
                    40, 82.5, 180, ProgramLevel.ADVANCED, ProgramGoal.LOSE_FAT, ProgramLocation.GYM, 8, 4,
                    ProgramFocus.UPPER_BODY
                )
            ),
            repository.created
        )
        assertEquals(listOf(ProgramCreateEffect.NavigateToDetail(42)), effects)
    }

    @Test
    fun `create failure shows an error and allows retrying`() = runTest {
        repository.failCreate = true
        val viewModel = filledViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(ProgramCreateIntent.OnCreateClicked)

        assertTrue(viewModel.state.value.createFailed)
        assertFalse(viewModel.state.value.isWorking)
        assertTrue(effects.isEmpty())
    }

    private fun TestScope.collectEffects(viewModel: ProgramCreateViewModel): List<ProgramCreateEffect> {
        val effects = mutableListOf<ProgramCreateEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        return effects
    }

    private val previewWorkout = ProgramWorkout.OutdoorRun(1.5, walk = false)
    private val lowerBodyPreviewWorkout = ProgramWorkout.Strength(StrengthDayTitle.LOWER_A, emptyList())

    private inner class FakeProgramRepository : ProgramRepository {
        val created = mutableListOf<ProgramProfile>()
        val previewed = mutableListOf<ProgramProfile>()
        var failCreate = false

        override suspend fun createProgram(profile: ProgramProfile): Long {
            if (failCreate) throw IllegalStateException("disk full")
            created += profile
            return 42
        }

        /** A different week 1 per focus, so the test sees which focus the preview used. */
        override suspend fun previewProgram(profile: ProgramProfile): GeneratedProgram {
            previewed += profile
            val workout = if (profile.focus == ProgramFocus.LOWER_BODY) lowerBodyPreviewWorkout else previewWorkout
            return GeneratedProgram(listOf(ProgramWeek(1, false, listOf(workout))))
        }

        override suspend fun getImpactRules(): ImpactRules = ImpactRules(30.0, 60, 60, 15, setOf(87))
        override suspend fun getExercises(): Map<Int, ProgramExercise> = emptyMap()
        override fun observePrograms(): Flow<List<ProgramSummary>> = flowOf(emptyList())
        override fun observeProgram(programId: Long): Flow<ProgramDetail?> = flowOf(null)
        override fun observeWorkout(programId: Long, week: Int, dayIndex: Int): Flow<ProgramWorkoutDetail?> = flowOf(null)
        override suspend fun deleteProgram(programId: Long) = Unit
        override suspend fun markSlotCompleted(programId: Long, week: Int, dayIndex: Int, position: Int) = Unit
        override suspend fun resetWorkout(programId: Long, week: Int, dayIndex: Int) = Unit
        override suspend fun markWorkoutCompleted(programId: Long, week: Int, dayIndex: Int, durationSeconds: Int?) = Unit
    }
}
