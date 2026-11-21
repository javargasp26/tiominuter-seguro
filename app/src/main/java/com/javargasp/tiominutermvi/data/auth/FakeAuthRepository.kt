package com.javargasp.tiominutermvi.data.auth

import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthResult
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.auth.LocalUserDataWiper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [AuthRepository] for tests and previews. Results are scripted with
 * [signInResult] / [deleteResults]; every call is recorded.
 */
class FakeAuthRepository(
    initialSession: AuthSession = AuthSession.Guest,
    private val wiper: LocalUserDataWiper = LocalUserDataWiper { },
    private val signedInSession: AuthSession.SignedIn = SAMPLE_USER
) : AuthRepository {

    private val _session = MutableStateFlow(initialSession)
    override val session: StateFlow<AuthSession> = _session

    var signInResult: AuthResult = AuthResult.Success

    /** Returned by successive [deleteAccount] calls; the last one repeats. */
    var deleteResults: List<AuthResult> = listOf(AuthResult.Success)

    val signInTokens = mutableListOf<String>()
    val deleteCalls = mutableListOf<Pair<Boolean, String>>()
    var signOutCalls = 0

    override suspend fun signInWithGoogle(idToken: String): AuthResult {
        signInTokens += idToken
        if (signInResult == AuthResult.Success) _session.value = signedInSession
        return signInResult
    }

    override suspend fun signOut() {
        signOutCalls++
        _session.value = AuthSession.Guest
    }

    override suspend fun deleteAccount(wipeLocalData: Boolean, reauthIdToken: String): AuthResult {
        deleteCalls += wipeLocalData to reauthIdToken
        val result = deleteResults[minOf(deleteCalls.size - 1, deleteResults.size - 1)]
        if (result == AuthResult.Success) {
            _session.value = AuthSession.Guest
            if (wipeLocalData) wiper.wipe()
        }
        return result
    }

    companion object {
        val SAMPLE_USER = AuthSession.SignedIn(
            uid = "uid-1",
            displayName = "Alex Rivera",
            email = "alex@example.com",
            photoUrl = null
        )
    }
}
