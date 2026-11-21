package com.javargasp.tiominutermvi.ui.account

import com.javargasp.tiominutermvi.domain.auth.GoogleTokenResult
import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface AccountIntent : MviIntent {
    data object OnSignInClicked : AccountIntent

    /** What the UI-side Credential Manager request returned (sign-in or re-authentication). */
    data class OnGoogleTokenResult(val result: GoogleTokenResult) : AccountIntent

    data object OnSignOutClicked : AccountIntent
    data object OnSignOutConfirmed : AccountIntent
    data object OnSignOutDismissed : AccountIntent

    /** In the "not backed up yet" sign-out dialog: back up, then sign out. */
    data object OnSignOutBackUpFirst : AccountIntent

    /** "Back up now" on the signed-in account. */
    data object OnBackUpNowClicked : AccountIntent

    /** Step 1 of the deletion: what happens + the local-wipe checkbox. */
    data object OnDeleteClicked : AccountIntent
    data class OnWipeLocalDataChanged(val wipe: Boolean) : AccountIntent

    /** Step 1 → step 2 (final confirmation). */
    data object OnDeleteContinue : AccountIntent
    data object OnDeleteConfirmed : AccountIntent
    data object OnDeleteDismissed : AccountIntent

    data object OnBackClicked : AccountIntent
}
