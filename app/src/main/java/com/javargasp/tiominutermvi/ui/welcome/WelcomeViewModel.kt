package com.javargasp.tiominutermvi.ui.welcome

import com.javargasp.tiominutermvi.domain.auth.AuthPreferences
import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthResult
import com.javargasp.tiominutermvi.domain.auth.GoogleTokenResult
import com.javargasp.tiominutermvi.ui.auth.AuthMessage
import com.javargasp.tiominutermvi.ui.auth.failureCause
import com.javargasp.tiominutermvi.ui.auth.failureMessage
import com.javargasp.tiominutermvi.ui.auth.toMessage
import com.javargasp.tiominutermvi.ui.base.BaseViewModel

/**
 * First launch: Google or guest. The choice is stored ([AuthPreferences.setStartChoiceMade]) only
 * when it succeeds, so Welcome shows again until one is made. Guest goes through a warning dialog.
 */
class WelcomeViewModel(
    private val authRepository: AuthRepository,
    private val preferences: AuthPreferences
) : BaseViewModel<WelcomeIntent, WelcomeState, WelcomeEffect>(WelcomeState()) {

    override suspend fun reduce(intent: WelcomeIntent, currentState: WelcomeState) {
        when (intent) {
            WelcomeIntent.OnContinueWithGoogle -> requestToken()
            WelcomeIntent.OnContinueAsGuest -> setState(state.value.copy(showGuestDialog = true))
            WelcomeIntent.OnGuestDialogDismissed -> setState(state.value.copy(showGuestDialog = false))
            WelcomeIntent.OnGuestDialogSignIn -> {
                setState(state.value.copy(showGuestDialog = false))
                requestToken()
            }
            WelcomeIntent.OnGuestConfirmed -> {
                setState(state.value.copy(showGuestDialog = false))
                finish()
            }
            is WelcomeIntent.OnGoogleTokenResult -> onToken(intent.result)
        }
    }

    private fun requestToken() {
        if (state.value.isSigningIn) return
        setState(state.value.copy(isSigningIn = true))
        sendEffect(WelcomeEffect.RequestGoogleToken)
    }

    private suspend fun onToken(result: GoogleTokenResult) {
        if (result !is GoogleTokenResult.Token) {
            setState(state.value.copy(isSigningIn = false))
            result.failureMessage()?.let { sendEffect(WelcomeEffect.ShowMessage(it, result.failureCause)) }
            return
        }
        val signIn = authRepository.signInWithGoogle(result.idToken)
        setState(state.value.copy(isSigningIn = false))
        when (signIn) {
            AuthResult.Success -> finish()
            is AuthResult.Failure -> sendEffect(WelcomeEffect.ShowMessage(signIn.error.toMessage(deleting = false), signIn.cause))
            AuthResult.RecentLoginRequired -> sendEffect(WelcomeEffect.ShowMessage(AuthMessage.SIGN_IN_FAILED))
        }
    }

    private fun finish() {
        preferences.setStartChoiceMade()
        sendEffect(WelcomeEffect.NavigateToHome)
    }
}
