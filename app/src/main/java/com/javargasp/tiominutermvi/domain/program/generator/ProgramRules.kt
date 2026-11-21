package com.javargasp.tiominutermvi.domain.program.generator

import com.javargasp.tiominutermvi.domain.program.model.Equipment
import com.javargasp.tiominutermvi.domain.program.model.Impact
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.Mechanic
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile

/** Equipment available at home; the gym has everything. */
val HOME_EQUIPMENT: Set<Equipment> =
    setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELL, Equipment.BENCH, Equipment.NONE)

/** Mechanics a home-circuit exercise may have. */
val HOME_CIRCUIT_MECHANICS: Set<Mechanic> = setOf(Mechanic.CARDIO, Mechanic.COMPOUND, Mechanic.CORE)

/**
 * Whether [exercise] may go into a home circuit: one of [HOME_CIRCUIT_MECHANICS] and not
 * [ProgramExercise.perSide] (a circuit station is one short block with no switch cue).
 */
fun isCircuitEligible(exercise: ProgramExercise): Boolean =
    exercise.mechanic in HOME_CIRCUIT_MECHANICS && !exercise.perSide

/** BMI = kg / m². */
fun computeBmi(weightKg: Double, heightCm: Int): Double {
    val meters = heightCm / 100.0
    return weightKg / (meters * meters)
}

/** Low impact applies when BMI >= [ImpactRules.lowImpactMinBmi] or age >= [ImpactRules.lowImpactMinAge]. */
fun isLowImpact(profile: ProgramProfile, rules: ImpactRules): Boolean =
    computeBmi(profile.weightKg, profile.heightCm) >= rules.lowImpactMinBmi ||
        profile.age >= rules.lowImpactMinAge

/** Seniors get [ImpactRules.seniorExtraRestSeconds] more rest. */
fun isSenior(profile: ProgramProfile, rules: ImpactRules): Boolean = profile.age >= rules.seniorMinAge

/**
 * Whether the generator may use [exercise]: canonical (not a duplicate), available at
 * [location] with its equipment, not HIGH impact when [lowImpact], and not in
 * [ImpactRules.excludedForBeginnerOrLowImpact] for beginners or low impact.
 */
fun isEligible(
    exercise: ProgramExercise,
    location: ProgramLocation,
    lowImpact: Boolean,
    level: ProgramLevel,
    rules: ImpactRules
): Boolean {
    if (exercise.duplicateOf != null) return false
    if (location !in exercise.locations) return false
    if (location == ProgramLocation.HOME && exercise.equipment !in HOME_EQUIPMENT) return false
    if (lowImpact && exercise.impact == Impact.HIGH) return false
    val restricted = level == ProgramLevel.BEGINNER || lowImpact
    if (restricted && exercise.exerciseId in rules.excludedForBeginnerOrLowImpact) return false
    return true
}
