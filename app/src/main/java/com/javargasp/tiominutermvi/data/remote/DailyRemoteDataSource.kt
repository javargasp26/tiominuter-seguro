package com.javargasp.tiominutermvi.data.remote

import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto

/**
 * Remote source of daily routines and the exercise catalog.
 */
interface DailyRemoteDataSource {
    suspend fun fetchDailyRoutines(): List<DailyRoutineDto>
    suspend fun fetchExercises(): List<ExerciseDto>
}
