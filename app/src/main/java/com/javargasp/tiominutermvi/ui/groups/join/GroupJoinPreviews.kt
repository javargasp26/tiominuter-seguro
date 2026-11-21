package com.javargasp.tiominutermvi.ui.groups.join

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.InvitePreview
import com.javargasp.tiominutermvi.ui.groups.common.GroupText
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class JoinPreviews

@JoinPreviews
@Composable
private fun GroupJoinPreviewFound() {
    TioMinuterMVITheme {
        GroupJoinContent(GroupJoinState(code = "K7MPX2QA", preview = InvitePreview("K7MPX2QA", "g1", "Morning crew")), onIntent = {})
    }
}

@JoinPreviews
@Composable
private fun GroupJoinPreviewError() {
    TioMinuterMVITheme {
        GroupJoinContent(GroupJoinState(code = "K7MPX2QO", error = GroupText(R.string.groups_join_code_invalid_chars)), onIntent = {})
    }
}
