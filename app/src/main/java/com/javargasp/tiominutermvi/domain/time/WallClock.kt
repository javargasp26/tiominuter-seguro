package com.javargasp.tiominutermvi.domain.time

/**
 * Wall-clock time in epoch milliseconds, used for timestamps that are stored
 * (e.g. when an exercise was completed).
 *
 * Replaces `java.time.Clock`, which needs API 26 while minSdk is 24. The app
 * provides `System.currentTimeMillis()`; tests provide a fixed value.
 */
fun interface WallClock {
    fun nowMillis(): Long
}
