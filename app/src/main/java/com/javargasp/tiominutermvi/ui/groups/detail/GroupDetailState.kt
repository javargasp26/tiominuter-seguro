package com.javargasp.tiominutermvi.ui.groups.detail

import com.javargasp.tiominutermvi.domain.groups.GroupInfo
import com.javargasp.tiominutermvi.domain.groups.GroupMember
import com.javargasp.tiominutermvi.domain.groups.RankedMember
import com.javargasp.tiominutermvi.ui.base.MviState

/** The confirmations and pickers of the detail screen (every destructive action has one). */
sealed interface GroupDetailDialog {
    data object Rename : GroupDetailDialog
    data object RegenerateCode : GroupDetailDialog
    data class RemoveMember(val member: GroupMember) : GroupDetailDialog

    /** Choose the new owner; [beforeLeaving] when the owner wants to leave a group with members. */
    data class PickNewOwner(val beforeLeaving: Boolean) : GroupDetailDialog
    data class ConfirmNewOwner(val member: GroupMember, val andLeave: Boolean) : GroupDetailDialog

    /** Delete group, in two steps. */
    data object DeleteExplain : GroupDetailDialog
    data object DeleteConfirm : GroupDetailDialog
    data object Leave : GroupDetailDialog

    /** The owner is the only member: leaving deletes the group. */
    data object LeaveAlone : GroupDetailDialog
}

/**
 * A group, live while the screen is visible: this week's [ranking] (computed here, never stored),
 * [weekDates] Monday–Sunday of my week for the strips, and the owner's actions.
 */
data class GroupDetailState(
    val isLoading: Boolean = true,
    val group: GroupInfo? = null,
    val ranking: List<RankedMember> = emptyList(),
    val weekDates: List<String> = emptyList(),
    val myUid: String? = null,
    val isWorking: Boolean = false,
    val dialog: GroupDetailDialog? = null,
    val renameText: String = "",
    /** [TAB_RANKING] or [TAB_FEED]; remembered per group. */
    val tab: Int = TAB_RANKING
) : MviState {
    val isOwner: Boolean get() = myUid != null && group?.ownerUid == myUid

    /** Everyone but me (removal and ownership pickers). */
    val otherMembers: List<GroupMember> get() = ranking.filterNot { it.isMe }.map { it.member }

    companion object {
        const val TAB_RANKING = 0
        const val TAB_FEED = 1
    }
}
