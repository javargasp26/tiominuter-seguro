package com.javargasp.tiominutermvi.data.auth

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult as FirebaseAuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.javargasp.tiominutermvi.domain.auth.AccountGroupsCleaner
import com.javargasp.tiominutermvi.domain.auth.AuthError
import com.javargasp.tiominutermvi.domain.auth.AuthFailureLog
import com.javargasp.tiominutermvi.domain.auth.AuthResult
import com.javargasp.tiominutermvi.domain.auth.UserDataEraser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** The Firebase side of the error mapping, with mocked Firebase objects (no network, no Activity). */
class FirebaseAuthRepositoryTest {

    private val logged = mutableListOf<String>()
    private val log = AuthFailureLog { logged += it }
    private var wiped = false

    /** The order of the deletion's steps. */
    private val steps = mutableListOf<String>()
    private var eraseError: Exception? = null
    private var groupsError: Exception? = null
    private val user = mockk<FirebaseUser> { every { uid } returns "uid-1" }
    private val auth = mockk<FirebaseAuth> { every { currentUser } returns user }
    private val repository = FirebaseAuthRepository(
        auth,
        {
            wiped = true
            steps += "wipe"
        },
        UserDataEraser { uid ->
            eraseError?.let { throw it }
            steps += "erase:$uid"
        },
        log,
        AccountGroupsCleaner { uid ->
            groupsError?.let { throw it }
            steps += "groups:$uid"
        }
    )

    @Before
    fun setUp() {
        mockkStatic(GoogleAuthProvider::class)
        every { GoogleAuthProvider.getCredential(any(), any()) } returns mockk<AuthCredential>()
    }

    @After
    fun tearDown() {
        unmockkStatic(GoogleAuthProvider::class)
    }

    // --- Sign-in ---

    @Test
    fun `sign-in succeeds`() = runTest {
        every { auth.signInWithCredential(any()) } returns task<FirebaseAuthResult>(mockk())

        assertEquals(AuthResult.Success, repository.signInWithGoogle("id-token"))
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `a Firebase auth exception on sign-in is a failed Google sign-in`() = runTest {
        val error = mockk<FirebaseAuthInvalidCredentialsException>(relaxed = true)
        every { auth.signInWithCredential(any()) } returns task<FirebaseAuthResult>(error = error)

        val result = repository.signInWithGoogle("id-token")

        assertTrue(result is AuthResult.Failure && result.error == AuthError.SIGN_IN_FAILED)
        assertEquals(1, logged.size)
    }

    @Test
    fun `network exceptions on sign-in are network failures`() = runTest {
        listOf(mockk<FirebaseNetworkException>(relaxed = true), IOException("reset")).forEach { error ->
            every { auth.signInWithCredential(any()) } returns task<FirebaseAuthResult>(error = error)

            val result = repository.signInWithGoogle("id-token")

            assertTrue(result is AuthResult.Failure && result.error == AuthError.NETWORK)
        }
        assertEquals(2, logged.size)
    }

    @Test
    fun `any other exception on sign-in is a failed Google sign-in`() = runTest {
        every { auth.signInWithCredential(any()) } returns task<FirebaseAuthResult>(error = IllegalStateException("x"))

        assertEquals(
            AuthResult.Failure(AuthError.SIGN_IN_FAILED, "IllegalStateException"),
            repository.signInWithGoogle("id-token")
        )
    }

    // --- Mapping ---

    @Test
    fun `the mapping - recent login is not an error, network and the rest are`() {
        val recentLogin = mockk<FirebaseAuthRecentLoginRequiredException>(relaxed = true)
        val authError = mockk<FirebaseAuthException>(relaxed = true)

        assertEquals(AuthResult.RecentLoginRequired, authFailureOf(recentLogin, AuthError.FAILED, log))
        assertTrue(logged.isEmpty())
        assertEquals(AuthResult.Failure(AuthError.NETWORK, "IOException"), authFailureOf(IOException(), AuthError.FAILED, log))
        assertEquals(AuthError.FAILED, (authFailureOf(authError, AuthError.FAILED, log) as AuthResult.Failure).error)
        assertEquals(AuthError.SIGN_IN_FAILED, (authFailureOf(authError, AuthError.SIGN_IN_FAILED, log) as AuthResult.Failure).error)
        assertEquals(3, logged.size)
    }

    // --- Delete and re-authentication ---

    // --- Delete account: re-authentication → server data → Firebase user → local wipe ---

    private fun reauthWorks() {
        every { user.reauthenticate(any()) } answers {
            steps += "reauth"
            task<Void>(null)
        }
    }

    @Test
    fun `delete account - re-authentication, the groups, the server data, the Firebase user, then the local wipe`() = runTest {
        reauthWorks()
        every { user.delete() } answers {
            steps += "delete user"
            task<Void>(null)
        }

        assertEquals(AuthResult.Success, repository.deleteAccount(wipeLocalData = true, reauthIdToken = "fresh-token"))

        assertEquals(listOf("reauth", "groups:uid-1", "erase:uid-1", "delete user", "wipe"), steps)
    }

    @Test
    fun `when the groups can't be left nothing is erased or deleted`() = runTest {
        reauthWorks()
        groupsError = IllegalStateException("denied")
        every { user.delete() } returns task<Void>(null)

        val result = repository.deleteAccount(wipeLocalData = true, reauthIdToken = "fresh-token")

        assertEquals(AuthResult.Failure(AuthError.GROUPS_FAILED, "IllegalStateException"), result)
        assertEquals(listOf("reauth"), steps)
        verify(exactly = 0) { user.delete() }
        assertFalse(wiped)
    }

    @Test
    fun `without the wipe, the local data stays`() = runTest {
        reauthWorks()
        every { user.delete() } returns task<Void>(null)

        assertEquals(AuthResult.Success, repository.deleteAccount(wipeLocalData = false, reauthIdToken = "fresh-token"))

        assertFalse(wiped)
    }

    @Test
    fun `a failed re-authentication erases and deletes nothing, with the sign-in message`() = runTest {
        every { user.reauthenticate(any()) } returns task<Void>(error = mockk<FirebaseAuthInvalidCredentialsException>(relaxed = true))
        every { user.delete() } returns task<Void>(null)

        val result = repository.deleteAccount(wipeLocalData = true, reauthIdToken = "fresh-token")

        assertTrue(result is AuthResult.Failure && result.error == AuthError.SIGN_IN_FAILED)
        assertTrue(steps.isEmpty())
        verify(exactly = 0) { user.delete() }
        assertFalse(wiped)
        assertEquals(1, logged.size)
    }

    @Test
    fun `a re-authentication without network erases and deletes nothing`() = runTest {
        every { user.reauthenticate(any()) } returns task<Void>(error = IOException("offline"))
        every { user.delete() } returns task<Void>(null)

        val result = repository.deleteAccount(wipeLocalData = false, reauthIdToken = "fresh-token")

        assertEquals(AuthResult.Failure(AuthError.NETWORK, "IOException"), result)
        assertTrue(steps.isEmpty())
        verify(exactly = 0) { user.delete() }
    }

    @Test
    fun `when the server data can't be erased the Firebase user is not deleted`() = runTest {
        reauthWorks()
        eraseError = IOException("offline")
        every { user.delete() } returns task<Void>(null)

        val result = repository.deleteAccount(wipeLocalData = true, reauthIdToken = "fresh-token")

        assertEquals(AuthResult.Failure(AuthError.SERVER_DATA_FAILED, "IOException"), result)
        verify(exactly = 0) { user.delete() }
        assertFalse(wiped)
    }

    @Test
    fun `when the Firebase user can't be deleted after the erase it is a specific failure, no wipe`() = runTest {
        reauthWorks()
        every { user.delete() } returns task<Void>(error = mockk<FirebaseAuthRecentLoginRequiredException>(relaxed = true))

        val result = repository.deleteAccount(wipeLocalData = true, reauthIdToken = "fresh-token")

        assertTrue(result is AuthResult.Failure && result.error == AuthError.ACCOUNT_NOT_DELETED)
        assertEquals(listOf("reauth", "groups:uid-1", "erase:uid-1"), steps)
        assertFalse(wiped)
    }

    @Test
    fun `a network failure deleting the Firebase user after the erase is the same specific failure`() = runTest {
        reauthWorks()
        every { user.delete() } returns task<Void>(error = IOException("reset"))

        val result = repository.deleteAccount(wipeLocalData = false, reauthIdToken = "fresh-token")

        assertEquals(AuthResult.Failure(AuthError.ACCOUNT_NOT_DELETED, "IOException"), result)
    }

    @Test
    fun `log lines never contain the ID token`() = runTest {
        every { auth.signInWithCredential(any()) } returns
            task<FirebaseAuthResult>(error = IllegalStateException("bad token eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln"))

        repository.signInWithGoogle("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln")

        assertFalse(logged.single().contains("eyJ"))
    }

    /** A completed Play services task: [result], or failed with [error]. */
    private fun <T> task(result: T? = null, error: Exception? = null): Task<T> {
        val task = mockk<Task<T>>()
        every { task.exception } returns error
        every { task.isCanceled } returns false
        @Suppress("UNCHECKED_CAST")
        every { task.result } answers { result as T }
        every { task.addOnCompleteListener(any<OnCompleteListener<T>>()) } answers {
            firstArg<OnCompleteListener<T>>().onComplete(task)
            task
        }
        return task
    }
}
