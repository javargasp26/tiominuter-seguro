package com.javargasp.tiominutermvi.domain.auth

/**
 * Result of asking Credential Manager for a Google ID token (done on the UI side, which has the
 * Activity). ViewModels receive it as an intent and pass the token to [AuthRepository].
 * Failures carry [cause], the short class name of the exception (shown in debug builds only).
 */
sealed interface GoogleTokenResult {
    data class Token(val idToken: String) : GoogleTokenResult

    /** The person closed the account picker: not an error, nothing is shown. The only silent case. */
    data object Cancelled : GoogleTokenResult

    data class NoGoogleAccount(val cause: String? = null) : GoogleTokenResult

    data class NoNetwork(val cause: String? = null) : GoogleTokenResult

    data class Failed(val cause: String? = null) : GoogleTokenResult
}
