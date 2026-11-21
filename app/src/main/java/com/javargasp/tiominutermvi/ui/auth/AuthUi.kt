package com.javargasp.tiominutermvi.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.javargasp.tiominutermvi.BuildConfig
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.auth.AuthError
import com.javargasp.tiominutermvi.domain.auth.GoogleTokenResult

/** Snackbar messages of the account screens. Cancelling the Google picker never shows one. */
enum class AuthMessage(@StringRes val text: Int) {
    NO_NETWORK(R.string.auth_error_no_network),
    NO_GOOGLE_ACCOUNT(R.string.auth_error_no_google_account),
    SIGN_IN_FAILED(R.string.auth_error_sign_in_failed),
    SIGNED_OUT(R.string.auth_signed_out),
    ACCOUNT_DELETED(R.string.auth_account_deleted),
    ACCOUNT_DELETED_AND_WIPED(R.string.auth_account_deleted_and_wiped),
    DELETE_FAILED(R.string.auth_error_delete_failed),
    WIPE_FAILED(R.string.auth_error_wipe_failed),
    SERVER_DATA_FAILED(R.string.auth_error_server_data_failed),
    GROUPS_FAILED(R.string.auth_error_groups_failed),
    BACKUP_FAILED(R.string.sync_error_backup_failed),
    BACKED_UP(R.string.sync_backed_up),
    ACCOUNT_NOT_DELETED(R.string.auth_error_account_not_deleted)
}

/** The message for a token request that produced no token; null for a token or a cancellation. */
fun GoogleTokenResult.failureMessage(): AuthMessage? = when (this) {
    is GoogleTokenResult.Token, GoogleTokenResult.Cancelled -> null
    is GoogleTokenResult.NoGoogleAccount -> AuthMessage.NO_GOOGLE_ACCOUNT
    is GoogleTokenResult.NoNetwork -> AuthMessage.NO_NETWORK
    is GoogleTokenResult.Failed -> AuthMessage.SIGN_IN_FAILED
}

/** The exception behind a failed token request (short class name), if any. */
val GoogleTokenResult.failureCause: String?
    get() = when (this) {
        is GoogleTokenResult.Token, GoogleTokenResult.Cancelled -> null
        is GoogleTokenResult.NoGoogleAccount -> cause
        is GoogleTokenResult.NoNetwork -> cause
        is GoogleTokenResult.Failed -> cause
    }

/** The message for a failed sign-in ([deleting] = false) or account deletion. */
fun AuthError.toMessage(deleting: Boolean): AuthMessage = when (this) {
    AuthError.NETWORK -> AuthMessage.NO_NETWORK
    AuthError.SIGN_IN_FAILED -> AuthMessage.SIGN_IN_FAILED
    AuthError.LOCAL_WIPE_FAILED -> AuthMessage.WIPE_FAILED
    AuthError.SERVER_DATA_FAILED -> AuthMessage.SERVER_DATA_FAILED
    AuthError.GROUPS_FAILED -> AuthMessage.GROUPS_FAILED
    AuthError.ACCOUNT_NOT_DELETED -> AuthMessage.ACCOUNT_NOT_DELETED
    AuthError.FAILED -> if (deleting) AuthMessage.DELETE_FAILED else AuthMessage.SIGN_IN_FAILED
}

/**
 * The snackbar text: the friendly message, plus " (ExceptionName)" in debug builds so a failure
 * can be diagnosed on a device without logcat. Release builds show only the friendly text.
 */
fun authMessageText(text: String, cause: String?, showCause: Boolean = BuildConfig.DEBUG): String =
    if (showCause && cause != null) "$text ($cause)" else text

/** The Google photo when there is one, otherwise the account icon. Decorative unless described. */
@Composable
fun AccountAvatar(
    photoUrl: String?,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    if (photoUrl == null) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = contentDescription,
            modifier = modifier.size(size)
        )
    } else {
        val fallback = rememberVectorPainter(Icons.Filled.AccountCircle)
        AsyncImage(
            model = photoUrl,
            contentDescription = contentDescription,
            placeholder = fallback,
            error = fallback,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    }
}
