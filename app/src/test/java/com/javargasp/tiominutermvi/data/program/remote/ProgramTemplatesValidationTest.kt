package com.javargasp.tiominutermvi.data.program.remote

import com.javargasp.tiominutermvi.data.program.mapper.toDomain
import com.javargasp.tiominutermvi.data.program.mapper.toProgramCatalog
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource.Companion.EXERCISES_FILE
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource.Companion.TEMPLATES_FILE
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource.Companion.programJson
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramExerciseDto
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramTemplatesDto
import com.javargasp.tiominutermvi.domain.program.generator.isCircuitEligible
import com.javargasp.tiominutermvi.domain.program.generator.isEligible
import com.javargasp.tiominutermvi.domain.program.model.MuscleGroup
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramTemplates
import com.javargasp.tiominutermvi.domain.program.model.SplitDay
import com.javargasp.tiominutermvi.testutil.CONTENT_DIR
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Validates the real `program_templates.json` against the real catalog. Plain JVM:
 * reads content/v1 ([CONTENT_DIR]) by relative path. Eligibility uses the generator's own
 * [isEligible], so the rule lives in one place.
 */
class ProgramTemplatesValidationTest {

    private val assetsDir = CONTENT_DIR

    private val catalog: Map<Int, ProgramExercise> by lazy {
        programJson.decodeFromString(
            ListSerializer(ProgramExerciseDto.serializer()),
            File(assetsDir, EXERCISES_FILE).readText()
        ).toProgramCatalog().associateBy { it.exerciseId }
    }

    private val templates: ProgramTemplates by lazy {
        programJson.decodeFromString(
            ProgramTemplatesDto.serializer(),
            File(assetsDir, TEMPLATES_FILE).readText()
        ).toDomain()
    }

    @Test
    fun `every split exists for every goal, day count and focus`() {
        val problems = ProgramFocus.entries.flatMap { focus ->
            ProgramGoal.entries.flatMap { goal ->
                (2..5).mapNotNull { days ->
                    val split = templates.split(goal, days, focus)
                    when {
                        split == null -> "$goal $days days $focus: missing"
                        split.days.size != days -> "$goal $days days $focus: has ${split.days.size} days"
                        else -> null
                    }
                }
            }
        }

        assertNoProblems("Splits", problems)
    }

    @Test
    fun `there is exactly one split per goal, day count and focus`() {
        val duplicates = templates.splits.groupBy { Triple(it.goal, it.daysPerWeek, it.focus) }
            .filterValues { it.size > 1 }.keys.map { "$it" }

        assertNoProblems("Duplicate splits", duplicates)
    }

    @Test
    fun `every goal, days, location, level, focus and impact has a split whose patterns all have an eligible candidate`() {
        val problems = ProgramFocus.entries.flatMap { focus ->
            ProgramGoal.entries.flatMap { goal ->
                (2..5).flatMap { days ->
                    val split = templates.split(goal, days, focus)
                        ?: return@flatMap listOf("$goal $days days $focus: no split")
                    val strengthDays = split.days.filterIsInstance<SplitDay.Strength>()
                        .mapNotNull { templates.strengthDay(it.strengthDayId) }
                    combinations().flatMap { (location, lowImpact, level) ->
                        strengthDays.flatMap { day ->
                            day.slots.withIndex().filterNot { (_, slot) ->
                                slot.candidates.mapNotNull { catalog[it] }.any {
                                    slot.muscle in it.primaryMuscles &&
                                        isEligible(it, location, lowImpact, level, templates.impact)
                                }
                            }.map { (index, slot) ->
                                "$goal $days $focus $location lowImpact=$lowImpact $level: ${day.id}[$index] ${slot.muscle}"
                            }
                        }
                    }
                }
            }
        }

        assertNoProblems("Combinations without an eligible candidate", problems)
    }

    @Test
    fun `focused splits keep the cardio days of the balanced split`() {
        fun cardioPositions(goal: ProgramGoal, days: Int, focus: ProgramFocus) =
            templates.split(goal, days, focus)!!.days.withIndex().filter { it.value == SplitDay.Cardio }.map { it.index }

        val problems = ProgramFocus.entries.flatMap { focus ->
            ProgramGoal.entries.flatMap { goal ->
                (2..5).mapNotNull { days ->
                    val expected = cardioPositions(goal, days, ProgramFocus.BALANCED)
                    val actual = cardioPositions(goal, days, focus)
                    if (actual == expected) null else "$goal $days $focus: cardio at $actual, balanced at $expected"
                }
            }
        }

        assertNoProblems("Cardio days moved", problems)
    }

    @Test
    fun `core focus days end with two required core holds and contain an anti-rotation pattern`() {
        val problems = ProgramGoal.entries.flatMap { goal ->
            (2..5).flatMap { days ->
                templates.split(goal, days, ProgramFocus.CORE)!!.days.filterIsInstance<SplitDay.Strength>()
                    .map { templates.strengthDay(it.strengthDayId)!! }
                    .mapNotNull { day ->
                        val last = day.slots.takeLast(2)
                        val endsWithHolds = last.size == 2 && last.all {
                            it.muscle == MuscleGroup.CORE && it.type == ProgramSlotType.TIME && it.required
                        }
                        val antiRotation = day.slots.dropLast(2).any { slot ->
                            slot.muscle == MuscleGroup.CORE && ANTI_ROTATION_IDS.any { it in slot.candidates }
                        }
                        if (endsWithHolds && antiRotation) null else "${day.id}: holds=$endsWithHolds antiRotation=$antiRotation"
                    }
            }
        }

        assertNoProblems("Core days", problems)
    }

    @Test
    fun `splits match the requested layouts`() {
        fun layout(goal: ProgramGoal, days: Int) = templates.split(goal, days)!!.days.map {
            when (it) {
                is SplitDay.Strength -> templates.strengthDay(it.strengthDayId)!!.title.name
                SplitDay.Cardio -> "CARDIO"
            }
        }

        assertEquals(listOf("FULL_BODY_A", "FULL_BODY_B"), layout(ProgramGoal.GAIN_MUSCLE, 2))
        assertEquals(listOf("FULL_BODY_A", "FULL_BODY_B", "FULL_BODY_C"), layout(ProgramGoal.GAIN_MUSCLE, 3))
        assertEquals(listOf("UPPER_A", "LOWER_A", "UPPER_B", "LOWER_B"), layout(ProgramGoal.GAIN_MUSCLE, 4))
        assertEquals(listOf("PUSH", "PULL", "LEGS", "UPPER_A", "LOWER_A"), layout(ProgramGoal.GAIN_MUSCLE, 5))
        assertEquals(listOf("FULL_BODY_A", "FULL_BODY_B"), layout(ProgramGoal.LOSE_FAT, 2))
        assertEquals(listOf("FULL_BODY_A", "CARDIO", "FULL_BODY_B"), layout(ProgramGoal.LOSE_FAT, 3))
        assertEquals(listOf("FULL_BODY_A", "CARDIO", "FULL_BODY_B", "FULL_BODY_C"), layout(ProgramGoal.LOSE_FAT, 4))
        assertEquals(
            listOf("FULL_BODY_A", "CARDIO", "FULL_BODY_B", "CARDIO", "FULL_BODY_C"),
            layout(ProgramGoal.LOSE_FAT, 5)
        )
    }

    @Test
    fun `focused splits match the requested layouts`() {
        fun layout(goal: ProgramGoal, days: Int, focus: ProgramFocus) = templates.split(goal, days, focus)!!.days.map {
            when (it) {
                is SplitDay.Strength -> templates.strengthDay(it.strengthDayId)!!.title.name
                SplitDay.Cardio -> "CARDIO"
            }
        }
        val gain = ProgramGoal.GAIN_MUSCLE
        val lose = ProgramGoal.LOSE_FAT

        // Lower body: Lower A / Upper / Lower B...; 2 days = two full-body days with extra legs.
        val lower = ProgramFocus.LOWER_BODY
        assertEquals(listOf("FULL_BODY_A", "FULL_BODY_B"), layout(gain, 2, lower))
        assertEquals(listOf("LOWER_A", "UPPER", "LOWER_B"), layout(gain, 3, lower))
        assertEquals(listOf("LOWER_A", "UPPER", "LOWER_B", "FULL_BODY"), layout(gain, 4, lower))
        assertEquals(listOf("LOWER_A", "UPPER_A", "LOWER_B", "UPPER_B", "LOWER_C"), layout(gain, 5, lower))
        assertEquals(listOf("FULL_BODY_A", "FULL_BODY_B"), layout(lose, 2, lower))
        assertEquals(listOf("FULL_BODY_A", "CARDIO", "FULL_BODY_B"), layout(lose, 3, lower))
        assertEquals(listOf("LOWER_A", "CARDIO", "UPPER", "LOWER_B"), layout(lose, 4, lower))
        assertEquals(listOf("LOWER_A", "CARDIO", "UPPER", "CARDIO", "LOWER_B"), layout(lose, 5, lower))

        // Upper body: the mirror.
        val upper = ProgramFocus.UPPER_BODY
        assertEquals(listOf("FULL_BODY_A", "FULL_BODY_B"), layout(gain, 2, upper))
        assertEquals(listOf("UPPER_A", "LOWER", "UPPER_B"), layout(gain, 3, upper))
        assertEquals(listOf("UPPER_A", "LOWER", "UPPER_B", "FULL_BODY"), layout(gain, 4, upper))
        assertEquals(listOf("UPPER_A", "LOWER_A", "UPPER_B", "LOWER_B", "UPPER_C"), layout(gain, 5, upper))
        assertEquals(listOf("FULL_BODY_A", "FULL_BODY_B"), layout(lose, 2, upper))
        assertEquals(listOf("FULL_BODY_A", "CARDIO", "FULL_BODY_B"), layout(lose, 3, upper))
        assertEquals(listOf("UPPER_A", "CARDIO", "LOWER", "UPPER_B"), layout(lose, 4, upper))
        assertEquals(listOf("UPPER_A", "CARDIO", "LOWER", "CARDIO", "UPPER_B"), layout(lose, 5, upper))

        // Core: the balanced layout with core days.
        ProgramGoal.entries.forEach { goal ->
            (2..5).forEach { days ->
                assertEquals(layout(goal, days, ProgramFocus.BALANCED), layout(goal, days, ProgramFocus.CORE))
            }
        }
    }

    @Test
    fun `every referenced strength day exists`() {
        val problems = templates.splits.flatMap { split ->
            split.days.filterIsInstance<SplitDay.Strength>()
                .filter { templates.strengthDay(it.strengthDayId) == null }
                .map { "${split.goal} ${split.daysPerWeek}: unknown day '${it.strengthDayId}'" }
        }

        assertNoProblems("Strength days", problems)
    }

    @Test
    fun `every candidate exists, is canonical and trains the pattern's muscle`() {
        val problems = templates.strengthDays.flatMap { day ->
            day.slots.flatMapIndexed { index, slot ->
                slot.candidates.mapNotNull { id ->
                    val exercise = catalog[id]
                    when {
                        exercise == null -> "${day.id}[$index]: exerciseId $id not in the catalog"
                        exercise.duplicateOf != null -> "${day.id}[$index]: exerciseId $id is a duplicate"
                        slot.muscle !in exercise.primaryMuscles ->
                            "${day.id}[$index]: exerciseId $id does not train ${slot.muscle}"
                        else -> null
                    }
                }
            }
        }

        assertNoProblems("Slot candidates", problems)
    }

    @Test
    fun `every slot pattern has an eligible candidate for every location, impact and level`() {
        val problems = templates.strengthDays.flatMap { day ->
            day.slots.flatMapIndexed { index, slot ->
                combinations().mapNotNull { (location, lowImpact, level) ->
                    val eligible = slot.candidates.mapNotNull { catalog[it] }
                        .any { isEligible(it, location, lowImpact, level, templates.impact) }
                    if (eligible) null else "${day.id}[$index] ${slot.muscle}: none for $location lowImpact=$lowImpact $level"
                }
            }
        }

        assertNoProblems("Patterns without eligible candidates", problems)
    }

    @Test
    fun `home circuits have enough eligible exercises for every impact and level`() {
        val circuit = templates.cardio.homeCircuit
        val problems = listOf(false, true).flatMap { lowImpact ->
            ProgramLevel.entries.mapNotNull { level ->
                val needed = circuit.levels.getValue(level).exercises
                val list = if (lowImpact) circuit.lowImpactCandidates else circuit.candidates
                val eligible = list.mapNotNull { catalog[it] }.filter {
                    isEligible(it, ProgramLocation.HOME, lowImpact, level, templates.impact) &&
                        isCircuitEligible(it)
                }
                if (eligible.size >= needed) null else "lowImpact=$lowImpact $level: ${eligible.size} < $needed"
            }
        }

        assertNoProblems("Home circuit candidates", problems)
    }

    @Test
    fun `home circuit candidates have no per-side exercise`() {
        val circuit = templates.cardio.homeCircuit
        val problems = (circuit.candidates + circuit.lowImpactCandidates).distinct()
            .mapNotNull { catalog[it] }
            .filter { it.perSide }
            .map { "exerciseId ${it.exerciseId} (${it.name})" }

        assertNoProblems("Per-side exercises in circuit candidates", problems)
    }

    @Test
    fun `prescriptions and cardio templates exist for every goal and level`() {
        val problems = buildList {
            ProgramGoal.entries.forEach { goal ->
                ProgramLevel.entries.forEach { level ->
                    if (templates.prescription(goal, level) == null) add("prescription $goal $level")
                }
            }
            ProgramLevel.entries.forEach { level ->
                if (level !in templates.cardio.treadmill) add("treadmill $level")
                if (level !in templates.cardio.treadmillLowImpact.baseIntervals) add("low-impact treadmill $level")
                if (level !in templates.cardio.outdoorRun.runStartKm) add("outdoor run $level")
                if (level !in templates.cardio.homeCircuit.levels) add("home circuit $level")
            }
            ProgramLocation.entries.forEach { location ->
                if (templates.cardio.rotation[location].isNullOrEmpty()) add("cardio rotation $location")
            }
        }

        assertNoProblems("Missing templates", problems)
    }

    companion object {
        /** Anti-rotation / stability exercises of the catalog (dead bug). */
        val ANTI_ROTATION_IDS = setOf(119)
    }

    private fun combinations() = ProgramLocation.entries.flatMap { location ->
        listOf(false, true).flatMap { lowImpact ->
            ProgramLevel.entries.map { level -> Triple(location, lowImpact, level) }
        }
    }

    private fun assertNoProblems(title: String, problems: List<String>) {
        if (problems.isNotEmpty()) {
            fail("$title (${problems.size}):\n" + problems.joinToString("\n"))
        }
    }
}
