/// The program generator, ported line by line from Android's
/// `domain/program/generator/ProgramGenerator.kt` and `ProgramRules.kt`. Pure and deterministic:
/// the same profile, catalog and templates always give the same program, the same as on Android
/// (checked against shared/golden/program_generator.json, web/test/parity/).
library;

import 'dart:math' as math;

import 'program.dart';

/// Equipment available at home; the gym has everything.
const Set<Equipment> homeEquipment = {Equipment.bodyweight, Equipment.dumbbell, Equipment.bench, Equipment.none};

/// Mechanics a home-circuit exercise may have.
const Set<Mechanic> homeCircuitMechanics = {Mechanic.cardio, Mechanic.compound, Mechanic.core};

/// A circuit station is one short block with no switch cue: never a per-side exercise.
bool isCircuitEligible(ProgramExercise exercise) =>
    homeCircuitMechanics.contains(exercise.mechanic) && !exercise.perSide;

/// BMI = kg / m².
double computeBmi(double weightKg, int heightCm) {
  final meters = heightCm / 100.0;
  return weightKg / (meters * meters);
}

/// Low impact when BMI >= the rule's minimum or age >= its minimum age.
bool isLowImpact(ProgramProfile profile, ImpactRules rules) =>
    computeBmi(profile.weightKg, profile.heightCm) >= rules.lowImpactMinBmi || profile.age >= rules.lowImpactMinAge;

bool isSenior(ProgramProfile profile, ImpactRules rules) => profile.age >= rules.seniorMinAge;

/// Canonical, available at [location] with its equipment, not HIGH impact when [lowImpact], not
/// excluded for beginners or low impact.
bool isEligible(
  ProgramExercise exercise,
  ProgramLocation location,
  bool lowImpact,
  ProgramLevel level,
  ImpactRules rules,
) {
  if (exercise.duplicateOf != null) return false;
  if (!exercise.locations.contains(location)) return false;
  if (location == ProgramLocation.home && !homeEquipment.contains(exercise.equipment)) return false;
  if (lowImpact && exercise.impact == Impact.high) return false;
  final restricted = level == ProgramLevel.beginner || lowImpact;
  if (restricted && rules.excludedForBeginnerOrLowImpact.contains(exercise.exerciseId)) return false;
  return true;
}

/// Kotlin's `Double.roundToInt()` (Java's `Math.round`: half up, computed without the rounding error
/// of `value + 0.5`).
int _roundToInt(double value) {
  final floor = value.floorToDouble();
  return value - floor >= 0.5 ? floor.toInt() + 1 : floor.toInt();
}

class _Context {
  _Context({
    required this.profile,
    required this.templates,
    required this.prescription,
    required this.lowImpact,
    required this.extraRestSeconds,
    required this.catalog,
  });

  final ProgramProfile profile;
  final ProgramTemplates templates;
  final Prescription prescription;
  final bool lowImpact;
  final int extraRestSeconds;
  final Map<int, ProgramExercise> catalog;

  bool eligible(ProgramExercise exercise) =>
      isEligible(exercise, profile.location, lowImpact, profile.level, templates.impact);
}

class _ChosenSlot {
  const _ChosenSlot(this.exercise, this.type);

  final ProgramExercise exercise;
  final ProgramSlotType type;
}

abstract final class ProgramGenerator {
  /// Throws [ArgumentError] for weeks out of range or a combination the templates lack (as
  /// Android's `require`).
  static GeneratedProgram generate(ProgramProfile profile, List<ProgramExercise> catalog, ProgramTemplates templates) {
    if (!ProgramProfile.weeksRange.contains(profile.weeks)) throw ArgumentError('weeks ${profile.weeks}');
    final split =
        templates.split(profile.goal, profile.daysPerWeek, profile.focus) ??
        (throw ArgumentError('No split for ${profile.goal.code} ${profile.daysPerWeek} days ${profile.focus.code}'));
    final prescription =
        templates.prescription(profile.goal, profile.level) ??
        (throw ArgumentError('No prescription for ${profile.goal.code} ${profile.level.code}'));
    // Kotlin's associateBy: the last entry with an id wins
    final byId = <int, ProgramExercise>{for (final exercise in catalog) exercise.exerciseId: exercise};
    final context = _Context(
      profile: profile,
      templates: templates,
      prescription: prescription,
      lowImpact: isLowImpact(profile, templates.impact),
      extraRestSeconds: isSenior(profile, templates.impact) ? templates.impact.seniorExtraRestSeconds : 0,
      catalog: byId,
    );

    // Exercises are chosen once per strength day; the weeks only change the prescription
    final dayPlans = <String, (StrengthDayTemplate, List<_ChosenSlot>)>{};
    for (final day in split.days.whereType<StrengthSplitDay>()) {
      if (dayPlans.containsKey(day.strengthDayId)) continue;
      final template =
          templates.strengthDay(day.strengthDayId) ??
          (throw ArgumentError('Unknown strength day ${day.strengthDayId}'));
      dayPlans[day.strengthDayId] = (template, _chooseExercises(template, context));
    }

    var cardioIndex = 0;
    var circuitIndex = 0;
    var step = 0;
    final weeks = <ProgramWeek>[];
    for (var weekNumber = 1; weekNumber <= profile.weeks; weekNumber++) {
      final deload = isDeloadWeek(weekNumber, profile.weeks, templates);
      final workouts = <ProgramWorkout>[];
      for (final day in split.days) {
        switch (day) {
          case StrengthSplitDay(:final strengthDayId):
            final (template, exercises) = dayPlans[strengthDayId]!;
            workouts.add(_strengthWorkout(template, exercises, step, deload, context));
          case CardioSplitDay():
            final types = templates.cardio.rotation[profile.location] ?? const <CardioType>[];
            final int index = cardioIndex % math.max(types.length, 1);
            final type = index < types.length ? types[index] : CardioType.treadmill;
            cardioIndex++;
            if (type == CardioType.homeCircuit) circuitIndex++;
            workouts.add(_cardioWorkout(type, step, deload, circuitIndex - 1, context));
        }
      }
      if (!deload) step++;
      weeks.add(ProgramWeek(weekNumber: weekNumber, isDeload: deload, workouts: workouts));
    }
    return GeneratedProgram(weeks);
  }

  /// Every deloadEveryWeeks-th week, only for programs of at least deloadMinProgramWeeks weeks.
  static bool isDeloadWeek(int weekNumber, int totalWeeks, ProgramTemplates templates) {
    final rules = templates.progression;
    return totalWeeks >= rules.deloadMinProgramWeeks && weekNumber % rules.deloadEveryWeeks == 0;
  }

  // ------------------------------------------------------------------
  // Strength
  // ------------------------------------------------------------------

  static List<_ChosenSlot> _chooseExercises(StrengthDayTemplate day, _Context context) {
    final size = context.profile.daysPerWeek <= 3
        ? context.prescription.exercisesPerSession.last
        : context.prescription.exercisesPerSession.first;
    final used = <int>{};
    // Template index -> chosen slot; required patterns first, then the others in order
    final chosen = <int, _ChosenSlot>{};
    final indexed = [for (var i = 0; i < day.slots.length; i++) (i, day.slots[i])];
    final ordered = [...indexed.where((it) => it.$2.required), ...indexed.where((it) => !it.$2.required)];
    for (final (index, pattern) in ordered) {
      if (chosen.length >= size) break;
      final candidates = [
        for (final id in pattern.candidates)
          if (context.catalog[id] case final exercise?)
            if (!used.contains(exercise.exerciseId) &&
                exercise.primaryMuscles.contains(pattern.muscle) &&
                context.eligible(exercise))
              exercise,
      ];
      final pick = candidates.where((it) => it.mechanic == pattern.mechanic).firstOrNull ?? candidates.firstOrNull;
      // Android logs "no candidate … skipped"; the pattern is skipped here too
      if (pick == null) continue;
      used.add(pick.exerciseId);
      chosen[index] = _ChosenSlot(pick, pattern.type);
    }
    final keys = chosen.keys.toList()..sort();
    return [for (final key in keys) chosen[key]!];
  }

  static StrengthWorkout _strengthWorkout(
    StrengthDayTemplate day,
    List<_ChosenSlot> exercises,
    int step,
    bool deload,
    _Context context,
  ) => StrengthWorkout(
    title: day.title,
    slots: [
      for (var position = 0; position < exercises.length; position++)
        _prescribe(position, exercises[position], step, deload, context),
    ],
  );

  static StrengthSlot _prescribe(int position, _ChosenSlot slot, int step, bool deload, _Context context) {
    final p = context.prescription;
    final rules = context.templates.progression;
    final compound = slot.exercise.mechanic == Mechanic.compound;
    final baseSets = compound ? p.setsCompound : p.setsIsolation;
    final rest = (compound ? p.restCompoundSeconds : p.restIsolationSeconds) + context.extraRestSeconds;

    if (slot.type == ProgramSlotType.time) {
      final sets = deload ? _deloadSets(baseSets, rules.deloadSetsDrop, rules.deloadMinSets) : baseSets;
      final seconds = deload ? p.holdSeconds : p.holdSeconds + rules.holdStepSeconds * step;
      return StrengthSlot(
        position: position,
        exerciseId: slot.exercise.exerciseId,
        type: ProgramSlotType.time,
        reps: List.filled(sets, seconds),
        restSeconds: rest,
        perSide: slot.exercise.perSide,
      );
    }

    final range = compound ? p.repsCompound : p.repsIsolation;
    final int sets;
    final int reps;
    if (deload) {
      final lastTrained = _repsProgression(baseSets, range, math.max(step - 1, 0), context);
      sets = _deloadSets(lastTrained.$1, rules.deloadSetsDrop, rules.deloadMinSets);
      reps = range.first;
    } else {
      (sets, reps) = _repsProgression(baseSets, range, step, context);
    }
    return StrengthSlot(
      position: position,
      exerciseId: slot.exercise.exerciseId,
      type: ProgramSlotType.reps,
      reps: List.filled(sets, reps),
      restSeconds: rest,
      perSide: slot.exercise.perSide,
    );
  }

  /// Sets and reps after [step] progression weeks, starting at the bottom of [range].
  static (int, int) _repsProgression(int baseSets, IntRange range, int step, _Context context) {
    final rules = context.templates.progression;
    var sets = baseSets;
    var reps = range.first;
    for (var i = 0; i < step; i++) {
      reps += rules.repStepPerWeek;
      if (reps > range.last) {
        sets = math.min(sets + 1, rules.maxSets);
        reps = range.first;
      }
    }
    return (sets, reps);
  }

  static int _deloadSets(int sets, int drop, int min) => math.max(sets - drop, min);

  // ------------------------------------------------------------------
  // Cardio
  // ------------------------------------------------------------------

  static CardioWorkout _cardioWorkout(CardioType type, int step, bool deload, int circuitIndex, _Context context) {
    final factor = deload ? context.templates.progression.deloadCardioFactor : 1.0;
    return switch (type) {
      CardioType.outdoorRun => _outdoorRun(step, factor, context),
      CardioType.treadmill => _treadmill(step, factor, context),
      CardioType.homeCircuit => _homeCircuit(circuitIndex, factor, context),
    };
  }

  static OutdoorRunWorkout _outdoorRun(int step, double factor, _Context context) {
    final template = context.templates.cardio.outdoorRun;
    final level = context.profile.level;
    final start = context.lowImpact ? template.walkStartKm[level]! : template.runStartKm[level]!;
    final distance = start * math.pow(1 + template.weeklyIncreasePercent / 100.0, step) * factor;
    return OutdoorRunWorkout(distanceKm: _roundToTenth(distance), walk: context.lowImpact);
  }

  static TreadmillWorkout _treadmill(int step, double factor, _Context context) {
    final level = context.profile.level;
    final segments = <TreadmillSegment>[];
    if (context.lowImpact) {
      final t = context.templates.cardio.treadmillLowImpact;
      final intervals = _scaled(t.baseIntervals[level]! + t.intervalsPerWeek * step, t.maxIntervals, factor);
      segments.add(
        TreadmillSegment(
          durationSeconds: t.warmUpSeconds,
          speedKmh: t.speedKmh,
          inclinePercent: t.lowIncline,
          label: TreadmillSegmentLabel.warmUp,
        ),
      );
      for (var i = 0; i < intervals; i++) {
        segments
          ..add(
            TreadmillSegment(
              durationSeconds: t.highSeconds,
              speedKmh: t.speedKmh,
              inclinePercent: t.highIncline,
              label: TreadmillSegmentLabel.inclineWalk,
            ),
          )
          ..add(
            TreadmillSegment(
              durationSeconds: t.lowSeconds,
              speedKmh: t.speedKmh,
              inclinePercent: t.lowIncline,
              label: TreadmillSegmentLabel.walk,
            ),
          );
      }
      segments.add(
        TreadmillSegment(
          durationSeconds: t.coolDownSeconds,
          speedKmh: t.speedKmh,
          inclinePercent: t.lowIncline,
          label: TreadmillSegmentLabel.coolDown,
        ),
      );
    } else {
      final t = context.templates.cardio.treadmill[level]!;
      final intervals = _scaled(t.baseIntervals + t.intervalsPerWeek * step, t.maxIntervals, factor);
      segments.add(
        TreadmillSegment(
          durationSeconds: t.warmUpSeconds,
          speedKmh: t.walkSpeedKmh,
          inclinePercent: 1.0,
          label: TreadmillSegmentLabel.warmUp,
        ),
      );
      for (var i = 0; i < intervals; i++) {
        segments
          ..add(
            TreadmillSegment(
              durationSeconds: t.jogSeconds,
              speedKmh: t.jogSpeedKmh,
              inclinePercent: t.inclines[i % t.inclines.length],
              label: TreadmillSegmentLabel.jog,
            ),
          )
          ..add(
            TreadmillSegment(
              durationSeconds: t.walkSeconds,
              speedKmh: t.walkSpeedKmh,
              inclinePercent: 1.0,
              label: TreadmillSegmentLabel.walk,
            ),
          );
      }
      segments.add(
        TreadmillSegment(
          durationSeconds: t.coolDownSeconds,
          speedKmh: t.walkSpeedKmh,
          inclinePercent: 0.0,
          label: TreadmillSegmentLabel.coolDown,
        ),
      );
    }
    return TreadmillWorkout(segments);
  }

  static HomeCircuitWorkout _homeCircuit(int circuitIndex, double factor, _Context context) {
    final template = context.templates.cardio.homeCircuit;
    final level = template.levels[context.profile.level]!;
    final ids = (context.lowImpact ? template.lowImpactCandidates : template.candidates).toSet();
    final list = [
      for (final id in ids)
        if (context.catalog[id] case final exercise?)
          if (isCircuitEligible(exercise) && context.eligible(exercise)) exercise,
    ];
    // Rotate the start so consecutive circuits vary
    final List<ProgramExercise> rotated;
    if (list.isEmpty) {
      rotated = list;
    } else {
      final offset = circuitIndex % list.length;
      rotated = [...list.skip(offset), ...list.take(offset)];
    }
    return HomeCircuitWorkout(
      rounds: _scaled(level.rounds, level.rounds, factor),
      workSeconds: level.workSeconds,
      restSeconds: level.restSeconds + context.extraRestSeconds,
      restBetweenRoundsSeconds: template.restBetweenRoundsSeconds + context.extraRestSeconds,
      exerciseIds: [for (final exercise in rotated.take(level.exercises)) exercise.exerciseId],
    );
  }

  /// [value] capped at [max], times [factor] (deload), at least 1.
  static int _scaled(int value, int max, double factor) => math.max(_roundToInt(math.min(value, max) * factor), 1);

  static double _roundToTenth(double value) => _roundToInt(value * 10) / 10.0;
}
