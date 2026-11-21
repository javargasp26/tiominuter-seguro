package com.javargasp.tiominutermvi.ui.logactivity

import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.testutil.FeedTestKit
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Share in my groups" in Log activity: ON by default, per workout, and the one-time explanation after Save. */
@OptIn(ExperimentalCoroutinesApi::class)
class LogActivityShareTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val kit = FeedTestKit()
    private val photos = mockk<TrainingPhotoRepository>(relaxed = true)

    private fun TestScope.newViewModel(): Pair<LogActivityViewModel, List<LogActivityEffect>> {
        val viewModel = LogActivityViewModel(null, kit.trainingLog, kit.groups.dates, photos, kit.sharing)
        val effects = mutableListOf<LogActivityEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    private fun TestScope.fillAndSave(viewModel: LogActivityViewModel, minutes: Int = 30) {
        viewModel.handleIntent(LogActivityIntent.OnTypeSelected(ActivityType.RUNNING))
        viewModel.handleIntent(LogActivityIntent.OnQuickDurationSelected(minutes))
        viewModel.handleIntent(LogActivityIntent.OnIntensitySelected(Intensity.MODERATE))
        viewModel.handleIntent(LogActivityIntent.OnSave)
        runCurrent()
    }

    private suspend fun savedShare(): Boolean = kit.store.get(kit.logDao.rows.value.single().uuid)!!.share

    @Test
    fun `the switch shows ON by default only for a member of a group`() = runTest {
        val (outside, _) = newViewModel()
        assertFalse(outside.state.value.canShare)

        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        val (member, _) = newViewModel()
        assertTrue(member.state.value.canShare)
        assertTrue(member.state.value.shareInGroups)
    }

    @Test
    fun `the first shared activity shows the explanation after Save, OK goes back`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        val (viewModel, effects) = newViewModel()
        fillAndSave(viewModel)
        assertTrue(viewModel.state.value.showShareIntro)
        assertTrue("the entry is already saved", kit.logDao.rows.value.size == 1)
        assertTrue(effects.none { it == LogActivityEffect.NavigateBack })

        viewModel.handleIntent(LogActivityIntent.OnShareIntroOk)
        runCurrent()
        assertTrue(savedShare())
        assertTrue(kit.preferences.isFeedIntroSeen("me"))
        assertEquals(LogActivityEffect.NavigateBack, effects.last())
    }

    @Test
    fun `Don't share this one saves the activity without sharing it`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        val (viewModel, effects) = newViewModel()
        fillAndSave(viewModel)
        viewModel.handleIntent(LogActivityIntent.OnShareIntroDontShare)
        runCurrent()
        assertFalse(savedShare())
        assertTrue(kit.preferences.isFeedIntroSeen("me"))
        assertEquals(LogActivityEffect.NavigateBack, effects.last())
    }

    @Test
    fun `turning the switch off keeps this activity private, with no explanation`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        val (viewModel, effects) = newViewModel()
        viewModel.handleIntent(LogActivityIntent.OnShareChanged(false))
        fillAndSave(viewModel)
        assertFalse(viewModel.state.value.showShareIntro)
        assertFalse(savedShare())
        assertEquals(LogActivityEffect.NavigateBack, effects.last())
        assertFalse("the switch is not an unsaved change", viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun `once the explanation was seen, Save goes straight back and shares`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        kit.preferences.setFeedIntroSeen("me")
        val (viewModel, effects) = newViewModel()
        fillAndSave(viewModel)
        assertFalse(viewModel.state.value.showShareIntro)
        assertTrue(savedShare())
        assertEquals(LogActivityEffect.NavigateBack, effects.last())
    }

    @Test
    fun `an activity under 10 minutes isn't shared and shows no explanation`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        val (viewModel, effects) = newViewModel()
        viewModel.handleIntent(LogActivityIntent.OnDurationChanged("5"))
        fillAndSave(viewModel, minutes = 5)
        assertFalse(viewModel.state.value.showShareIntro)
        assertEquals(LogActivityEffect.NavigateBack, effects.last())
    }

    @Test
    fun `editing shows the workout's own choice and saves a change of it`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        kit.preferences.setFeedIntroSeen("me")
        val (create, _) = newViewModel()
        fillAndSave(create)
        val id = kit.logDao.rows.value.single().id

        val edit = LogActivityViewModel(id, kit.trainingLog, kit.groups.dates, photos, kit.sharing)
        runCurrent()
        assertTrue(edit.state.value.canShare)
        assertTrue(edit.state.value.shareInGroups)
        edit.handleIntent(LogActivityIntent.OnShareChanged(false))
        edit.handleIntent(LogActivityIntent.OnSave)
        runCurrent()
        assertFalse(savedShare())
    }
}
