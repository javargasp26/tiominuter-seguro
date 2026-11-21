package com.javargasp.tiominutermvi.domain.repository

import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.Routine

/**
 * Repository interface for workout data (daily routines and the exercise catalog).
 */
interface WorkoutRepository {
    /**
     * Get all exercises.
     * @return List of all exercises
     */
    suspend fun getExercises(): List<Exercise>
    
    /**
     * Get all routines.
     * @return List of all routines
     */
    suspend fun getRoutines(): List<Routine>
    
    /**
     * Get a specific exercise by ID.
     * @param exerciseId The exercise ID
     * @return The exercise if found, null otherwise
     */
    suspend fun getExercise(exerciseId: Int): Exercise?
    
    /**
     * Get a specific routine by ID.
     * @param routineId The routine ID
     * @return The routine if found, null otherwise
     */
    suspend fun getRoutine(routineId: Int): Routine?

    /**
     * Fetch the daily catalog from the remote source and store it locally in
     * one transaction. On failure the stored data is left untouched.
     * The read methods call this once per process before their first read.
     */
    suspend fun refresh(): Result<Unit>
}