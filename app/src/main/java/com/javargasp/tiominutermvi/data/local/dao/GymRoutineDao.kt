package com.javargasp.tiominutermvi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.local.GymRoutineWithExercises
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineExerciseEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for gym routines and their exercise slots.
 */
@Dao
interface GymRoutineDao {

    @Transaction
    @Query(
        """
        SELECT * FROM gym_routines
        """
    )
    fun observeRoutinesWithExercises(): Flow<List<GymRoutineWithExercises>>

    @Transaction
    @Query(
        """
        SELECT * FROM gym_routines
        WHERE routineId = :routineId
        """
    )
    fun observeRoutineWithExercises(routineId: Int): Flow<GymRoutineWithExercises?>

    /**
     * One routine with its exercise slots, read synchronously (inside a
     * transaction); null when the routine does not exist.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM gym_routines
        WHERE routineId = :routineId
        """
    )
    suspend fun getRoutineWithExercises(routineId: Int): GymRoutineWithExercises?

    /**
     * Replaces the whole gym catalog in one transaction:
     * 1. upserts the given routines (rows are never delete-and-reinserted, so
     *    progress is not cascaded away),
     * 2. deletes routines whose ids are no longer present (cascading to their
     *    exercises and progress),
     * 3. for each surviving routine, deletes its exercises and inserts the
     *    new ones.
     */
    @Transaction
    suspend fun replaceCatalog(
        routines: List<GymRoutineEntity>,
        exercisesByRoutine: Map<Int, List<GymRoutineExerciseEntity>>
    ) {
        val incomingIds = routines.map { it.routineId }.toSet()

        // Upsert routines that still exist (no delete-and-reinsert, so
        // progress rows are not cascaded away).
        upsertRoutines(routines)

        // Delete routines that disappeared (cascades to exercises + progress).
        val placeholders = incomingIds.ifEmpty { setOf(-1) }
        deleteRoutinesNotIn(placeholders)

        // Replace each routine's exercise slots.
        exercisesByRoutine.forEach { (routineId, exercises) ->
            deleteExercises(routineId)
            insertExercises(exercises)
        }
    }

    @Upsert
    suspend fun upsertRoutines(routines: List<GymRoutineEntity>)

    @Query(
        """
        DELETE FROM gym_routines
        WHERE routineId NOT IN (:ids)
        """
    )
    suspend fun deleteRoutinesNotIn(ids: Set<Int>)

    @Query(
        """
        DELETE FROM gym_routine_exercises
        WHERE routineId = :routineId
        """
    )
    suspend fun deleteExercises(routineId: Int)

    @Insert
    suspend fun insertExercises(exercises: List<GymRoutineExerciseEntity>)
}
