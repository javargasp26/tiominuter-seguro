package com.javargasp.tiominutermvi.domain.auth

import kotlinx.coroutines.flow.Flow

/**
 * Who is using the app. [Guest] is a first-class mode (the whole app works without an account);
 * [SignedIn] is a Firebase account created with Google.
 */
sealed interface AuthSession {
    data object Guest : AuthSession

    data class SignedIn(
        val uid: String,
        val displayName: String?,
        val email: String?,
        val photoUrl: String?
    ) : AuthSession
}

/** Outcome of a sign-in or an account deletion. */
sealed interface AuthResult {
    data object Success : AuthResult

    /** Firebase wants a recent sign-in before deleting: get a new Google ID token and retry once. */
    data object RecentLoginRequired : AuthResult

    /** [cause] is the short class name of the exception (shown in debug builds only). */
    data class Failure(val error: AuthError, val cause: String? = null) : AuthResult
}

enum class AuthError {
    NETWORK,

    /** Firebase refused the Google credential (sign-in or the re-authentication before a deletion). */
    SIGN_IN_FAILED,

    /** Any other failure (deleting the account). */
    FAILED,

    /** The account was deleted but the local data couldn't be wiped completely. */
    LOCAL_WIPE_FAILED,

    /** The backed-up (server) data couldn't be deleted: the account was NOT deleted. */
    SERVER_DATA_FAILED,

    /** The account couldn't leave its groups: nothing was erased and the account was NOT deleted. */
    GROUPS_FAILED,

    /**
     * The server data was deleted, but the Firebase user couldn't be (rare: it was just
     * re-authenticated). The account's syncs stay off until Delete account is retried or the
     * person signs out, so its local data is not uploaded again.
     */
    ACCOUNT_NOT_DELETED
}

/**
 * Identity only (Phase 1): no user data leaves the device. Never holds an Activity: the Google
 * ID token is obtained on the UI side (Credential Manager) and passed in.
 */
interface AuthRepository {

    /** The current session; emits again on every sign-in, sign-out or deletion. */
    val session: Flow<AuthSession>

    /** Signs in to Firebase with a Google ID token. Local data is kept as it is. */
    suspend fun signInWithGoogle(idToken: String): AuthResult

    /** Back to guest mode; local data stays on the device. */
    suspend fun signOut()

    /**
     * Deletes the account in this order, each step only if the previous one worked:
     * 1. re-authentication with [reauthIdToken] (a fresh Google credential, always asked for):
     *    a failure erases and deletes nothing;
     * 2. leaving every group ([AccountGroupsCleaner]): a failure is [AuthError.GROUPS_FAILED],
     *    before anything is erased;
     * 3. every server document ([UserDataEraser]): a failure is [AuthError.SERVER_DATA_FAILED];
     * 4. the Firebase user: a failure is [AuthError.ACCOUNT_NOT_DELETED];
     * 5. when [wipeLocalData], the user's local data ([LocalUserDataWiper]).
     */
    suspend fun deleteAccount(wipeLocalData: Boolean, reauthIdToken: String): AuthResult
}

/**
 * Deletes the user's local data (training history, weekly goals, gym and program progress,
 * programs, training photos). Never touches the content (catalog tables, the downloaded
 * content cache or the bundled assets) nor the database files and schemas.
 */
fun interface LocalUserDataWiper {
    suspend fun wipe()
}

/**
 * Deletes every server document of an account (Delete account, before the Firebase user);
 * throws when it can't, so the account is not deleted.
 */
fun interface UserDataEraser {
    suspend fun eraseServerData(uid: String)
}

/**
 * Delete account, before the server data is erased: leaves every group (owned groups go to their
 * oldest other member, or are deleted when alone). Throws when it can't, so nothing is erased.
 */
fun interface AccountGroupsCleaner {
    suspend fun leaveAllGroups(uid: String)

    companion object {
        /** No groups (tests). */
        val None = AccountGroupsCleaner { }
    }
}
