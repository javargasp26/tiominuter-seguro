package com.javargasp.tiominutermvi.ui.groups.list

import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface GroupListIntent : MviIntent {
    /** Every time the screen is shown (also back from create, join or a group). */
    data object OnStart : GroupListIntent
    data object OnRetry : GroupListIntent
    data object OnCreateClicked : GroupListIntent
    data object OnJoinClicked : GroupListIntent
    data class OnGroupClicked(val groupId: String) : GroupListIntent
    data object OnSignInClicked : GroupListIntent
    data object OnSetGoalClicked : GroupListIntent
    data object OnGoalDialogDismissed : GroupListIntent
    data object OnBackClicked : GroupListIntent
}
