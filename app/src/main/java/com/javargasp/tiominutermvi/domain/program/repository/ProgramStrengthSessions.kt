package com.javargasp.tiominutermvi.domain.program.repository

import com.javargasp.tiominutermvi.domain.session.ClosedSession
import com.javargasp.tiominutermvi.domain.session.ClosedSessionNotices
import com.javargasp.tiominutermvi.domain.session.SessionLock
import com.javargasp.tiominutermvi.domain.session.SessionRules
import com.javargasp.tiominutermvi.domain.session.SessionTransactions
import com.javargasp.tiominutermvi.domain.time.WallClock

/**
 * The open session of a strength workout: [startedAt] is its first completed set and
 * [lastActivityAt] the latest one (wall-clock millis), as in the gym.
 */
data class StrengthSession(
    val programId: Long,
    val week: Int,
    val dayIndex: Int,
    val startedAt: Long,
    val lastActivityAt: Long
) {
    /** First set → last set, never negative. */
    val durationSeconds: Int get() = SessionRules.durationSeconds(startedAt, lastActivityAt)
}

/** Storage of the strength sessions and how an abandoned one is recorded. */
interface StrengthSessionStore : SessionTransactions {
    suspend fun get(programId: Long, week: Int, dayIndex: Int): StrengthSession?
    suspend fun getAll(): List<StrengthSession>
    suspend fun save(session: StrengthSession)
    suspend fun delete(programId: Long, week: Int, dayIndex: Int)

    /** One PROGRAM entry with the real duration, completed at the last set, dated on the start. */
    suspend fun recordAbandoned(session: StrengthSession)
}

/**
 * Strength workout sessions: the gym's rule on the shared session core. Every completed set
 * extends the workout's session; after 2 hours without a set it is closed lazily (at app start,
 * when a program screen opens, or on the next set) as one entry with the real duration. The
 * workout is **left resumable**: its completed slots stay and it is not marked completed, so
 * finishing it later is a new session. A normal completion takes the session in its own
 * transaction ([ProgramRepository.markSlotCompleted]), so it is never recorded twice.
 */
class ProgramStrengthSessions(
    private val store: StrengthSessionStore,
    private val clock: WallClock
) {
    private val lock = SessionLock("ProgramStrengthSession")
    private val notices = ClosedSessionNotices<ClosedSession>()

    /** A set was completed now: an inactive session is closed first, then this set extends or starts one. */
    suspend fun onSetCompleted(programId: Long, week: Int, dayIndex: Int) {
        lock.run(store, "record a set of $programId/$week/$dayIndex", default = Unit) {
            val now = clock.nowMillis()
            val current = store.get(programId, week, dayIndex)
            val open = if (current != null && SessionRules.isInactive(current.lastActivityAt, now)) {
                close(current)
                null
            } else {
                current
            }
            store.save(
                StrengthSession(
                    programId, week, dayIndex,
                    // A start after now (the clock moved back) moves to now: never negative
                    startedAt = open?.startedAt?.let { minOf(it, now) } ?: now,
                    lastActivityAt = now
                )
            )
        }
    }

    /** Closes every strength session inactive for more than 2 hours. */
    suspend fun closeInactive() {
        lock.run(store, "close inactive strength sessions", default = Unit) {
            val now = clock.nowMillis()
            store.getAll().filter { SessionRules.isInactive(it.lastActivityAt, now) }.forEach { close(it) }
        }
    }

    /** Sessions closed for inactivity not announced yet; each one only once. */
    fun takeClosedSessions(): List<ClosedSession> = notices.take()

    /** Removes the session, then records it (the entry may live in another database). */
    private suspend fun close(session: StrengthSession) {
        store.delete(session.programId, session.week, session.dayIndex)
        store.recordAbandoned(session)
        notices.add(ClosedSession(session.durationSeconds))
    }
}
