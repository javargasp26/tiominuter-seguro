package com.javargasp.tiominutermvi.ui.groups

import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.CreatedGroup
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.InvitePreview
import com.javargasp.tiominutermvi.testutil.FakeGroupRepository.Companion.summary
import com.javargasp.tiominutermvi.testutil.GroupsTestKit
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.base.MviEffect
import com.javargasp.tiominutermvi.ui.base.MviIntent
import com.javargasp.tiominutermvi.ui.base.MviState
import com.javargasp.tiominutermvi.ui.groups.common.GroupText
import com.javargasp.tiominutermvi.ui.groups.common.toText
import com.javargasp.tiominutermvi.ui.groups.create.GroupCreateEffect
import com.javargasp.tiominutermvi.ui.groups.create.GroupCreateIntent
import com.javargasp.tiominutermvi.ui.groups.create.GroupCreateViewModel
import com.javargasp.tiominutermvi.ui.groups.detail.GroupDetailDialog
import com.javargasp.tiominutermvi.ui.groups.detail.GroupDetailEffect
import com.javargasp.tiominutermvi.ui.groups.detail.GroupDetailIntent
import com.javargasp.tiominutermvi.ui.groups.detail.GroupDetailState
import com.javargasp.tiominutermvi.ui.groups.detail.GroupDetailViewModel
import com.javargasp.tiominutermvi.ui.groups.join.GroupJoinEffect
import com.javargasp.tiominutermvi.ui.groups.join.GroupJoinIntent
import com.javargasp.tiominutermvi.ui.groups.join.GroupJoinViewModel
import com.javargasp.tiominutermvi.ui.groups.list.GoalRequiredFor
import com.javargasp.tiominutermvi.ui.groups.list.GroupListEffect
import com.javargasp.tiominutermvi.ui.groups.list.GroupListIntent
import com.javargasp.tiominutermvi.ui.groups.list.GroupListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The four group screens' ViewModels over the groups domain and a fake Firestore. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val kit = GroupsTestKit()
    private val repository = kit.repository
    private val feedPreferences = com.javargasp.tiominutermvi.testutil.InMemoryFeedPreferences()

    private fun <I : MviIntent, S : MviState, E : MviEffect> TestScope.effectsOf(viewModel: BaseViewModel<I, S, E>): List<E> {
        val effects = mutableListOf<E>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.collect { effects += it } }
        return effects
    }

    // ---------- List ----------

    @Test
    fun `the list shows my groups with my position, a guest sees the explanation`() = runTest {
        repository.seedGroup("g", "Crew", "CREWCREW", listOf("me", "ana"))
        val viewModel = GroupListViewModel(kit.operations)

        viewModel.handleIntent(GroupListIntent.OnStart)
        runCurrent()
        assertEquals(false, viewModel.state.value.isGuest)
        assertEquals(listOf("g"), viewModel.state.value.groups.map { it.group.id })
        assertTrue(viewModel.state.value.groups.single().isOwner)

        kit.auth.signOut()
        viewModel.handleIntent(GroupListIntent.OnStart)
        runCurrent()
        assertEquals(true, viewModel.state.value.isGuest)
        assertTrue(viewModel.state.value.groups.isEmpty())
    }

    @Test
    fun `create and join need a weekly goal first, then open their screens`() = runTest {
        val viewModel = GroupListViewModel(kit.operations)
        val effects = effectsOf(viewModel)
        viewModel.handleIntent(GroupListIntent.OnStart)
        kit.goals.goals.value = emptyList()

        viewModel.handleIntent(GroupListIntent.OnJoinClicked)
        runCurrent()
        assertEquals(GoalRequiredFor.JOIN, viewModel.state.value.goalRequiredFor)
        viewModel.handleIntent(GroupListIntent.OnSetGoalClicked)
        runCurrent()
        assertNull(viewModel.state.value.goalRequiredFor)

        kit.goals.goals.value = listOf(com.javargasp.tiominutermvi.domain.goal.WeeklyGoal(3, "2026-10-19"))
        viewModel.handleIntent(GroupListIntent.OnCreateClicked)
        viewModel.handleIntent(GroupListIntent.OnJoinClicked)
        runCurrent()
        assertEquals(
            listOf(GroupListEffect.NavigateToWeeklyGoal, GroupListEffect.NavigateToCreate, GroupListEffect.NavigateToJoin),
            effects
        )
    }

    @Test
    fun `with 5 groups, create and join show the limit, and a load error offers retry`() = runTest {
        (1..5).forEach { repository.seedGroup("g$it", "G$it", "CODE000$it", listOf("me")) }
        val viewModel = GroupListViewModel(kit.operations)
        val effects = effectsOf(viewModel)
        viewModel.handleIntent(GroupListIntent.OnStart)
        runCurrent()

        viewModel.handleIntent(GroupListIntent.OnCreateClicked)
        runCurrent()
        assertEquals(listOf(GroupListEffect.ShowMessage(GroupError.GROUP_LIMIT.toText())), effects)

        val fresh = GroupListViewModel(kit.operations)
        repository.failOn("memberships", GroupError.NETWORK)
        fresh.handleIntent(GroupListIntent.OnStart)
        runCurrent()
        assertTrue(fresh.state.value.loadError)
        fresh.handleIntent(GroupListIntent.OnRetry)
        runCurrent()
        assertFalse(fresh.state.value.loadError)
        assertEquals(5, fresh.state.value.groups.size)
    }

    // ---------- Create ----------

    @Test
    fun `create shows the name error, then the created group with copy, share and open`() = runTest {
        val viewModel = GroupCreateViewModel(kit.operations)
        val effects = effectsOf(viewModel)

        viewModel.handleIntent(GroupCreateIntent.OnCreateClicked)
        runCurrent()
        assertTrue(viewModel.state.value.showNameError)

        viewModel.handleIntent(GroupCreateIntent.OnNameChanged("Morning crew"))
        viewModel.handleIntent(GroupCreateIntent.OnCreateClicked)
        runCurrent()
        assertEquals(CreatedGroup("group1", "Morning crew", "AAAAAAAA"), viewModel.state.value.created)

        viewModel.handleIntent(GroupCreateIntent.OnCopyClicked)
        viewModel.handleIntent(GroupCreateIntent.OnShareClicked)
        viewModel.handleIntent(GroupCreateIntent.OnOpenGroupClicked)
        runCurrent()
        assertEquals(
            listOf(
                GroupCreateEffect.CopyCode("AAAAAAAA"),
                GroupCreateEffect.ShareInvite("Morning crew", "AAAAAAAA"),
                GroupCreateEffect.NavigateToGroup("group1")
            ),
            effects
        )
    }

    @Test
    fun `a failed creation shows its message and allows another try`() = runTest {
        val viewModel = GroupCreateViewModel(kit.operations)
        val effects = effectsOf(viewModel)
        kit.network.online = false

        viewModel.handleIntent(GroupCreateIntent.OnNameChanged("Crew"))
        viewModel.handleIntent(GroupCreateIntent.OnCreateClicked)
        runCurrent()

        assertEquals(listOf(GroupCreateEffect.ShowMessage(GroupError.NETWORK.toText())), effects)
        assertFalse(viewModel.state.value.isCreating)
        assertNull(viewModel.state.value.created)
    }

    // ---------- Join ----------

    @Test
    fun `typing a code normalizes it, flags ambiguous characters and previews the group`() = runTest {
        repository.seedGroup("g", "Crew", "K7MPX2QA", listOf("ana"))
        val viewModel = GroupJoinViewModel(kit.operations)
        val effects = effectsOf(viewModel)

        viewModel.handleIntent(GroupJoinIntent.OnCodeChanged("k7mp x2qo"))
        runCurrent()
        assertEquals("K7MPX2QO", viewModel.state.value.code)
        assertEquals(GroupText(R.string.groups_join_code_invalid_chars), viewModel.state.value.error)
        assertNull(viewModel.state.value.preview)

        viewModel.handleIntent(GroupJoinIntent.OnCodeChanged("k7mp-x2qa"))
        runCurrent()
        assertEquals(InvitePreview("K7MPX2QA", "g", "Crew"), viewModel.state.value.preview)
        assertNull(viewModel.state.value.error)

        viewModel.handleIntent(GroupJoinIntent.OnJoinClicked)
        runCurrent()
        assertEquals(listOf(GroupJoinEffect.NavigateToGroup("g")), effects)
    }

    @Test
    fun `join shows each error - unknown code, full, already a member, limit`() = runTest {
        repository.seedGroup("full", "Full", "FZFZFZFZ", (1..20).map { "m$it" })
        repository.seedGroup("mine", "Mine", "MXNEMXNE", listOf("ana", "me"))
        val viewModel = GroupJoinViewModel(kit.operations)

        viewModel.handleIntent(GroupJoinIntent.OnCodeChanged("ZZZZZZZZ"))
        runCurrent()
        assertEquals(GroupError.INVALID_CODE.toText(), viewModel.state.value.error)

        viewModel.handleIntent(GroupJoinIntent.OnCodeChanged("FZFZFZFZ"))
        runCurrent()
        viewModel.handleIntent(GroupJoinIntent.OnJoinClicked)
        runCurrent()
        assertEquals(GroupError.GROUP_FULL.toText(), viewModel.state.value.error)

        viewModel.handleIntent(GroupJoinIntent.OnCodeChanged("MXNEMXNE"))
        runCurrent()
        viewModel.handleIntent(GroupJoinIntent.OnJoinClicked)
        runCurrent()
        assertEquals(GroupError.ALREADY_MEMBER.toText(), viewModel.state.value.error)

        (1..4).forEach { repository.seedGroup("g$it", "G$it", "CODE000$it", listOf("me")) }
        repository.seedGroup("open", "Open", "PPPPPPPP", listOf("ana"))
        viewModel.handleIntent(GroupJoinIntent.OnCodeChanged("PPPPPPPP"))
        runCurrent()
        viewModel.handleIntent(GroupJoinIntent.OnJoinClicked)
        runCurrent()
        assertEquals(GroupError.GROUP_LIMIT.toText(), viewModel.state.value.error)
    }

    // ---------- Detail ----------

    private fun TestScope.detail(groupId: String = "g"): Pair<GroupDetailViewModel, List<GroupDetailEffect>> {
        val viewModel = GroupDetailViewModel(groupId, kit.operations, repository, kit.dates, feedPreferences)
        val effects = effectsOf(viewModel)
        viewModel.handleIntent(GroupDetailIntent.OnVisible)
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun `the detail remembers the last tab of each group`() = runTest {
        repository.seedGroup("g", "Crew", "CREWCREW", listOf("me", "ana"))
        val (first, _) = detail()
        assertEquals(GroupDetailState.TAB_RANKING, first.state.value.tab)
        first.handleIntent(GroupDetailIntent.OnTabSelected(GroupDetailState.TAB_FEED))
        runCurrent()
        assertEquals(GroupDetailState.TAB_FEED, first.state.value.tab)
        first.handleIntent(GroupDetailIntent.OnHidden)
        runCurrent()

        val (again, _) = detail()
        assertEquals(GroupDetailState.TAB_FEED, again.state.value.tab)
        again.handleIntent(GroupDetailIntent.OnHidden)
        repository.seedGroup("other", "Other", "OTHEROTH", listOf("me"))
        val (other, _) = detail("other")
        assertEquals("per group", GroupDetailState.TAB_RANKING, other.state.value.tab)
        other.handleIntent(GroupDetailIntent.OnHidden)
        runCurrent()
    }

    @Test
    fun `the detail ranks this week live, with a stale member at 0, and stops listening when hidden`() = runTest {
        repository.seedGroup(
            "g", "Crew", "CREWCREW", listOf("me", "ana", "sam"),
            summaries = mapOf(
                "me" to summary(workouts = 1, goal = 3),
                "ana" to summary(workouts = 3, goal = 3),
                "sam" to summary(week = "2026-10-12", workouts = 5, goal = 3, streak = 9)
            )
        )
        val (viewModel, _) = detail()

        val state = viewModel.state.value
        assertEquals(listOf("ana", "me", "sam"), state.ranking.map { it.member.uid })
        assertEquals(0, state.ranking[2].workouts)
        assertTrue(state.isOwner)
        assertEquals((19..25).map { "2026-10-$it" }, state.weekDates)

        repository.updateSummary("g", "sam", state.ranking[2].member.profile, summary(workouts = 3, goal = 3, streak = 1))
        runCurrent()
        assertEquals("sam", viewModel.state.value.ranking.first().member.uid)

        viewModel.handleIntent(GroupDetailIntent.OnHidden)
        runCurrent()
        repository.updateSummary("g", "me", state.ranking[1].member.profile, summary(workouts = 3, goal = 3, streak = 5))
        runCurrent()
        assertEquals("sam", viewModel.state.value.ranking.first().member.uid)
    }

    @Test
    fun `leave - a member confirms and leaves, an owner alone deletes, an owner with members picks an heir`() = runTest {
        repository.seedGroup("g", "Crew", "CREWCREW", listOf("ana", "me"))
        val (member, memberEffects) = detail()
        member.handleIntent(GroupDetailIntent.OnLeaveClicked)
        runCurrent()
        assertEquals(GroupDetailDialog.Leave, member.state.value.dialog)
        member.handleIntent(GroupDetailIntent.OnConfirm)
        runCurrent()
        assertEquals(listOf(GroupDetailEffect.Close(GroupText(R.string.groups_message_left))), memberEffects)

        repository.seedGroup("solo", "Solo", "SOLOSOLO", listOf("me"))
        val (solo, soloEffects) = detail("solo")
        solo.handleIntent(GroupDetailIntent.OnLeaveClicked)
        runCurrent()
        assertEquals(GroupDetailDialog.LeaveAlone, solo.state.value.dialog)
        solo.handleIntent(GroupDetailIntent.OnConfirm)
        runCurrent()
        assertEquals(listOf(GroupDetailEffect.Close(GroupText(R.string.groups_message_deleted))), soloEffects)
        assertNull(repository.groups["solo"])

        repository.seedGroup("mine", "Mine", "MXNEMXNE", listOf("me", "ana"))
        val (owner, ownerEffects) = detail("mine")
        owner.handleIntent(GroupDetailIntent.OnLeaveClicked)
        runCurrent()
        assertEquals(GroupDetailDialog.PickNewOwner(beforeLeaving = true), owner.state.value.dialog)
        owner.handleIntent(GroupDetailIntent.OnNewOwnerPicked("ana"))
        runCurrent()
        assertTrue((owner.state.value.dialog as GroupDetailDialog.ConfirmNewOwner).andLeave)
        owner.handleIntent(GroupDetailIntent.OnConfirm)
        runCurrent()
        assertEquals("ana", repository.groups.getValue("mine").ownerUid)
        assertEquals(listOf(GroupDetailEffect.Close(GroupText(R.string.groups_message_left))), ownerEffects)
    }

    @Test
    fun `owner actions go through confirmations with a snackbar result, delete in two steps`() = runTest {
        repository.seedGroup("g", "Crew", "CREWCREW", listOf("me", "ana", "luis"))
        val (viewModel, effects) = detail()

        viewModel.handleIntent(GroupDetailIntent.OnRemoveClicked("luis"))
        runCurrent()
        assertTrue(viewModel.state.value.dialog is GroupDetailDialog.RemoveMember)
        viewModel.handleIntent(GroupDetailIntent.OnConfirm)
        runCurrent()
        assertFalse(repository.members.getValue("g").containsKey("luis"))

        viewModel.handleIntent(GroupDetailIntent.OnRenameClicked)
        viewModel.handleIntent(GroupDetailIntent.OnRenameTextChanged("Evening crew"))
        viewModel.handleIntent(GroupDetailIntent.OnConfirm)
        runCurrent()
        assertEquals("Evening crew", repository.groups.getValue("g").name)

        viewModel.handleIntent(GroupDetailIntent.OnRegenerateClicked)
        viewModel.handleIntent(GroupDetailIntent.OnConfirm)
        runCurrent()
        assertEquals("AAAAAAAA", repository.groups.getValue("g").inviteCode)

        viewModel.handleIntent(GroupDetailIntent.OnDeleteClicked)
        viewModel.handleIntent(GroupDetailIntent.OnConfirm)
        runCurrent()
        assertEquals(GroupDetailDialog.DeleteConfirm, viewModel.state.value.dialog)
        assertTrue(repository.groups.containsKey("g"))
        viewModel.handleIntent(GroupDetailIntent.OnConfirm)
        runCurrent()
        assertNull(repository.groups["g"])

        assertEquals(
            listOf(
                GroupDetailEffect.ShowMessage(GroupText(R.string.groups_message_member_removed, listOf("Luis"))),
                GroupDetailEffect.ShowMessage(GroupText(R.string.groups_message_renamed)),
                GroupDetailEffect.ShowMessage(GroupText(R.string.groups_message_code_regenerated, listOf("AAAAAAAA"))),
                GroupDetailEffect.Close(GroupText(R.string.groups_message_deleted))
            ),
            effects
        )
    }

    @Test
    fun `a member gets no owner actions, transfer needs a confirmation, a failure keeps the screen`() = runTest {
        repository.seedGroup("g", "Crew", "CREWCREW", listOf("ana", "me"))
        val (member, _) = detail()
        member.handleIntent(GroupDetailIntent.OnRemoveClicked("ana"))
        member.handleIntent(GroupDetailIntent.OnDeleteClicked)
        member.handleIntent(GroupDetailIntent.OnRenameClicked)
        runCurrent()
        assertNull(member.state.value.dialog)

        repository.seedGroup("mine", "Mine", "MXNEMXNE", listOf("me", "ana"))
        val (owner, effects) = detail("mine")
        owner.handleIntent(GroupDetailIntent.OnMakeOwnerClicked("ana"))
        runCurrent()
        assertFalse((owner.state.value.dialog as GroupDetailDialog.ConfirmNewOwner).andLeave)
        repository.failOn("transferOwnership", GroupError.NETWORK)
        owner.handleIntent(GroupDetailIntent.OnConfirm)
        runCurrent()
        assertEquals(listOf(GroupDetailEffect.ShowMessage(GroupError.NETWORK.toText())), effects)
        assertEquals("me", repository.groups.getValue("mine").ownerUid)
        assertFalse(owner.state.value.isWorking)
    }

    @Test
    fun `being removed while looking at the group closes it`() = runTest {
        repository.seedGroup("g", "Crew", "CREWCREW", listOf("ana", "me"))
        val (_, effects) = detail()

        repository.deleteGroup("ana", "g", "CREWCREW")
        runCurrent()

        assertEquals(listOf(GroupDetailEffect.Close(GroupError.NOT_FOUND.toText())), effects)
    }
}
