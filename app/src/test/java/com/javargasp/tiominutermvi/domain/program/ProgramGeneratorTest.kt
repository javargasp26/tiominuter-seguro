package com.javargasp.tiominutermvi.domain.program

import com.javargasp.tiominutermvi.domain.program.ProgramTestAssets.catalog
import com.javargasp.tiominutermvi.domain.program.ProgramTestAssets.profile
import com.javargasp.tiominutermvi.domain.program.ProgramTestAssets.templates
import com.javargasp.tiominutermvi.domain.program.generator.ProgramGenerator
import com.javargasp.tiominutermvi.domain.program.model.Equipment
import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.Impact
import com.javargasp.tiominutermvi.domain.program.model.Mechanic
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTitle
import com.javargasp.tiominutermvi.domain.program.model.StrengthSlot
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegmentLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProgramGenerator] with the real catalog and templates.
 */
class ProgramGeneratorTest {

    private val byId = catalog.associateBy { it.exerciseId }

    private fun generate(profile: ProgramProfile): GeneratedProgram =
        ProgramGenerator.generate(profile, catalog, templates)

    private fun allProfiles(weeks: Int = 6, age: Int = 30, weightKg: Double = 70.0): List<ProgramProfile> =
        ProgramGoal.entries.flatMap { goal ->
            (2..5).flatMap { days ->
                ProgramLocation.entries.flatMap { location ->
                    ProgramLevel.entries.map { level ->
                        profile(age = age, weightKg = weightKg, level = level, goal = goal,
                            location = location, weeks = weeks, daysPerWeek = days)
                    }
                }
            }
        }

    private fun GeneratedProgram.strength() =
        weeks.flatMap { week -> week.workouts.filterIsInstance<ProgramWorkout.Strength>().map { week to it } }

    private fun ProgramWorkout.exerciseIds(): List<Int> = when (this) {
        is ProgramWorkout.Strength -> slots.map { it.exerciseId }
        is ProgramWorkout.HomeCircuit -> exerciseIds
        else -> emptyList()
    }

    // ------------------------------------------------------------------
    // Shape
    // ------------------------------------------------------------------

    @Test
    fun `every goal, days, location and level gives the right weeks and workouts`() {
        (4..12).forEach { weeks ->
            allProfiles(weeks).forEach { p ->
                val program = generate(p)
                assertEquals("$p", weeks, program.weeks.size)
                assertEquals("$p", (1..weeks).toList(), program.weeks.map { it.weekNumber })
                program.weeks.forEach { week ->
                    assertEquals("$p week ${week.weekNumber}", p.daysPerWeek, week.workouts.size)
                }
            }
        }
    }

    @Test
    fun `strength workouts have the session size of the level`() {
        allProfiles().forEach { p ->
            val range = templates.prescription(p.goal, p.level)!!.exercisesPerSession
            val expected = if (p.daysPerWeek <= 3) range.last else range.first
            generate(p).strength().forEach { (_, workout) ->
                assertEquals("$p ${workout.title}", expected, workout.slots.size)
                assertEquals((0 until expected).toList(), workout.slots.map { it.position })
            }
        }
    }

    @Test
    fun `gain muscle uses the requested splits`() {
        fun titles(days: Int) = generate(profile(daysPerWeek = days)).weeks.first().workouts
            .map { (it as ProgramWorkout.Strength).title }

        assertEquals(listOf(StrengthDayTitle.FULL_BODY_A, StrengthDayTitle.FULL_BODY_B), titles(2))
        assertEquals(listOf(StrengthDayTitle.UPPER_A, StrengthDayTitle.LOWER_A, StrengthDayTitle.UPPER_B, StrengthDayTitle.LOWER_B), titles(4))
        assertEquals(
            listOf(StrengthDayTitle.PUSH, StrengthDayTitle.PULL, StrengthDayTitle.LEGS, StrengthDayTitle.UPPER_A, StrengthDayTitle.LOWER_A),
            titles(5)
        )
    }

    @Test
    fun `lose fat adds cardio days`() {
        val cardioPerWeek = (2..5).associateWith { days ->
            generate(profile(goal = ProgramGoal.LOSE_FAT, daysPerWeek = days)).weeks.first().workouts
                .count { it is ProgramWorkout.Cardio }
        }

        assertEquals(mapOf(2 to 0, 3 to 1, 4 to 1, 5 to 2), cardioPerWeek)
    }

    // ------------------------------------------------------------------
    // Exercise choice
    // ------------------------------------------------------------------

    @Test
    fun `no exercise twice in one workout`() {
        allProfiles(weeks = 8).forEach { p ->
            generate(p).weeks.forEach { week ->
                week.workouts.forEach { workout ->
                    val ids = workout.exerciseIds()
                    assertEquals("$p week ${week.weekNumber}: $ids", ids.size, ids.toSet().size)
                }
            }
        }
    }

    @Test
    fun `duplicates and out-of-location exercises are never used`() {
        allProfiles().forEach { p ->
            generate(p).weeks.flatMap { it.workouts }.flatMap { it.exerciseIds() }.forEach { id ->
                val exercise = byId.getValue(id)
                assertEquals("$p exerciseId $id", null, exercise.duplicateOf)
                assertTrue("$p exerciseId $id", p.location in exercise.locations)
                if (p.location == ProgramLocation.HOME) {
                    assertTrue("$p exerciseId $id", exercise.equipment in setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELL, Equipment.BENCH, Equipment.NONE))
                }
            }
        }
    }

    @Test
    fun `A, B and C days use different exercises`() {
        val week = generate(profile(daysPerWeek = 3)).weeks.first()
        val (a, b, c) = week.workouts.map { it.exerciseIds().toSet() }

        assertTrue(a != b && b != c && a != c)
    }

    @Test
    fun `beginners never get the behind-the-neck press`() {
        allProfiles().filter { it.level == ProgramLevel.BEGINNER }.forEach { p ->
            assertFalse("$p", generate(p).weeks.flatMap { it.workouts }.flatMap { it.exerciseIds() }.contains(87))
        }
    }

    @Test
    fun `advanced gym upper B can use the behind-the-neck press, low impact cannot`() {
        val advanced = profile(level = ProgramLevel.ADVANCED, daysPerWeek = 4)
        val lowImpact = advanced.copy(age = 62)

        assertTrue(generate(advanced).weeks.first().workouts.flatMap { it.exerciseIds() }.contains(87))
        assertFalse(generate(lowImpact).weeks.flatMap { it.workouts }.flatMap { it.exerciseIds() }.contains(87))
    }

    // ------------------------------------------------------------------
    // Prescription
    // ------------------------------------------------------------------

    private fun firstWeekSlots(p: ProgramProfile): List<StrengthSlot> =
        generate(p).strength().first().second.slots

    private fun StrengthSlot.mechanic(): Mechanic = byId.getValue(exerciseId).mechanic

    @Test
    fun `prescription by goal and level in week 1`() {
        data class Expected(val setsC: Int, val setsI: Int, val repsC: Int, val repsI: Int, val restC: Int, val restI: Int)
        val expected = mapOf(
            (ProgramGoal.GAIN_MUSCLE to ProgramLevel.BEGINNER) to Expected(3, 3, 6, 8, 90, 60),
            (ProgramGoal.GAIN_MUSCLE to ProgramLevel.INTERMEDIATE) to Expected(4, 3, 6, 8, 90, 60),
            (ProgramGoal.GAIN_MUSCLE to ProgramLevel.ADVANCED) to Expected(4, 4, 6, 8, 120, 60),
            (ProgramGoal.LOSE_FAT to ProgramLevel.BEGINNER) to Expected(2, 2, 12, 12, 45, 30),
            (ProgramGoal.LOSE_FAT to ProgramLevel.INTERMEDIATE) to Expected(3, 3, 12, 12, 45, 30),
            (ProgramGoal.LOSE_FAT to ProgramLevel.ADVANCED) to Expected(3, 3, 12, 12, 45, 30)
        )
        expected.forEach { (key, e) ->
            val (goal, level) = key
            val slots = firstWeekSlots(profile(goal = goal, level = level, daysPerWeek = 3))
            slots.filter { it.type == ProgramSlotType.REPS }.forEach { slot ->
                val compound = slot.mechanic() == Mechanic.COMPOUND
                val label = "$goal $level exerciseId ${slot.exerciseId}"
                assertEquals(label, if (compound) e.setsC else e.setsI, slot.sets)
                assertEquals(label, List(slot.sets) { if (compound) e.repsC else e.repsI }, slot.reps)
                assertEquals(label, if (compound) e.restC else e.restI, slot.restSeconds)
            }
        }
    }

    @Test
    fun `core holds are TIME slots with the level's seconds`() {
        mapOf(ProgramLevel.BEGINNER to 30, ProgramLevel.INTERMEDIATE to 40, ProgramLevel.ADVANCED to 50).forEach { (level, seconds) ->
            val plank = firstWeekSlots(profile(level = level)).single { it.type == ProgramSlotType.TIME }

            assertEquals(4, plank.exerciseId)
            assertTrue(plank.reps.all { it == seconds })
        }
    }

    @Test
    fun `age 60 or more adds 15 seconds of rest`() {
        val young = firstWeekSlots(profile(age = 59))
        val senior = firstWeekSlots(profile(age = 60))

        assertEquals(young.map { it.restSeconds + 15 }, senior.map { it.restSeconds })
    }

    // ------------------------------------------------------------------
    // Progression and deload
    // ------------------------------------------------------------------

    private fun compoundSlotByWeek(p: ProgramProfile): List<StrengthSlot> =
        generate(p).weeks.map { week ->
            (week.workouts.first() as ProgramWorkout.Strength).slots.first { it.mechanic() == Mechanic.COMPOUND && it.type == ProgramSlotType.REPS }
        }

    @Test
    fun `reps go up each week, then one more set with reps back to the bottom`() {
        val slots = compoundSlotByWeek(profile(level = ProgramLevel.BEGINNER, weeks = 12))

        // week:            1  2  3  4(D) 5  6  7  8(D) 9  10 11 12(D)
        val expectedReps = listOf(6, 7, 8, 6, 9, 10, 6, 6, 7, 8, 9, 6)
        val expectedSets = listOf(3, 3, 3, 2, 3, 3, 4, 3, 4, 4, 4, 3)
        assertEquals(expectedReps, slots.map { it.reps.first() })
        assertEquals(expectedSets, slots.map { it.sets })
    }

    @Test
    fun `sets never go above 5`() {
        allProfiles(weeks = 12).forEach { p ->
            generate(p).strength().forEach { (_, workout) ->
                workout.slots.forEach { assertTrue("$p", it.sets in 2..5) }
            }
        }
    }

    @Test
    fun `deload every 4th week only for programs of 6 weeks or more`() {
        fun deloads(weeks: Int) = generate(profile(weeks = weeks)).weeks.filter { it.isDeload }.map { it.weekNumber }

        assertEquals(emptyList<Int>(), deloads(4))
        assertEquals(emptyList<Int>(), deloads(5))
        assertEquals(listOf(4), deloads(6))
        assertEquals(listOf(4, 8), deloads(8))
        assertEquals(listOf(4, 8, 12), deloads(12))
    }

    @Test
    fun `TIME holds add 5 seconds per week and drop back on deload`() {
        val holds = generate(profile(level = ProgramLevel.BEGINNER, weeks = 8)).weeks.map { week ->
            (week.workouts.first() as ProgramWorkout.Strength).slots.single { it.type == ProgramSlotType.TIME }
        }

        assertEquals(listOf(30, 35, 40, 30, 45, 50, 55, 30), holds.map { it.reps.first() })
        assertEquals(listOf(3, 3, 3, 2, 3, 3, 3, 2), holds.map { it.sets })
    }

    @Test
    fun `outdoor run grows 10 percent per week and drops 30 percent on deload`() {
        val runs = generate(profile(goal = ProgramGoal.LOSE_FAT, location = ProgramLocation.HOME,
            level = ProgramLevel.BEGINNER, daysPerWeek = 3, weeks = 6))
            .weeks.map { it.workouts.filterIsInstance<ProgramWorkout.Cardio>().single() }

        // HOME alternates run / circuit: runs in weeks 1, 3, 5.
        assertTrue(runs[0] is ProgramWorkout.OutdoorRun && runs[1] is ProgramWorkout.HomeCircuit)
        val week1 = runs[0] as ProgramWorkout.OutdoorRun
        val week3 = runs[2] as ProgramWorkout.OutdoorRun
        assertEquals(1.5, week1.distanceKm, 0.001)
        assertEquals(1.8, week3.distanceKm, 0.001) // 1.5 x 1.1^2 = 1.815
        val deloadCircuit = runs[3] as ProgramWorkout.HomeCircuit
        assertEquals(2, deloadCircuit.rounds) // 3 x 0.7
        assertFalse(week1.walk)
    }

    @Test
    fun `treadmill intervals increase week by week`() {
        val sessions = generate(profile(goal = ProgramGoal.LOSE_FAT, location = ProgramLocation.GYM,
            level = ProgramLevel.INTERMEDIATE, daysPerWeek = 3, weeks = 6))
            .weeks.map { it.workouts.filterIsInstance<ProgramWorkout.Treadmill>().single() }
        val jogs = sessions.map { s -> s.segments.count { it.label == TreadmillSegmentLabel.JOG } }

        assertEquals(listOf(5, 6, 7, 6, 8, 9), jogs) // week 4 deload: 8 x 0.7
        sessions.forEach { s ->
            assertEquals(TreadmillSegmentLabel.WARM_UP, s.segments.first().label)
            assertEquals(300, s.segments.first().durationSeconds)
            assertEquals(TreadmillSegmentLabel.COOL_DOWN, s.segments.last().label)
            assertTrue(s.segments.filter { it.label == TreadmillSegmentLabel.JOG }.all { it.speedKmh == 8.5 && it.inclinePercent in 1.0..8.0 })
        }
    }

    // ------------------------------------------------------------------
    // Low impact
    // ------------------------------------------------------------------

    @Test
    fun `low impact by bmi or age removes high impact exercises`() {
        val heavy = allProfiles(weightKg = 100.0) // BMI 32.7
        val senior = allProfiles(age = 65)
        (heavy + senior).forEach { p ->
            generate(p).weeks.flatMap { it.workouts }.flatMap { it.exerciseIds() }.forEach { id ->
                assertEquals("$p exerciseId $id", Impact.LOW, byId.getValue(id).impact)
            }
        }
    }

    @Test
    fun `normal impact home circuit can use jumping exercises`() {
        val circuit = generate(profile(goal = ProgramGoal.LOSE_FAT, location = ProgramLocation.HOME, daysPerWeek = 3))
            .weeks[1].workouts.filterIsInstance<ProgramWorkout.HomeCircuit>().single()

        assertTrue(circuit.exerciseIds.any { byId.getValue(it).impact == Impact.HIGH })
        assertEquals(6, circuit.exerciseIds.size)
        assertEquals(4, circuit.rounds)
    }

    @Test
    fun `low impact switches outdoor run to brisk walk`() {
        val run = generate(profile(goal = ProgramGoal.LOSE_FAT, location = ProgramLocation.HOME, weightKg = 100.0,
            level = ProgramLevel.BEGINNER))
            .weeks.first().workouts.filterIsInstance<ProgramWorkout.OutdoorRun>().single()

        assertTrue(run.walk)
        assertEquals(2.0, run.distanceKm, 0.001)
    }

    @Test
    fun `low impact treadmill is incline walking only`() {
        val session = generate(profile(goal = ProgramGoal.LOSE_FAT, location = ProgramLocation.GYM, age = 65))
            .weeks.first().workouts.filterIsInstance<ProgramWorkout.Treadmill>().single()

        assertTrue(session.segments.all { it.speedKmh == 5.0 })
        assertTrue(session.segments.none { it.label == TreadmillSegmentLabel.JOG })
        assertEquals(setOf(2.0, 8.0), session.segments.map { it.inclinePercent }.toSet())
    }

    @Test
    fun `gym cardio is always treadmill, home alternates run and circuit`() {
        val gym = generate(profile(goal = ProgramGoal.LOSE_FAT, location = ProgramLocation.GYM, daysPerWeek = 5))
        val home = generate(profile(goal = ProgramGoal.LOSE_FAT, location = ProgramLocation.HOME, daysPerWeek = 5))

        assertTrue(gym.weeks.flatMap { it.workouts }.filterIsInstance<ProgramWorkout.Cardio>().all { it is ProgramWorkout.Treadmill })
        val homeCardio = home.weeks.flatMap { it.workouts }.filterIsInstance<ProgramWorkout.Cardio>()
        homeCardio.forEachIndexed { index, cardio ->
            if (index % 2 == 0) assertTrue(cardio is ProgramWorkout.OutdoorRun) else assertTrue(cardio is ProgramWorkout.HomeCircuit)
        }
    }

    // ------------------------------------------------------------------
    // Determinism and robustness
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Per-side exercises
    // ------------------------------------------------------------------

    @Test
    fun `every slot carries perSide from the catalog`() {
        val slots = allProfiles().flatMap { p -> generate(p).strength().flatMap { (_, w) -> w.slots } }

        assertTrue(slots.all { it.perSide == byId.getValue(it.exerciseId).perSide })
        assertTrue(slots.any { it.perSide && it.type == ProgramSlotType.TIME }) // side plank
        assertTrue(slots.any { it.perSide && it.type == ProgramSlotType.REPS })
    }

    @Test
    fun `perSide does not change sets, reps or rest`() {
        val bilateralCatalog = catalog.map { it.copy(perSide = false) }

        allProfiles().forEach { p ->
            val withSides = generate(p)
            val withoutSides = ProgramGenerator.generate(p, bilateralCatalog, templates)
            val normalized = withSides.copy(
                weeks = withSides.weeks.map { week ->
                    week.copy(workouts = week.workouts.map { w ->
                        if (w is ProgramWorkout.Strength) w.copy(slots = w.slots.map { it.copy(perSide = false) }) else w
                    })
                }
            )
            assertEquals(withoutSides, normalized)
        }
    }

    @Test
    fun `home circuits never use per-side exercises`() {
        val circuits = (allProfiles() + allProfiles(age = 65) + allProfiles(weightKg = 100.0))
            .flatMap { p -> generate(p).weeks.flatMap { it.workouts.filterIsInstance<ProgramWorkout.HomeCircuit>() } }

        assertTrue(circuits.isNotEmpty())
        assertTrue(circuits.all { c -> c.exerciseIds.none { byId.getValue(it).perSide } })
    }

    @Test
    fun `a per-side circuit candidate is excluded by the generator`() {
        // Squats (2) is a circuit candidate; mark it per side.
        val sidedCatalog = catalog.map { if (it.exerciseId == 2) it.copy(perSide = true) else it }
        val p = profile(goal = ProgramGoal.LOSE_FAT, location = ProgramLocation.HOME, daysPerWeek = 3)
        val circuit = generate(p).weeks[1].workouts.filterIsInstance<ProgramWorkout.HomeCircuit>().single()
        assertTrue(2 in circuit.exerciseIds)

        val sidedCircuit = ProgramGenerator.generate(p, sidedCatalog, templates)
            .weeks[1].workouts.filterIsInstance<ProgramWorkout.HomeCircuit>().single()

        assertFalse(2 in sidedCircuit.exerciseIds)
        assertEquals(circuit.exerciseIds.size, sidedCircuit.exerciseIds.size)
    }

    @Test
    fun `same input gives the same program`() {
        allProfiles().forEach { p -> assertEquals(generate(p), generate(p)) }
    }

    @Test
    fun `a pattern without candidates is skipped without crashing`() {
        val emptyCatalog = catalog.filter { it.exerciseId != 4 && it.exerciseId != 120 } // no planks

        val program = ProgramGenerator.generate(profile(level = ProgramLevel.BEGINNER), emptyCatalog, templates)

        assertTrue(program.strength().all { (_, w) -> w.slots.none { it.type == ProgramSlotType.TIME } })
        assertEquals(5, program.strength().first().second.slots.size) // later patterns fill the session
    }
}
