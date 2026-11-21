package com.javargasp.tiominutermvi.ui.groups.feed

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.groups.FeedPost
import com.javargasp.tiominutermvi.domain.groups.Reaction
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

@Preview(name = "Light", showBackground = true, heightDp = 800)
@Preview(name = "Dark", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class FeedPreviews

private const val WEEK = "2026-10-19"

private fun post(id: String, author: String, name: String, source: TrainingSource, title: String, minutes: Int?, activity: ActivityType? = null) =
    FeedPost(
        id = id,
        authorUid = author,
        authorName = name,
        authorPhotoUrl = null,
        source = source,
        title = title,
        activityType = activity,
        durationSeconds = minutes?.let { it * 60 },
        dateLocal = "2026-10-21",
        weekStartLocal = WEEK,
        createdAtMillis = null,
        reactions = mapOf("me" to Reaction.FIRE, "ana" to Reaction.FIRE, "luis" to Reaction.STRONG),
        thumb = null
    )

private val POSTS = listOf(
    post("p1", "ana", "Ana", TrainingSource.GYM, "Pull Day", 52),
    post("p2", "me", "Alex Rivera", TrainingSource.ACTIVITY, "", 30, ActivityType.RUNNING),
    post("p3", "luis", "Luis", TrainingSource.PROGRAM, "Week 2 · Full body A", null)
)

@FeedPreviews
@Composable
private fun GroupFeedPreview() {
    TioMinuterMVITheme {
        Surface { GroupFeedContent(GroupFeedState(isLoading = false, myUid = "me", isOwner = true, weekStart = WEEK, posts = POSTS), {}) }
    }
}

@FeedPreviews
@Composable
private fun GroupFeedEmptyMondayPreview() {
    TioMinuterMVITheme {
        Surface { GroupFeedContent(GroupFeedState(isLoading = false, myUid = "me", weekStart = WEEK, isFirstDayOfWeek = true), {}) }
    }
}

@FeedPreviews
@Composable
private fun GroupFeedDeletePreview() {
    TioMinuterMVITheme {
        Surface {
            GroupFeedContent(
                GroupFeedState(isLoading = false, myUid = "me", weekStart = WEEK, posts = POSTS, dialog = GroupFeedDialog.DeleteOwn("p2")),
                {}
            )
        }
    }
}
