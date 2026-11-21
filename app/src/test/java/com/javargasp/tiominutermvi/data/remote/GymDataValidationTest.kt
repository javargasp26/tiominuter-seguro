package com.javargasp.tiominutermvi.data.remote

import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineDto
import com.javargasp.tiominutermvi.testutil.CONTENT_DIR
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Validates the real gym content files. Plain JVM: reads content/v1 ([CONTENT_DIR]) by relative
 * path (Gradle runs unit tests from the module directory).
 */
class GymDataValidationTest {

    private val assetsDir = CONTENT_DIR
    private val json = remoteJson

    private val routines: List<GymRoutineDto> by lazy {
        json.decodeFromString(
            ListSerializer(GymRoutineDto.serializer()),
            File(assetsDir, AssetGymRemoteDataSource.GYM_ROUTINES_FILE).readText()
        )
    }

    private val exercises: List<ExerciseDto> by lazy {
        json.decodeFromString(
            ListSerializer(ExerciseDto.serializer()),
            File(assetsDir, EXERCISES_FILE).readText()
        )
    }

    @Test
    fun `routineIds are unique`() {
        val duplicates = routines.groupBy { it.routineId }.filterValues { it.size > 1 }.keys

        assertNoProblems("Duplicate routineIds", duplicates.map { "routineId $it" })
    }

    @Test
    fun `every exerciseId exists in exercises json`() {
        val knownIds = exercises.map { it.exerciseId }.toSet()
        val problems = routines.flatMap { routine ->
            routine.exercises
                .filter { it.exerciseId !in knownIds }
                .map { "routineId ${routine.routineId}: exerciseId ${it.exerciseId}" }
        }

        assertNoProblems("exerciseIds missing from ${EXERCISES_FILE}", problems)
    }

    @Test
    fun `every exercise has consistent sets reps rest and type`() {
        val problems = routines.flatMap { routine ->
            routine.exercises.flatMap { exercise ->
                val where = "routineId ${routine.routineId}, exerciseId ${exercise.exerciseId}"
                buildList {
                    if (exercise.sets != exercise.reps.size) {
                        add("$where: sets=${exercise.sets} but reps has ${exercise.reps.size} values")
                    }
                    if (exercise.reps.any { it <= 0 }) add("$where: non-positive rep value in ${exercise.reps}")
                    if (exercise.restSeconds < 0) add("$where: negative rest ${exercise.restSeconds}")
                    if (exercise.type !in VALID_TYPES) add("$where: invalid type \"${exercise.type}\"")
                }
            }
        }

        assertNoProblems("Invalid gym exercises", problems)
    }

    @Test
    fun `every referenced animation file exists`() {
        val exercisesById = exercises.associateBy { it.exerciseId }
        val animationsDir = File(assetsDir, "animations")
        val missing = routines
            .flatMap { it.exercises }
            .mapNotNull { exercisesById[it.exerciseId] }
            .distinctBy { it.exerciseId }
            .filter { !File(animationsDir, it.animation).isFile }
            .map { "exerciseId ${it.exerciseId} (${it.name}): ${it.animation}" }

        assertNoProblems("Missing animation files in ${animationsDir.path}", missing)
    }

    @Test
    fun `every routine has a Spanish name`() {
        val missing = routines.filter { it.routineNameEs.isNullOrBlank() }
            .map { "routineId ${it.routineId} (${it.routineName})" }

        assertNoProblems("Routines without routineNameEs", missing)
    }

    @Test
    fun `every exercise used by a routine has a Spanish name`() {
        val exercisesById = exercises.associateBy { it.exerciseId }
        val missing = routines
            .flatMap { it.exercises }
            .mapNotNull { exercisesById[it.exerciseId] }
            .distinctBy { it.exerciseId }
            .filter { it.nameEs.isNullOrBlank() }
            .map { "exerciseId ${it.exerciseId} (${it.name})" }

        assertNoProblems("Exercises without nameEs in ${EXERCISES_FILE}", missing)
    }

    @Test
    fun `Spanish names match the program catalog for the same id`() {
        // Raw JSON: the program catalog belongs to the Program feature, so its DTO is not used here.
        val programNames = json.parseToJsonElement(File(assetsDir, PROGRAM_EXERCISES_FILE).readText()).jsonArray
            .associate { element ->
                val entry = element.jsonObject
                entry.getValue("exerciseId").jsonPrimitive.int to entry.getValue("nameEs").jsonPrimitive.content
            }
        val problems = exercises.mapNotNull { exercise ->
            val programName = programNames[exercise.exerciseId] ?: return@mapNotNull null
            if (exercise.nameEs == programName) null
            else "exerciseId ${exercise.exerciseId}: \"${exercise.nameEs}\" vs \"$programName\""
        }

        assertNoProblems("Spanish names different from $PROGRAM_EXERCISES_FILE", problems)
    }

    private fun assertNoProblems(title: String, problems: List<String>) {
        if (problems.isNotEmpty()) {
            fail("$title (${problems.size}):\n" + problems.joinToString("\n") { "  - $it" })
        }
    }

    private companion object {
        val VALID_TYPES = setOf("REPS", "TIME")
        const val PROGRAM_EXERCISES_FILE = "program_exercises.json"
    }
}
