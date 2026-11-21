package com.javargasp.tiominutermvi.data.program.remote.dto

import kotlinx.serialization.Serializable

/**
 * Raw content of `program_templates.json`: everything the generator needs as data.
 * Enum values stay strings until the program mappers convert them.
 */
@Serializable
data class ProgramTemplatesDto(
    val splits: List<SplitDto>,
    val strengthDays: List<StrengthDayDto>,
    val prescriptions: List<PrescriptionDto>,
    val progression: ProgressionDto,
    val cardio: CardioTemplatesDto,
    val impact: ImpactRulesDto
)

/**
 * Week layout for one goal, number of days and focus (a ProgramFocus code): an ordered
 * list of days. Splits without `focus` are the BALANCED ones.
 */
@Serializable
data class SplitDto(
    val goal: String,
    val daysPerWeek: Int,
    val days: List<SplitDayDto>,
    val focus: String = "BALANCED"
)

/** `kind` is STRENGTH (with [strengthDayId]) or CARDIO. */
@Serializable
data class SplitDayDto(
    val kind: String,
    val strengthDayId: String? = null
)

/** A strength day: title and ordered slot patterns (most important first). */
@Serializable
data class StrengthDayDto(
    val id: String,
    val title: String,
    val slots: List<SlotPatternDto>
)

/**
 * One slot of a strength day: target muscle, preferred mechanic and candidate
 * exerciseIds in priority order. `type` TIME makes it a hold (e.g. plank).
 * `required` keeps the slot even when the session size cuts the day short.
 */
@Serializable
data class SlotPatternDto(
    val muscle: String,
    val mechanic: String,
    val type: String = "REPS",
    val candidates: List<Int>,
    val required: Boolean = false
)

@Serializable
data class RangeDto(val min: Int, val max: Int)

/** Sets, reps, rest and session size for one goal and level. */
@Serializable
data class PrescriptionDto(
    val goal: String,
    val level: String,
    val exercisesPerSession: RangeDto,
    val setsCompound: Int,
    val setsIsolation: Int,
    val repsCompound: RangeDto,
    val repsIsolation: RangeDto,
    val restCompoundSeconds: Int,
    val restIsolationSeconds: Int,
    val holdSeconds: Int
)

@Serializable
data class ProgressionDto(
    val repStepPerWeek: Int,
    val maxSets: Int,
    val holdStepSeconds: Int,
    val deloadEveryWeeks: Int,
    val deloadMinProgramWeeks: Int,
    val deloadSetsDrop: Int,
    val deloadMinSets: Int,
    val deloadCardioFactor: Double
)

@Serializable
data class CardioTemplatesDto(
    /** Cardio formats to rotate through, per location (e.g. HOME: OUTDOOR_RUN, HOME_CIRCUIT). */
    val rotation: List<CardioRotationDto>,
    val outdoorRun: OutdoorRunTemplateDto,
    val treadmill: List<TreadmillTemplateDto>,
    val treadmillLowImpact: TreadmillLowImpactDto,
    val homeCircuit: HomeCircuitTemplateDto
)

@Serializable
data class CardioRotationDto(val location: String, val types: List<String>)

/** Start distance per level (running and brisk walk) and weekly increase. */
@Serializable
data class OutdoorRunTemplateDto(
    val weeklyIncreasePercent: Int,
    val levels: List<OutdoorRunLevelDto>
)

@Serializable
data class OutdoorRunLevelDto(
    val level: String,
    val runStartKm: Double,
    val walkStartKm: Double
)

/**
 * Treadmill session for one level: warm-up, [baseIntervals] (+[intervalsPerWeek],
 * capped at [maxIntervals]) blocks of jog + walk, cool-down. Jog blocks cycle through [inclines].
 */
@Serializable
data class TreadmillTemplateDto(
    val level: String,
    val warmUpSeconds: Int,
    val coolDownSeconds: Int,
    val jogSpeedKmh: Double,
    val walkSpeedKmh: Double,
    val jogSeconds: Int,
    val walkSeconds: Int,
    val inclines: List<Double>,
    val baseIntervals: Int,
    val intervalsPerWeek: Int,
    val maxIntervals: Int
)

/** Low-impact treadmill: walking only, alternating a low and a high incline. */
@Serializable
data class TreadmillLowImpactDto(
    val warmUpSeconds: Int,
    val coolDownSeconds: Int,
    val speedKmh: Double,
    val lowIncline: Double,
    val highIncline: Double,
    val lowSeconds: Int,
    val highSeconds: Int,
    val baseIntervals: List<LevelIntDto>,
    val intervalsPerWeek: Int,
    val maxIntervals: Int
)

@Serializable
data class LevelIntDto(val level: String, val value: Int)

@Serializable
data class HomeCircuitTemplateDto(
    val levels: List<HomeCircuitLevelDto>,
    val restBetweenRoundsSeconds: Int,
    val candidates: List<Int>,
    val lowImpactCandidates: List<Int>
)

@Serializable
data class HomeCircuitLevelDto(
    val level: String,
    val rounds: Int,
    val workSeconds: Int,
    val restSeconds: Int,
    val exercises: Int
)

/** When a program becomes low impact, and what that changes. */
@Serializable
data class ImpactRulesDto(
    val lowImpactMinBmi: Double,
    val lowImpactMinAge: Int,
    val seniorMinAge: Int,
    val seniorExtraRestSeconds: Int,
    val excludedForBeginnerOrLowImpact: List<Int>
)
