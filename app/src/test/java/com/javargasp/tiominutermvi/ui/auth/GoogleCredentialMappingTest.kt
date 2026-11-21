package com.javargasp.tiominutermvi.ui.auth

import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.javargasp.tiominutermvi.domain.auth.AuthFailureLog
import com.javargasp.tiominutermvi.domain.auth.GoogleTokenResult
import com.javargasp.tiominutermvi.domain.auth.describeAuthFailure
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Every Credential Manager outcome maps to one result; only a cancellation is silent and unlogged. */
class GoogleCredentialMappingTest {

    private val logged = mutableListOf<String>()
    private val log = AuthFailureLog { logged += it }

    private fun map(error: Exception, isOnline: Boolean = true) = googleTokenFailure(error, isOnline, log)

    @Test
    fun `cancellation is silent and not logged`() {
        val result = map(GetCredentialCancellationException("closed"))

        assertEquals(GoogleTokenResult.Cancelled, result)
        assertEquals(null, result.failureMessage())
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `no credential means no Google account on the device`() {
        val result = map(NoCredentialException("none"))

        assertEquals(GoogleTokenResult.NoGoogleAccount("NoCredentialException"), result)
        assertEquals(AuthMessage.NO_GOOGLE_ACCOUNT, result.failureMessage())
        assertEquals(1, logged.size)
    }

    @Test
    fun `an IOException is a network failure`() {
        val result = map(IOException("timeout"))

        assertEquals(GoogleTokenResult.NoNetwork("IOException"), result)
        assertEquals(AuthMessage.NO_NETWORK, result.failureMessage())
        assertEquals(1, logged.size)
    }

    @Test
    fun `a Credential Manager error while offline is a network failure`() {
        val result = map(GetCredentialUnknownException("lost"), isOnline = false)

        assertEquals(GoogleTokenResult.NoNetwork("GetCredentialUnknownException"), result)
    }

    @Test
    fun `custom, provider configuration, interrupted and unknown errors show the generic message`() {
        val errors = listOf(
            GetCredentialCustomException("com.example.TYPE", "GetCredentialResponse error returned from framework"),
            GetCredentialProviderConfigurationException("no provider"),
            GetCredentialInterruptedException("interrupted"),
            GetCredentialUnknownException("unknown")
        )

        val results = errors.map { map(it) }

        assertEquals(
            listOf(
                GoogleTokenResult.Failed("GetCredentialCustomException"),
                GoogleTokenResult.Failed("GetCredentialProviderConfigurationException"),
                GoogleTokenResult.Failed("GetCredentialInterruptedException"),
                GoogleTokenResult.Failed("GetCredentialUnknownException")
            ),
            results
        )
        assertTrue(results.all { it.failureMessage() == AuthMessage.SIGN_IN_FAILED })
        assertEquals(4, logged.size)
        assertTrue(logged[0].startsWith("GetCredentialCustomException type=com.example.TYPE message="))
    }

    @Test
    fun `an ID token that can't be parsed shows the generic message`() {
        val result = map(GoogleIdTokenParsingException(IllegalStateException("bad")))

        assertEquals(GoogleTokenResult.Failed("GoogleIdTokenParsingException"), result)
        assertEquals(AuthMessage.SIGN_IN_FAILED, result.failureMessage())
        assertEquals(1, logged.size)
    }

    @Test
    fun `any other exception shows the generic message`() {
        val result = map(SecurityException("denied"))

        assertEquals(GoogleTokenResult.Failed("SecurityException"), result)
    }

    @Test
    fun `a Google ID token credential gives its token`() {
        val credential = mockk<CustomCredential> {
            every { type } returns GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        }

        val result = googleTokenFrom(credential, log) { "id-token" }

        assertEquals(GoogleTokenResult.Token("id-token"), result)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `an unexpected credential type shows the generic message`() {
        val password = mockk<PasswordCredential> { every { type } returns PasswordCredential.TYPE_PASSWORD_CREDENTIAL }
        val otherCustom = mockk<CustomCredential> { every { type } returns "com.example.OTHER" }
        val parse: (Credential) -> String = { error("must not parse another credential") }

        val results = listOf(googleTokenFrom(password, log, parse), googleTokenFrom(otherCustom, log, parse))

        assertTrue(results.all { it is GoogleTokenResult.Failed && it.failureMessage() == AuthMessage.SIGN_IN_FAILED })
        assertEquals(2, logged.size)
    }

    @Test
    fun `log lines never contain tokens or emails`() {
        val token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl"
        val line = describeAuthFailure(
            IllegalStateException("user alex.rivera@example.com sent $token"),
            type = "TYPE"
        )

        assertEquals("IllegalStateException type=TYPE message=user <email> sent <token>", line)
        assertFalse(line.contains("example.com"))
        assertFalse(line.contains(token))
    }

    @Test
    fun `debug builds append the exception name, release builds show only the message`() {
        val text = "Couldn't sign in with Google. Please try again."

        assertEquals("$text (GetCredentialCustomException)", authMessageText(text, "GetCredentialCustomException", showCause = true))
        assertEquals(text, authMessageText(text, "GetCredentialCustomException", showCause = false))
        assertEquals(text, authMessageText(text, null, showCause = true))
    }
}
