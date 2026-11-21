package com.javargasp.tiominutermvi.domain.program

import com.javargasp.tiominutermvi.domain.program.ProgramTestAssets.catalog
import com.javargasp.tiominutermvi.domain.program.ProgramTestAssets.profile
import com.javargasp.tiominutermvi.domain.program.ProgramTestAssets.templates
import com.javargasp.tiominutermvi.domain.program.generator.ProgramGenerator
import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.MuscleGroup
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.SplitDay
import com.javargasp.tiominutermvi.domain.program.model.StrengthSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [ProgramGenerator] with a focus, real catalog and templates. Every check runs for every
 * goal × days × location × level, with a normal and a low-impact profile.
 */
class ProgramFocusTest {

    private val byId = catalog.associateBy { it.exerciseId }

    private fun generate(profile: ProgramProfile): GeneratedProgram =
        ProgramGenerator.generate(profile, catalog, templates)

    /** Normal (BMI ~22.9) and low impact by age (also adds the senior rest). */
    private fun allProfiles(focus: ProgramFocus): List<ProgramProfile> =
        ProgramGoal.entries.flatMap { goal ->
            (2..5).flatMap { days ->
                ProgramLocation.entries.flatMap { location ->
                    ProgramLevel.entries.flatMap { level ->
                        listOf(30, 65).map { age ->
                            profile(age = age, level = level, goal = goal, location = location, weeks = 8,
                                daysPerWeek = days).copy(focus = focus)
                        }
                    }
                }
            }
        }

    private fun ProgramProfile.describe() =
        "$goal $daysPerWeek days $location $level age=$age $focus"

    private fun StrengthSlot.targets(muscles: Set<MuscleGroup>): Boolean =
        byId.getValue(exerciseId).primaryMuscles.any { it in muscles }

    /** Fails with every share under 60 %: counted strength slots of each week. */
    private fun assertWeeklyShare(focus: ProgramFocus, muscles: Set<MuscleGroup>) {
        val problems = allProfiles(focus).flatMap { profile ->
            generate(profile).weeks.mapNotNull { week ->
                val slots = week.workouts.filterIsInstance<ProgramWorkout.Strength>().flatMap { it.slots }
                val hits = slots.count { it.targets(muscles) }
                // hits / slots >= 60 %, in integers.
                if (hits * 5 >= slots.size * 3) null else "${profile.describe()} week ${week.weekNumber}: $hits/${slots.size}"
            }
        }

        assertNoProblems("Weeks under 60 %", problems)
    }

    @Test
    fun `lower body puts at least 60 percent of each week's strength slots on the legs`() {
        assertWeeklyShare(ProgramFocus.LOWER_BODY, LOWER_BODY_MUSCLES)
    }

    @Test
    fun `upper body puts at least 60 percent of each week's strength slots on the upper body`() {
        assertWeeklyShare(ProgramFocus.UPPER_BODY, UPPER_BODY_MUSCLES)
    }

    @Test
    fun `core gives every strength workout at least two core slots, ending with two holds`() {
        val problems = allProfiles(ProgramFocus.CORE).flatMap { profile ->
            generate(profile).weeks.flatMap { week ->
                week.workouts.filterIsInstance<ProgramWorkout.Strength>().mapNotNull { workout ->
                    val core = workout.slots.count { it.targets(setOf(MuscleGroup.CORE)) }
                    val endsWithHolds = workout.slots.takeLast(2).all {
                        it.type == ProgramSlotType.TIME && it.targets(setOf(MuscleGroup.CORE))
                    }
                    if (core >= 2 && endsWithHolds) null
                    else "${profile.describe()} week ${week.weekNumber} ${workout.title}: $core core, holds at end=$endsWithHolds"
                }
            }
        }

        assertNoProblems("Core workouts", problems)
    }

    @Test
    fun `core keeps the session size of the balanced program`() {
        val problems = allProfiles(ProgramFocus.CORE).flatMap { profile ->
            val balanced = generate(profile.copy(focus = ProgramFocus.BALANCED)).weeks.first().workouts
            val core = generate(profile).weeks.first().workouts
            balanced.zip(core).mapNotNull { (b, c) ->
                if (b !is ProgramWorkout.Strength || c !is ProgramWorkout.Strength) return@mapNotNull null
                if (b.slots.size == c.slots.size) null else "${profile.describe()} ${c.title}: ${c.slots.size} vs ${b.slots.size}"
            }
        }

        assertNoProblems("Different session sizes", problems)
    }

    @Test
    fun `focused workouts never repeat an exercise and never skip a pattern`() {
        val problems = listOf(ProgramFocus.LOWER_BODY, ProgramFocus.UPPER_BODY, ProgramFocus.CORE).flatMap { focus ->
            allProfiles(focus).flatMap { profile ->
                val split = templates.split(profile.goal, profile.daysPerWeek, focus)!!
                val prescription = templates.prescription(profile.goal, profile.level)!!
                val size = if (profile.daysPerWeek <= 3) prescription.exercisesPerSession.last
                else prescription.exercisesPerSession.first
                val week1 = generate(profile).weeks.first().workouts
                split.days.zip(week1).mapNotNull { (day, workout) ->
                    if (day !is SplitDay.Strength || workout !is ProgramWorkout.Strength) return@mapNotNull null
                    val expected = minOf(size, templates.strengthDay(day.strengthDayId)!!.slots.size)
                    val ids = workout.slots.map { it.exerciseId }
                    when {
                        ids.size != ids.distinct().size -> "${profile.describe()} ${day.strengthDayId}: repeated $ids"
                        ids.size != expected -> "${profile.describe()} ${day.strengthDayId}: ${ids.size} of $expected slots"
                        else -> null
                    }
                }
            }
        }

        assertNoProblems("Focused workouts", problems)
    }

    @Test
    fun `the focus only changes strength days, never cardio`() {
        val problems = listOf(ProgramFocus.LOWER_BODY, ProgramFocus.UPPER_BODY, ProgramFocus.CORE).flatMap { focus ->
            allProfiles(focus).mapNotNull { profile ->
                fun cardio(p: GeneratedProgram) = p.weeks.map { week -> week.workouts.filterIsInstance<ProgramWorkout.Cardio>() }
                val balanced = cardio(generate(profile.copy(focus = ProgramFocus.BALANCED)))
                if (cardio(generate(profile)) == balanced) null else profile.describe()
            }
        }

        assertNoProblems("Cardio changed by the focus", problems)
    }

    @Test
    fun `prescriptions, progression and per side stay as in balanced`() {
        // Same exercise, same profile: identical sets, reps, rest and perSide whatever the focus.
        val base = profile(goal = ProgramGoal.GAIN_MUSCLE, location = ProgramLocation.HOME, daysPerWeek = 3, weeks = 8)
        val slotsByExercise = ProgramFocus.entries.associateWith { focus ->
            generate(base.copy(focus = focus)).weeks.flatMap { week ->
                week.workouts.filterIsInstance<ProgramWorkout.Strength>().flatMap { w ->
                    w.slots.map { (week.weekNumber to it.exerciseId) to it.copy(position = 0) }
                }
            }.toMap()
        }
        val balanced = slotsByExercise.getValue(ProgramFocus.BALANCED)

        var compared = 0
        ProgramFocus.entries.forEach { focus ->
            slotsByExercise.getValue(focus).forEach { (key, slot) ->
                val other = balanced[key] ?: return@forEach
                if (other.type != slot.type) return@forEach // the same exercise can be a hold in one day only
                compared++
                assertEquals("$focus $key", other, slot)
            }
        }
        assertTrue("nothing compared", compared > 0)
    }

    @Test
    fun `the default focus is balanced`() {
        val profile = profile()

        assertEquals(ProgramFocus.BALANCED, profile.focus)
        assertEquals(generate(profile), generate(profile.copy(focus = ProgramFocus.BALANCED)))
    }

    private fun assertNoProblems(title: String, problems: List<String>) {
        if (problems.isNotEmpty()) fail("$title (${problems.size}):\n" + problems.take(50).joinToString("\n"))
    }

    companion object {
        val LOWER_BODY_MUSCLES = setOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)
        val UPPER_BODY_MUSCLES =
            setOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.BICEPS, MuscleGroup.TRICEPS)
    }
}
