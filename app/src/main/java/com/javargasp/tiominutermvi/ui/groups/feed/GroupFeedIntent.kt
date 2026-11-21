package com.javargasp.tiominutermvi.ui.groups.feed

import com.javargasp.tiominutermvi.domain.groups.Reaction
import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface GroupFeedIntent : MviIntent {
    /** The Feed tab is visible (and the screen started): the listener starts. */
    data object OnVisible : GroupFeedIntent
    data object OnHidden : GroupFeedIntent

    /** The group's owner, from the detail screen (the owner may delete any post). */
    data class OnOwnerChanged(val ownerUid: String?) : GroupFeedIntent
    data object OnLoadMore : GroupFeedIntent
    data class OnReactionTapped(val postId: String, val reaction: Reaction) : GroupFeedIntent
    data class OnDeleteClicked(val postId: String) : GroupFeedIntent
    data class OnHideClicked(val postId: String) : GroupFeedIntent
    data object OnConfirm : GroupFeedIntent
    data object OnDialogDismissed : GroupFeedIntent
}
