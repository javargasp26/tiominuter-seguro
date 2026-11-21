package com.javargasp.tiominutermvi.ui.account

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.javargasp.tiominutermvi.data.auth.FakeAuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.ui.theme.TioMinuterMVITheme

private val signedIn = AccountState(session = FakeAuthRepository.SAMPLE_USER)
private val guest = AccountState(session = AuthSession.Guest)

@Preview(name = "Account - signed in, light", showBackground = true)
@Composable
private fun AccountSignedInPreview() {
    TioMinuterMVITheme(darkTheme = false) { AccountContent(state = signedIn, onIntent = {}) }
}

@Preview(name = "Account - signed in, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountSignedInDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) { AccountContent(state = signedIn, onIntent = {}) }
}

@Preview(name = "Account - guest, light", showBackground = true)
@Composable
private fun AccountGuestPreview() {
    TioMinuterMVITheme(darkTheme = false) { AccountContent(state = guest, onIntent = {}) }
}

@Preview(name = "Account - guest, dark Spanish", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "es")
@Composable
private fun AccountGuestDarkPreview() {
    TioMinuterMVITheme(darkTheme = true) { AccountContent(state = guest.copy(isBusy = true), onIntent = {}) }
}

@Preview(name = "Account - delete step 1", showBackground = true)
@Composable
private fun AccountDeleteExplainPreview() {
    TioMinuterMVITheme(darkTheme = false) {
        AccountContent(state = signedIn.copy(deleteDialog = DeleteDialog.Explain(wipeLocalData = true)), onIntent = {})
    }
}

@Preview(name = "Account - delete step 2, dark Spanish", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "es")
@Composable
private fun AccountDeleteConfirmPreview() {
    TioMinuterMVITheme(darkTheme = true) {
        AccountContent(state = signedIn.copy(deleteDialog = DeleteDialog.Confirm(wipeLocalData = true)), onIntent = {})
    }
}

@Preview(name = "Account - sign out", showBackground = true)
@Composable
private fun AccountSignOutPreview() {
    TioMinuterMVITheme(darkTheme = false) { AccountContent(state = signedIn.copy(showSignOutDialog = true), onIntent = {}) }
}
