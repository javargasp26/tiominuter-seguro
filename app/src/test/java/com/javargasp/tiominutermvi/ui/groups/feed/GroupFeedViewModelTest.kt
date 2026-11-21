package com.javargasp.tiominutermvi.ui.groups.feed

import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.FeedLimits
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.Reaction
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalog
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalogSource
import com.javargasp.tiominutermvi.testutil.FeedTestKit
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import com.javargasp.tiominutermvi.testutil.XmlWorkoutNameTexts
import com.javargasp.tiominutermvi.testutil.dailyRoutine
import com.javargasp.tiominutermvi.testutil.feedPost
import com.javargasp.tiominutermvi.ui.components.text
import com.javargasp.tiominutermvi.ui.groups.common.GroupText
import com.javargasp.tiominutermvi.ui.groups.common.toText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Feed tab: this week's posts live while visible, reactions, hide for me, deletes, cleanup. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupFeedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val kit = FeedTestKit()
    private val feed = kit.feed
    private val now = kit.groups.now

    /** This reader's catalogs: Pull Day (64) with its Spanish name, and the 30 daily days. */
    private val names = WorkoutNameCatalogSource {
        flowOf(
            WorkoutNameCatalog.of(
                gymRoutines = listOf(GymRoutine(64, "Pull Day", emptyList(), routineNameEs = "Día de tirón")),
                dailyRoutines = (1..30).map { dailyRoutine(it) }
            )
        )
    }

    private fun TestScope.feedOf(
        groupId: String = "g",
        ownerUid: String? = "ana"
    ): Pair<GroupFeedViewModel, List<GroupFeedEffect>> {
        val viewModel = GroupFeedViewModel(
            groupId, kit.operations, feed, kit.cleaner, kit.preferences, kit.groups.network, kit.groups.dates, names
        ) { kit.groups.now }
        val effects = mutableListOf<GroupFeedEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        viewModel.handleIntent(GroupFeedIntent.OnVisible)
        viewModel.handleIntent(GroupFeedIntent.OnOwnerChanged(ownerUid))
        runCurrent()
        return viewModel to effects
    }

    private fun seedWeek() {
        kit.groups.repository.seedGroup("g", "Crew", "AAAAAAAA", listOf("ana", "me", "luis"))
        feed.seed("g", feedPost("older", "luis", now - 3_000))
        feed.seed("g", feedPost("mine", "me", now - 2_000))
        feed.seed("g", feedPost("newest", "ana", now - 1_000, reactions = mapOf("luis" to Reaction.FIRE)))
        feed.seed("g", feedPost("lastWeek", "ana", now - 500, week = "2026-10-12"))
    }

    @Test
    fun `shows this week's posts newest first, live only while visible`() = runTest {
        seedWeek()
        val (viewModel, _) = feedOf()
        assertEquals(listOf("newest", "mine", "older"), viewModel.state.value.posts.map { it.id })
        assertEquals("2026-10-19", viewModel.state.value.weekStart)

        feed.seed("g", feedPost("live", "luis", now))
        runCurrent()
        assertEquals("live", viewModel.state.value.posts.first().id)

        viewModel.handleIntent(GroupFeedIntent.OnHidden)
        runCurrent()
        feed.seed("g", feedPost("whileHidden", "luis", now + 1))
        runCurrent()
        assertFalse(viewModel.state.value.posts.any { it.id == "whileHidden" })
        viewModel.handleIntent(GroupFeedIntent.OnVisible)
        runCurrent()
        assertEquals("whileHidden", viewModel.state.value.posts.first().id)
    }

    @Test
    fun `pages of 20, and load more raises the listener's limit`() = runTest {
        kit.groups.repository.seedGroup("g", "Crew", "AAAAAAAA", listOf("ana", "me"))
        (1..25).forEach { feed.seed("g", feedPost("p$it", "ana", now - it)) }
        val (viewModel, _) = feedOf()
        assertEquals(FeedLimits.PAGE_SIZE, viewModel.state.value.posts.size)
        assertTrue(viewModel.state.value.canLoadMore)
        viewModel.handleIntent(GroupFeedIntent.OnLoadMore)
        runCurrent()
        assertEquals(25, viewModel.state.value.posts.size)
        assertTrue("observeWeek:g:40" in feed.calls)
    }

    @Test
    fun `an empty feed on Monday says a new week started`() = runTest {
        kit.groups.repository.seedGroup("g", "Crew", "AAAAAAAA", listOf("ana", "me"))
        kit.groups.now = 1_792_404_000_000L // Monday 2026-10-19 10:00 UTC
        val (viewModel, _) = feedOf()
        assertTrue(viewModel.state.value.posts.isEmpty())
        assertTrue(viewModel.state.value.isFirstDayOfWeek)
    }

    @Test
    fun `tapping a reaction sets it, the same again removes it, another changes it`() = runTest {
        seedWeek()
        val (viewModel, _) = feedOf()
        fun mine() = viewModel.state.value.posts.first { it.id == "newest" }.reactions["me"]

        viewModel.handleIntent(GroupFeedIntent.OnReactionTapped("newest", Reaction.STRONG))
        runCurrent()
        assertEquals(Reaction.STRONG, mine())
        assertEquals(Reaction.STRONG, feed.post("g", "newest")!!.reactions["me"])
        assertEquals("the other member's reaction stays", Reaction.FIRE, feed.post("g", "newest")!!.reactions["luis"])

        viewModel.handleIntent(GroupFeedIntent.OnReactionTapped("newest", Reaction.CLAP))
        runCurrent()
        assertEquals(Reaction.CLAP, mine())
        viewModel.handleIntent(GroupFeedIntent.OnReactionTapped("newest", Reaction.CLAP))
        runCurrent()
        assertNull(mine())
        assertNull(feed.post("g", "newest")!!.reactions["me"])
        assertTrue("setReaction:g:newest:none" in feed.calls)
    }

    @Test
    fun `a reaction is optimistic and rolled back when the write fails`() = runTest {
        seedWeek()
        val (viewModel, effects) = feedOf()
        feed.failOn("setReaction", GroupError.PERMISSION_DENIED)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        feed.reactionGate = gate
        viewModel.handleIntent(GroupFeedIntent.OnReactionTapped("newest", Reaction.RAISED))
        runCurrent()
        assertEquals("shown before the write answers", Reaction.RAISED, viewModel.state.value.posts.first { it.id == "newest" }.reactions["me"])

        gate.complete(Unit)
        runCurrent()
        assertNull("rolled back", viewModel.state.value.posts.first { it.id == "newest" }.reactions["me"])
        assertEquals(GroupFeedEffect.ShowMessage(GroupText(R.string.feed_message_reaction_failed)), effects.last())
    }

    @Test
    fun `offline a reaction isn't sent`() = runTest {
        seedWeek()
        val (viewModel, effects) = feedOf()
        kit.groups.network.online = false
        viewModel.handleIntent(GroupFeedIntent.OnReactionTapped("newest", Reaction.FIRE))
        runCurrent()
        assertTrue(feed.calls.none { it.startsWith("setReaction") })
        assertEquals(GroupFeedEffect.ShowMessage(GroupError.NETWORK.toText()), effects.last())
    }

    @Test
    fun `hide for me filters the post locally, per account`() = runTest {
        seedWeek()
        val (viewModel, _) = feedOf()
        viewModel.handleIntent(GroupFeedIntent.OnHideClicked("older"))
        runCurrent()
        assertFalse(viewModel.state.value.posts.any { it.id == "older" })
        assertNotNull("nothing changes on the server", feed.post("g", "older"))
        assertEquals(setOf("g/older"), kit.preferences.hiddenPosts("me"))
    }

    @Test
    fun `deleting my post asks first, then deletes it`() = runTest {
        seedWeek()
        val (viewModel, effects) = feedOf()
        viewModel.handleIntent(GroupFeedIntent.OnDeleteClicked("mine"))
        runCurrent()
        assertEquals(GroupFeedDialog.DeleteOwn("mine"), viewModel.state.value.dialog)
        assertNotNull(feed.post("g", "mine"))

        viewModel.handleIntent(GroupFeedIntent.OnConfirm)
        runCurrent()
        assertNull(feed.post("g", "mine"))
        assertNull(viewModel.state.value.dialog)
        assertEquals(GroupFeedEffect.ShowMessage(GroupText(R.string.feed_message_deleted)), effects.last())
    }

    @Test
    fun `the owner deletes anyone's post after confirming, a member can't`() = runTest {
        seedWeek()
        val (member, _) = feedOf(ownerUid = "ana")
        member.handleIntent(GroupFeedIntent.OnDeleteClicked("older"))
        runCurrent()
        assertNull("no delete for someone else's post", member.state.value.dialog)

        kit.groups.repository.transferOwnership("g", "me")
        val (owner, _) = feedOf(ownerUid = "me")
        owner.handleIntent(GroupFeedIntent.OnDeleteClicked("older"))
        runCurrent()
        assertEquals(GroupFeedDialog.DeleteAsOwner("older", "Luis"), owner.state.value.dialog)
        owner.handleIntent(GroupFeedIntent.OnConfirm)
        runCurrent()
        assertNull(feed.post("g", "older"))
    }

    @Test
    fun `cancelling a delete keeps the post`() = runTest {
        seedWeek()
        val (viewModel, _) = feedOf()
        viewModel.handleIntent(GroupFeedIntent.OnDeleteClicked("mine"))
        viewModel.handleIntent(GroupFeedIntent.OnDialogDismissed)
        runCurrent()
        assertNull(viewModel.state.value.dialog)
        assertNotNull(feed.post("g", "mine"))
    }

    @Test
    fun `opening the feed deletes expired posts once`() = runTest {
        seedWeek()
        feed.seed("g", feedPost("expired", "luis", now - 9 * 24 * 60 * 60 * 1000L, week = "2026-10-05"))
        val (viewModel, _) = feedOf()
        assertNull(feed.post("g", "expired"))
        viewModel.handleIntent(GroupFeedIntent.OnHidden)
        viewModel.handleIntent(GroupFeedIntent.OnVisible)
        runCurrent()
        assertEquals(1, feed.calls.count { it == "expiredPostIds:g" })
    }

    // ---------- Workout names in the reader's language ----------

    private fun GroupFeedState.titleOf(id: String, texts: XmlWorkoutNameTexts) = nameOf(posts.first { it.id == id }).text(texts)

    @Test
    fun `a Spanish reader sees an English author's posts in Spanish, and the reverse`() = runTest {
        kit.groups.repository.seedGroup("g", "Crew", "AAAAAAAA", listOf("ana", "me", "luis"))
        // Ana writes in English, Luis in Spanish
        feed.seed("g", feedPost("anaGym", "ana", now - 6, title = "Pull Day", titleKey = "gym/64"))
        feed.seed("g", feedPost("anaDaily", "ana", now - 5, source = TrainingSource.DAILY, title = "Day 5", titleKey = "daily/5"))
        feed.seed("g", feedPost("anaProgram", "ana", now - 4, source = TrainingSource.PROGRAM, title = "Upper A", titleKey = "program/UPPER_A"))
        feed.seed("g", feedPost("luisGym", "luis", now - 3, title = "Día de tirón", titleKey = "gym/64"))
        feed.seed("g", feedPost("luisDaily", "luis", now - 2, source = TrainingSource.DAILY, title = "Día 5", titleKey = "daily/5"))
        feed.seed("g", feedPost("luisProgram", "luis", now - 1, source = TrainingSource.PROGRAM, title = "Tren superior A", titleKey = "program/UPPER_A"))
        val (viewModel, _) = feedOf()
        val state = viewModel.state.value

        for (author in listOf("ana", "luis")) {
            assertEquals("Día de tirón", state.titleOf("${author}Gym", XmlWorkoutNameTexts.ES))
            assertEquals("Día 5", state.titleOf("${author}Daily", XmlWorkoutNameTexts.ES))
            assertEquals("Tren superior A", state.titleOf("${author}Program", XmlWorkoutNameTexts.ES))
            assertEquals("Pull Day", state.titleOf("${author}Gym", XmlWorkoutNameTexts.EN))
            assertEquals("Day 5", state.titleOf("${author}Daily", XmlWorkoutNameTexts.EN))
            assertEquals("Upper A", state.titleOf("${author}Program", XmlWorkoutNameTexts.EN))
        }
    }

    @Test
    fun `a post without a key, with an unknown code or a routine this reader lacks shows its title`() = runTest {
        kit.groups.repository.seedGroup("g", "Crew", "AAAAAAAA", listOf("ana", "me"))
        feed.seed("g", feedPost("old", "ana", now - 3, title = "Pull Day"))
        feed.seed("g", feedPost("newer", "ana", now - 2, source = TrainingSource.PROGRAM, title = "Hyrox", titleKey = "program/HYROX"))
        feed.seed("g", feedPost("removed", "ana", now - 1, title = "Old routine", titleKey = "gym/99"))
        val (viewModel, _) = feedOf()
        val state = viewModel.state.value

        assertEquals("Pull Day", state.titleOf("old", XmlWorkoutNameTexts.ES))
        assertEquals("Hyrox", state.titleOf("newer", XmlWorkoutNameTexts.ES))
        assertEquals("Old routine", state.titleOf("removed", XmlWorkoutNameTexts.ES))
    }
}
