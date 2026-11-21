package com.javargasp.tiominutermvi.ui.groups.join

import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface GroupJoinEffect : MviEffect {
    data class NavigateToGroup(val groupId: String) : GroupJoinEffect
    data object NavigateBack : GroupJoinEffect
}
