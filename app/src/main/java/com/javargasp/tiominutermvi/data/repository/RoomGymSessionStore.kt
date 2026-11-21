package com.javargasp.tiominutermvi.data.repository

import androidx.room.withTransaction
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.local.entity.GymSessionEntity
import com.javargasp.tiominutermvi.domain.gymsession.GymSession
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionStore

/**
 * [GymSessionStore] on [TioMinuterDatabase]: the session, the progress and the training log
 * share the database, so a close (entry + progress reset + session removal) is one transaction.
 */
class RoomGymSessionStore(private val database: TioMinuterDatabase) : GymSessionStore {

    private val sessions get() = database.gymSessionDao()

    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction { block() }

    override suspend fun get(routineId: Int): GymSession? = sessions.get(routineId)?.toDomain()

    override suspend fun getAll(): List<GymSession> = sessions.getAll().map { it.toDomain() }

    override suspend fun save(session: GymSession) {
        sessions.upsert(GymSessionEntity(session.routineId, session.sessionStartedAt, session.lastSetAt))
    }

    override suspend fun delete(routineId: Int) {
        sessions.delete(routineId)
    }

    override suspend fun clearProgress(routineId: Int) {
        database.gymProgressDao().clearRoutine(routineId)
    }

    override suspend fun routineTitle(routineId: Int): String? =
        database.gymRoutineDao().getRoutineWithExercises(routineId)?.routine?.routineName

    private fun GymSessionEntity.toDomain() = GymSession(routineId, sessionStartedAt, lastSetAt)
}
