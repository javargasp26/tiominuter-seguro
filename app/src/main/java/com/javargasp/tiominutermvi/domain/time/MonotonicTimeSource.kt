package com.javargasp.tiominutermvi.domain.time

/**
 * Monotonic clock in milliseconds, used to compute countdowns from an end
 * timestamp so they stay correct while the app is in the background.
 *
 * Only differences between two readings are meaningful. The app provides
 * `SystemClock.elapsedRealtime()` (keeps counting during deep sleep); tests
 * provide the test scheduler's virtual time.
 */
fun interface MonotonicTimeSource {
    fun nowMillis(): Long
}
