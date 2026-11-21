package com.javargasp.tiominutermvi.data.repository

import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.local.entity.GymExerciseProgressEntity
import com.javargasp.tiominutermvi.domain.repository.GymProgressRepository
import com.javargasp.tiominutermvi.domain.time.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [GymProgressRepository] backed by Room. [clock] is injected so tests can
 * freeze time and be deterministic.
 */
class GymProgressRepositoryImpl(
    private val database: TioMinuterDatabase,
    private val clock: WallClock
) : GymProgressRepository {

    override fun observeCompletedPositions(routineId: Int): Flow<Set<Int>> =
        database.gymProgressDao().observeProgress(routineId).map { rows ->
            rows.map { it.position }.toSet()
        }

    override suspend fun getCompletionTimes(routineId: Int): List<Long> =
        database.gymProgressDao().getCompletionTimes(routineId)

    override suspend fun markCompleted(routineId: Int, position: Int) {
        database.gymProgressDao().markCompleted(
            GymExerciseProgressEntity(
                routineId = routineId,
                position = position,
                completedAt = clock.nowMillis()
            )
        )
    }

    override suspend fun resetRoutine(routineId: Int) {
        database.gymProgressDao().clearRoutine(routineId)
    }
}
