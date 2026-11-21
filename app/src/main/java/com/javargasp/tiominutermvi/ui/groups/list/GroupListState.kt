package com.javargasp.tiominutermvi.ui.groups.list

import com.javargasp.tiominutermvi.domain.groups.GroupLimits
import com.javargasp.tiominutermvi.domain.groups.MyGroup
import com.javargasp.tiominutermvi.ui.base.MviState

/** Where the person was going when the weekly goal was missing. */
enum class GoalRequiredFor { CREATE, JOIN }

/**
 * My groups. [isGuest] (null until the session is known) shows the sign-in explanation instead;
 * [goalRequiredFor] shows "Set your weekly goal first".
 */
data class GroupListState(
    val isGuest: Boolean? = null,
    val isLoading: Boolean = false,
    val groups: List<MyGroup> = emptyList(),
    val loadError: Boolean = false,
    val goalRequiredFor: GoalRequiredFor? = null
) : MviState {
    val canAddGroup: Boolean get() = groups.size < GroupLimits.MAX_GROUPS
}
