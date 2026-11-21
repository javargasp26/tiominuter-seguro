package com.javargasp.tiominutermvi.ui.groups.detail

import com.javargasp.tiominutermvi.ui.base.MviEffect
import com.javargasp.tiominutermvi.ui.groups.common.GroupText

sealed interface GroupDetailEffect : MviEffect {
    data class CopyCode(val code: String) : GroupDetailEffect
    data class ShareInvite(val groupName: String, val code: String) : GroupDetailEffect
    data class ShowMessage(val text: GroupText) : GroupDetailEffect

    /** I'm no longer in the group (left, deleted it, or was removed): a toast, then back. */
    data class Close(val text: GroupText) : GroupDetailEffect
    data object NavigateBack : GroupDetailEffect
}
