package com.javargasp.tiominutermvi.ui.groups.join

import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface GroupJoinIntent : MviIntent {
    data class OnCodeChanged(val input: String) : GroupJoinIntent
    data object OnJoinClicked : GroupJoinIntent
    data object OnBackClicked : GroupJoinIntent
}
