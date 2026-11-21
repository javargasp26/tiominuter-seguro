package com.javargasp.tiominutermvi.domain.program.model

/**
 * Everything the generator needs, loaded as data from `program_templates.json`.
 * Look-ups return null when a combination is missing; the template validation
 * test makes sure the shipped data is complete.
 */
data class ProgramTemplates(
    val splits: List<ProgramSplit>,
    val strengthDays: List<StrengthDayTemplate>,
    val prescriptions: List<Prescription>,
    val progression: ProgressionRules,
    val cardio: CardioTemplates,
    val impact: ImpactRules
) {
    fun split(goal: ProgramGoal, daysPerWeek: Int, focus: ProgramFocus = ProgramFocus.DEFAULT): ProgramSplit? =
        splits.firstOrNull { it.goal == goal && it.daysPerWeek == daysPerWeek && it.focus == focus }

    fun strengthDay(id: String): StrengthDayTemplate? = strengthDays.firstOrNull { it.id == id }

    fun prescription(goal: ProgramGoal, level: ProgramLevel): Prescription? =
        prescriptions.firstOrNull { it.goal == goal && it.level == level }
}

/** Week layout for one goal, number of training days and focus. */
data class ProgramSplit(
    val goal: ProgramGoal,
    val daysPerWeek: Int,
    val days: List<SplitDay>,
    val focus: ProgramFocus = ProgramFocus.DEFAULT
)

sealed interface SplitDay {
    data class Strength(val strengthDayId: String) : SplitDay
    data object Cardio : SplitDay
}

data class StrengthDayTemplate(
    val id: String,
    val title: StrengthDayTitle,
    val slots: List<SlotPattern>
)

/**
 * Target muscle + preferred mechanic + candidate exerciseIds in priority order.
 * A [required] pattern always gets a place in the workout, even when the session size
 * cuts the day short (e.g. the closing core holds of the CORE focus).
 */
data class SlotPattern(
    val muscle: MuscleGroup,
    val mechanic: Mechanic,
    val type: ProgramSlotType,
    val candidates: List<Int>,
    val required: Boolean = false
)

data class Prescription(
    val goal: ProgramGoal,
    val level: ProgramLevel,
    val exercisesPerSession: IntRange,
    val setsCompound: Int,
    val setsIsolation: Int,
    val repsCompound: IntRange,
    val repsIsolation: IntRange,
    val restCompoundSeconds: Int,
    val restIsolationSeconds: Int,
    val holdSeconds: Int
)

data class ProgressionRules(
    val repStepPerWeek: Int,
    val maxSets: Int,
    val holdStepSeconds: Int,
    val deloadEveryWeeks: Int,
    val deloadMinProgramWeeks: Int,
    val deloadSetsDrop: Int,
    val deloadMinSets: Int,
    val deloadCardioFactor: Double
)

data class CardioTemplates(
    val rotation: Map<ProgramLocation, List<CardioType>>,
    val outdoorRun: OutdoorRunTemplate,
    val treadmill: Map<ProgramLevel, TreadmillTemplate>,
    val treadmillLowImpact: TreadmillLowImpactTemplate,
    val homeCircuit: HomeCircuitTemplate
)

data class OutdoorRunTemplate(
    val weeklyIncreasePercent: Int,
    val runStartKm: Map<ProgramLevel, Double>,
    val walkStartKm: Map<ProgramLevel, Double>
)

data class TreadmillTemplate(
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

data class TreadmillLowImpactTemplate(
    val warmUpSeconds: Int,
    val coolDownSeconds: Int,
    val speedKmh: Double,
    val lowIncline: Double,
    val highIncline: Double,
    val lowSeconds: Int,
    val highSeconds: Int,
    val baseIntervals: Map<ProgramLevel, Int>,
    val intervalsPerWeek: Int,
    val maxIntervals: Int
)

data class HomeCircuitTemplate(
    val levels: Map<ProgramLevel, HomeCircuitLevel>,
    val restBetweenRoundsSeconds: Int,
    val candidates: List<Int>,
    val lowImpactCandidates: List<Int>
)

data class HomeCircuitLevel(
    val rounds: Int,
    val workSeconds: Int,
    val restSeconds: Int,
    val exercises: Int
)

data class ImpactRules(
    val lowImpactMinBmi: Double,
    val lowImpactMinAge: Int,
    val seniorMinAge: Int,
    val seniorExtraRestSeconds: Int,
    val excludedForBeginnerOrLowImpact: Set<Int>
)
