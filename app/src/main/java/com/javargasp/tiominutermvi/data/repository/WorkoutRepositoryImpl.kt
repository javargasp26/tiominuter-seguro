package com.javargasp.tiominutermvi.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.mapper.toDomain
import com.javargasp.tiominutermvi.data.mapper.toEntities
import com.javargasp.tiominutermvi.data.mapper.toExerciseEntities
import com.javargasp.tiominutermvi.data.remote.DailyRemoteDataSource
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline-first [WorkoutRepository] for the daily-routine flow: the remote
 * source fills Room, the app reads only from Room.
 *
 * The first read in the process refreshes the catalog once (concurrent first
 * readers wait for that single refresh). If it fails and Room already has
 * routines, the stored data is served; if Room is empty, the read throws and
 * the next read tries again.
 */
class WorkoutRepositoryImpl(
    private val database: TioMinuterDatabase,
    private val remoteDataSource: DailyRemoteDataSource
) : WorkoutRepository {

    private val refreshMutex = Mutex()
    private val initialRefreshMutex = Mutex()

    @Volatile
    private var hasInitialRefreshCompleted = false

    override suspend fun getExercises(): List<Exercise> {
        ensureInitialRefresh()
        return database.exerciseDao().getAll().map { it.toDomain() }
    }

    override suspend fun getRoutines(): List<Routine> {
        ensureInitialRefresh()
        return database.dailyRoutineDao().getRoutinesWithExercises().map { it.toDomain() }
    }

    override suspend fun getExercise(exerciseId: Int): Exercise? {
        ensureInitialRefresh()
        return database.exerciseDao().getById(exerciseId)?.toDomain()
    }

    override suspend fun getRoutine(routineId: Int): Routine? {
        ensureInitialRefresh()
        return database.dailyRoutineDao().getRoutineWithExercises(routineId)?.toDomain()
    }

    override suspend fun refresh(): Result<Unit> = refreshMutex.withLock {
        try {
            val remoteRoutines = remoteDataSource.fetchDailyRoutines()
            val remoteExercises = remoteDataSource.fetchExercises()

            database.withTransaction {
                database.exerciseDao().upsertAll(remoteExercises.toExerciseEntities())

                val routinesWithSlots = remoteRoutines.map { it.toEntities() }
                database.dailyRoutineDao().replaceCatalog(
                    routines = routinesWithSlots.map { it.first },
                    exercisesByRoutine = routinesWithSlots.associate { it.first.routineId to it.second }
                )
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            // Database content untouched; existing data stays available offline.
            Result.failure(t)
        }
    }

    /**
     * Refreshes once per process, before the first read. A failure is
     * tolerated when Room already has routines; otherwise it is thrown so the
     * ViewModels show their error state, and the next read retries.
     */
    private suspend fun ensureInitialRefresh() {
        if (hasInitialRefreshCompleted) return
        initialRefreshMutex.withLock {
            if (hasInitialRefreshCompleted) return

            val error = refresh().exceptionOrNull()
            if (error != null) {
                if (database.dailyRoutineDao().countRoutines() == 0) throw error
                Log.w(TAG, "Daily catalog refresh failed; serving stored data", error)
            }
            hasInitialRefreshCompleted = true
        }
    }

    private companion object {
        const val TAG = "WorkoutRepository"
    }
}
