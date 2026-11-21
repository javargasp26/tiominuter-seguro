package com.javargasp.tiominutermvi.ui.components

import java.util.Locale

/**
 * Formats a duration in seconds as `mm:ss`.
 *
 * Locale-independent (always uses ASCII digits and a plain colon), so the
 * output is stable regardless of the device locale.
 *
 * Examples: 0 -> "00:00", 65 -> "01:05", 3599 -> "59:59".
 * Values of one hour or more wrap around (minutes are not capped).
 */
fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.ROOT, "%02d:%02d", minutes, remainingSeconds)
}
