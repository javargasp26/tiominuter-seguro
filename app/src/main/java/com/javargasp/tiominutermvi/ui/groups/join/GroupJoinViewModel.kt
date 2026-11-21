package com.javargasp.tiominutermvi.ui.groups.join

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.GroupOperations
import com.javargasp.tiominutermvi.domain.groups.InviteCodes
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.groups.common.GroupText
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.ui.groups.common.toText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Join with a code: typed text is uppercased and stripped of spaces; ambiguous characters are
 * flagged; a complete code is looked up in inviteCodes to preview the group's name; Join.
 */
class GroupJoinViewModel(
    private val operations: GroupOperations
) : BaseViewModel<GroupJoinIntent, GroupJoinState, GroupJoinEffect>(GroupJoinState()) {

    private var lookup: Job? = null

    override suspend fun reduce(intent: GroupJoinIntent, currentState: GroupJoinState) {
        when (intent) {
            is GroupJoinIntent.OnCodeChanged -> onCodeChanged(intent.input)
            GroupJoinIntent.OnJoinClicked -> join()
            GroupJoinIntent.OnBackClicked -> sendEffect(GroupJoinEffect.NavigateBack)
        }
    }

    private fun onCodeChanged(input: String) {
        val code = InviteCodes.normalize(input).take(InviteCodes.LENGTH)
        if (code == state.value.code) return
        lookup?.cancel()
        val invalid = InviteCodes.hasInvalidCharacters(code)
        setState(
            state.value.copy(
                code = code,
                preview = null,
                isLookingUp = false,
                error = if (invalid) GroupText(R.string.groups_join_code_invalid_chars) else null
            )
        )
        if (!InviteCodes.isValid(code)) return
        setState(state.value.copy(isLookingUp = true))
        lookup = viewModelScope.launch {
            try {
                val preview = operations.findInvite(code)
                if (state.value.code == code) setState(state.value.copy(isLookingUp = false, preview = preview))
            } catch (e: GroupException) {
                if (state.value.code == code) setState(state.value.copy(isLookingUp = false, error = e.error.toText()))
            }
        }
    }

    private suspend fun join() {
        val current = state.value
        val preview = current.preview ?: return
        if (current.isJoining) return
        setState(current.copy(isJoining = true, error = null))
        try {
            val groupId = operations.join(preview)
            setState(state.value.copy(isJoining = false))
            sendEffect(GroupJoinEffect.NavigateToGroup(groupId))
        } catch (e: GroupException) {
            setState(state.value.copy(isJoining = false, error = e.error.toText()))
        }
    }
}
