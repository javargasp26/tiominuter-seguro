package com.javargasp.tiominutermvi.ui.welcome

import com.javargasp.tiominutermvi.domain.auth.GoogleTokenResult
import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface WelcomeIntent : MviIntent {
    data object OnContinueWithGoogle : WelcomeIntent

    /** Opens the guest warning dialog. */
    data object OnContinueAsGuest : WelcomeIntent
    data object OnGuestConfirmed : WelcomeIntent
    data object OnGuestDialogSignIn : WelcomeIntent
    data object OnGuestDialogDismissed : WelcomeIntent

    /** What the UI-side Credential Manager request returned. */
    data class OnGoogleTokenResult(val result: GoogleTokenResult) : WelcomeIntent
}
