package com.javargasp.tiominutermvi.ui.account

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.auth.AuthError
import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthResult
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.auth.GoogleTokenResult
import com.javargasp.tiominutermvi.ui.auth.AuthMessage
import com.javargasp.tiominutermvi.ui.auth.failureCause
import com.javargasp.tiominutermvi.ui.auth.failureMessage
import com.javargasp.tiominutermvi.ui.auth.toMessage
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.javargasp.tiominutermvi.domain.sync.SyncRepository
import com.javargasp.tiominutermvi.domain.sync.SyncOutcome

/**
 * Account screen. Guest: sign in with Google (local data stays). Signed in: sign out (local data
 * stays, back to guest) or delete the account in two steps, optionally wiping the local data.
 * Deleting always asks for a fresh Google credential first (re-authentication), before anything
 * is erased.
 * Signed in, it shows the backup state; signing out with changes not backed up yet asks first
 * (back up now, sign out anyway, or cancel).
 */
class AccountViewModel(
    private val authRepository: AuthRepository,
    private val sync: SyncRepository
) : BaseViewModel<AccountIntent, AccountState, AccountEffect>(AccountState()) {

    /** Wipe choice of a deletion waiting for re-authentication; null when none is pending. */
    private var pendingDeletion: Boolean? = null

    init {
        authRepository.session
            .onEach { session ->
                val current = state.value
                // Dialogs of a signed-in account make no sense once it is gone
                setState(
                    if (session is AuthSession.Guest) {
                        current.copy(session = session, showSignOutDialog = false, deleteDialog = null)
                    } else {
                        current.copy(session = session)
                    }
                )
            }
            .launchIn(viewModelScope)
        sync.status
            .onEach { setState(state.value.copy(syncStatus = it)) }
            .launchIn(viewModelScope)
    }

    override suspend fun reduce(intent: AccountIntent, currentState: AccountState) {
        when (intent) {
            AccountIntent.OnSignInClicked -> requestToken()
            is AccountIntent.OnGoogleTokenResult -> onToken(intent.result)

            AccountIntent.OnSignOutClicked -> askSignOut()
            AccountIntent.OnSignOutDismissed -> setState(state.value.copy(showSignOutDialog = false, pendingSignOut = null))
            AccountIntent.OnSignOutConfirmed -> signOut()
            AccountIntent.OnSignOutBackUpFirst -> backUpThenSignOut()
            AccountIntent.OnBackUpNowClicked -> backUpNow()

            AccountIntent.OnDeleteClicked -> setState(state.value.copy(deleteDialog = DeleteDialog.Explain()))
            is AccountIntent.OnWipeLocalDataChanged -> {
                val dialog = state.value.deleteDialog as? DeleteDialog.Explain ?: return
                setState(state.value.copy(deleteDialog = dialog.copy(wipeLocalData = intent.wipe)))
            }
            AccountIntent.OnDeleteContinue -> {
                val dialog = state.value.deleteDialog as? DeleteDialog.Explain ?: return
                setState(state.value.copy(deleteDialog = DeleteDialog.Confirm(dialog.wipeLocalData)))
            }
            AccountIntent.OnDeleteDismissed -> setState(state.value.copy(deleteDialog = null))
            AccountIntent.OnDeleteConfirmed -> {
                val dialog = state.value.deleteDialog as? DeleteDialog.Confirm ?: return
                if (state.value.isBusy) return
                // Always a fresh Google credential first: nothing is erased before it
                pendingDeletion = dialog.wipeLocalData
                setState(state.value.copy(deleteDialog = null, isBusy = true))
                sendEffect(AccountEffect.RequestGoogleToken)
            }

            AccountIntent.OnBackClicked -> sendEffect(AccountEffect.NavigateBack)
        }
    }

    private fun requestToken() {
        if (state.value.isBusy) return
        setState(state.value.copy(isBusy = true))
        sendEffect(AccountEffect.RequestGoogleToken)
    }

    private suspend fun onToken(result: GoogleTokenResult) {
        val wipe = pendingDeletion
        pendingDeletion = null
        if (result !is GoogleTokenResult.Token) {
            setState(state.value.copy(isBusy = false))
            // A cancelled or failed re-authentication lands here too: nothing is erased or deleted
            result.failureMessage()?.let { sendEffect(AccountEffect.ShowMessage(it, result.failureCause)) }
            return
        }
        if (wipe != null) {
            delete(wipe, reauthIdToken = result.idToken)
            return
        }
        val signIn = authRepository.signInWithGoogle(result.idToken)
        setState(state.value.copy(isBusy = false))
        if (signIn is AuthResult.Failure) {
            sendEffect(AccountEffect.ShowMessage(signIn.error.toMessage(deleting = false), signIn.cause))
        }
    }

    /** Pending changes would only stay on this device: say so before signing out. */
    private suspend fun askSignOut() {
        val pending = sync.pendingCount()
        setState(
            if (pending > 0) state.value.copy(pendingSignOut = pending) else state.value.copy(showSignOutDialog = true)
        )
    }

    private suspend fun backUpThenSignOut() {
        setState(state.value.copy(pendingSignOut = null, isBusy = true))
        if (sync.syncNow() == SyncOutcome.SUCCESS) {
            signOut()
        } else {
            setState(state.value.copy(isBusy = false))
            sendEffect(AccountEffect.ShowMessage(AuthMessage.BACKUP_FAILED))
        }
    }

    private suspend fun backUpNow() {
        if (state.value.isBusy) return
        setState(state.value.copy(isBusy = true))
        val outcome = sync.syncNow()
        setState(state.value.copy(isBusy = false))
        sendEffect(AccountEffect.ShowMessage(if (outcome == SyncOutcome.SUCCESS) AuthMessage.BACKED_UP else AuthMessage.BACKUP_FAILED))
    }

    private suspend fun signOut() {
        setState(state.value.copy(showSignOutDialog = false, pendingSignOut = null, isBusy = true))
        authRepository.signOut()
        setState(state.value.copy(isBusy = false))
        sendEffect(AccountEffect.ClearGoogleCredentials)
        sendEffect(AccountEffect.ShowMessage(AuthMessage.SIGNED_OUT))
    }

    private suspend fun delete(wipeLocalData: Boolean, reauthIdToken: String) {
        setState(state.value.copy(isBusy = true))
        when (val result = authRepository.deleteAccount(wipeLocalData, reauthIdToken)) {
            AuthResult.Success -> {
                setState(state.value.copy(isBusy = false))
                sendEffect(AccountEffect.ClearGoogleCredentials)
                val message = if (wipeLocalData) AuthMessage.ACCOUNT_DELETED_AND_WIPED else AuthMessage.ACCOUNT_DELETED
                sendEffect(AccountEffect.ShowMessage(message))
            }
            // Not returned after a fresh re-authentication; a failure just in case
            AuthResult.RecentLoginRequired -> {
                setState(state.value.copy(isBusy = false))
                sendEffect(AccountEffect.ShowMessage(AuthMessage.DELETE_FAILED))
            }
            is AuthResult.Failure -> {
                setState(state.value.copy(isBusy = false))
                if (result.error == AuthError.LOCAL_WIPE_FAILED) {
                    sendEffect(AccountEffect.ClearGoogleCredentials)
                }
                sendEffect(AccountEffect.ShowMessage(result.error.toMessage(deleting = true), result.cause))
            }
        }
    }
}
