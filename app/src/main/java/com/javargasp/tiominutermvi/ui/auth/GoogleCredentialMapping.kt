package com.javargasp.tiominutermvi.ui.auth

import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.javargasp.tiominutermvi.domain.auth.AuthFailureLog
import com.javargasp.tiominutermvi.domain.auth.GoogleTokenResult
import com.javargasp.tiominutermvi.domain.auth.describeAuthFailure
import java.io.IOException

/**
 * Maps what Credential Manager threw to a [GoogleTokenResult]. Only a cancellation is silent;
 * every other failure is logged once (without personal data) and shows a message:
 * - [GetCredentialCancellationException] → [GoogleTokenResult.Cancelled], not logged
 * - [NoCredentialException] → [GoogleTokenResult.NoGoogleAccount]
 * - [IOException], or any other [GetCredentialException] while offline → [GoogleTokenResult.NoNetwork]
 * - anything else (other [GetCredentialException]s, a token that can't be parsed…) → [GoogleTokenResult.Failed]
 */
fun googleTokenFailure(error: Exception, isOnline: Boolean, log: AuthFailureLog): GoogleTokenResult {
    if (error is GetCredentialCancellationException) return GoogleTokenResult.Cancelled
    log.warn(describeAuthFailure(error, (error as? GetCredentialException)?.type))
    val cause = error.javaClass.simpleName
    return when {
        error is NoCredentialException -> GoogleTokenResult.NoGoogleAccount(cause)
        error is IOException -> GoogleTokenResult.NoNetwork(cause)
        error is GetCredentialException && !isOnline -> GoogleTokenResult.NoNetwork(cause)
        else -> GoogleTokenResult.Failed(cause)
    }
}

/**
 * The Google ID token of the credential Credential Manager returned, or [GoogleTokenResult.Failed]
 * (logged) when it is another kind of credential. [idTokenOf] parses it and may throw
 * `GoogleIdTokenParsingException`, which the caller maps with [googleTokenFailure].
 */
fun googleTokenFrom(
    credential: Credential,
    log: AuthFailureLog,
    idTokenOf: (Credential) -> String = { GoogleIdTokenCredential.createFrom(it.data).idToken }
): GoogleTokenResult {
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleTokenResult.Token(idTokenOf(credential))
    }
    val cause = credential.javaClass.simpleName
    log.warn("Unexpected credential $cause type=${credential.type}")
    return GoogleTokenResult.Failed(cause)
}
