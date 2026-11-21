package com.javargasp.tiominutermvi.domain.feedback

/**
 * One-shot user feedback for workout events (completion beeps, haptics).
 *
 * Implementations must never throw: feedback failures are silently ignored so
 * they can never crash the app.
 */
interface WorkoutFeedback {
    fun playCompletionSound()
    fun vibrate()
}
