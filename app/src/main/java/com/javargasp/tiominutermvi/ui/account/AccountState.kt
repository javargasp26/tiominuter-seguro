package com.javargasp.tiominutermvi.ui.account

import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.ui.base.MviState
import com.javargasp.tiominutermvi.domain.sync.SyncStatus

/**
 * @param session null until the first emission
 * @param isBusy a sign-in, sign-out, back-up or deletion is running
 * @param syncStatus the backup state shown to a signed-in account
 * @param pendingSignOut sign-out was asked with this many changes not backed up yet (its dialog)
 */
data class AccountState(
    val session: AuthSession? = null,
    val isBusy: Boolean = false,
    val showSignOutDialog: Boolean = false,
    val deleteDialog: DeleteDialog? = null,
    val syncStatus: SyncStatus = SyncStatus(),
    val pendingSignOut: Int? = null
) : MviState

/** The two-step account deletion. The checkbox starts unchecked. */
sealed interface DeleteDialog {
    val wipeLocalData: Boolean

    data class Explain(override val wipeLocalData: Boolean = false) : DeleteDialog
    data class Confirm(override val wipeLocalData: Boolean) : DeleteDialog
}
