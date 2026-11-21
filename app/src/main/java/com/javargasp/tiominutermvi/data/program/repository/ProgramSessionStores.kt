package com.javargasp.tiominutermvi.data.program.repository

import androidx.room.withTransaction
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramCardioSessionEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramStrengthSessionEntity
import com.javargasp.tiominutermvi.domain.program.repository.StrengthSession
import com.javargasp.tiominutermvi.domain.program.repository.StrengthSessionStore
import com.javargasp.tiominutermvi.domain.program.repository.programRefId
import com.javargasp.tiominutermvi.domain.program.repository.programWorkoutOf
import com.javargasp.tiominutermvi.domain.session.ActiveSession
import com.javargasp.tiominutermvi.domain.session.ActiveSessionStore
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource

/**
 * [ActiveSessionStore] of the cardio workouts (treadmill, circuit, outdoor run) on
 * [ProgramDatabase]: at most one session for all of them. An abandoned one is recorded as a
 * PROGRAM entry titled like a completed workout ([titleOf]); the workout is not marked done.
 */
class ProgramCardioSessionStore(
    private val database: ProgramDatabase,
    private val recorder: SessionEntryRecorder,
    private val titleOf: suspend (programId: Long, week: Int, dayIndex: Int) -> String?
) : ActiveSessionStore {

    private val sessions get() = database.programSessionDao()

    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction { block() }

    override suspend fun get(): ActiveSession? = sessions.getCardio()?.let {
        ActiveSession(programRefId(it.programId, it.week, it.dayIndex), it.startedAt, it.activeSeconds, it.lastActivityAt)
    }

    override suspend fun save(session: ActiveSession) {
        val (programId, week, dayIndex) = requireNotNull(programWorkoutOf(session.key)) { "Not a program session: ${session.key}" }
        // At most one cardio session: another workout's row goes
        sessions.deleteCardio()
        sessions.upsertCardio(
            ProgramCardioSessionEntity(programId, week, dayIndex, session.startedAt, session.activeSeconds, session.lastActivityAt)
        )
    }

    override suspend fun delete() {
        sessions.deleteCardio()
    }

    override suspend fun recordAbandoned(session: ActiveSession) {
        val (programId, week, dayIndex) = programWorkoutOf(session.key) ?: return
        recorder.recordSession(
            source = TrainingSource.PROGRAM,
            refId = session.key,
            title = titleOf(programId, week, dayIndex).orEmpty(),
            durationSeconds = session.activeSeconds,
            completedAt = session.lastActivityAt,
            dateOfMillis = session.startedAt
        )
    }
}

/**
 * [StrengthSessionStore] on [ProgramDatabase]. An abandoned session is recorded as a PROGRAM
 * entry titled like a completed workout ([titleOf]); the workout keeps its slots (resumable).
 */
class ProgramStrengthSessionStore(
    private val database: ProgramDatabase,
    private val recorder: SessionEntryRecorder,
    private val titleOf: suspend (programId: Long, week: Int, dayIndex: Int) -> String?
) : StrengthSessionStore {

    private val sessions get() = database.programSessionDao()

    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction { block() }

    override suspend fun get(programId: Long, week: Int, dayIndex: Int): StrengthSession? =
        sessions.getStrength(programId, week, dayIndex)?.toDomain()

    override suspend fun getAll(): List<StrengthSession> = sessions.getAllStrength().map { it.toDomain() }

    override suspend fun save(session: StrengthSession) {
        sessions.upsertStrength(
            ProgramStrengthSessionEntity(session.programId, session.week, session.dayIndex, session.startedAt, session.lastActivityAt)
        )
    }

    override suspend fun delete(programId: Long, week: Int, dayIndex: Int) {
        sessions.deleteStrength(programId, week, dayIndex)
    }

    override suspend fun recordAbandoned(session: StrengthSession) {
        recorder.recordSession(
            source = TrainingSource.PROGRAM,
            refId = programRefId(session.programId, session.week, session.dayIndex),
            title = titleOf(session.programId, session.week, session.dayIndex).orEmpty(),
            durationSeconds = session.durationSeconds,
            completedAt = session.lastActivityAt,
            dateOfMillis = session.startedAt
        )
    }

    private fun ProgramStrengthSessionEntity.toDomain() =
        StrengthSession(programId, week, dayIndex, sessionStartedAt, lastActivityAt)
}
