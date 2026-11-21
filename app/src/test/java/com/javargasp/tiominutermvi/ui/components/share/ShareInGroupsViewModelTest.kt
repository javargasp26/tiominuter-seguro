package com.javargasp.tiominutermvi.ui.components.share

import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.testutil.FeedTestKit
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The "Share in my groups" switch of the completion screens, over the real controller and queue. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareInGroupsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val kit = FeedTestKit()

    private fun TestScope.viewModelFor(entryId: Long): ShareInGroupsViewModel {
        val viewModel = ShareInGroupsViewModel(kit.sharing) { kit.groups.now }
        viewModel.handleIntent(ShareInGroupsIntent.OnEntry(entryId))
        runCurrent()
        return viewModel
    }

    private suspend fun shareOf(entryId: Long) = kit.store.get(kit.uuidOf(entryId))!!.share

    @Test
    fun `the switch is ON by default and the explanation shows the first time`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        val viewModel = viewModelFor(kit.record())
        val state = viewModel.state.value
        assertTrue(state.visible)
        assertTrue(state.share)
        assertTrue(state.showIntro)
    }

    @Test
    fun `OK acknowledges the explanation once, and it never shows again`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        val id = kit.record()
        val first = viewModelFor(id)
        first.handleIntent(ShareInGroupsIntent.OnIntroOk)
        runCurrent()
        assertFalse(first.state.value.showIntro)
        assertTrue(shareOf(id))
        assertTrue(kit.preferences.isFeedIntroSeen("me"))

        val next = viewModelFor(kit.record())
        assertTrue(next.state.value.visible)
        assertFalse(next.state.value.showIntro)
    }

    @Test
    fun `Don't share this one turns this workout off and acknowledges the explanation`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        val id = kit.record()
        val viewModel = viewModelFor(id)
        viewModel.handleIntent(ShareInGroupsIntent.OnIntroDontShare)
        runCurrent()
        assertFalse(viewModel.state.value.share)
        assertFalse(shareOf(id))
        assertTrue(kit.preferences.isFeedIntroSeen("me"))
        // Only this workout: the next one is shared again
        assertTrue(viewModelFor(kit.record()).state.value.share)
    }

    @Test
    fun `closing the explanation without choosing posts nothing yet and asks again next time`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        val viewModel = viewModelFor(kit.record())
        viewModel.handleIntent(ShareInGroupsIntent.OnIntroDismissed)
        runCurrent()
        assertFalse(viewModel.state.value.showIntro)
        assertFalse(kit.preferences.isFeedIntroSeen("me"))
        assertTrue(viewModelFor(kit.record()).state.value.showIntro)
    }

    @Test
    fun `the switch is remembered per workout`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        kit.preferences.setFeedIntroSeen("me")
        val off = kit.record()
        val viewModel = viewModelFor(off)
        viewModel.handleIntent(ShareInGroupsIntent.OnShareChanged(false))
        runCurrent()
        assertFalse(shareOf(off))
        assertFalse(viewModelFor(off).state.value.share)
        assertTrue(viewModelFor(kit.record()).state.value.share)
    }

    @Test
    fun `nothing shows for a guest, without groups, or for a workout that doesn't count`() = runTest {
        val noGroups = viewModelFor(kit.record())
        assertFalse(noGroups.state.value.visible)
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        assertFalse(viewModelFor(kit.record(minutes = 5)).state.value.visible)
        kit.signedInUid = null
        kit.groups.auth.signOut()
        assertFalse(viewModelFor(kit.record()).state.value.visible)
    }

    @Test
    fun `the daily completion screen picks the entry recorded as it opens`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        val viewModel = ShareInGroupsViewModel(kit.sharing) { kit.groups.now }
        viewModel.handleIntent(ShareInGroupsIntent.OnLatestOf(TrainingSource.DAILY))
        runCurrent()
        assertFalse(viewModel.state.value.visible)

        val id = kit.record(source = TrainingSource.DAILY, title = "Day 3")
        runCurrent()
        assertEquals(id, viewModel.state.value.entryId)
        assertTrue(viewModel.state.value.visible)
    }

    @Test
    fun `a daily entry recorded long before the screen opened is not picked`() = runTest {
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        kit.record(source = TrainingSource.DAILY)
        kit.groups.now += 10 * 60 * 1000L
        val viewModel = ShareInGroupsViewModel(kit.sharing) { kit.groups.now }
        viewModel.handleIntent(ShareInGroupsIntent.OnLatestOf(TrainingSource.DAILY))
        runCurrent()
        assertEquals(null, viewModel.state.value.entryId)
    }
}
