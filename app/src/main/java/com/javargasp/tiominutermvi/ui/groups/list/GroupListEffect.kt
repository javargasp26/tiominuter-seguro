package com.javargasp.tiominutermvi.ui.groups.list

import com.javargasp.tiominutermvi.ui.base.MviEffect
import com.javargasp.tiominutermvi.ui.groups.common.GroupText

sealed interface GroupListEffect : MviEffect {
    data object NavigateBack : GroupListEffect
    data object NavigateToCreate : GroupListEffect
    data object NavigateToJoin : GroupListEffect
    data class NavigateToDetail(val groupId: String) : GroupListEffect
    data object NavigateToAccount : GroupListEffect
    data object NavigateToWeeklyGoal : GroupListEffect
    data class ShowMessage(val text: GroupText) : GroupListEffect
}
