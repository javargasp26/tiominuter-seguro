package com.javargasp.tiominutermvi.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.ui.auth.AccountAvatar
import com.javargasp.tiominutermvi.ui.auth.authMessageText
import com.javargasp.tiominutermvi.ui.auth.rememberGoogleIdTokenRequester
import com.javargasp.tiominutermvi.ui.base.MviComposable
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.javargasp.tiominutermvi.domain.sync.SyncStatus
import androidx.compose.ui.res.pluralStringResource
import android.text.format.DateUtils

/**
 * Account screen (from Home's top bar): guest explanation and "Sign in with Google", or the
 * signed-in profile with "Sign out" and "Delete account".
 */
@Composable
fun AccountScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccountViewModel = koinViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val googleTokens = rememberGoogleIdTokenRequester()

    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                AccountEffect.RequestGoogleToken -> scope.launch {
                    viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(googleTokens.request()))
                }
                AccountEffect.ClearGoogleCredentials -> scope.launch { googleTokens.clearCredentialState() }
                is AccountEffect.ShowMessage -> scope.launch {
                    snackbarHostState.showSnackbar(authMessageText(resources.getString(effect.message.text), effect.cause))
                }
                AccountEffect.NavigateBack -> onNavigateBack()
            }
        }
    ) { state, onIntent ->
        AccountContent(state = state, onIntent = onIntent, snackbarHostState = snackbarHostState)
    }
}

/** Stateless Account layout and dialogs, also rendered by the previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountContent(
    state: AccountState,
    onIntent: (AccountIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(AccountIntent.OnBackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val session = state.session) {
                null -> CircularProgressIndicator()
                AuthSession.Guest -> GuestAccount(isBusy = state.isBusy, onIntent = onIntent)
                is AuthSession.SignedIn ->
                    SignedInAccount(session = session, syncStatus = state.syncStatus, isBusy = state.isBusy, onIntent = onIntent)
            }
            if (state.isBusy) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }

    if (state.showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(AccountIntent.OnSignOutDismissed) },
            title = { Text(stringResource(R.string.sign_out_dialog_title)) },
            text = { Text(stringResource(R.string.sign_out_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { onIntent(AccountIntent.OnSignOutConfirmed) }) {
                    Text(stringResource(R.string.account_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(AccountIntent.OnSignOutDismissed) }) {
                    Text(stringResource(R.string.auth_cancel))
                }
            }
        )
    }

    state.pendingSignOut?.let { pending ->
        AlertDialog(
            onDismissRequest = { onIntent(AccountIntent.OnSignOutDismissed) },
            title = { Text(stringResource(R.string.sign_out_pending_title)) },
            text = { Text(pluralStringResource(R.plurals.sign_out_pending_message, pending, pending)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onIntent(AccountIntent.OnSignOutBackUpFirst) }) {
                        Text(stringResource(R.string.sync_back_up_now))
                    }
                    TextButton(onClick = { onIntent(AccountIntent.OnSignOutConfirmed) }) {
                        Text(stringResource(R.string.sign_out_anyway))
                    }
                    TextButton(onClick = { onIntent(AccountIntent.OnSignOutDismissed) }) {
                        Text(stringResource(R.string.auth_cancel))
                    }
                }
            }
        )
    }

    when (val dialog = state.deleteDialog) {
        null -> Unit
        is DeleteDialog.Explain -> DeleteExplainDialog(dialog, onIntent)
        is DeleteDialog.Confirm -> DeleteConfirmDialog(dialog, onIntent)
    }
}

@Composable
private fun GuestAccount(isBusy: Boolean, onIntent: (AccountIntent) -> Unit) {
    AccountAvatar(photoUrl = null, size = 72.dp, contentDescription = null)
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.account_guest_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.account_guest_message),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { onIntent(AccountIntent.OnSignInClicked) },
        enabled = !isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Text(stringResource(R.string.auth_sign_in_with_google))
    }
}

@Composable
private fun SignedInAccount(
    session: AuthSession.SignedIn,
    syncStatus: SyncStatus,
    isBusy: Boolean,
    onIntent: (AccountIntent) -> Unit
) {
    AccountAvatar(
        photoUrl = session.photoUrl,
        size = 96.dp,
        contentDescription = stringResource(R.string.account_photo_description)
    )
    Spacer(Modifier.height(16.dp))
    session.displayName?.let {
        Text(text = it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    session.email?.let {
        Spacer(Modifier.height(4.dp))
        Text(text = it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(24.dp))
    Text(
        text = syncStatusText(syncStatus),
        style = MaterialTheme.typography.bodyMedium,
        color = if (syncStatus.failing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onIntent(AccountIntent.OnBackUpNowClicked) },
        enabled = !isBusy && !syncStatus.running,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Text(stringResource(R.string.sync_back_up_now))
    }
    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        onClick = { onIntent(AccountIntent.OnSignOutClicked) },
        enabled = !isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Text(stringResource(R.string.account_sign_out))
    }
    Spacer(Modifier.height(12.dp))
    TextButton(
        onClick = { onIntent(AccountIntent.OnDeleteClicked) },
        enabled = !isBusy,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Text(stringResource(R.string.account_delete))
    }
}

/** Step 1: what happens, and the local-wipe checkbox (unchecked by default). */
@Composable
private fun DeleteExplainDialog(dialog: DeleteDialog.Explain, onIntent: (AccountIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(AccountIntent.OnDeleteDismissed) },
        title = { Text(stringResource(R.string.delete_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_dialog_message))
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = dialog.wipeLocalData,
                            role = Role.Checkbox,
                            onValueChange = { onIntent(AccountIntent.OnWipeLocalDataChanged(it)) }
                        )
                ) {
                    Checkbox(checked = dialog.wipeLocalData, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.delete_wipe_checkbox))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(AccountIntent.OnDeleteContinue) }) {
                Text(stringResource(R.string.delete_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(AccountIntent.OnDeleteDismissed) }) {
                Text(stringResource(R.string.auth_cancel))
            }
        }
    )
}

/** Step 2: the final confirmation, which repeats what happens to the local data. */
@Composable
private fun DeleteConfirmDialog(dialog: DeleteDialog.Confirm, onIntent: (AccountIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(AccountIntent.OnDeleteDismissed) },
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = {
            Text(
                stringResource(
                    if (dialog.wipeLocalData) R.string.delete_confirm_message_wipe else R.string.delete_confirm_message_keep
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(AccountIntent.OnDeleteConfirmed) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.account_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(AccountIntent.OnDeleteDismissed) }) {
                Text(stringResource(R.string.auth_cancel))
            }
        }
    )
}

/** "Backed up · 5 minutes ago", "3 changes pending", "Couldn't back up, will retry"… */
@Composable
private fun syncStatusText(status: SyncStatus): String = when {
    status.running -> stringResource(R.string.sync_status_running)
    status.failing -> stringResource(R.string.sync_status_failing)
    status.pendingChanges > 0 ->
        pluralStringResource(R.plurals.sync_status_pending, status.pendingChanges, status.pendingChanges)
    status.lastSuccessAt != null -> stringResource(
        R.string.sync_status_backed_up,
        DateUtils.getRelativeTimeSpanString(status.lastSuccessAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    )
    else -> stringResource(R.string.sync_status_never)
}
