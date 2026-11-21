package com.javargasp.tiominutermvi.data.program.remote

import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource.Companion.EXERCISES_FILE
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource.Companion.programJson
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramExerciseDto
import com.javargasp.tiominutermvi.domain.program.model.Equipment
import com.javargasp.tiominutermvi.domain.program.model.Impact
import com.javargasp.tiominutermvi.domain.program.model.Mechanic
import com.javargasp.tiominutermvi.domain.program.model.MuscleGroup
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.testutil.CONTENT_DIR
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Validates the real `program_exercises.json`. Plain JVM: reads content/v1 ([CONTENT_DIR]) by
 * relative path (Gradle runs unit tests from the module directory).
 */
class ProgramCatalogValidationTest {

    private val assetsDir = CONTENT_DIR

    private val exercises: List<ProgramExerciseDto> by lazy {
        programJson.decodeFromString(
            ListSerializer(ProgramExerciseDto.serializer()),
            File(assetsDir, EXERCISES_FILE).readText()
        )
    }

    @Test
    fun `exerciseIds are unique`() {
        val duplicates = exercises.groupBy { it.exerciseId }.filterValues { it.size > 1 }.keys

        assertNoProblems("Duplicate exerciseIds", duplicates.map { "exerciseId $it" })
    }

    @Test
    fun `rest and exercises without animation are left out`() {
        val ids = exercises.map { it.exerciseId }.toSet()

        assertNoProblems("Excluded exerciseIds present", (EXCLUDED_IDS intersect ids).map { "exerciseId $it" })
    }

    @Test
    fun `every duplicateOf points to an existing canonical entry`() {
        val byId = exercises.associateBy { it.exerciseId }
        val problems = exercises.mapNotNull { exercise ->
            val target = exercise.duplicateOf ?: return@mapNotNull null
            when {
                target == exercise.exerciseId -> "exerciseId ${exercise.exerciseId} is a duplicate of itself"
                byId[target] == null -> "exerciseId ${exercise.exerciseId}: duplicateOf $target does not exist"
                byId.getValue(target).duplicateOf != null ->
                    "exerciseId ${exercise.exerciseId}: duplicateOf $target is itself a duplicate"
                else -> null
            }
        }

        assertNoProblems("Invalid duplicateOf", problems)
    }

    @Test
    fun `every enum value is valid`() {
        val problems = exercises.flatMap { exercise ->
            val id = exercise.exerciseId
            buildList {
                exercise.primaryMuscles.filterNot { it in names<MuscleGroup>() }
                    .forEach { add("exerciseId $id: muscle '$it'") }
                exercise.locations.filterNot { it in names<ProgramLocation>() }
                    .forEach { add("exerciseId $id: location '$it'") }
                if (exercise.equipment !in names<Equipment>()) add("exerciseId $id: equipment '${exercise.equipment}'")
                if (exercise.mechanic !in names<Mechanic>()) add("exerciseId $id: mechanic '${exercise.mechanic}'")
                if (exercise.impact !in names<Impact>()) add("exerciseId $id: impact '${exercise.impact}'")
            }
        }

        assertNoProblems("Invalid enum values", problems)
    }

    @Test
    fun `every canonical entry has a location and a primary muscle`() {
        val problems = exercises.filter { it.duplicateOf == null }.flatMap { exercise ->
            buildList {
                if (exercise.locations.isEmpty()) add("exerciseId ${exercise.exerciseId}: no location")
                if (exercise.primaryMuscles.isEmpty()) add("exerciseId ${exercise.exerciseId}: no primary muscle")
            }
        }

        assertNoProblems("Incomplete entries", problems)
    }

    @Test
    fun `every name has a Spanish translation`() {
        val problems = exercises.filter { it.nameEs.isBlank() }.map { "exerciseId ${it.exerciseId}" }

        assertNoProblems("Missing nameEs", problems)
    }

    @Test
    fun `every entry has a boolean perSide`() {
        // Read the raw JSON: the DTO defaults perSide to false, so it would hide a missing key.
        val entries = programJson.parseToJsonElement(File(assetsDir, EXERCISES_FILE).readText()).jsonArray
        val problems = entries.mapNotNull { element ->
            val entry = element.jsonObject
            val id = entry.getValue("exerciseId").jsonPrimitive.int
            val value = entry["perSide"]
            when {
                value == null -> "exerciseId $id: perSide missing"
                value !is JsonPrimitive || value.isString || value.booleanOrNull == null ->
                    "exerciseId $id: perSide is not a boolean ($value)"
                else -> null
            }
        }

        assertNoProblems("Invalid perSide", problems)
    }

    @Test
    fun `unilateral exercises are marked perSide`() {
        val perSide = exercises.filter { it.perSide }.map { it.exerciseId }.toSet()

        assertEquals(PER_SIDE_IDS, perSide)
    }

    @Test
    fun `duplicates have the same perSide as their canonical entry`() {
        val byId = exercises.associateBy { it.exerciseId }
        val problems = exercises.mapNotNull { exercise ->
            val canonical = exercise.duplicateOf?.let { byId[it] } ?: return@mapNotNull null
            if (canonical.perSide == exercise.perSide) null
            else "exerciseId ${exercise.exerciseId}: perSide ${exercise.perSide}, canonical ${canonical.exerciseId} ${canonical.perSide}"
        }

        assertNoProblems("perSide differs from canonical", problems)
    }

    @Test
    fun `animation files exist`() {
        val animationsDir = File(assetsDir, "animations")
        val problems = exercises
            .filterNot { it.animation in PENDING_ANIMATIONS }
            .filterNot { File(animationsDir, it.animation).isFile }
            .map { "exerciseId ${it.exerciseId}: ${it.animation}" }

        assertNoProblems("Missing animation files", problems)
    }

    @Test
    fun `pending animations belong to the catalog and are still missing`() {
        // Once a GIF is added, remove it from PENDING_ANIMATIONS so the test above checks it.
        val animationsDir = File(assetsDir, "animations")
        val used = exercises.map { it.animation }.toSet()
        val problems = PENDING_ANIMATIONS.mapNotNull { name ->
            when {
                name !in used -> "$name: not used by any exercise"
                File(animationsDir, name).isFile -> "$name: the file exists now, remove it from PENDING_ANIMATIONS"
                else -> null
            }
        }

        assertNoProblems("Stale pending animations", problems)
    }

    @Test
    fun `new exercise animations are named after their id`() {
        val pattern = Regex("""(\d+)_[a-z0-9]+(_[a-z0-9]+)*\.gif""")
        val problems = exercises.filter { it.exerciseId >= FIRST_FOCUS_EXERCISE_ID }.mapNotNull { exercise ->
            val match = pattern.matchEntire(exercise.animation)
            if (match != null && match.groupValues[1] == exercise.exerciseId.toString()) null
            else "exerciseId ${exercise.exerciseId}: ${exercise.animation}"
        }

        assertNoProblems("Animation names not like <id>_<snake_case>.gif", problems)
    }

    private inline fun <reified E : Enum<E>> names(): Set<String> = enumValues<E>().map { it.name }.toSet()

    private fun assertNoProblems(title: String, problems: List<String>) {
        if (problems.isNotEmpty()) {
            fail("$title (${problems.size}):\n" + problems.joinToString("\n"))
        }
    }

    companion object {
        /** Rest (13) and the exercises of exercises.json whose animation file does not exist. */
        val EXCLUDED_IDS = setOf(13, 26, 27, 33, 35, 36, 37, 39, 41, 42, 43, 44, 45)

        /**
         * Unilateral exercises (one side at a time): cross crunch, lunge (split stance),
         * standing leg curl, single-arm dumbbell row (70 and its duplicate 75), single-arm
         * preacher curl, Bulgarian split squat, step-up, side plank, and the focus exercises
         * single-leg glute bridge, donkey kick, fire hydrant, side-lying leg raise, lateral lunge.
         */
        val PER_SIDE_IDS = setOf(9, 20, 49, 70, 75, 80, 112, 114, 120, 123, 124, 125, 126, 127)

        /** First id of the home-friendly lower-body exercises added with the program focus. */
        const val FIRST_FOCUS_EXERCISE_ID = 121

        /**
         * Animations of catalog entries whose GIF has not been delivered yet. Empty: every
         * entry has its GIF (121-127 were the last ones). Add a file name here only while
         * its GIF is pending.
         */
        val PENDING_ANIMATIONS = emptySet<String>()
    }
}
