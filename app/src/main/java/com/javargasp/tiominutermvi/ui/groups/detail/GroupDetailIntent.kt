package com.javargasp.tiominutermvi.ui.groups.detail

import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface GroupDetailIntent : MviIntent {
    /** The screen became visible: start the real-time listener. */
    data object OnVisible : GroupDetailIntent

    /** The screen is no longer visible: stop it. */
    data object OnHidden : GroupDetailIntent
    data object OnCopyCode : GroupDetailIntent
    data object OnShareCode : GroupDetailIntent
    data object OnLeaveClicked : GroupDetailIntent
    data object OnRenameClicked : GroupDetailIntent
    data class OnRenameTextChanged(val text: String) : GroupDetailIntent
    data object OnRegenerateClicked : GroupDetailIntent
    data class OnRemoveClicked(val memberUid: String) : GroupDetailIntent
    data object OnTransferClicked : GroupDetailIntent
    data class OnMakeOwnerClicked(val memberUid: String) : GroupDetailIntent
    data class OnNewOwnerPicked(val memberUid: String) : GroupDetailIntent
    data object OnDeleteClicked : GroupDetailIntent

    /** The confirm button of the open dialog. */
    data object OnConfirm : GroupDetailIntent
    data object OnDialogDismissed : GroupDetailIntent
    data object OnBackClicked : GroupDetailIntent

    /** Ranking or Feed ([GroupDetailState.TAB_RANKING], [GroupDetailState.TAB_FEED]). */
    data class OnTabSelected(val tab: Int) : GroupDetailIntent
}
