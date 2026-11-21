package com.javargasp.tiominutermvi.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for per-exercise-slot completion markers of gym routines.
 * Reads come from Room; writes go straight to Room.
 */
interface GymProgressRepository {

    /**
     * Positions of the exercise slots the user has completed for a routine.
     */
    fun observeCompletedPositions(routineId: Int): Flow<Set<Int>>

    /**
     * Completion timestamps (epoch millis) of the routine's completed slots, in no order.
     */
    suspend fun getCompletionTimes(routineId: Int): List<Long>

    /**
     * Marks one exercise slot of a routine as completed now.
     */
    suspend fun markCompleted(routineId: Int, position: Int)

    /**
     * Clears all completion markers of the routine.
     */
    suspend fun resetRoutine(routineId: Int)
}
