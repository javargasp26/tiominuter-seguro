package com.javargasp.tiominutermvi.ui.groups.feed

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.FeedCleaner
import com.javargasp.tiominutermvi.domain.groups.FeedLimits
import com.javargasp.tiominutermvi.domain.groups.FeedPost
import com.javargasp.tiominutermvi.domain.groups.FeedPreferences
import com.javargasp.tiominutermvi.domain.groups.FeedRepository
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.GroupOperations
import com.javargasp.tiominutermvi.domain.groups.Reaction
import com.javargasp.tiominutermvi.domain.groups.ReactionRules
import com.javargasp.tiominutermvi.domain.groups.hiddenKey
import com.javargasp.tiominutermvi.domain.sync.NetworkMonitor
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalogSource
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.groups.common.GroupText
import com.javargasp.tiominutermvi.ui.groups.common.toText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The Feed tab of a group: this week's posts (the reader's current week, as the ranking), live only
 * while the tab is visible ([GroupFeedIntent.OnVisible] / [GroupFeedIntent.OnHidden]), 20 more per
 * "Load more". The first time it opens, up to 20 expired posts of the group are deleted (the Spark
 * cleanup). Reactions are optimistic and rolled back on failure; "Hide for me" is local. Posts are
 * named through WorkoutNameResolver with this device's catalogs ([names]).
 */
class GroupFeedViewModel(
    private val groupId: String,
    private val operations: GroupOperations,
    private val feed: FeedRepository,
    private val cleaner: FeedCleaner,
    private val preferences: FeedPreferences,
    private val network: NetworkMonitor,
    private val dates: LocalDateProvider,
    private val names: WorkoutNameCatalogSource = WorkoutNameCatalogSource.Empty,
    private val clock: WallClock
) : BaseViewModel<GroupFeedIntent, GroupFeedState, GroupFeedEffect>(GroupFeedState()) {

    private var listener: Job? = null
    private var pages = 1
    private var cleanedUp = false
    private var serverPosts: List<FeedPost> = emptyList()
    private var hasMore = false
    private var ownerUid: String? = null

    /** My reactions tapped but not yet confirmed by the write (null: removed). */
    private val pendingReactions = mutableMapOf<String, Reaction?>()

    init {
        // This device's catalogs (Room), to name each post in the reader's language
        names.observeCatalog()
            .onEach { setState(state.value.copy(names = it)) }
            .launchIn(viewModelScope)
    }

    override suspend fun reduce(intent: GroupFeedIntent, currentState: GroupFeedState) {
        when (intent) {
            GroupFeedIntent.OnVisible -> {
                listen()
                cleanUpOnce()
            }
            GroupFeedIntent.OnHidden -> stopListening()
            is GroupFeedIntent.OnOwnerChanged -> {
                ownerUid = intent.ownerUid
                setState(currentState.copy(isOwner = intent.ownerUid != null && intent.ownerUid == currentState.myUid))
            }
            GroupFeedIntent.OnLoadMore -> if (hasMore) {
                pages++
                stopListening()
                listen()
            }
            is GroupFeedIntent.OnReactionTapped -> react(intent.postId, intent.reaction)
            is GroupFeedIntent.OnDeleteClicked -> {
                val post = currentState.posts.firstOrNull { it.id == intent.postId } ?: return
                val dialog = when {
                    post.authorUid == currentState.myUid -> GroupFeedDialog.DeleteOwn(post.id)
                    currentState.isOwner -> GroupFeedDialog.DeleteAsOwner(post.id, post.authorName)
                    else -> return
                }
                setState(currentState.copy(dialog = dialog))
            }
            is GroupFeedIntent.OnHideClicked -> {
                val uid = currentState.myUid ?: return
                preferences.hidePost(uid, hiddenKey(groupId, intent.postId), clock.nowMillis())
                render()
                sendEffect(GroupFeedEffect.ShowMessage(GroupText(R.string.feed_message_hidden)))
            }
            GroupFeedIntent.OnConfirm -> confirmDelete(currentState)
            GroupFeedIntent.OnDialogDismissed -> if (!currentState.isWorking) setState(currentState.copy(dialog = null))
        }
    }

    private fun listen() {
        if (listener?.isActive == true) return
        listener = viewModelScope.launch {
            val uid = try {
                operations.currentUser().uid
            } catch (e: GroupException) {
                setState(state.value.copy(isLoading = false))
                return@launch
            }
            val today = dates.today()
            val weekStart = dates.mondayOf(today)
            setState(
                state.value.copy(
                    myUid = uid,
                    isOwner = ownerUid == uid,
                    weekStart = weekStart,
                    isFirstDayOfWeek = today == weekStart
                )
            )
            try {
                feed.observeWeek(groupId, weekStart, FeedLimits.PAGE_SIZE * pages).collect { page ->
                    serverPosts = page.posts
                    hasMore = page.hasMore
                    render()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroupException) {
                // Losing access closes the whole detail screen (its own listener); here only stop
                setState(state.value.copy(isLoading = false))
                if (e.error != GroupError.NOT_FOUND && e.error != GroupError.PERMISSION_DENIED) {
                    sendEffect(GroupFeedEffect.ShowMessage(e.error.toText()))
                }
            }
        }
    }

    private fun stopListening() {
        listener?.cancel()
        listener = null
    }

    private fun cleanUpOnce() {
        if (cleanedUp) return
        cleanedUp = true
        viewModelScope.launch { cleaner.cleanUpExpired(groupId) }
    }

    /** The posts shown: this week's (defensive: the query already filters), not hidden, with my pending reactions. */
    private fun render() {
        val current = state.value
        val uid = current.myUid
        val hidden = uid?.let(preferences::hiddenPosts).orEmpty()
        val posts = serverPosts
            .filter { it.weekStartLocal == current.weekStart && hiddenKey(groupId, it.id) !in hidden }
            .map { post ->
                if (uid == null || post.id !in pendingReactions) post
                else post.copy(reactions = ReactionRules.withMine(post.reactions, uid, pendingReactions[post.id]))
            }
        setState(current.copy(isLoading = false, posts = posts, canLoadMore = hasMore))
    }

    private fun react(postId: String, tapped: Reaction) {
        val uid = state.value.myUid ?: return
        val post = state.value.posts.firstOrNull { it.id == postId } ?: return
        if (!network.isOnline()) {
            sendEffect(GroupFeedEffect.ShowMessage(GroupError.NETWORK.toText()))
            return
        }
        val next = ReactionRules.toggle(post.reactions[uid], tapped)
        pendingReactions[postId] = next
        render()
        viewModelScope.launch {
            try {
                feed.setReaction(groupId, postId, uid, next)
                // The listener shows the written value (latency-compensated) from now on
                if (pendingReactions[postId] == next) pendingReactions.remove(postId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroupException) {
                // Rollback: the post shows what the server has
                if (pendingReactions[postId] == next) pendingReactions.remove(postId)
                sendEffect(GroupFeedEffect.ShowMessage(GroupText(R.string.feed_message_reaction_failed)))
            }
            render()
        }
    }

    private suspend fun confirmDelete(currentState: GroupFeedState) {
        val postId = when (val dialog = currentState.dialog) {
            is GroupFeedDialog.DeleteOwn -> dialog.postId
            is GroupFeedDialog.DeleteAsOwner -> dialog.postId
            null -> return
        }
        if (currentState.isWorking) return
        if (!network.isOnline()) {
            setState(currentState.copy(dialog = null))
            sendEffect(GroupFeedEffect.ShowMessage(GroupError.NETWORK.toText()))
            return
        }
        setState(currentState.copy(isWorking = true))
        try {
            feed.deletePost(groupId, postId)
            setState(state.value.copy(isWorking = false, dialog = null))
            sendEffect(GroupFeedEffect.ShowMessage(GroupText(R.string.feed_message_deleted)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: GroupException) {
            setState(state.value.copy(isWorking = false, dialog = null))
            sendEffect(GroupFeedEffect.ShowMessage(e.error.toText()))
        }
    }
}
