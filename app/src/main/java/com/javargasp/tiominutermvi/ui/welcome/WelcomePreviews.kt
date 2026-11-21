package com.javargasp.tiominutermvi.ui.welcome

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

@Preview(name = "Welcome - light", showBackground = true)
@Composable
private fun WelcomePreview() {
    TioMinuterMVITheme(darkTheme = false) {
        WelcomeContent(state = WelcomeState(), onIntent = {})
    }
}

@Preview(name = "Welcome - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WelcomeDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        WelcomeContent(state = WelcomeState(), onIntent = {})
    }
}

@Preview(name = "Welcome - signing in, Spanish", showBackground = true, locale = "es")
@Composable
private fun WelcomeSigningInPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        WelcomeContent(state = WelcomeState(isSigningIn = true), onIntent = {})
    }
}

@Preview(name = "Welcome - guest warning", showBackground = true)
@Composable
private fun WelcomeGuestDialogPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        WelcomeContent(state = WelcomeState(showGuestDialog = true), onIntent = {})
    }
}

@Preview(name = "Welcome - guest warning, dark Spanish", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "es")
@Composable
private fun WelcomeGuestDialogDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        WelcomeContent(state = WelcomeState(showGuestDialog = true), onIntent = {})
    }
}
