package com.javargasp.tiominutermvi.ui.groups.list

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.groups.GroupInfo
import com.javargasp.tiominutermvi.domain.groups.MyGroup
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class ListPreviews

@ListPreviews
@Composable
private fun GroupListPreview() {
    TioMinuterMVITheme {
        GroupListContent(
            state = GroupListState(
                isGuest = false,
                groups = listOf(
                    MyGroup(GroupInfo("g1", "Morning crew", "me", "K7MPX2QA", 6), myPosition = 2, isOwner = true),
                    MyGroup(GroupInfo("g2", "Office runners", "ana", "R4TZ9WHN", 14), myPosition = 9, isOwner = false)
                )
            ),
            onIntent = {}
        )
    }
}

@ListPreviews
@Composable
private fun GroupListEmptyPreview() {
    TioMinuterMVITheme {
        GroupListContent(state = GroupListState(isGuest = false), onIntent = {})
    }
}

@ListPreviews
@Composable
private fun GroupListGuestPreview() {
    TioMinuterMVITheme {
        GroupListContent(state = GroupListState(isGuest = true), onIntent = {})
    }
}

@ListPreviews
@Composable
private fun GroupListGoalRequiredPreview() {
    TioMinuterMVITheme {
        GroupListContent(state = GroupListState(isGuest = false, goalRequiredFor = GoalRequiredFor.JOIN), onIntent = {})
    }
}
