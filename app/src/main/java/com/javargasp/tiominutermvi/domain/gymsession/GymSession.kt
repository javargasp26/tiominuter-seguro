package com.javargasp.tiominutermvi.domain.gymsession

import com.javargasp.tiominutermvi.domain.session.SESSION_INACTIVITY_TIMEOUT
import com.javargasp.tiominutermvi.domain.session.SessionRules

/**
 * The gym routine being done now: [sessionStartedAt] is its first completed set and [lastSetAt]
 * the latest one (wall-clock millis), updated on every set, so the duration is always known.
 */
data class GymSession(
    val routineId: Int,
    val sessionStartedAt: Long,
    val lastSetAt: Long
) {
    /** First set → last set, in seconds; never negative (a clock moved back clamps at 0). */
    val durationSeconds: Int
        get() = SessionRules.durationSeconds(sessionStartedAt, lastSetAt)
}

/** A session the app closed for inactivity: shown once, the next time a gym screen opens. */
data class ClosedGymSession(val routineId: Int, val durationSeconds: Int)

/** Rules of the gym session, on the shared [SessionRules]. */
object GymSessionRules {

    /** More than [SESSION_INACTIVITY_TIMEOUT] since [lastSetAt]; never when the clock moved back. */
    fun isInactive(lastSetAt: Long, now: Long): Boolean = SessionRules.isInactive(lastSetAt, now)

    /**
     * The session after a set completed at [now]: a new one when there is none, otherwise the
     * same start with [now] as the last set (a start later than [now], after the clock moved
     * back, moves to [now] so the duration can't go negative). The caller closes an inactive
     * session first.
     */
    fun afterSet(routineId: Int, current: GymSession?, now: Long): GymSession =
        if (current == null) {
            GymSession(routineId, sessionStartedAt = now, lastSetAt = now)
        } else {
            current.copy(sessionStartedAt = minOf(current.sessionStartedAt, now), lastSetAt = now)
        }

    /**
     * The session of progress stored before sessions existed (database v7 migration), from its
     * exercise completion times ([SessionRules.latestRun]). Null without completions.
     */
    fun fromCompletionTimes(routineId: Int, completionTimes: List<Long>): GymSession? =
        SessionRules.latestRun(completionTimes)?.let { (start, last) -> GymSession(routineId, start, last) }
}
