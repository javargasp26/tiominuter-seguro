package com.javargasp.tiominutermvi.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.javargasp.tiominutermvi.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the exercise catalog (name + animation asset).
 */
@Dao
interface ExerciseDao {

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Query(
        """
        SELECT * FROM exercises
        """
    )
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT * FROM exercises
        WHERE exerciseId IN (:ids)
        """
    )
    suspend fun getByIds(ids: Set<Int>): List<ExerciseEntity>

    @Query(
        """
        SELECT * FROM exercises
        ORDER BY exerciseId
        """
    )
    suspend fun getAll(): List<ExerciseEntity>

    @Query(
        """
        SELECT * FROM exercises
        WHERE exerciseId = :exerciseId
        """
    )
    suspend fun getById(exerciseId: Int): ExerciseEntity?
}
