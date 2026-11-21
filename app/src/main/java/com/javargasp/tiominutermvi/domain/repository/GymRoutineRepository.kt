package com.javargasp.tiominutermvi.domain.repository

import com.javargasp.tiominutermvi.domain.model.GymRoutine
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first repository for gym routines: reads come from Room, writes come
 * from the remote source via [refresh].
 */
interface GymRoutineRepository {

    /**
     * All gym routines with their exercise slots, sorted by position.
     */
    fun observeGymRoutines(): Flow<List<GymRoutine>>

    /**
     * One gym routine with its exercise slots, sorted by position;
     * null when the routine does not exist.
     */
    fun observeGymRoutine(routineId: Int): Flow<GymRoutine?>

    /**
     * Fetches routines and exercises from the remote source and replaces the
     * Room content in one transaction. On failure the existing database
     * content is untouched.
     *
     * For each routine that still exists: if its exercise slots changed
     * (exerciseId, type, reps, restSeconds, in order), that routine's progress
     * is cleared inside the same transaction; otherwise the progress is kept.
     */
    suspend fun refresh(): Result<Unit>
}
