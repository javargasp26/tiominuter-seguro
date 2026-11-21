package com.javargasp.tiominutermvi.parity

import com.javargasp.tiominutermvi.data.program.mapper.toPayload
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource.Companion.EXERCISES_FILE
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource.Companion.TEMPLATES_FILE
import com.javargasp.tiominutermvi.domain.program.ProgramTestAssets
import com.javargasp.tiominutermvi.domain.program.generator.ProgramGenerator
import com.javargasp.tiominutermvi.domain.program.generator.computeBmi
import com.javargasp.tiominutermvi.domain.program.generator.isLowImpact
import com.javargasp.tiominutermvi.domain.program.generator.isSenior
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.kind
import com.javargasp.tiominutermvi.testutil.CONTENT_DIR
import org.junit.Test
import java.io.File

/**
 * shared/golden/program_generator.json: [ProgramGenerator] over the real content/v1 templates and
 * catalog. Matrix: every goal × location × level × days per week × focus for an adult profile
 * (6 weeks), plus edge profiles (low impact by BMI and just below it, senior, the range limits,
 * 4 and 12 weeks) × every focus × days × location. The generator is deterministic (no randomness),
 * so the input is the profile alone. Each workout is written as its stored payload, the exact
 * string Android stores in Room and Firestore (`toPayload`), so the web's encoder must match it
 * byte for byte. Distinct payloads are listed once in `payloads` and referenced by index.
 */
class ProgramGeneratorGoldenTest {

    private class Edge(
        val name: String,
        val age: Int,
        val weightKg: Double,
        val heightCm: Int,
        val level: ProgramLevel,
        val goal: ProgramGoal,
        val weeks: Int
    )

    private val edges = listOf(
        // BMI 30.04: low impact by BMI
        Edge("lowImpactBmi", 30, 92.0, 175, ProgramLevel.BEGINNER, ProgramGoal.LOSE_FAT, 6),
        // BMI 29.98: just below the threshold
        Edge("bmiJustBelow", 30, 91.8, 175, ProgramLevel.ADVANCED, ProgramGoal.GAIN_MUSCLE, 6),
        // 60: low impact and senior (extra rest)
        Edge("senior60", 60, 70.0, 170, ProgramLevel.INTERMEDIATE, ProgramGoal.LOSE_FAT, 6),
        // The upper limits: 90 years, 250 kg, 120 cm
        Edge("upperLimits", 90, 250.0, 120, ProgramLevel.ADVANCED, ProgramGoal.GAIN_MUSCLE, 12),
        // The lower limits: 16 years, 30 kg, 230 cm (underweight)
        Edge("lowerLimits", 16, 30.0, 230, ProgramLevel.BEGINNER, ProgramGoal.LOSE_FAT, 4),
        // 4 weeks: never a deload
        Edge("fourWeeks", 25, 65.5, 168, ProgramLevel.INTERMEDIATE, ProgramGoal.GAIN_MUSCLE, 4),
        // 12 weeks: deloads in weeks 4, 8, 12
        Edge("twelveWeeks", 45, 80.3, 181, ProgramLevel.ADVANCED, ProgramGoal.LOSE_FAT, 12)
    )

    @Test
    fun `program generator golden`() {
        val days = ProgramProfile.DAYS_PER_WEEK_RANGE.toList()
        val profiles = buildList {
            for (goal in ProgramGoal.entries) for (location in ProgramLocation.entries) for (level in ProgramLevel.entries)
                for (daysPerWeek in days) for (focus in ProgramFocus.entries) {
                    add("adult" to ProgramProfile(30, 70.0, 175, level, goal, location, 6, daysPerWeek, focus))
                }
            for (edge in edges) for (location in ProgramLocation.entries) for (daysPerWeek in days)
                for (focus in ProgramFocus.entries) {
                    add(
                        edge.name to ProgramProfile(
                            edge.age, edge.weightKg, edge.heightCm, edge.level, edge.goal, location, edge.weeks, daysPerWeek, focus
                        )
                    )
                }
        }
        val templates = ProgramTestAssets.templates
        val catalog = ProgramTestAssets.catalog
        val programs = profiles.map { (name, profile) -> Triple(name, profile, ProgramGenerator.generate(profile, catalog, templates)) }
        // Each distinct payload once (sorted), referenced by index: weeks and programs repeat many
        val payloads = programs.flatMap { (_, _, program) -> program.weeks.flatMap { it.workouts.map { w -> w.toPayload() } } }
            .distinct().sorted()
        val payloadIndex = payloads.withIndex().associate { (index, payload) -> payload to index }
        val cases = programs.map { (name, profile, program) ->
            obj(
                "id" to str(
                    "$name/${profile.goal}/${profile.location}/${profile.level}/${profile.daysPerWeek}d/${profile.focus.code}/${profile.weeks}w"
                ),
                "input" to obj(
                    "age" to num(profile.age),
                    "weightKg" to num(profile.weightKg),
                    "heightCm" to num(profile.heightCm),
                    "level" to str(profile.level.name),
                    "goal" to str(profile.goal.name),
                    "location" to str(profile.location.name),
                    "weeks" to num(profile.weeks),
                    "daysPerWeek" to num(profile.daysPerWeek),
                    "focus" to str(profile.focus.code)
                ),
                "output" to obj(
                    "bmi" to num(computeBmi(profile.weightKg, profile.heightCm)),
                    "lowImpact" to bool(isLowImpact(profile, templates.impact)),
                    "senior" to bool(isSenior(profile, templates.impact)),
                    "weeks" to arr(
                        program.weeks.map { week ->
                            obj(
                                "weekNumber" to num(week.weekNumber),
                                "isDeload" to bool(week.isDeload),
                                "workouts" to arr(
                                    week.workouts.map { workout ->
                                        obj("kind" to str(workout.kind.name), "payload" to num(payloadIndex.getValue(workout.toPayload())))
                                    }
                                )
                            )
                        }
                    )
                )
            )
        }
        Golden.verify(
            "program_generator",
            Golden.document(
                domain = "program_generator",
                generatedBy = "app/src/test/java/com/javargasp/tiominutermvi/parity/ProgramGeneratorGoldenTest.kt",
                description = "ProgramGenerator.generate over content/v1. Each workout's payload is an index into " +
                    "payloads: the exact strings Android stores (toPayload), each distinct one once",
                extra = mapOf(
                    "payloads" to arr(payloads.map(::str)),
                    "content" to obj(
                        EXERCISES_FILE to str(Golden.sha256(File(CONTENT_DIR, EXERCISES_FILE))),
                        TEMPLATES_FILE to str(Golden.sha256(File(CONTENT_DIR, TEMPLATES_FILE)))
                    )
                ),
                cases = cases
            )
        )
    }
}
