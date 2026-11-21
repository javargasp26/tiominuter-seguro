package com.javargasp.tiominutermvi.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.ui.auth.authMessageText
import com.javargasp.tiominutermvi.ui.auth.rememberGoogleIdTokenRequester
import com.javargasp.tiominutermvi.ui.base.MviComposable
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * First-launch screen: "Continue with Google" or "Continue as guest" (after a warning dialog).
 * Shown until a choice succeeds; then the app starts on Home.
 */
@Composable
fun WelcomeScreen(
    onNavigateToHome: () -> Unit,
    viewModel: WelcomeViewModel = koinViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val googleTokens = rememberGoogleIdTokenRequester()

    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                WelcomeEffect.RequestGoogleToken -> scope.launch {
                    viewModel.handleIntent(WelcomeIntent.OnGoogleTokenResult(googleTokens.request()))
                }
                WelcomeEffect.NavigateToHome -> onNavigateToHome()
                is WelcomeEffect.ShowMessage -> scope.launch {
                    snackbarHostState.showSnackbar(authMessageText(resources.getString(effect.message.text), effect.cause))
                }
            }
        }
    ) { state, onIntent ->
        WelcomeContent(state = state, onIntent = onIntent, snackbarHostState = snackbarHostState)
    }
}

/** Stateless Welcome layout, also rendered by the previews. */
@Composable
internal fun WelcomeContent(
    state: WelcomeState,
    onIntent: (WelcomeIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.uncle),
                    contentDescription = null,
                    modifier = Modifier.size(112.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.welcome_message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { onIntent(WelcomeIntent.OnContinueWithGoogle) },
                    enabled = !state.isSigningIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(stringResource(R.string.auth_continue_with_google))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onIntent(WelcomeIntent.OnContinueAsGuest) },
                    enabled = !state.isSigningIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(stringResource(R.string.auth_continue_as_guest))
                }
                if (state.isSigningIn) {
                    Spacer(Modifier.height(24.dp))
                    SigningInIndicator()
                }
            }
        }
    }

    if (state.showGuestDialog) {
        GuestWarningDialog(
            onContinueAsGuest = { onIntent(WelcomeIntent.OnGuestConfirmed) },
            onSignIn = { onIntent(WelcomeIntent.OnGuestDialogSignIn) },
            onDismiss = { onIntent(WelcomeIntent.OnGuestDialogDismissed) }
        )
    }
}

@Composable
private fun SigningInIndicator() {
    val label = stringResource(R.string.auth_signing_in)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Data lives only on this device; uninstalling or changing phone loses it; groups need an account. */
@Composable
internal fun GuestWarningDialog(
    onContinueAsGuest: () -> Unit,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.guest_dialog_title)) },
        text = { Text(stringResource(R.string.guest_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onContinueAsGuest) { Text(stringResource(R.string.auth_continue_as_guest)) }
        },
        dismissButton = {
            TextButton(onClick = onSignIn) { Text(stringResource(R.string.auth_sign_in_with_google)) }
        }
    )
}
