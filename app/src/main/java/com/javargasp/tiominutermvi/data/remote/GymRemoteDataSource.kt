package com.javargasp.tiominutermvi.data.remote

import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineDto

/**
 * Remote source of gym routines and the exercise catalog.
 */
interface GymRemoteDataSource {
    suspend fun fetchGymRoutines(): List<GymRoutineDto>
    suspend fun fetchExercises(): List<ExerciseDto>
}
