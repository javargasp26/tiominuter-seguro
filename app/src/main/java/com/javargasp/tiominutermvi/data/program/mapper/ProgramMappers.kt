package com.javargasp.tiominutermvi.data.program.mapper

import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramExerciseDto
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramTemplatesDto
import com.javargasp.tiominutermvi.data.program.remote.dto.RangeDto
import com.javargasp.tiominutermvi.domain.program.model.CardioTemplates
import com.javargasp.tiominutermvi.domain.program.model.CardioType
import com.javargasp.tiominutermvi.domain.program.model.Equipment
import com.javargasp.tiominutermvi.domain.program.model.HomeCircuitLevel
import com.javargasp.tiominutermvi.domain.program.model.HomeCircuitTemplate
import com.javargasp.tiominutermvi.domain.program.model.Impact
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.Mechanic
import com.javargasp.tiominutermvi.domain.program.model.MuscleGroup
import com.javargasp.tiominutermvi.domain.program.model.OutdoorRunTemplate
import com.javargasp.tiominutermvi.domain.program.model.Prescription
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramSplit
import com.javargasp.tiominutermvi.domain.program.model.ProgramTemplates
import com.javargasp.tiominutermvi.domain.program.model.ProgressionRules
import com.javargasp.tiominutermvi.domain.program.model.SlotPattern
import com.javargasp.tiominutermvi.domain.program.model.SplitDay
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTemplate
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTitle
import com.javargasp.tiominutermvi.domain.program.model.TreadmillLowImpactTemplate
import com.javargasp.tiominutermvi.domain.program.model.TreadmillTemplate

/**
 * Pure mappers from the program DTOs to the domain models.
 *
 * - Catalog: an entry with an unknown enum value is skipped and logged, so bad
 *   remote data cannot crash the app.
 * - Templates: they are one consistent document, so an unknown value throws
 *   [IllegalArgumentException] (the template validation test keeps the asset valid).
 */

// ---------------------------------------------------------------------------
// Catalog
// ---------------------------------------------------------------------------

fun List<ProgramExerciseDto>.toProgramCatalog(): List<ProgramExercise> = mapNotNull { dto ->
    dto.toDomainOrNull() ?: run {
        println("ProgramMappers: skipping exercise ${dto.exerciseId} with an unknown enum value")
        null
    }
}

fun ProgramExerciseDto.toDomainOrNull(): ProgramExercise? {
    val muscles = primaryMuscles.map { enumOrNull<MuscleGroup>(it) ?: return null }
    val places = locations.map { enumOrNull<ProgramLocation>(it) ?: return null }.toSet()
    return ProgramExercise(
        exerciseId = exerciseId,
        name = name,
        nameEs = nameEs,
        animation = animation,
        primaryMuscles = muscles,
        equipment = enumOrNull<Equipment>(equipment) ?: return null,
        mechanic = enumOrNull<Mechanic>(mechanic) ?: return null,
        impact = enumOrNull<Impact>(impact) ?: return null,
        locations = places,
        perSide = perSide,
        duplicateOf = duplicateOf
    )
}

// ---------------------------------------------------------------------------
// Templates
// ---------------------------------------------------------------------------

fun ProgramTemplatesDto.toDomain(): ProgramTemplates = ProgramTemplates(
    splits = splits.map { split ->
        ProgramSplit(
            goal = enumOf(split.goal),
            daysPerWeek = split.daysPerWeek,
            focus = ProgramFocus.fromCode(split.focus)
                ?: throw IllegalArgumentException("Unknown ProgramFocus '${split.focus}'"),
            days = split.days.map { day ->
                when (day.kind) {
                    "STRENGTH" -> SplitDay.Strength(
                        requireNotNull(day.strengthDayId) { "STRENGTH day without strengthDayId" }
                    )
                    "CARDIO" -> SplitDay.Cardio
                    else -> throw IllegalArgumentException("Unknown split day kind '${day.kind}'")
                }
            }
        )
    },
    strengthDays = strengthDays.map { day ->
        StrengthDayTemplate(
            id = day.id,
            title = enumOf<StrengthDayTitle>(day.title),
            slots = day.slots.map { slot ->
                SlotPattern(
                    muscle = enumOf(slot.muscle),
                    mechanic = enumOf(slot.mechanic),
                    type = enumOf<ProgramSlotType>(slot.type),
                    candidates = slot.candidates,
                    required = slot.required
                )
            }
        )
    },
    prescriptions = prescriptions.map { p ->
        Prescription(
            goal = enumOf(p.goal),
            level = enumOf(p.level),
            exercisesPerSession = p.exercisesPerSession.toRange(),
            setsCompound = p.setsCompound,
            setsIsolation = p.setsIsolation,
            repsCompound = p.repsCompound.toRange(),
            repsIsolation = p.repsIsolation.toRange(),
            restCompoundSeconds = p.restCompoundSeconds,
            restIsolationSeconds = p.restIsolationSeconds,
            holdSeconds = p.holdSeconds
        )
    },
    progression = with(progression) {
        ProgressionRules(
            repStepPerWeek = repStepPerWeek,
            maxSets = maxSets,
            holdStepSeconds = holdStepSeconds,
            deloadEveryWeeks = deloadEveryWeeks,
            deloadMinProgramWeeks = deloadMinProgramWeeks,
            deloadSetsDrop = deloadSetsDrop,
            deloadMinSets = deloadMinSets,
            deloadCardioFactor = deloadCardioFactor
        )
    },
    cardio = with(cardio) {
        CardioTemplates(
            rotation = rotation.associate { r ->
                enumOf<ProgramLocation>(r.location) to r.types.map { enumOf<CardioType>(it) }
            },
            outdoorRun = OutdoorRunTemplate(
                weeklyIncreasePercent = outdoorRun.weeklyIncreasePercent,
                runStartKm = outdoorRun.levels.associate { enumOf<ProgramLevel>(it.level) to it.runStartKm },
                walkStartKm = outdoorRun.levels.associate { enumOf<ProgramLevel>(it.level) to it.walkStartKm }
            ),
            treadmill = treadmill.associate { t ->
                enumOf<ProgramLevel>(t.level) to TreadmillTemplate(
                    warmUpSeconds = t.warmUpSeconds,
                    coolDownSeconds = t.coolDownSeconds,
                    jogSpeedKmh = t.jogSpeedKmh,
                    walkSpeedKmh = t.walkSpeedKmh,
                    jogSeconds = t.jogSeconds,
                    walkSeconds = t.walkSeconds,
                    inclines = t.inclines,
                    baseIntervals = t.baseIntervals,
                    intervalsPerWeek = t.intervalsPerWeek,
                    maxIntervals = t.maxIntervals
                )
            },
            treadmillLowImpact = with(treadmillLowImpact) {
                TreadmillLowImpactTemplate(
                    warmUpSeconds = warmUpSeconds,
                    coolDownSeconds = coolDownSeconds,
                    speedKmh = speedKmh,
                    lowIncline = lowIncline,
                    highIncline = highIncline,
                    lowSeconds = lowSeconds,
                    highSeconds = highSeconds,
                    baseIntervals = baseIntervals.associate { enumOf<ProgramLevel>(it.level) to it.value },
                    intervalsPerWeek = intervalsPerWeek,
                    maxIntervals = maxIntervals
                )
            },
            homeCircuit = HomeCircuitTemplate(
                levels = homeCircuit.levels.associate { l ->
                    enumOf<ProgramLevel>(l.level) to HomeCircuitLevel(
                        rounds = l.rounds,
                        workSeconds = l.workSeconds,
                        restSeconds = l.restSeconds,
                        exercises = l.exercises
                    )
                },
                restBetweenRoundsSeconds = homeCircuit.restBetweenRoundsSeconds,
                candidates = homeCircuit.candidates,
                lowImpactCandidates = homeCircuit.lowImpactCandidates
            )
        )
    },
    impact = with(impact) {
        ImpactRules(
            lowImpactMinBmi = lowImpactMinBmi,
            lowImpactMinAge = lowImpactMinAge,
            seniorMinAge = seniorMinAge,
            seniorExtraRestSeconds = seniorExtraRestSeconds,
            excludedForBeginnerOrLowImpact = excludedForBeginnerOrLowImpact.toSet()
        )
    }
)

private fun RangeDto.toRange(): IntRange = min..max

private inline fun <reified E : Enum<E>> enumOrNull(value: String): E? =
    enumValues<E>().firstOrNull { it.name == value }

private inline fun <reified E : Enum<E>> enumOf(value: String): E =
    enumOrNull<E>(value) ?: throw IllegalArgumentException("Unknown ${E::class.simpleName} '$value'")

