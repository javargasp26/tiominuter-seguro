package com.javargasp.tiominutermvi.domain.gymsession

import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.gymRefId
import com.javargasp.tiominutermvi.domain.session.ClosedSessionNotices
import com.javargasp.tiominutermvi.domain.session.SessionLock

/**
 * [GymSessionRepository] over a [GymSessionStore]. Every operation runs under the shared
 * [SessionLock] (one lock, one store transaction, not cancelled with the caller), so a close is
 * idempotent: two checks at once, or one after the other, write a single entry (the second finds
 * no session). Failures are logged and never break a workout.
 */
class GymSessionTracker(
    private val store: GymSessionStore,
    private val recorder: SessionEntryRecorder,
    private val clock: WallClock
) : GymSessionRepository {

    private val lock = SessionLock("GymSession")
    private val closedSessions = ClosedSessionNotices<ClosedGymSession>()

    override suspend fun onSetCompleted(routineId: Int) {
        val closed = safely("record a set of routine $routineId", default = null) {
            val now = clock.nowMillis()
            val current = store.get(routineId)
            val closed = current?.takeIf { GymSessionRules.isInactive(it.lastSetAt, now) }?.let { close(it) }
            store.save(GymSessionRules.afterSet(routineId, if (closed != null) null else current, now))
            closed
        }
        closed?.let(::notify)
    }

    override suspend fun completeSession(routineId: Int, title: String): Long? =
        safely("complete routine $routineId", default = null) {
            val session = store.get(routineId) ?: return@safely null
            val now = clock.nowMillis()
            val entryId = recorder.recordSession(
                TrainingSource.GYM, gymRefId(routineId), title, session.durationSeconds,
                completedAt = now, dateOfMillis = now
            )
            store.delete(routineId)
            entryId
        }

    override suspend fun closeInactiveSessions(): List<ClosedGymSession> {
        val closed = safely("close inactive sessions", default = emptyList()) {
            val now = clock.nowMillis()
            store.getAll()
                .filter { GymSessionRules.isInactive(it.lastSetAt, now) }
                .map { close(it) }
        }
        closed.forEach(::notify)
        return closed
    }

    override suspend fun discardSession(routineId: Int) {
        safely("discard the session of routine $routineId", default = Unit) { store.delete(routineId) }
    }

    override fun takeClosedSessions(): List<ClosedGymSession> = closedSessions.take()

    /**
     * One entry with the real duration (first → last set), dated on the session's start and
     * completed at its last set; then the routine's progress is reset and the session removed.
     */
    private suspend fun close(session: GymSession): ClosedGymSession {
        val title = store.routineTitle(session.routineId).orEmpty()
        recorder.recordSession(
            TrainingSource.GYM, gymRefId(session.routineId), title, session.durationSeconds,
            completedAt = session.lastSetAt, dateOfMillis = session.sessionStartedAt
        )
        store.clearProgress(session.routineId)
        store.delete(session.routineId)
        return ClosedGymSession(session.routineId, session.durationSeconds)
    }

    private fun notify(closed: ClosedGymSession) {
        closedSessions.add(closed)
    }

    /** Runs [block] under the lock in one transaction; a failure rolls it back and is logged. */
    private suspend fun <T> safely(action: String, default: T, block: suspend () -> T): T =
        lock.run(store, action, default, block)
}
