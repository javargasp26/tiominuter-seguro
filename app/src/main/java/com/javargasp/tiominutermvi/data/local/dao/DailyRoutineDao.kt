package com.javargasp.tiominutermvi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.local.DailyRoutineWithExercises
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineExerciseEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for daily routines and their exercise slots.
 */
@Dao
interface DailyRoutineDao {

    @Transaction
    @Query(
        """
        SELECT * FROM daily_routines
        ORDER BY routineId
        """
    )
    fun observeRoutinesWithExercises(): Flow<List<DailyRoutineWithExercises>>

    @Transaction
    @Query(
        """
        SELECT * FROM daily_routines
        ORDER BY routineId
        """
    )
    suspend fun getRoutinesWithExercises(): List<DailyRoutineWithExercises>

    /**
     * One routine with its exercise slots; null when the routine does not exist.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM daily_routines
        WHERE routineId = :routineId
        """
    )
    suspend fun getRoutineWithExercises(routineId: Int): DailyRoutineWithExercises?

    @Query(
        """
        SELECT COUNT(*) FROM daily_routines
        """
    )
    suspend fun countRoutines(): Int

    /**
     * Replaces the whole daily catalog in one transaction:
     * 1. upserts the given routines (rows are never delete-and-reinserted),
     * 2. deletes routines whose ids are no longer present (cascading to their
     *    exercise slots),
     * 3. for each surviving routine, deletes its exercise slots and inserts
     *    the new ones.
     */
    @Transaction
    suspend fun replaceCatalog(
        routines: List<DailyRoutineEntity>,
        exercisesByRoutine: Map<Int, List<DailyRoutineExerciseEntity>>
    ) {
        val incomingIds = routines.map { it.routineId }.toSet()

        // Upsert routines that still exist (no delete-and-reinsert).
        upsertRoutines(routines)

        // Delete routines that disappeared (cascades to their slots).
        val placeholders = incomingIds.ifEmpty { setOf(-1) }
        deleteRoutinesNotIn(placeholders)

        // Replace each routine's exercise slots.
        exercisesByRoutine.forEach { (routineId, exercises) ->
            deleteExercises(routineId)
            insertExercises(exercises)
        }
    }

    @Upsert
    suspend fun upsertRoutines(routines: List<DailyRoutineEntity>)

    @Query(
        """
        DELETE FROM daily_routines
        WHERE routineId NOT IN (:ids)
        """
    )
    suspend fun deleteRoutinesNotIn(ids: Set<Int>)

    @Query(
        """
        DELETE FROM daily_routine_exercises
        WHERE routineId = :routineId
        """
    )
    suspend fun deleteExercises(routineId: Int)

    @Insert
    suspend fun insertExercises(exercises: List<DailyRoutineExerciseEntity>)
}
