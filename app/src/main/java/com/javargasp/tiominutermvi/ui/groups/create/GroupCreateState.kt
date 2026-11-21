package com.javargasp.tiominutermvi.ui.groups.create

import com.javargasp.tiominutermvi.domain.groups.CreatedGroup
import com.javargasp.tiominutermvi.domain.groups.GroupLimits
import com.javargasp.tiominutermvi.ui.base.MviState

/** The name form, then (once [created]) the invite code with Copy and Share. */
data class GroupCreateState(
    val name: String = "",
    val showNameError: Boolean = false,
    val isCreating: Boolean = false,
    val created: CreatedGroup? = null
) : MviState {
    val isNameValid: Boolean get() = GroupLimits.normalizeName(name) != null
}
