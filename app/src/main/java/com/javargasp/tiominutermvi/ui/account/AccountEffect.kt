package com.javargasp.tiominutermvi.ui.account

import com.javargasp.tiominutermvi.ui.auth.AuthMessage
import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface AccountEffect : MviEffect {
    /** Ask Credential Manager for a Google ID token, then send [AccountIntent.OnGoogleTokenResult]. */
    data object RequestGoogleToken : AccountEffect

    /** After a sign-out or deletion, so the next sign-in shows the account picker. */
    data object ClearGoogleCredentials : AccountEffect

    /** [cause]: the exception behind a failure (short class name), appended in debug builds. */
    data class ShowMessage(val message: AuthMessage, val cause: String? = null) : AccountEffect
    data object NavigateBack : AccountEffect
}
