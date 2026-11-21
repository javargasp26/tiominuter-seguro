package com.javargasp.tiominutermvi.ui.groups.detail

import com.javargasp.tiominutermvi.domain.groups.FeedPreferences
import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.GroupLimits
import com.javargasp.tiominutermvi.domain.groups.GroupOperations
import com.javargasp.tiominutermvi.domain.groups.GroupRanking
import com.javargasp.tiominutermvi.domain.groups.GroupRepository
import com.javargasp.tiominutermvi.domain.groups.GroupSnapshot
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.groups.common.GroupText
import com.javargasp.tiominutermvi.ui.groups.common.toText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One group: a real-time listener only while the screen is visible ([GroupDetailIntent.OnVisible]
 * / [GroupDetailIntent.OnHidden]), this week's ranking, the invite code, Leave, and the owner's
 * actions, each behind a confirmation. Losing access (removed, or the group deleted) closes the
 * screen with a message.
 */
class GroupDetailViewModel(
    private val groupId: String,
    private val operations: GroupOperations,
    private val repository: GroupRepository,
    private val dates: LocalDateProvider,
    private val preferences: FeedPreferences
) : BaseViewModel<GroupDetailIntent, GroupDetailState, GroupDetailEffect>(
    GroupDetailState(tab = if (preferences.detailTab(groupId) == GroupDetailState.TAB_FEED) GroupDetailState.TAB_FEED else GroupDetailState.TAB_RANKING)
) {

    private var listener: Job? = null
    private var visible = false

    override suspend fun reduce(intent: GroupDetailIntent, currentState: GroupDetailState) {
        when (intent) {
            GroupDetailIntent.OnVisible -> {
                visible = true
                listen()
            }
            GroupDetailIntent.OnHidden -> {
                visible = false
                stopListening()
            }
            GroupDetailIntent.OnCopyCode -> currentState.group?.let { sendEffect(GroupDetailEffect.CopyCode(it.inviteCode)) }
            GroupDetailIntent.OnShareCode -> currentState.group?.let {
                sendEffect(GroupDetailEffect.ShareInvite(it.name, it.inviteCode))
            }
            GroupDetailIntent.OnLeaveClicked -> openDialog(
                when {
                    !currentState.isOwner -> GroupDetailDialog.Leave
                    currentState.otherMembers.isEmpty() -> GroupDetailDialog.LeaveAlone
                    else -> GroupDetailDialog.PickNewOwner(beforeLeaving = true)
                }
            )
            GroupDetailIntent.OnRenameClicked -> if (currentState.isOwner) {
                setState(currentState.copy(dialog = GroupDetailDialog.Rename, renameText = currentState.group?.name.orEmpty()))
            }
            is GroupDetailIntent.OnRenameTextChanged ->
                setState(currentState.copy(renameText = intent.text.take(GroupLimits.NAME_MAX_LENGTH + 10)))
            GroupDetailIntent.OnRegenerateClicked -> if (currentState.isOwner) openDialog(GroupDetailDialog.RegenerateCode)
            is GroupDetailIntent.OnRemoveClicked -> memberOf(intent.memberUid)?.let {
                if (currentState.isOwner) openDialog(GroupDetailDialog.RemoveMember(it))
            }
            GroupDetailIntent.OnTransferClicked -> if (currentState.isOwner && currentState.otherMembers.isNotEmpty()) {
                openDialog(GroupDetailDialog.PickNewOwner(beforeLeaving = false))
            }
            is GroupDetailIntent.OnMakeOwnerClicked -> memberOf(intent.memberUid)?.let {
                if (currentState.isOwner) openDialog(GroupDetailDialog.ConfirmNewOwner(it, andLeave = false))
            }
            is GroupDetailIntent.OnNewOwnerPicked -> {
                val picker = currentState.dialog as? GroupDetailDialog.PickNewOwner ?: return
                memberOf(intent.memberUid)?.let { openDialog(GroupDetailDialog.ConfirmNewOwner(it, picker.beforeLeaving)) }
            }
            GroupDetailIntent.OnDeleteClicked -> if (currentState.isOwner) openDialog(GroupDetailDialog.DeleteExplain)
            GroupDetailIntent.OnConfirm -> confirm(currentState)
            GroupDetailIntent.OnDialogDismissed -> if (!currentState.isWorking) setState(currentState.copy(dialog = null))
            GroupDetailIntent.OnBackClicked -> sendEffect(GroupDetailEffect.NavigateBack)
            is GroupDetailIntent.OnTabSelected -> if (intent.tab == GroupDetailState.TAB_RANKING || intent.tab == GroupDetailState.TAB_FEED) {
                preferences.setDetailTab(groupId, intent.tab)
                setState(currentState.copy(tab = intent.tab))
            }
        }
    }

    private fun openDialog(dialog: GroupDetailDialog) {
        if (!state.value.isWorking) setState(state.value.copy(dialog = dialog))
    }

    private fun memberOf(uid: String) = state.value.ranking.firstOrNull { it.member.uid == uid }?.member

    private fun listen() {
        if (listener?.isActive == true) return
        listener = viewModelScope.launch {
            val uid = try {
                operations.currentUser().uid
            } catch (e: GroupException) {
                sendEffect(GroupDetailEffect.Close(e.error.toText()))
                return@launch
            }
            try {
                repository.observeGroup(groupId).collect { snapshot -> show(snapshot, uid) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroupException) {
                when (e.error) {
                    GroupError.NOT_FOUND, GroupError.PERMISSION_DENIED ->
                        sendEffect(GroupDetailEffect.Close(GroupError.NOT_FOUND.toText()))
                    else -> {
                        setState(state.value.copy(isLoading = false))
                        sendEffect(GroupDetailEffect.ShowMessage(e.error.toText()))
                    }
                }
            }
        }
    }

    private fun stopListening() {
        listener?.cancel()
        listener = null
    }

    private fun show(snapshot: GroupSnapshot, uid: String) {
        val today = dates.today()
        setState(
            state.value.copy(
                isLoading = false,
                group = snapshot.group,
                myUid = uid,
                ranking = GroupRanking.rank(snapshot.members, dates.mondayOf(today), uid),
                weekDates = dates.weekDates(today)
            )
        )
    }

    private suspend fun confirm(currentState: GroupDetailState) {
        val group = currentState.group ?: return
        if (currentState.isWorking) return
        when (val dialog = currentState.dialog) {
            GroupDetailDialog.Rename -> work(closing = false) {
                operations.rename(group, currentState.renameText)
                message(GroupText(R.string.groups_message_renamed))
            }
            GroupDetailDialog.RegenerateCode -> work(closing = false) {
                val code = operations.regenerateCode(group)
                message(GroupText(R.string.groups_message_code_regenerated, listOf(code)))
            }
            is GroupDetailDialog.RemoveMember -> work(closing = false) {
                operations.removeMember(group, dialog.member.uid)
                message(GroupText(R.string.groups_message_member_removed, listOf(dialog.member.profile.displayName)))
            }
            is GroupDetailDialog.ConfirmNewOwner -> if (dialog.andLeave) {
                work(closing = true) {
                    operations.leave(group, dialog.member.uid)
                    sendEffect(GroupDetailEffect.Close(GroupText(R.string.groups_message_left)))
                }
            } else {
                work(closing = false) {
                    operations.transfer(group, dialog.member.uid)
                    message(GroupText(R.string.groups_message_owner_changed, listOf(dialog.member.profile.displayName)))
                }
            }
            GroupDetailDialog.DeleteExplain -> setState(currentState.copy(dialog = GroupDetailDialog.DeleteConfirm))
            GroupDetailDialog.DeleteConfirm -> work(closing = true) {
                operations.delete(group)
                sendEffect(GroupDetailEffect.Close(GroupText(R.string.groups_message_deleted)))
            }
            GroupDetailDialog.Leave -> work(closing = true) {
                operations.leave(group)
                sendEffect(GroupDetailEffect.Close(GroupText(R.string.groups_message_left)))
            }
            GroupDetailDialog.LeaveAlone -> work(closing = true) {
                operations.leave(group)
                sendEffect(GroupDetailEffect.Close(GroupText(R.string.groups_message_deleted)))
            }
            is GroupDetailDialog.PickNewOwner, null -> Unit
        }
    }

    private fun message(text: GroupText) {
        sendEffect(GroupDetailEffect.ShowMessage(text))
    }

    /**
     * Runs an action with the dialog open and [GroupDetailState.isWorking]. An action that ends my
     * membership ([closing]) stops the listener first, so its own "not found" doesn't race the result.
     */
    private suspend fun work(closing: Boolean, action: suspend () -> Unit) {
        setState(state.value.copy(isWorking = true))
        if (closing) stopListening()
        try {
            action()
            setState(state.value.copy(isWorking = false, dialog = null))
        } catch (e: GroupException) {
            setState(state.value.copy(isWorking = false, dialog = null))
            sendEffect(GroupDetailEffect.ShowMessage(e.error.toText()))
            if (closing && visible) listen()
        }
    }
}
