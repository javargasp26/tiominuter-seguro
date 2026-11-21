package com.javargasp.tiominutermvi.ui.groups.join

import com.javargasp.tiominutermvi.domain.groups.InviteCodes
import com.javargasp.tiominutermvi.domain.groups.InvitePreview
import com.javargasp.tiominutermvi.ui.base.MviState
import com.javargasp.tiominutermvi.ui.groups.common.GroupText

/**
 * [code] is already normalized (uppercase, no spaces or dashes). A complete valid code is looked up
 * at once; [preview] names the group before joining. [error] is the last failure, shown inline.
 */
data class GroupJoinState(
    val code: String = "",
    val isLookingUp: Boolean = false,
    val preview: InvitePreview? = null,
    val isJoining: Boolean = false,
    val error: GroupText? = null
) : MviState {
    val hasInvalidCharacters: Boolean get() = InviteCodes.hasInvalidCharacters(code)
    val canJoin: Boolean get() = preview != null && !isJoining
}
