package com.javargasp.tiominutermvi.ui.groups.detail

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.groups.GroupInfo
import com.javargasp.tiominutermvi.domain.groups.GroupMember
import com.javargasp.tiominutermvi.domain.groups.GroupRanking
import com.javargasp.tiominutermvi.domain.groups.MemberProfile
import com.javargasp.tiominutermvi.domain.groups.MemberSummary
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

@Preview(name = "Light", showBackground = true, heightDp = 900)
@Preview(name = "Dark", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class DetailPreviews

private const val WEEK = "2026-10-19"
private val WEEK_DATES = (19..25).map { "2026-10-$it" }

private fun member(uid: String, name: String, workouts: Int, goal: Int, streak: Int, week: String = WEEK) = GroupMember(
    uid = uid,
    profile = MemberProfile(name, null),
    joinedAtMillis = 0L,
    summary = MemberSummary(
        weekStartLocal = week,
        workoutsThisWeek = workouts,
        weeklyGoal = goal,
        streakWeeks = streak,
        daysThisWeek = mapOf(1 to listOf(TrainingSource.GYM), 3 to listOf(TrainingSource.DAILY, TrainingSource.ACTIVITY)),
        lastTrainedLocal = "2026-10-21"
    )
)

private fun previewState(dialog: GroupDetailDialog? = null): GroupDetailState {
    val members = listOf(
        member("me", "Alex Rivera", 2, 4, 3),
        member("ana", "Ana", 4, 4, 5),
        member("luis", "Luis", 1, 3, 0),
        member("sam", "Sam", 3, 3, 1, week = "2026-10-12")
    )
    return GroupDetailState(
        isLoading = false,
        group = GroupInfo("g1", "Morning crew", "me", "K7MPX2QA", members.size),
        ranking = GroupRanking.rank(members, WEEK, "me"),
        weekDates = WEEK_DATES,
        myUid = "me",
        dialog = dialog
    )
}

@DetailPreviews
@Composable
private fun GroupDetailPreview() {
    TioMinuterMVITheme { GroupDetailContent(previewState(), onIntent = {}) }
}

@DetailPreviews
@Composable
private fun GroupDetailPickOwnerPreview() {
    TioMinuterMVITheme { GroupDetailContent(previewState(GroupDetailDialog.PickNewOwner(beforeLeaving = true)), onIntent = {}) }
}
