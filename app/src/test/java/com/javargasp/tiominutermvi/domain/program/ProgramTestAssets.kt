package com.javargasp.tiominutermvi.domain.program

import com.javargasp.tiominutermvi.data.program.mapper.toDomain
import com.javargasp.tiominutermvi.data.program.mapper.toProgramCatalog
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource.Companion.EXERCISES_FILE
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource.Companion.TEMPLATES_FILE
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource.Companion.programJson
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramExerciseDto
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramTemplatesDto
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramTemplates
import com.javargasp.tiominutermvi.testutil.CONTENT_DIR
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * The real program content (content/v1, [CONTENT_DIR]), read by relative path (unit tests run
 * from the module directory).
 */
object ProgramTestAssets {
    private val assetsDir = CONTENT_DIR

    val catalog: List<ProgramExercise> by lazy {
        programJson.decodeFromString(
            ListSerializer(ProgramExerciseDto.serializer()),
            File(assetsDir, EXERCISES_FILE).readText()
        ).toProgramCatalog()
    }

    val templates: ProgramTemplates by lazy {
        programJson.decodeFromString(ProgramTemplatesDto.serializer(), File(assetsDir, TEMPLATES_FILE).readText())
            .toDomain()
    }

    /** A normal-impact adult profile (BMI ~22.9), overridable per test. */
    fun profile(
        age: Int = 30,
        weightKg: Double = 70.0,
        heightCm: Int = 175,
        level: ProgramLevel = ProgramLevel.INTERMEDIATE,
        goal: ProgramGoal = ProgramGoal.GAIN_MUSCLE,
        location: ProgramLocation = ProgramLocation.GYM,
        weeks: Int = 6,
        daysPerWeek: Int = 3
    ) = ProgramProfile(age, weightKg, heightCm, level, goal, location, weeks, daysPerWeek)
}
