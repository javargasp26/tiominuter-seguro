package com.javargasp.tiominutermvi.data.remote

import com.javargasp.tiominutermvi.data.mapper.toDomain
import com.javargasp.tiominutermvi.data.mapper.toEntity
import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import com.javargasp.tiominutermvi.domain.goal.MIN_COUNTED_DURATION_SECONDS
import com.javargasp.tiominutermvi.testutil.CONTENT_DIR
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Validates the real daily-routine content files. Plain JVM: reads content/v1 ([CONTENT_DIR])
 * by relative path (Gradle runs unit tests from the module directory).
 */
class DailyDataValidationTest {

    private val assetsDir = CONTENT_DIR
    private val json = remoteJson

    private val routines: List<DailyRoutineDto> by lazy {
        json.decodeFromString(
            ListSerializer(DailyRoutineDto.serializer()),
            File(assetsDir, AssetDailyRemoteDataSource.DAILY_ROUTINES_FILE).readText()
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
    fun `every routineId is its day number (the app names it Day N in each language)`() {
        val problems = routines.filter { it.routineId < 1 || it.routineDay != "Day ${it.routineId}" }
            .map { "routineId ${it.routineId}: routineDay \"${it.routineDay}\"" }

        assertNoProblems("routineDay must be \"Day <routineId>\"", problems)
    }

    @Test
    fun `a Spanish title needs a title, and neither is blank`() {
        val problems = routines.filter { routine ->
            (routine.titleEs != null && routine.title == null) ||
                routine.title?.isBlank() == true || routine.titleEs?.isBlank() == true
        }.map { "routineId ${it.routineId}" }

        assertNoProblems("Bad title / titleEs", problems)
    }

    @Test
    fun `every routine has at least one exercise`() {
        val empty = routines.filter { it.exercises.isEmpty() }.map { "routineId ${it.routineId}" }

        assertNoProblems("Routines without exercises", empty)
    }

    @Test
    fun `every exerciseId exists in exercises json`() {
        val knownIds = exercises.map { it.exerciseId }.toSet()
        val problems = routines.flatMap { routine ->
            routine.exercises
                .filter { it.exerciseId !in knownIds }
                .map { "routineId ${routine.routineId}: exerciseId ${it.exerciseId}" }
        }

        assertNoProblems("exerciseIds missing from $EXERCISES_FILE", problems)
    }

    @Test
    fun `every exercise has a valid type and a positive amount`() {
        val problems = routines.flatMap { routine ->
            routine.exercises.flatMap { exercise ->
                val where = "routineId ${routine.routineId}, exerciseId ${exercise.exerciseId}"
                buildList {
                    if (exercise.type !in VALID_TYPES) add("$where: invalid type \"${exercise.type}\"")
                    if (exercise.amount <= 0) add("$where: non-positive amount ${exercise.amount}")
                }
            }
        }

        assertNoProblems("Invalid daily exercises", problems)
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
    fun `every routine lasts at least 10 and a half minutes of active time`() {
        val problems = routines.mapNotNull { routine ->
            val slots = routine.exercises.mapIndexedNotNull { index, dto ->
                dto.toEntity(routineId = routine.routineId, position = index).toDomain()
            }
            val seconds = DailyDurationEstimate.conservativeMinimumSeconds(slots)
            if (seconds < MIN_ROUTINE_SECONDS) {
                "routineId ${routine.routineId}: ${seconds / 60} min ${seconds % 60} s"
            } else {
                null
            }
        }

        assertNoProblems(
            "Daily routines under ${MIN_ROUTINE_SECONDS}s of conservative active time " +
                "(DailyDurationEstimate)",
            problems
        )
    }

    private fun assertNoProblems(title: String, problems: List<String>) {
        if (problems.isNotEmpty()) {
            fail("$title (${problems.size}):\n" + problems.joinToString("\n") { "  - $it" })
        }
    }

    private companion object {
        val VALID_TYPES = setOf("REPS", "TIME")

        /**
         * A workout counts toward the goal from [MIN_COUNTED_DURATION_SECONDS]; every day keeps at
         * least 30 s above it (the content itself aims at 11 minutes).
         */
        const val MIN_ROUTINE_SECONDS = MIN_COUNTED_DURATION_SECONDS + 30
    }
}
