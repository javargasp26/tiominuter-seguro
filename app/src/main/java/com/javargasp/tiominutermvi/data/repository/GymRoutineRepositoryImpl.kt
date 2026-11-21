package com.javargasp.tiominutermvi.data.repository

import androidx.room.withTransaction
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.mapper.toDomain
import com.javargasp.tiominutermvi.data.mapper.toEntities
import com.javargasp.tiominutermvi.data.mapper.toExerciseEntities
import com.javargasp.tiominutermvi.data.remote.GymRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineDto
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Identity of one exercise slot, used to compare current vs remote content. */
private data class SlotSignature(
    val exerciseId: Int,
    val type: String,
    val reps: List<Int>,
    val restSeconds: Int
)

/**
 * Offline-first [GymRoutineRepository]: the remote source fills Room, the app
 * reads only from Room.
 */
class GymRoutineRepositoryImpl(
    private val database: TioMinuterDatabase,
    private val remoteDataSource: GymRemoteDataSource
) : GymRoutineRepository {

    private val refreshMutex = Mutex()

    override fun observeGymRoutines(): Flow<List<GymRoutine>> =
        database.gymRoutineDao().observeRoutinesWithExercises().map { routines ->
            val exercisesById = currentExercisesById()
            routines.mapNotNull { it.toDomain(exercisesById) }
        }

    override fun observeGymRoutine(routineId: Int): Flow<GymRoutine?> =
        database.gymRoutineDao().observeRoutineWithExercises(routineId).map { routine ->
            routine?.toDomain(currentExercisesById())
        }

    override suspend fun refresh(): Result<Unit> = refreshMutex.withLock {
        try {
            val remoteRoutines = remoteDataSource.fetchGymRoutines()
            val remoteExercises = remoteDataSource.fetchExercises()

            database.withTransaction {
                // Compare each surviving routine's current slots with the new
                // ones (exerciseId, type, reps, restSeconds, in order) before
                // replacing, so progress is only cleared when content changed.
                val changedRoutineIds = remoteRoutines
                    .filter { dto -> slotsChanged(dto.routineId, dto) }
                    .map { it.routineId }

                database.exerciseDao().upsertAll(remoteExercises.toExerciseEntities())

                val routinesByEntity = remoteRoutines.map { it.toEntities() }
                database.gymRoutineDao().replaceCatalog(
                    routines = routinesByEntity.map { it.first },
                    exercisesByRoutine = routinesByEntity.associate { it.first.routineId to it.second }
                )

                changedRoutineIds.forEach { routineId ->
                    database.gymProgressDao().clearRoutine(routineId)
                }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            // Database content untouched; existing data stays available offline.
            Result.failure(t)
        }
    }

    /** True when the routine's current slots differ from the remote ones. */
    private suspend fun slotsChanged(routineId: Int, dto: GymRoutineDto): Boolean {
        val current = database.gymRoutineDao().getRoutineWithExercises(routineId) ?: return false
        val currentSlots = current.exercises
            .sortedBy { it.position }
            .map { SlotSignature(it.exerciseId, it.type, it.reps, it.restSeconds) }
        val newSlots = dto.exercises.map {
            SlotSignature(it.exerciseId, it.type, it.reps, it.restSeconds)
        }
        return currentSlots != newSlots
    }

    private suspend fun currentExercisesById(): Map<Int, Exercise> =
        database.exerciseDao().observeAll().first()
            .associateBy({ it.exerciseId }, { it.toDomain() })
}
