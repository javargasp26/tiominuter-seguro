package com.javargasp.tiominutermvi.ui.welcome

import com.javargasp.tiominutermvi.ui.auth.AuthMessage
import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed interface WelcomeEffect : MviEffect {
    /** Ask Credential Manager for a Google ID token (needs the Activity), then send [WelcomeIntent.OnGoogleTokenResult]. */
    data object RequestGoogleToken : WelcomeEffect
    data object NavigateToHome : WelcomeEffect
    /** [cause]: the exception behind a failure (short class name), appended in debug builds. */
    data class ShowMessage(val message: AuthMessage, val cause: String? = null) : WelcomeEffect
}
