package com.javargasp.tiominutermvi.ui.components.share

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class SharePreviews

@SharePreviews
@Composable
private fun ShareOnPreview() {
    TioMinuterMVITheme { Surface { ShareInGroupsContent(ShareInGroupsState(entryId = 1, visible = true, share = true), {}) } }
}

@SharePreviews
@Composable
private fun ShareOffPreview() {
    TioMinuterMVITheme { Surface { ShareInGroupsContent(ShareInGroupsState(entryId = 1, visible = true, share = false), {}) } }
}

@SharePreviews
@Composable
private fun ShareIntroPreview() {
    TioMinuterMVITheme { ShareIntroDialog(onOk = {}, onDontShare = {}, onDismiss = {}) }
}
