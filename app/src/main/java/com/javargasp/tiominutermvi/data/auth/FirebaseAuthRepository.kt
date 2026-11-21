package com.javargasp.tiominutermvi.data.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.javargasp.tiominutermvi.domain.auth.AccountGroupsCleaner
import com.javargasp.tiominutermvi.domain.auth.AuthError
import com.javargasp.tiominutermvi.domain.auth.AuthFailureLog
import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthResult
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.auth.LocalUserDataWiper
import com.javargasp.tiominutermvi.domain.auth.UserDataEraser
import com.javargasp.tiominutermvi.domain.auth.describeAuthFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [AuthRepository] over Firebase Authentication: a Google ID token (from Credential Manager, on
 * the UI side) becomes a Firebase credential. Firebase persists the session itself; no user data
 * is uploaded.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val wiper: LocalUserDataWiper,
    private val eraser: UserDataEraser,
    private val log: AuthFailureLog = LogcatAuthFailureLog(TAG),
    private val groupsCleaner: AccountGroupsCleaner = AccountGroupsCleaner.None
) : AuthRepository {

    override val session: Flow<AuthSession> = callbackFlow {
        // Called on registration with the current user, then on every change
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser.toSession()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogle(idToken: String): AuthResult = runCatchingAuth(AuthError.SIGN_IN_FAILED) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        AuthResult.Success
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun deleteAccount(wipeLocalData: Boolean, reauthIdToken: String): AuthResult {
        val user = auth.currentUser ?: return AuthResult.Failure(AuthError.FAILED).also {
            log.warn("deleteAccount without a signed-in user")
        }
        // 1. Always a fresh re-authentication: if it fails, nothing is erased or deleted
        val reauthenticated = runCatchingAuth(AuthError.SIGN_IN_FAILED) {
            user.reauthenticate(GoogleAuthProvider.getCredential(reauthIdToken, null)).await()
            AuthResult.Success
        }
        if (reauthenticated == AuthResult.RecentLoginRequired) {
            return AuthResult.Failure(AuthError.SIGN_IN_FAILED, RECENT_LOGIN_REQUIRED)
        }
        if (reauthenticated != AuthResult.Success) return reauthenticated
        // 2. Leave every group (shared docs other people read): if it fails, nothing is erased
        try {
            groupsCleaner.leaveAllGroups(user.uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Groups not left, account kept: ${describeAuthFailure(e)}")
            return AuthResult.Failure(AuthError.GROUPS_FAILED, e.javaClass.simpleName)
        }
        // 3. The server data: if it can't be deleted, the account stays (nothing is lost)
        try {
            eraser.eraseServerData(user.uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Server data not deleted, account kept: ${describeAuthFailure(e)}")
            return AuthResult.Failure(AuthError.SERVER_DATA_FAILED, e.javaClass.simpleName)
        }
        // 4. The Firebase user (just re-authenticated, so a failure is rare). The server data is
        // gone: whatever went wrong, the account's syncs stay off (the eraser marked it)
        val deleted = runCatchingAuth(AuthError.FAILED) {
            user.delete().await()
            AuthResult.Success
        }
        if (deleted != AuthResult.Success) {
            log.warn("Server data deleted but the account was not")
            val cause = (deleted as? AuthResult.Failure)?.cause ?: RECENT_LOGIN_REQUIRED
            return AuthResult.Failure(AuthError.ACCOUNT_NOT_DELETED, cause)
        }
        // 5. The optional local wipe
        if (!wipeLocalData) return deleted
        return try {
            wiper.wipe()
            AuthResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Local wipe failed after deleting the account: ${describeAuthFailure(e)}")
            AuthResult.Failure(AuthError.LOCAL_WIPE_FAILED, e.javaClass.simpleName)
        }
    }

    /** Runs a Firebase call; see [authFailureOf] for the mapping of what it throws. */
    private suspend fun runCatchingAuth(otherwise: AuthError, block: suspend () -> AuthResult): AuthResult =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            authFailureOf(e, otherwise, log)
        }

    private fun FirebaseUser?.toSession(): AuthSession =
        if (this == null) {
            AuthSession.Guest
        } else {
            AuthSession.SignedIn(uid, displayName, email, photoUrl?.toString())
        }

    private companion object {
        const val TAG = "FirebaseAuthRepository"
        const val RECENT_LOGIN_REQUIRED = "FirebaseAuthRecentLoginRequiredException"
    }
}

/**
 * Maps an exception of a Firebase Auth call: a recent login required → [AuthResult.RecentLoginRequired]
 * (not an error: the caller re-authenticates); [FirebaseNetworkException] or [IOException] →
 * [AuthError.NETWORK]; anything else ([com.google.firebase.auth.FirebaseAuthException]s included)
 * → [otherwise]. Failures are logged once, without personal data.
 */
internal fun authFailureOf(error: Exception, otherwise: AuthError, log: AuthFailureLog): AuthResult {
    if (error is FirebaseAuthRecentLoginRequiredException) return AuthResult.RecentLoginRequired
    log.warn(describeAuthFailure(error))
    val cause = error.javaClass.simpleName
    return when (error) {
        is FirebaseNetworkException, is IOException -> AuthResult.Failure(AuthError.NETWORK, cause)
        else -> AuthResult.Failure(otherwise, cause)
    }
}

/** Awaits a Play services [Task] without an extra dependency. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> continuation.resumeWithException(error)
            task.isCanceled -> continuation.cancel()
            else -> continuation.resume(task.result)
        }
    }
}
