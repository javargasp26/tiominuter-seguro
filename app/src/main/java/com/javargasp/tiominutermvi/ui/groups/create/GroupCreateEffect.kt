package com.javargasp.tiominutermvi.ui.groups.create

import com.javargasp.tiominutermvi.ui.base.MviEffect
import com.javargasp.tiominutermvi.ui.groups.common.GroupText

sealed interface GroupCreateEffect : MviEffect {
    data class CopyCode(val code: String) : GroupCreateEffect
    data class ShareInvite(val groupName: String, val code: String) : GroupCreateEffect
    data class NavigateToGroup(val groupId: String) : GroupCreateEffect
    data object NavigateBack : GroupCreateEffect
    data class ShowMessage(val text: GroupText) : GroupCreateEffect
}
