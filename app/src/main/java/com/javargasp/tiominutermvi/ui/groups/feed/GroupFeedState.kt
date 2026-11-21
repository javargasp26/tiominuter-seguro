package com.javargasp.tiominutermvi.ui.groups.feed

import com.javargasp.tiominutermvi.domain.groups.FeedPost
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalog
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import com.javargasp.tiominutermvi.ui.base.MviState

/** The confirmations of the Feed tab (every delete has one). */
sealed interface GroupFeedDialog {
    data class DeleteOwn(val postId: String) : GroupFeedDialog
    data class DeleteAsOwner(val postId: String, val authorName: String) : GroupFeedDialog
}

/**
 * This week's feed of one group, live while the tab is visible: [posts] newest first, without the
 * posts I hid, with my reactions as I tapped them (optimistic until the write answers).
 */
data class GroupFeedState(
    val isLoading: Boolean = true,
    val myUid: String? = null,
    val isOwner: Boolean = false,
    val weekStart: String = "",
    /** Today is Monday: the empty feed says a new week started. */
    val isFirstDayOfWeek: Boolean = false,
    val posts: List<FeedPost> = emptyList(),
    val canLoadMore: Boolean = false,
    val dialog: GroupFeedDialog? = null,
    val isWorking: Boolean = false,
    /** This device's gym and daily names, for [nameOf]. */
    val names: WorkoutNameCatalog = WorkoutNameCatalog.EMPTY
) : MviState {

    /** What [post] is called for this reader: its titleKey when known here, else its title. */
    fun nameOf(post: FeedPost): WorkoutName =
        WorkoutNameResolver.nameOfPost(post.titleKey, post.source, post.activityType, post.title, names)
}
