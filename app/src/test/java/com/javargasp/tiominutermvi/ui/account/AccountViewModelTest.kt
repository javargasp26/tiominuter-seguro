package com.javargasp.tiominutermvi.ui.account

import com.javargasp.tiominutermvi.data.auth.FakeAuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthError
import com.javargasp.tiominutermvi.domain.auth.AuthResult
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.auth.GoogleTokenResult
import com.javargasp.tiominutermvi.domain.auth.LocalUserDataWiper
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import com.javargasp.tiominutermvi.ui.auth.AuthMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.javargasp.tiominutermvi.testutil.FakeSyncRepository
import com.javargasp.tiominutermvi.domain.sync.SyncStatus
import com.javargasp.tiominutermvi.domain.sync.SyncOutcome

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Stands for the local data: true while the workouts are on the device. */
    private var localDataPresent = true
    private val wiper = LocalUserDataWiper { localDataPresent = false }

    private val sync = FakeSyncRepository()

    private fun viewModel(auth: FakeAuthRepository) = AccountViewModel(auth, sync)

    private fun signedIn() = FakeAuthRepository(initialSession = FakeAuthRepository.SAMPLE_USER, wiper = wiper)

    // --- Guest: sign in ---

    @Test
    fun `a guest signs in and keeps the local data`() = runTest {
        val auth = FakeAuthRepository(wiper = wiper)
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)
        assertEquals(AuthSession.Guest, viewModel.state.value.session)

        viewModel.handleIntent(AccountIntent.OnSignInClicked)
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Token("id-token")))

        assertEquals(FakeAuthRepository.SAMPLE_USER, viewModel.state.value.session)
        assertFalse(viewModel.state.value.isBusy)
        assertTrue(localDataPresent)
        assertEquals(listOf(AccountEffect.RequestGoogleToken), effects)
    }

    @Test
    fun `cancelling sign-in shows nothing, an error shows its message`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(AccountIntent.OnSignInClicked)
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Cancelled))
        assertEquals(listOf(AccountEffect.RequestGoogleToken), effects)

        auth.signInResult = AuthResult.Failure(AuthError.FAILED)
        viewModel.handleIntent(AccountIntent.OnSignInClicked)
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Token("id-token")))

        assertEquals(AccountEffect.ShowMessage(AuthMessage.SIGN_IN_FAILED), effects.last())
        assertEquals(AuthSession.Guest, viewModel.state.value.session)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `each sign-in failure shows its message with the cause`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        listOf(
            GoogleTokenResult.NoNetwork("IOException"),
            GoogleTokenResult.NoGoogleAccount("NoCredentialException"),
            GoogleTokenResult.Failed("GetCredentialProviderConfigurationException")
        ).forEach {
            viewModel.handleIntent(AccountIntent.OnSignInClicked)
            viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(it))
        }
        auth.signInResult = AuthResult.Failure(AuthError.NETWORK, "FirebaseNetworkException")
        viewModel.handleIntent(AccountIntent.OnSignInClicked)
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Token("id-token")))

        assertEquals(
            listOf(
                AccountEffect.ShowMessage(AuthMessage.NO_NETWORK, "IOException"),
                AccountEffect.ShowMessage(AuthMessage.NO_GOOGLE_ACCOUNT, "NoCredentialException"),
                AccountEffect.ShowMessage(AuthMessage.SIGN_IN_FAILED, "GetCredentialProviderConfigurationException"),
                AccountEffect.ShowMessage(AuthMessage.NO_NETWORK, "FirebaseNetworkException")
            ),
            effects.filterIsInstance<AccountEffect.ShowMessage>()
        )
        assertEquals(AuthSession.Guest, viewModel.state.value.session)
        assertFalse(viewModel.state.value.isBusy)
    }

    // --- Sign out ---

    @Test
    fun `sign out asks first, then goes back to guest and keeps the data`() = runTest {
        val auth = signedIn()
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(AccountIntent.OnSignOutClicked)
        assertTrue(viewModel.state.value.showSignOutDialog)
        assertEquals(0, auth.signOutCalls)

        viewModel.handleIntent(AccountIntent.OnSignOutConfirmed)

        assertEquals(1, auth.signOutCalls)
        assertEquals(AuthSession.Guest, viewModel.state.value.session)
        assertFalse(viewModel.state.value.showSignOutDialog)
        assertTrue(localDataPresent)
        assertEquals(
            listOf(AccountEffect.ClearGoogleCredentials, AccountEffect.ShowMessage(AuthMessage.SIGNED_OUT)),
            effects
        )
    }

    // --- Sign out with changes not backed up ---

    @Test
    fun `with pending changes sign out warns first, cancel keeps the session`() = runTest {
        sync.pending = 3
        val auth = signedIn()
        val viewModel = viewModel(auth)

        viewModel.handleIntent(AccountIntent.OnSignOutClicked)

        assertEquals(3, viewModel.state.value.pendingSignOut)
        assertFalse(viewModel.state.value.showSignOutDialog)

        viewModel.handleIntent(AccountIntent.OnSignOutDismissed)
        assertNull(viewModel.state.value.pendingSignOut)
        assertEquals(0, auth.signOutCalls)
    }

    @Test
    fun `back up now then sign out`() = runTest {
        sync.pending = 2
        val auth = signedIn()
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(AccountIntent.OnSignOutClicked)
        viewModel.handleIntent(AccountIntent.OnSignOutBackUpFirst)

        assertEquals(1, sync.syncCalls)
        assertEquals(1, auth.signOutCalls)
        assertEquals(AuthSession.Guest, viewModel.state.value.session)
        assertTrue(localDataPresent)
        assertEquals(AccountEffect.ShowMessage(AuthMessage.SIGNED_OUT), effects.last())
    }

    @Test
    fun `if backing up fails, the account stays signed in and a message says so`() = runTest {
        sync.pending = 2
        sync.outcome = SyncOutcome.FAILED
        val auth = signedIn()
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(AccountIntent.OnSignOutClicked)
        viewModel.handleIntent(AccountIntent.OnSignOutBackUpFirst)

        assertEquals(0, auth.signOutCalls)
        assertFalse(viewModel.state.value.isBusy)
        assertEquals(AccountEffect.ShowMessage(AuthMessage.BACKUP_FAILED), effects.last())
    }

    @Test
    fun `sign out anyway leaves the pending changes on the device`() = runTest {
        sync.pending = 2
        val auth = signedIn()
        val viewModel = viewModel(auth)

        viewModel.handleIntent(AccountIntent.OnSignOutClicked)
        viewModel.handleIntent(AccountIntent.OnSignOutConfirmed)

        assertEquals(1, auth.signOutCalls)
        assertEquals(0, sync.syncCalls)
        assertEquals(2, sync.pending)
        assertTrue(localDataPresent)
    }

    @Test
    fun `without pending changes the usual confirmation is shown`() = runTest {
        val viewModel = viewModel(signedIn())

        viewModel.handleIntent(AccountIntent.OnSignOutClicked)

        assertTrue(viewModel.state.value.showSignOutDialog)
        assertNull(viewModel.state.value.pendingSignOut)
    }

    @Test
    fun `back up now syncs and says whether it worked, the status is shown`() = runTest {
        val viewModel = viewModel(signedIn())
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(AccountIntent.OnBackUpNowClicked)
        sync.outcome = SyncOutcome.OFFLINE
        viewModel.handleIntent(AccountIntent.OnBackUpNowClicked)
        sync.status.value = SyncStatus(lastSuccessAt = 1_000L, pendingChanges = 4)

        assertEquals(
            listOf(AccountEffect.ShowMessage(AuthMessage.BACKED_UP), AccountEffect.ShowMessage(AuthMessage.BACKUP_FAILED)),
            effects
        )
        assertEquals(SyncStatus(lastSuccessAt = 1_000L, pendingChanges = 4), viewModel.state.value.syncStatus)
    }

    @Test
    fun `dismissing the sign-out dialog keeps the session`() = runTest {
        val auth = signedIn()
        val viewModel = viewModel(auth)

        viewModel.handleIntent(AccountIntent.OnSignOutClicked)
        viewModel.handleIntent(AccountIntent.OnSignOutDismissed)

        assertEquals(0, auth.signOutCalls)
        assertEquals(FakeAuthRepository.SAMPLE_USER, viewModel.state.value.session)
    }

    // --- Delete: always re-authenticate first ---

    /** Both steps, then the confirmation asks Google for a fresh credential. */
    private fun AccountViewModel.confirmDeletion(wipe: Boolean = false) {
        handleIntent(AccountIntent.OnDeleteClicked)
        if (wipe) handleIntent(AccountIntent.OnWipeLocalDataChanged(true))
        handleIntent(AccountIntent.OnDeleteContinue)
        handleIntent(AccountIntent.OnDeleteConfirmed)
    }

    @Test
    fun `delete takes two steps, re-authenticates first, and keeps the local data by default`() = runTest {
        val auth = signedIn()
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(AccountIntent.OnDeleteClicked)
        assertEquals(DeleteDialog.Explain(wipeLocalData = false), viewModel.state.value.deleteDialog)
        viewModel.handleIntent(AccountIntent.OnDeleteContinue)
        assertEquals(DeleteDialog.Confirm(wipeLocalData = false), viewModel.state.value.deleteDialog)
        viewModel.handleIntent(AccountIntent.OnDeleteConfirmed)

        // Nothing is deleted before the fresh credential arrives
        assertEquals(AccountEffect.RequestGoogleToken, effects.last())
        assertTrue(auth.deleteCalls.isEmpty())
        assertTrue(viewModel.state.value.isBusy)

        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Token("fresh-token")))

        assertEquals(listOf(false to "fresh-token"), auth.deleteCalls)
        assertTrue(auth.signInTokens.isEmpty())
        assertTrue(localDataPresent)
        assertEquals(AuthSession.Guest, viewModel.state.value.session)
        assertNull(viewModel.state.value.deleteDialog)
        assertFalse(viewModel.state.value.isBusy)
        assertEquals(AccountEffect.ShowMessage(AuthMessage.ACCOUNT_DELETED), effects.last())
        assertTrue(AccountEffect.ClearGoogleCredentials in effects)
    }

    @Test
    fun `delete with the checkbox wipes the local data`() = runTest {
        val auth = signedIn()
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.confirmDeletion(wipe = true)
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Token("fresh-token")))

        assertEquals(listOf(true to "fresh-token"), auth.deleteCalls)
        assertFalse(localDataPresent)
        assertEquals(AccountEffect.ShowMessage(AuthMessage.ACCOUNT_DELETED_AND_WIPED), effects.last())
    }

    @Test
    fun `cancelling either step deletes nothing`() = runTest {
        val auth = signedIn()
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(AccountIntent.OnDeleteClicked)
        viewModel.handleIntent(AccountIntent.OnDeleteDismissed)
        viewModel.handleIntent(AccountIntent.OnDeleteClicked)
        viewModel.handleIntent(AccountIntent.OnDeleteContinue)
        viewModel.handleIntent(AccountIntent.OnDeleteDismissed)
        // A stray confirm without the dialog does nothing
        viewModel.handleIntent(AccountIntent.OnDeleteConfirmed)

        assertTrue(auth.deleteCalls.isEmpty())
        assertTrue(effects.isEmpty())
        assertEquals(FakeAuthRepository.SAMPLE_USER, viewModel.state.value.session)
    }

    @Test
    fun `cancelling the re-authentication erases and deletes nothing, silently`() = runTest {
        val auth = signedIn()
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.confirmDeletion(wipe = true)
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Cancelled))

        assertTrue(auth.deleteCalls.isEmpty())
        assertEquals(listOf<AccountEffect>(AccountEffect.RequestGoogleToken), effects)
        assertFalse(viewModel.state.value.isBusy)
        assertEquals(FakeAuthRepository.SAMPLE_USER, viewModel.state.value.session)
        assertTrue(localDataPresent)

        // A later sign-in token is not taken as a re-authentication
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Token("other")))
        assertTrue(auth.deleteCalls.isEmpty())
    }

    @Test
    fun `a failed re-authentication token shows its message and deletes nothing`() = runTest {
        val auth = signedIn()
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.confirmDeletion(wipe = true)
        viewModel.handleIntent(
            AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Failed("GetCredentialCustomException"))
        )

        assertTrue(auth.deleteCalls.isEmpty())
        assertEquals(AccountEffect.ShowMessage(AuthMessage.SIGN_IN_FAILED, "GetCredentialCustomException"), effects.last())
        assertFalse(AccountEffect.ClearGoogleCredentials in effects)
        assertEquals(FakeAuthRepository.SAMPLE_USER, viewModel.state.value.session)
        assertTrue(localDataPresent)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `Firebase refusing the re-authentication shows the sign-in message and keeps everything`() = runTest {
        val auth = signedIn().apply {
            deleteResults = listOf(AuthResult.Failure(AuthError.SIGN_IN_FAILED, "FirebaseAuthInvalidCredentialsException"))
        }
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.confirmDeletion(wipe = true)
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Token("fresh-token")))

        assertEquals(
            AccountEffect.ShowMessage(AuthMessage.SIGN_IN_FAILED, "FirebaseAuthInvalidCredentialsException"),
            effects.last()
        )
        assertFalse(AccountEffect.ClearGoogleCredentials in effects)
        assertEquals(FakeAuthRepository.SAMPLE_USER, viewModel.state.value.session)
        assertTrue(localDataPresent)
    }

    @Test
    fun `when the server data can't be deleted the account stays and a message says so`() = runTest {
        val auth = signedIn().apply { deleteResults = listOf(AuthResult.Failure(AuthError.SERVER_DATA_FAILED)) }
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.confirmDeletion(wipe = true)
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Token("fresh-token")))

        assertEquals(AccountEffect.ShowMessage(AuthMessage.SERVER_DATA_FAILED), effects.last())
        assertEquals(FakeAuthRepository.SAMPLE_USER, viewModel.state.value.session)
        assertTrue(localDataPresent)
    }

    @Test
    fun `when the account can't be deleted after the erase a specific message is shown`() = runTest {
        val auth = signedIn().apply { deleteResults = listOf(AuthResult.Failure(AuthError.ACCOUNT_NOT_DELETED)) }
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.confirmDeletion(wipe = true)
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Token("fresh-token")))

        assertEquals(AccountEffect.ShowMessage(AuthMessage.ACCOUNT_NOT_DELETED), effects.last())
        assertEquals(FakeAuthRepository.SAMPLE_USER, viewModel.state.value.session)
        assertTrue(localDataPresent)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `a network error while deleting keeps the account`() = runTest {
        val auth = signedIn().apply { deleteResults = listOf(AuthResult.Failure(AuthError.NETWORK)) }
        val viewModel = viewModel(auth)
        val effects = collectEffects(viewModel)

        viewModel.confirmDeletion()
        viewModel.handleIntent(AccountIntent.OnGoogleTokenResult(GoogleTokenResult.Token("fresh-token")))

        assertEquals(AccountEffect.ShowMessage(AuthMessage.NO_NETWORK), effects.last())
        assertEquals(FakeAuthRepository.SAMPLE_USER, viewModel.state.value.session)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `back navigates back`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(AccountIntent.OnBackClicked)

        assertEquals(listOf(AccountEffect.NavigateBack), effects)
    }

    private fun TestScope.collectEffects(viewModel: AccountViewModel): List<AccountEffect> {
        val effects = mutableListOf<AccountEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }
        return effects
    }
}
