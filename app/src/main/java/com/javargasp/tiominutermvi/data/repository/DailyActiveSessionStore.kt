package com.javargasp.tiominutermvi.data.repository

import androidx.room.withTransaction
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.local.entity.DailySessionEntity
import com.javargasp.tiominutermvi.domain.session.ActiveSession
import com.javargasp.tiominutermvi.domain.session.ActiveSessionStore
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.dailyRefId
import com.javargasp.tiominutermvi.domain.traininglog.dailyRoutineIdOf
import com.javargasp.tiominutermvi.domain.traininglog.dailyWorkoutTitle

/**
 * [ActiveSessionStore] of the daily workout on [TioMinuterDatabase]: the session and the
 * training log share the database, so recording an abandoned session is one transaction.
 */
class DailyActiveSessionStore(
    private val database: TioMinuterDatabase,
    private val recorder: SessionEntryRecorder
) : ActiveSessionStore {

    private val sessions get() = database.dailySessionDao()

    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction { block() }

    override suspend fun get(): ActiveSession? = sessions.get()?.let {
        ActiveSession(dailyRefId(it.routineId), it.startedAt, it.activeSeconds, it.lastActivityAt)
    }

    override suspend fun save(session: ActiveSession) {
        val routineId = requireNotNull(dailyRoutineIdOf(session.key)) { "Not a daily session: ${session.key}" }
        // At most one daily session: another routine's row goes
        sessions.deleteAll()
        sessions.upsert(DailySessionEntity(routineId, session.startedAt, session.activeSeconds, session.lastActivityAt))
    }

    override suspend fun delete() {
        sessions.deleteAll()
    }

    override suspend fun recordAbandoned(session: ActiveSession) {
        val routine = dailyRoutineIdOf(session.key)?.let { database.dailyRoutineDao().getRoutineWithExercises(it)?.routine }
        recorder.recordSession(
            source = TrainingSource.DAILY,
            refId = session.key,
            title = routine?.let { dailyWorkoutTitle(it.routineDay, it.title) }.orEmpty(),
            durationSeconds = session.activeSeconds,
            completedAt = session.lastActivityAt,
            dateOfMillis = session.startedAt
        )
    }
}
