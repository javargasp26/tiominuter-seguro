package com.javargasp.tiominutermvi.domain.program

import com.javargasp.tiominutermvi.domain.program.ProgramTestAssets.profile
import com.javargasp.tiominutermvi.domain.program.generator.computeBmi
import com.javargasp.tiominutermvi.domain.program.generator.isEligible
import com.javargasp.tiominutermvi.domain.program.generator.isLowImpact
import com.javargasp.tiominutermvi.domain.program.generator.isSenior
import com.javargasp.tiominutermvi.domain.program.model.Equipment
import com.javargasp.tiominutermvi.domain.program.model.Impact
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.Mechanic
import com.javargasp.tiominutermvi.domain.program.model.MuscleGroup
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramRulesTest {

    private val rules = ImpactRules(
        lowImpactMinBmi = 30.0,
        lowImpactMinAge = 60,
        seniorMinAge = 60,
        seniorExtraRestSeconds = 15,
        excludedForBeginnerOrLowImpact = setOf(87)
    )

    @Test
    fun `bmi is kg over squared meters`() {
        assertEquals(22.86, computeBmi(70.0, 175), 0.01)
        assertEquals(30.0, computeBmi(86.7, 170), 0.01)
    }

    @Test
    fun `low impact from bmi 30 or age 60`() {
        assertFalse(isLowImpact(profile(weightKg = 86.0, heightCm = 170), rules)) // 29.76
        assertTrue(isLowImpact(profile(weightKg = 86.7, heightCm = 170), rules)) // 30.0
        assertFalse(isLowImpact(profile(age = 59), rules))
        assertTrue(isLowImpact(profile(age = 60), rules))
        assertTrue(isSenior(profile(age = 60), rules))
        assertFalse(isSenior(profile(age = 59, weightKg = 120.0), rules))
    }

    @Test
    fun `duplicates are never eligible`() {
        assertFalse(isEligible(exercise(duplicateOf = 1), ProgramLocation.GYM, false, ProgramLevel.ADVANCED, rules))
    }

    @Test
    fun `home allows only bodyweight, dumbbell, bench and no equipment`() {
        Equipment.entries.forEach { equipment ->
            val eligible = isEligible(
                exercise(equipment = equipment, locations = setOf(ProgramLocation.HOME, ProgramLocation.GYM)),
                ProgramLocation.HOME, false, ProgramLevel.ADVANCED, rules
            )
            val expected = equipment in setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELL, Equipment.BENCH, Equipment.NONE)
            assertEquals(equipment.name, expected, eligible)
        }
    }

    @Test
    fun `exercise must list the location`() {
        assertFalse(isEligible(exercise(locations = setOf(ProgramLocation.GYM)), ProgramLocation.HOME, false, ProgramLevel.ADVANCED, rules))
        assertTrue(isEligible(exercise(locations = setOf(ProgramLocation.GYM)), ProgramLocation.GYM, false, ProgramLevel.ADVANCED, rules))
    }

    @Test
    fun `high impact excluded only for low impact`() {
        val jump = exercise(impact = Impact.HIGH)
        assertTrue(isEligible(jump, ProgramLocation.GYM, false, ProgramLevel.BEGINNER, rules))
        assertFalse(isEligible(jump, ProgramLocation.GYM, true, ProgramLevel.BEGINNER, rules))
    }

    @Test
    fun `behind the neck press excluded for beginners and low impact`() {
        val press = exercise(id = 87)
        assertFalse(isEligible(press, ProgramLocation.GYM, false, ProgramLevel.BEGINNER, rules))
        assertFalse(isEligible(press, ProgramLocation.GYM, true, ProgramLevel.ADVANCED, rules))
        assertTrue(isEligible(press, ProgramLocation.GYM, false, ProgramLevel.INTERMEDIATE, rules))
    }

    private fun exercise(
        id: Int = 1,
        equipment: Equipment = Equipment.BARBELL,
        impact: Impact = Impact.LOW,
        locations: Set<ProgramLocation> = setOf(ProgramLocation.GYM),
        duplicateOf: Int? = null
    ) = ProgramExercise(
        exerciseId = id,
        name = "Exercise $id",
        nameEs = "Ejercicio $id",
        animation = "$id.gif",
        primaryMuscles = listOf(MuscleGroup.SHOULDERS),
        equipment = equipment,
        mechanic = Mechanic.COMPOUND,
        impact = impact,
        locations = locations,
        duplicateOf = duplicateOf
    )
}
