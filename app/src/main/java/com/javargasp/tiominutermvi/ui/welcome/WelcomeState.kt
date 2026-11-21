package com.javargasp.tiominutermvi.ui.welcome

import com.javargasp.tiominutermvi.ui.base.MviState

data class WelcomeState(
    val showGuestDialog: Boolean = false,
    val isSigningIn: Boolean = false
) : MviState
