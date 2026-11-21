package com.javargasp.tiominutermvi.ui.welcome

import com.javargasp.tiominutermvi.data.auth.FakeAuthRepository
import com.javargasp.tiominutermvi.data.auth.InMemoryAuthPreferences
import com.javargasp.tiominutermvi.domain.auth.AuthError
import com.javargasp.tiominutermvi.domain.auth.AuthResult
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.auth.GoogleTokenResult
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import com.javargasp.tiominutermvi.ui.auth.AuthMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val preferences = InMemoryAuthPreferences()
    private val viewModel = WelcomeViewModel(auth, preferences)

    @Test
    fun `continue as guest asks first, then remembers the choice`() = runTest {
        val effects = collectEffects()

        viewModel.handleIntent(WelcomeIntent.OnContinueAsGuest)
        assertTrue(viewModel.state.value.showGuestDialog)
        assertFalse(preferences.isStartChoiceMade())
        assertTrue(effects.isEmpty())

        viewModel.handleIntent(WelcomeIntent.OnGuestConfirmed)

        assertFalse(viewModel.state.value.showGuestDialog)
        assertTrue(preferences.isStartChoiceMade())
        assertEquals(listOf(WelcomeEffect.NavigateToHome), effects)
        assertEquals(AuthSession.Guest, auth.session.first())
    }

    @Test
    fun `closing the guest dialog stays on Welcome without a choice`() = runTest {
        val effects = collectEffects()

        viewModel.handleIntent(WelcomeIntent.OnContinueAsGuest)
        viewModel.handleIntent(WelcomeIntent.OnGuestDialogDismissed)

        assertFalse(viewModel.state.value.showGuestDialog)
        assertFalse(preferences.isStartChoiceMade())
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `sign in with Google succeeds, remembers the choice and goes Home`() = runTest {
        val effects = collectEffects()

        viewModel.handleIntent(WelcomeIntent.OnContinueWithGoogle)
        assertTrue(viewModel.state.value.isSigningIn)
        viewModel.handleIntent(WelcomeIntent.OnGoogleTokenResult(GoogleTokenResult.Token("id-token")))

        assertEquals(listOf("id-token"), auth.signInTokens)
        assertTrue(preferences.isStartChoiceMade())
        assertFalse(viewModel.state.value.isSigningIn)
        assertEquals(listOf(WelcomeEffect.RequestGoogleToken, WelcomeEffect.NavigateToHome), effects)
    }

    @Test
    fun `the guest dialog can switch to Google sign-in`() = runTest {
        val effects = collectEffects()

        viewModel.handleIntent(WelcomeIntent.OnContinueAsGuest)
        viewModel.handleIntent(WelcomeIntent.OnGuestDialogSignIn)

        assertFalse(viewModel.state.value.showGuestDialog)
        assertEquals(listOf(WelcomeEffect.RequestGoogleToken), effects)
    }

    @Test
    fun `cancelling the account picker shows nothing and stays`() = runTest {
        val effects = collectEffects()

        viewModel.handleIntent(WelcomeIntent.OnContinueWithGoogle)
        viewModel.handleIntent(WelcomeIntent.OnGoogleTokenResult(GoogleTokenResult.Cancelled))

        assertEquals(listOf(WelcomeEffect.RequestGoogleToken), effects)
        assertFalse(viewModel.state.value.isSigningIn)
        assertFalse(preferences.isStartChoiceMade())
        assertTrue(auth.signInTokens.isEmpty())
    }

    @Test
    fun `no network, no account and a failed token show their message with the cause and stay`() = runTest {
        val effects = collectEffects()

        listOf(
            GoogleTokenResult.NoNetwork(),
            GoogleTokenResult.NoNetwork("IOException"),
            GoogleTokenResult.NoGoogleAccount("NoCredentialException"),
            GoogleTokenResult.Failed("GetCredentialCustomException"),
            GoogleTokenResult.Failed("GoogleIdTokenParsingException")
        ).forEach {
            viewModel.handleIntent(WelcomeIntent.OnContinueWithGoogle)
            viewModel.handleIntent(WelcomeIntent.OnGoogleTokenResult(it))
        }

        assertEquals(
            listOf(
                WelcomeEffect.ShowMessage(AuthMessage.NO_NETWORK),
                WelcomeEffect.ShowMessage(AuthMessage.NO_NETWORK, "IOException"),
                WelcomeEffect.ShowMessage(AuthMessage.NO_GOOGLE_ACCOUNT, "NoCredentialException"),
                WelcomeEffect.ShowMessage(AuthMessage.SIGN_IN_FAILED, "GetCredentialCustomException"),
                WelcomeEffect.ShowMessage(AuthMessage.SIGN_IN_FAILED, "GoogleIdTokenParsingException")
            ),
            effects.filterIsInstance<WelcomeEffect.ShowMessage>()
        )
        assertTrue(auth.signInTokens.isEmpty())
        assertFalse(preferences.isStartChoiceMade())
        assertFalse(viewModel.state.value.isSigningIn)
    }

    @Test
    fun `Firebase refusing the Google credential shows the generic sign-in message`() = runTest {
        auth.signInResult = AuthResult.Failure(AuthError.SIGN_IN_FAILED, "FirebaseAuthInvalidCredentialsException")
        val effects = collectEffects()

        viewModel.handleIntent(WelcomeIntent.OnContinueWithGoogle)
        viewModel.handleIntent(WelcomeIntent.OnGoogleTokenResult(GoogleTokenResult.Token("id-token")))

        assertEquals(
            WelcomeEffect.ShowMessage(AuthMessage.SIGN_IN_FAILED, "FirebaseAuthInvalidCredentialsException"),
            effects.last()
        )
        assertFalse(preferences.isStartChoiceMade())
        assertFalse(viewModel.state.value.isSigningIn)
    }

    @Test
    fun `a Firebase error keeps the person on Welcome with a message`() = runTest {
        auth.signInResult = AuthResult.Failure(AuthError.NETWORK)
        val effects = collectEffects()

        viewModel.handleIntent(WelcomeIntent.OnContinueWithGoogle)
        viewModel.handleIntent(WelcomeIntent.OnGoogleTokenResult(GoogleTokenResult.Token("id-token")))

        assertEquals(WelcomeEffect.ShowMessage(AuthMessage.NO_NETWORK), effects.last())
        assertFalse(preferences.isStartChoiceMade())
        assertEquals(AuthSession.Guest, auth.session.first())
    }

    @Test
    fun `a second tap while signing in doesn't open another picker`() = runTest {
        val effects = collectEffects()

        viewModel.handleIntent(WelcomeIntent.OnContinueWithGoogle)
        viewModel.handleIntent(WelcomeIntent.OnContinueWithGoogle)

        assertEquals(listOf(WelcomeEffect.RequestGoogleToken), effects)
    }

    private fun TestScope.collectEffects(): List<WelcomeEffect> {
        val effects = mutableListOf<WelcomeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }
        return effects
    }
}
