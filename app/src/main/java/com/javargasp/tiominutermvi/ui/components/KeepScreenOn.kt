package com.javargasp.tiominutermvi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the device screen on while this composable is in the composition, by setting
 * `keepScreenOn` on the current view; leaving the composition restores it.
 * Call it once at the top of a screen that must not time out (e.g. a running workout).
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
