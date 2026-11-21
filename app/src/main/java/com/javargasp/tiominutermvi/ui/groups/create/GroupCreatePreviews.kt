package com.javargasp.tiominutermvi.ui.groups.create

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.domain.groups.CreatedGroup
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class CreatePreviews

@CreatePreviews
@Composable
private fun GroupCreateFormPreview() {
    TioMinuterMVITheme { GroupCreateContent(GroupCreateState(name = "Morning crew"), onIntent = {}) }
}

@CreatePreviews
@Composable
private fun GroupCreatedPreview() {
    TioMinuterMVITheme {
        GroupCreateContent(GroupCreateState(name = "Morning crew", created = CreatedGroup("g1", "Morning crew", "K7MPX2QA")), onIntent = {})
    }
}
