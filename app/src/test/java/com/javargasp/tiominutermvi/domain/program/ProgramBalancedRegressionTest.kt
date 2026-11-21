package com.javargasp.tiominutermvi.domain.program

import com.javargasp.tiominutermvi.domain.program.ProgramTestAssets.catalog
import com.javargasp.tiominutermvi.domain.program.ProgramTestAssets.templates
import com.javargasp.tiominutermvi.domain.program.generator.ProgramGenerator
import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BALANCED programs must not change: the generator output for a fixed set of profiles
 * is compared with golden files captured before the focus feature existed
 * (`src/test/resources/program_golden/<goal>_<location>.json`).
 */
class ProgramBalancedRegressionTest {

    @Test
    fun `balanced programs match the golden files`() {
        val mismatches = goldenFiles().flatMap { (name, cases) ->
            cases.mapNotNull { case ->
                val actual = ProgramGenerator.generate(case.profile.toDomain(), catalog, templates)
                if (actual == case.program) null else "$name: ${case.profile}"
            }
        }

        assertTrue("Changed BALANCED programs (${mismatches.size}):\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun `golden files cover every captured profile`() {
        val stored = goldenFiles().flatMap { (_, cases) -> cases.map { it.profile } }

        assertEquals(goldenProfiles(), stored)
    }

    private fun goldenFiles(): List<Pair<String, List<GoldenCase>>> = goldenFileNames().map { name ->
        val file = File(GOLDEN_DIR, name)
        name to goldenJson.decodeFromString(ListSerializer(GoldenCase.serializer()), file.readText())
    }

    /** Profile fields of a golden case; no focus, since the goldens predate it (BALANCED). */
    @Serializable
    data class GoldenProfile(
        val age: Int,
        val weightKg: Double,
        val heightCm: Int,
        val level: ProgramLevel,
        val goal: ProgramGoal,
        val location: ProgramLocation,
        val weeks: Int,
        val daysPerWeek: Int
    ) {
        fun toDomain() =
            ProgramProfile(age, weightKg, heightCm, level, goal, location, weeks, daysPerWeek, ProgramFocus.BALANCED)
    }

    @Serializable
    data class GoldenCase(val profile: GoldenProfile, val program: GeneratedProgram)

    companion object {
        /** Read by relative path, like the asset tests (unit tests run from the module directory). */
        val GOLDEN_DIR = File("src/test/resources/program_golden")

        val goldenJson = Json { classDiscriminator = "kind" }

        fun goldenFileName(goal: ProgramGoal, location: ProgramLocation) =
            "${goal.name.lowercase()}_${location.name.lowercase()}.json"

        fun goldenFileNames(): List<String> = ProgramGoal.entries.flatMap { goal ->
            ProgramLocation.entries.map { location -> goldenFileName(goal, location) }
        }

        /** Normal adult, low impact by BMI (~34.6) and senior (low impact + extra rest). */
        private val bodies = listOf(Triple(30, 70.0, 175), Triple(35, 100.0, 170), Triple(65, 70.0, 175))
        private val weekOptions = listOf(4, 6, 8, 12)

        /**
         * Every goal × days × location × level, each with the three bodies; the program
         * length cycles through 4, 6, 8 and 12 weeks so deloads and long progressions are covered.
         */
        fun goldenProfiles(): List<GoldenProfile> {
            var index = 0
            return ProgramGoal.entries.flatMap { goal ->
                ProgramLocation.entries.flatMap { location ->
                    (2..5).flatMap { days ->
                        ProgramLevel.entries.flatMap { level ->
                            bodies.map { (age, weight, height) ->
                                GoldenProfile(
                                    age, weight, height, level, goal, location,
                                    weeks = weekOptions[index++ % weekOptions.size],
                                    daysPerWeek = days
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
