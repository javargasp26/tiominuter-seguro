package com.javargasp.tiominutermvi.data.program.remote

import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramExerciseDto
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramTemplatesDto

/** Simulated backend for the Program feature. */
interface ProgramRemoteDataSource {
    /** Program exercise catalog (`program_exercises.json`). */
    suspend fun fetchExercises(): List<ProgramExerciseDto>

    /** Splits, slot patterns, prescriptions, progression and cardio rules (`program_templates.json`). */
    suspend fun fetchTemplates(): ProgramTemplatesDto
}
