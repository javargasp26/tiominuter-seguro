package com.javargasp.tiominutermvi.domain.program.generator

import com.javargasp.tiominutermvi.domain.program.model.CardioType
import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.Mechanic
import com.javargasp.tiominutermvi.domain.program.model.Prescription
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramTemplates
import com.javargasp.tiominutermvi.domain.program.model.ProgramWeek
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.SplitDay
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTemplate
import com.javargasp.tiominutermvi.domain.program.model.StrengthSlot
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegment
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegmentLabel
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Builds a multi-week program from a profile, the exercise catalog and the templates.
 *
 * Pure and deterministic: the same input always gives the same program.
 * - Split: by goal, days per week and [ProgramProfile.focus]; the focus only changes
 *   which strength days a week has.
 * - Exercises: each strength day picks, per slot pattern in order, the first eligible
 *   candidate not used yet in that workout (preferring the pattern's mechanic) until it
 *   has the session size. A pattern with no candidate is skipped and logged.
 *   [com.javargasp.tiominutermvi.domain.program.model.SlotPattern.required] patterns are
 *   picked first and always keep their place; the other patterns fill the rest of the
 *   session. The workout keeps the template order.
 * - Session size: the top of the level's range for 2-3 days a week, the bottom for 4-5.
 * - Progression: +reps each week up to the top of the range, then +1 set (up to the max)
 *   and reps back to the bottom; holds +[com.javargasp.tiominutermvi.domain.program.model.ProgressionRules.holdStepSeconds].
 *   Deload weeks don't advance it.
 * - Per-side exercises keep their set count; the slot carries [StrengthSlot.perSide].
 *   They never go into a home circuit (see [isCircuitEligible]).
 */
object ProgramGenerator {

    fun generate(
        profile: ProgramProfile,
        catalog: List<ProgramExercise>,
        templates: ProgramTemplates
    ): GeneratedProgram {
        require(profile.weeks in ProgramProfile.WEEKS_RANGE) { "weeks ${profile.weeks}" }
        val split = requireNotNull(templates.split(profile.goal, profile.daysPerWeek, profile.focus)) {
            "No split for ${profile.goal} ${profile.daysPerWeek} days ${profile.focus}"
        }
        val prescription = requireNotNull(templates.prescription(profile.goal, profile.level)) {
            "No prescription for ${profile.goal} ${profile.level}"
        }
        val context = Context(
            profile = profile,
            templates = templates,
            prescription = prescription,
            lowImpact = isLowImpact(profile, templates.impact),
            extraRestSeconds = if (isSenior(profile, templates.impact)) templates.impact.seniorExtraRestSeconds else 0,
            catalog = catalog.associateBy { it.exerciseId }
        )

        // Exercises are chosen once per strength day; the weeks only change the prescription.
        val dayPlans = split.days.filterIsInstance<SplitDay.Strength>()
            .map { it.strengthDayId }
            .distinct()
            .associateWith { id ->
                val day = requireNotNull(templates.strengthDay(id)) { "Unknown strength day $id" }
                day to chooseExercises(day, context)
            }

        var cardioIndex = 0
        var circuitIndex = 0
        var step = 0
        val weeks = (1..profile.weeks).map { weekNumber ->
            val deload = isDeloadWeek(weekNumber, profile.weeks, templates)
            val workouts = split.days.map { day ->
                when (day) {
                    is SplitDay.Strength -> {
                        val (template, exercises) = dayPlans.getValue(day.strengthDayId)
                        strengthWorkout(template, exercises, step, deload, context)
                    }
                    SplitDay.Cardio -> {
                        val types = templates.cardio.rotation[profile.location].orEmpty()
                        val type = types.getOrElse(cardioIndex % types.size.coerceAtLeast(1)) { CardioType.TREADMILL }
                        cardioIndex++
                        if (type == CardioType.HOME_CIRCUIT) circuitIndex++
                        cardioWorkout(type, step, deload, circuitIndex - 1, context)
                    }
                }
            }
            if (!deload) step++
            ProgramWeek(weekNumber = weekNumber, isDeload = deload, workouts = workouts)
        }
        return GeneratedProgram(weeks)
    }

    /** Every [deloadEveryWeeks]th week is a deload, only for programs of at least [deloadMinProgramWeeks] weeks. */
    fun isDeloadWeek(weekNumber: Int, totalWeeks: Int, templates: ProgramTemplates): Boolean {
        val rules = templates.progression
        return totalWeeks >= rules.deloadMinProgramWeeks && weekNumber % rules.deloadEveryWeeks == 0
    }

    // ------------------------------------------------------------------
    // Strength
    // ------------------------------------------------------------------

    private class Context(
        val profile: ProgramProfile,
        val templates: ProgramTemplates,
        val prescription: Prescription,
        val lowImpact: Boolean,
        val extraRestSeconds: Int,
        val catalog: Map<Int, ProgramExercise>
    ) {
        fun eligible(exercise: ProgramExercise): Boolean =
            isEligible(exercise, profile.location, lowImpact, profile.level, templates.impact)
    }

    private class ChosenSlot(val exercise: ProgramExercise, val type: ProgramSlotType)

    private fun chooseExercises(day: StrengthDayTemplate, context: Context): List<ChosenSlot> {
        val size = if (context.profile.daysPerWeek <= 3) {
            context.prescription.exercisesPerSession.last
        } else {
            context.prescription.exercisesPerSession.first
        }
        val used = mutableSetOf<Int>()
        // Template index -> chosen slot; required patterns first, then the others in order.
        val chosen = sortedMapOf<Int, ChosenSlot>()
        val (required, others) = day.slots.withIndex().partition { it.value.required }
        for ((index, pattern) in required + others) {
            if (chosen.size >= size) break
            val candidates = pattern.candidates
                .mapNotNull { context.catalog[it] }
                .filter { it.exerciseId !in used && pattern.muscle in it.primaryMuscles && context.eligible(it) }
            val pick = candidates.firstOrNull { it.mechanic == pattern.mechanic } ?: candidates.firstOrNull()
            if (pick == null) {
                println("ProgramGenerator: no candidate for ${day.id} slot $index (${pattern.muscle}), skipped")
                continue
            }
            used += pick.exerciseId
            chosen[index] = ChosenSlot(pick, pattern.type)
        }
        return chosen.values.toList()
    }

    private fun strengthWorkout(
        day: StrengthDayTemplate,
        exercises: List<ChosenSlot>,
        step: Int,
        deload: Boolean,
        context: Context
    ): ProgramWorkout.Strength = ProgramWorkout.Strength(
        title = day.title,
        slots = exercises.mapIndexed { position, slot ->
            prescribe(position, slot, step, deload, context)
        }
    )

    private fun prescribe(position: Int, slot: ChosenSlot, step: Int, deload: Boolean, context: Context): StrengthSlot {
        val p = context.prescription
        val rules = context.templates.progression
        val compound = slot.exercise.mechanic == Mechanic.COMPOUND
        val baseSets = if (compound) p.setsCompound else p.setsIsolation
        val rest = (if (compound) p.restCompoundSeconds else p.restIsolationSeconds) + context.extraRestSeconds

        if (slot.type == ProgramSlotType.TIME) {
            val sets = if (deload) deloadSets(baseSets, rules.deloadSetsDrop, rules.deloadMinSets) else baseSets
            val seconds = if (deload) p.holdSeconds else p.holdSeconds + rules.holdStepSeconds * step
            return StrengthSlot(position, slot.exercise.exerciseId, ProgramSlotType.TIME, List(sets) { seconds }, rest, slot.exercise.perSide)
        }

        val range = if (compound) p.repsCompound else p.repsIsolation
        val (sets, reps) = if (deload) {
            val lastTrained = repsProgression(baseSets, range, (step - 1).coerceAtLeast(0), context)
            deloadSets(lastTrained.first, rules.deloadSetsDrop, rules.deloadMinSets) to range.first
        } else {
            repsProgression(baseSets, range, step, context)
        }
        return StrengthSlot(position, slot.exercise.exerciseId, ProgramSlotType.REPS, List(sets) { reps }, rest, slot.exercise.perSide)
    }

    /** Sets and reps after [step] progression weeks, starting at the bottom of [range]. */
    private fun repsProgression(baseSets: Int, range: IntRange, step: Int, context: Context): Pair<Int, Int> {
        val rules = context.templates.progression
        var sets = baseSets
        var reps = range.first
        repeat(step) {
            reps += rules.repStepPerWeek
            if (reps > range.last) {
                sets = (sets + 1).coerceAtMost(rules.maxSets)
                reps = range.first
            }
        }
        return sets to reps
    }

    private fun deloadSets(sets: Int, drop: Int, min: Int): Int = (sets - drop).coerceAtLeast(min)

    // ------------------------------------------------------------------
    // Cardio
    // ------------------------------------------------------------------

    private fun cardioWorkout(
        type: CardioType,
        step: Int,
        deload: Boolean,
        circuitIndex: Int,
        context: Context
    ): ProgramWorkout.Cardio {
        val factor = if (deload) context.templates.progression.deloadCardioFactor else 1.0
        return when (type) {
            CardioType.OUTDOOR_RUN -> outdoorRun(step, factor, context)
            CardioType.TREADMILL -> treadmill(step, factor, context)
            CardioType.HOME_CIRCUIT -> homeCircuit(circuitIndex, factor, context)
        }
    }

    private fun outdoorRun(step: Int, factor: Double, context: Context): ProgramWorkout.OutdoorRun {
        val template = context.templates.cardio.outdoorRun
        val level = context.profile.level
        val start = if (context.lowImpact) template.walkStartKm.getValue(level) else template.runStartKm.getValue(level)
        val distance = start * (1 + template.weeklyIncreasePercent / 100.0).pow(step) * factor
        return ProgramWorkout.OutdoorRun(distanceKm = roundToTenth(distance), walk = context.lowImpact)
    }

    private fun treadmill(step: Int, factor: Double, context: Context): ProgramWorkout.Treadmill {
        val level = context.profile.level
        val segments = mutableListOf<TreadmillSegment>()
        if (context.lowImpact) {
            val t = context.templates.cardio.treadmillLowImpact
            val intervals = scaled(t.baseIntervals.getValue(level) + t.intervalsPerWeek * step, t.maxIntervals, factor)
            segments += TreadmillSegment(t.warmUpSeconds, t.speedKmh, t.lowIncline, TreadmillSegmentLabel.WARM_UP)
            repeat(intervals) {
                segments += TreadmillSegment(t.highSeconds, t.speedKmh, t.highIncline, TreadmillSegmentLabel.INCLINE_WALK)
                segments += TreadmillSegment(t.lowSeconds, t.speedKmh, t.lowIncline, TreadmillSegmentLabel.WALK)
            }
            segments += TreadmillSegment(t.coolDownSeconds, t.speedKmh, t.lowIncline, TreadmillSegmentLabel.COOL_DOWN)
        } else {
            val t = context.templates.cardio.treadmill.getValue(level)
            val intervals = scaled(t.baseIntervals + t.intervalsPerWeek * step, t.maxIntervals, factor)
            segments += TreadmillSegment(t.warmUpSeconds, t.walkSpeedKmh, 1.0, TreadmillSegmentLabel.WARM_UP)
            repeat(intervals) { i ->
                segments += TreadmillSegment(t.jogSeconds, t.jogSpeedKmh, t.inclines[i % t.inclines.size], TreadmillSegmentLabel.JOG)
                segments += TreadmillSegment(t.walkSeconds, t.walkSpeedKmh, 1.0, TreadmillSegmentLabel.WALK)
            }
            segments += TreadmillSegment(t.coolDownSeconds, t.walkSpeedKmh, 0.0, TreadmillSegmentLabel.COOL_DOWN)
        }
        return ProgramWorkout.Treadmill(segments)
    }

    private fun homeCircuit(circuitIndex: Int, factor: Double, context: Context): ProgramWorkout.HomeCircuit {
        val template = context.templates.cardio.homeCircuit
        val level = template.levels.getValue(context.profile.level)
        val list = (if (context.lowImpact) template.lowImpactCandidates else template.candidates)
            .distinct()
            .mapNotNull { context.catalog[it] }
            .filter { isCircuitEligible(it) && context.eligible(it) }
        // Rotate the start so consecutive circuits vary.
        val rotated = if (list.isEmpty()) list else {
            val offset = circuitIndex % list.size
            list.drop(offset) + list.take(offset)
        }
        return ProgramWorkout.HomeCircuit(
            rounds = scaled(level.rounds, level.rounds, factor),
            workSeconds = level.workSeconds,
            restSeconds = level.restSeconds + context.extraRestSeconds,
            restBetweenRoundsSeconds = template.restBetweenRoundsSeconds + context.extraRestSeconds,
            exerciseIds = rotated.take(level.exercises).map { it.exerciseId }
        )
    }

    /** [value] capped at [max], times [factor] (deload), at least 1. */
    private fun scaled(value: Int, max: Int, factor: Double): Int =
        (value.coerceAtMost(max) * factor).roundToInt().coerceAtLeast(1)

    private fun roundToTenth(value: Double): Double = (value * 10).roundToInt() / 10.0
}
