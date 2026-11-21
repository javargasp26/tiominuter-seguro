package com.javargasp.tiominutermvi.ui.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.data.auth.LogcatAuthFailureLog
import com.javargasp.tiominutermvi.domain.auth.GoogleTokenResult
import com.javargasp.tiominutermvi.domain.auth.describeAuthFailure
import kotlinx.coroutines.CancellationException

/**
 * Gets a Google ID token with Credential Manager ("Sign in with Google" button flow). Needs an
 * Activity context to show the account picker, so it lives on the UI side and is created per
 * screen ([rememberGoogleIdTokenRequester]); repositories only receive the token.
 * The server client id is the generated `default_web_client_id` (from google-services.json).
 */
class GoogleIdTokenRequester(private val activityContext: Context) {

    private val credentialManager = CredentialManager.create(activityContext)
    private val log = LogcatAuthFailureLog(TAG)

    suspend fun request(): GoogleTokenResult {
        if (!isOnline()) {
            log.warn("No network: the account picker was not opened")
            return GoogleTokenResult.NoNetwork()
        }
        val option = GetSignInWithGoogleOption
            .Builder(activityContext.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            googleTokenFrom(credentialManager.getCredential(activityContext, request).credential, log)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            googleTokenFailure(e, isOnline(), log)
        }
    }

    /** After a sign-out or deletion, so the next sign-in shows the account picker again. Best effort. */
    suspend fun clearCredentialState() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("clearCredentialState failed: ${describeAuthFailure(e)}")
        }
    }

    private fun isOnline(): Boolean {
        val connectivity = activityContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TAG = "GoogleIdTokenRequester"
    }
}

/** A [GoogleIdTokenRequester] for the current Activity. */
@Composable
fun rememberGoogleIdTokenRequester(): GoogleIdTokenRequester {
    val context = LocalContext.current
    return remember(context) { GoogleIdTokenRequester(context) }
}
