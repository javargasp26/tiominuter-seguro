package com.javargasp.tiominutermvi.ui.groups.feed

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.FeedPost
import com.javargasp.tiominutermvi.domain.groups.Reaction
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.ui.auth.AccountAvatar
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.text
import com.javargasp.tiominutermvi.ui.groups.common.sourceLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Feed tab of a group's detail. Composed only while the tab is selected, so its listener runs
 * only while the tab is visible and the screen started.
 */
@Composable
fun GroupFeedTab(
    groupId: String,
    ownerUid: String?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: GroupFeedViewModel = koinViewModel(key = "feed-$groupId") { parametersOf(groupId) }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LifecycleStartEffect(viewModel) {
        viewModel.handleIntent(GroupFeedIntent.OnVisible)
        onStopOrDispose { viewModel.handleIntent(GroupFeedIntent.OnHidden) }
    }
    LaunchedEffect(ownerUid) { viewModel.handleIntent(GroupFeedIntent.OnOwnerChanged(ownerUid)) }
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is GroupFeedEffect.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(effect.text.resolve(context)) }
            }
        }
    ) { state, onIntent ->
        GroupFeedContent(state, onIntent, modifier)
    }
}

@Composable
internal fun GroupFeedContent(state: GroupFeedState, onIntent: (GroupFeedIntent) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.isLoading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.posts.isEmpty() -> Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (state.isFirstDayOfWeek) R.string.feed_empty_monday else R.string.feed_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.posts, key = { it.id }) { post ->
                FeedPostCard(
                    post = post,
                    name = state.nameOf(post),
                    myUid = state.myUid,
                    canDelete = post.authorUid == state.myUid || state.isOwner,
                    onIntent = onIntent
                )
            }
            if (state.canLoadMore) {
                item {
                    OutlinedButton(
                        onClick = { onIntent(GroupFeedIntent.OnLoadMore) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.feed_load_more)) }
                }
            }
        }
    }
    state.dialog?.let { FeedDialog(it, state.isWorking, onIntent) }
}

/** Avatar, author, the workout's [name] in my language, "Gym routine · 45 min · 2 h ago", the thumbnail and the reactions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedPostCard(
    post: FeedPost,
    name: WorkoutName,
    myUid: String?,
    canDelete: Boolean,
    onIntent: (GroupFeedIntent) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isMine = post.authorUid == myUid
    val author = if (isMine) "${post.authorName} (${stringResource(R.string.groups_detail_me)})" else post.authorName
    val title = name.text()
    val details = buildList {
        add(stringResource(sourceLabel(post.source)))
        post.durationSeconds?.let { seconds ->
            val minutes = (seconds / 60).coerceAtLeast(1)
            add(pluralStringResource(R.plurals.training_history_duration_minutes, minutes, minutes))
        }
        add(relativeTime(post.createdAtMillis))
    }.joinToString(" · ")
    val deleteLabel = stringResource(R.string.feed_delete)
    val hideLabel = stringResource(R.string.feed_hide)
    val actions = buildList {
        if (canDelete) add(CustomAccessibilityAction(deleteLabel) { onIntent(GroupFeedIntent.OnDeleteClicked(post.id)); true })
        if (!isMine) add(CustomAccessibilityAction(hideLabel) { onIntent(GroupFeedIntent.OnHideClicked(post.id)); true })
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
            .semantics { customActions = actions }
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(photoUrl = post.authorPhotoUrl, size = 40.dp, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(author, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.feed_post_menu, post.authorName))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (canDelete) {
                            DropdownMenuItem(text = { Text(deleteLabel) }, onClick = {
                                menuOpen = false
                                onIntent(GroupFeedIntent.OnDeleteClicked(post.id))
                            })
                        }
                        if (!isMine) {
                            DropdownMenuItem(text = { Text(hideLabel) }, onClick = {
                                menuOpen = false
                                onIntent(GroupFeedIntent.OnHideClicked(post.id))
                            })
                        }
                    }
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            post.thumb?.let { bytes ->
                rememberFeedThumbnail(post.id, bytes)?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.feed_photo_description, post.authorName),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
            }
            ReactionBar(post, myUid, onIntent)
        }
    }
}

/** 💪 🔥 👏 🙌 with their counts; mine selected (also as a state description). */
@Composable
private fun ReactionBar(post: FeedPost, myUid: String?, onIntent: (GroupFeedIntent) -> Unit) {
    val counts = post.reactionCounts
    val mine = myUid?.let { post.reactions[it] }
    val resources = LocalResources.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Reaction.entries.forEach { reaction ->
            val count = counts[reaction] ?: 0
            val selected = reaction == mine
            val description = resources.getQuantityString(
                R.plurals.feed_reaction_description, count, resources.getString(reactionName(reaction)), count
            )
            val selectedLabel = stringResource(if (selected) R.string.feed_reaction_mine else R.string.feed_reaction_not_mine)
            FilterChip(
                selected = selected,
                onClick = { onIntent(GroupFeedIntent.OnReactionTapped(post.id, reaction)) },
                label = { Text(if (count > 0) "${reaction.emoji} $count" else reaction.emoji) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = description
                        stateDescription = selectedLabel
                    }
            )
        }
    }
}

private fun reactionName(reaction: Reaction): Int = when (reaction) {
    Reaction.STRONG -> R.string.feed_reaction_strong
    Reaction.FIRE -> R.string.feed_reaction_fire
    Reaction.CLAP -> R.string.feed_reaction_clap
    Reaction.RAISED -> R.string.feed_reaction_raised
}

@Composable
private fun relativeTime(createdAtMillis: Long?): String {
    if (createdAtMillis == null) return stringResource(R.string.feed_just_now)
    val now = remember(createdAtMillis) { System.currentTimeMillis() }
    if (now - createdAtMillis < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.feed_just_now)
    return DateUtils.getRelativeTimeSpanString(createdAtMillis, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

@Composable
private fun FeedDialog(dialog: GroupFeedDialog, isWorking: Boolean, onIntent: (GroupFeedIntent) -> Unit) {
    val dismiss = { onIntent(GroupFeedIntent.OnDialogDismissed) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(
                when (dialog) {
                    is GroupFeedDialog.DeleteOwn -> stringResource(R.string.feed_dialog_delete_own_title)
                    is GroupFeedDialog.DeleteAsOwner -> stringResource(R.string.feed_dialog_delete_owner_title, dialog.authorName)
                }
            )
        },
        text = {
            Text(
                stringResource(
                    if (dialog is GroupFeedDialog.DeleteOwn) R.string.feed_dialog_delete_own_text else R.string.feed_dialog_delete_owner_text
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onIntent(GroupFeedIntent.OnConfirm) }, enabled = !isWorking) {
                if (isWorking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.feed_delete))
            }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !isWorking) { Text(stringResource(R.string.groups_cancel)) } }
    )
}

/** Decoded thumbnails, small and in memory only (≤ 4 MB of pixels), keyed by post and content. */
private val thumbnailCache = object : LruCache<String, ImageBitmap>(THUMBNAIL_CACHE_BYTES) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** The post's thumbnail, decoded off the main thread (null while decoding or when undecodable). */
@Composable
private fun rememberFeedThumbnail(postId: String, bytes: ByteArray): ImageBitmap? {
    val key = remember(postId, bytes) { "$postId:${bytes.size}:${bytes.contentHashCode()}" }
    val bitmap by produceState(initialValue = thumbnailCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { thumbnailCache.put(key, it) }
            }
        }
    }
    return bitmap
}

private const val THUMBNAIL_CACHE_BYTES = 4 * 1024 * 1024
