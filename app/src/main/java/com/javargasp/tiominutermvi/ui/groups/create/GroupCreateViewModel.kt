package com.javargasp.tiominutermvi.ui.groups.create

import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.GroupLimits
import com.javargasp.tiominutermvi.domain.groups.GroupOperations
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.groups.common.toText

/** Create a group: a name (1–40) → created → its invite code to copy or share. */
class GroupCreateViewModel(
    private val operations: GroupOperations
) : BaseViewModel<GroupCreateIntent, GroupCreateState, GroupCreateEffect>(GroupCreateState()) {

    override suspend fun reduce(intent: GroupCreateIntent, currentState: GroupCreateState) {
        when (intent) {
            is GroupCreateIntent.OnNameChanged ->
                setState(currentState.copy(name = intent.name.take(GroupLimits.NAME_MAX_LENGTH + LENGTH_SLACK)))
            GroupCreateIntent.OnCreateClicked -> create(currentState)
            GroupCreateIntent.OnCopyClicked -> currentState.created?.let { sendEffect(GroupCreateEffect.CopyCode(it.code)) }
            GroupCreateIntent.OnShareClicked -> currentState.created?.let {
                sendEffect(GroupCreateEffect.ShareInvite(it.name, it.code))
            }
            GroupCreateIntent.OnOpenGroupClicked -> currentState.created?.let {
                sendEffect(GroupCreateEffect.NavigateToGroup(it.id))
            }
            GroupCreateIntent.OnBackClicked -> sendEffect(GroupCreateEffect.NavigateBack)
        }
    }

    private suspend fun create(currentState: GroupCreateState) {
        if (currentState.isCreating || currentState.created != null) return
        if (!currentState.isNameValid) {
            setState(currentState.copy(showNameError = true))
            return
        }
        setState(currentState.copy(isCreating = true, showNameError = false))
        try {
            val created = operations.create(currentState.name)
            setState(state.value.copy(isCreating = false, created = created))
        } catch (e: GroupException) {
            setState(state.value.copy(isCreating = false))
            sendEffect(GroupCreateEffect.ShowMessage(e.error.toText()))
        }
    }

    private companion object {
        // Spaces around the name are trimmed, so a few extra characters are allowed while typing
        const val LENGTH_SLACK = 10
    }
}
