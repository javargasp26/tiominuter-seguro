package com.javargasp.tiominutermvi.domain.gymsession

import com.javargasp.tiominutermvi.domain.session.InactiveSessionCloser
import com.javargasp.tiominutermvi.domain.session.SESSION_INACTIVITY_TIMEOUT
import com.javargasp.tiominutermvi.domain.session.SessionTransactions

/**
 * The gym session of each routine (see [GymSession]). A session closes lazily, never with
 * background work: when the app starts (Home), when a gym screen opens or resumes, and when a
 * set is completed. Closing writes one training log entry with the real duration.
 */
interface GymSessionRepository : InactiveSessionCloser {

    /**
     * A set of [routineId] was completed now. An inactive session is closed first (and the
     * routine's progress reset), then this set starts a new session or extends the open one.
     */
    suspend fun onSetCompleted(routineId: Int)

    /**
     * The routine was completed: records its entry (duration first → last set, date of now)
     * and ends the session; the progress stays until the completion dialog is dismissed.
     * Returns the entry id, or null when there is no session (already recorded).
     */
    suspend fun completeSession(routineId: Int, title: String): Long?

    /** Closes every session inactive for more than [SESSION_INACTIVITY_TIMEOUT]. */
    suspend fun closeInactiveSessions(): List<ClosedGymSession>

    override suspend fun closeInactive() {
        closeInactiveSessions()
    }

    /** Ends the session without recording it (the progress was reset by hand). */
    suspend fun discardSession(routineId: Int)

    /** Sessions closed for inactivity not shown yet; each one is returned only once. */
    fun takeClosedSessions(): List<ClosedGymSession>
}

/**
 * Storage of the gym sessions and of what closing one touches. [inTransaction] makes a close
 * atomic: the entry, the progress reset and the session removal all happen, or none.
 */
interface GymSessionStore : SessionTransactions {
    suspend fun get(routineId: Int): GymSession?
    suspend fun getAll(): List<GymSession>
    suspend fun save(session: GymSession)
    suspend fun delete(routineId: Int)

    /** Clears the routine's exercise completion markers. */
    suspend fun clearProgress(routineId: Int)

    /** The routine's English name (the entry title, like a normal completion), or null. */
    suspend fun routineTitle(routineId: Int): String?
}
