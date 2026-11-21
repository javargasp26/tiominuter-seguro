/// The Program feature's model, ported from Android
/// (app/src/main/java/com/javargasp/tiominutermvi/domain/program/model/):
/// `ProgramEnums.kt`, `ProgramProfile.kt`, `ProgramExercise.kt`, `ProgramTemplates.kt`,
/// `GeneratedProgram.kt`, and the payload codec of `data/program/mapper/ProgramEntityMappers.kt`
/// (`programPayloadJson`: discriminator `kind`, defaults not encoded, unknown keys ignored).
/// [ProgramWorkout.toPayload] gives the exact string Android stores, byte for byte (the parity
/// golden shared/golden/program_generator.json checks it).
library;

import 'dart:convert';

import 'workout_names.dart';

/// An enum stored by its Kotlin name ([code]).
abstract interface class Coded {
  String get code;
}

T? _byCode<T extends Coded>(List<T> values, Object? code) {
  for (final value in values) {
    if (value.code == code) return value;
  }
  return null;
}

enum ProgramGoal implements Coded {
  gainMuscle('GAIN_MUSCLE'),
  loseFat('LOSE_FAT');

  const ProgramGoal(this.code);
  @override
  final String code;
  static ProgramGoal? fromCode(Object? code) => _byCode(values, code);
}

enum ProgramLevel implements Coded {
  beginner('BEGINNER'),
  intermediate('INTERMEDIATE'),
  advanced('ADVANCED');

  const ProgramLevel(this.code);
  @override
  final String code;
  static ProgramLevel? fromCode(Object? code) => _byCode(values, code);
}

/// Picks the split; BALANCED is the split every program had before the focus existed.
enum ProgramFocus implements Coded {
  balanced('BALANCED'),
  lowerBody('LOWER_BODY'),
  upperBody('UPPER_BODY'),
  core('CORE');

  const ProgramFocus(this.code);
  @override
  final String code;
  static const ProgramFocus defaultFocus = balanced;
  static ProgramFocus? fromCode(Object? code) => _byCode(values, code);
}

enum ProgramLocation implements Coded {
  home('HOME'),
  gym('GYM');

  const ProgramLocation(this.code);
  @override
  final String code;
  static ProgramLocation? fromCode(Object? code) => _byCode(values, code);
}

enum Equipment implements Coded {
  bodyweight('BODYWEIGHT'),
  dumbbell('DUMBBELL'),
  barbell('BARBELL'),
  machine('MACHINE'),
  cable('CABLE'),
  pullUpBar('PULL_UP_BAR'),
  bench('BENCH'),
  treadmill('TREADMILL'),
  bike('BIKE'),
  none('NONE');

  const Equipment(this.code);
  @override
  final String code;
  static Equipment? fromCode(Object? code) => _byCode(values, code);
}

enum Mechanic implements Coded {
  compound('COMPOUND'),
  isolation('ISOLATION'),
  cardio('CARDIO'),
  core('CORE');

  const Mechanic(this.code);
  @override
  final String code;
  static Mechanic? fromCode(Object? code) => _byCode(values, code);
}

enum Impact implements Coded {
  low('LOW'),
  high('HIGH');

  const Impact(this.code);
  @override
  final String code;
  static Impact? fromCode(Object? code) => _byCode(values, code);
}

enum MuscleGroup implements Coded {
  chest('CHEST'),
  back('BACK'),
  shoulders('SHOULDERS'),
  biceps('BICEPS'),
  triceps('TRICEPS'),
  quads('QUADS'),
  hamstrings('HAMSTRINGS'),
  glutes('GLUTES'),
  calves('CALVES'),
  core('CORE'),
  fullBody('FULL_BODY'),
  cardio('CARDIO');

  const MuscleGroup(this.code);
  @override
  final String code;
  static MuscleGroup? fromCode(Object? code) => _byCode(values, code);
}

enum ProgramSlotType implements Coded {
  reps('REPS'),
  time('TIME');

  const ProgramSlotType(this.code);
  @override
  final String code;
  static ProgramSlotType? fromCode(Object? code) => _byCode(values, code);
}

enum CardioType implements Coded {
  outdoorRun('OUTDOOR_RUN'),
  treadmill('TREADMILL'),
  homeCircuit('HOME_CIRCUIT');

  const CardioType(this.code);
  @override
  final String code;
  static CardioType? fromCode(Object? code) => _byCode(values, code);
}

/// Stored by name in the payloads: values are only ever added.
enum StrengthDayTitle implements Coded {
  fullBodyA('FULL_BODY_A'),
  fullBodyB('FULL_BODY_B'),
  fullBodyC('FULL_BODY_C'),
  upperA('UPPER_A'),
  upperB('UPPER_B'),
  lowerA('LOWER_A'),
  lowerB('LOWER_B'),
  push('PUSH'),
  pull('PULL'),
  legs('LEGS'),
  fullBody('FULL_BODY'),
  upper('UPPER'),
  lower('LOWER'),
  upperC('UPPER_C'),
  lowerC('LOWER_C');

  const StrengthDayTitle(this.code);
  @override
  final String code;
  static StrengthDayTitle? fromCode(Object? code) => _byCode(values, code);
}

enum TreadmillSegmentLabel implements Coded {
  warmUp('WARM_UP'),
  jog('JOG'),
  walk('WALK'),
  inclineWalk('INCLINE_WALK'),
  coolDown('COOL_DOWN');

  const TreadmillSegmentLabel(this.code);
  @override
  final String code;
  static TreadmillSegmentLabel? fromCode(Object? code) => _byCode(values, code);
}

/// The stored kind of a workout (the payload's `kind`).
enum ProgramWorkoutKind implements Coded {
  strength('STRENGTH'),
  outdoorRun('OUTDOOR_RUN'),
  treadmill('TREADMILL'),
  homeCircuit('HOME_CIRCUIT');

  const ProgramWorkoutKind(this.code);
  @override
  final String code;
  static ProgramWorkoutKind? fromCode(Object? code) => _byCode(values, code);
}

/// An inclusive integer range (Kotlin's `IntRange`): [first]..[last].
class IntRange {
  const IntRange(this.first, this.last);

  final int first;
  final int last;

  bool contains(num value) => value >= first && value <= last;
}

/// What a program is generated from (`ProgramProfile.kt`).
class ProgramProfile {
  const ProgramProfile({
    required this.age,
    required this.weightKg,
    required this.heightCm,
    required this.level,
    required this.goal,
    required this.location,
    required this.weeks,
    required this.daysPerWeek,
    this.focus = ProgramFocus.defaultFocus,
  });

  static const IntRange ageRange = IntRange(16, 90);
  static const double minWeightKg = 30.0;
  static const double maxWeightKg = 250.0;
  static const IntRange heightRangeCm = IntRange(120, 230);
  static const IntRange weeksRange = IntRange(4, 12);
  static const IntRange daysPerWeekRange = IntRange(2, 5);
  static const int defaultWeeks = 6;
  static const int defaultDaysPerWeek = 3;

  final int age;
  final double weightKg;
  final int heightCm;
  final ProgramLevel level;
  final ProgramGoal goal;
  final ProgramLocation location;
  final int weeks;
  final int daysPerWeek;
  final ProgramFocus focus;
}

/// One entry of `program_exercises.json` (`ProgramExercise.kt`).
class ProgramExercise {
  const ProgramExercise({
    required this.exerciseId,
    required this.name,
    required this.nameEs,
    required this.animation,
    required this.primaryMuscles,
    required this.equipment,
    required this.mechanic,
    required this.impact,
    required this.locations,
    this.perSide = false,
    this.duplicateOf,
  });

  final int exerciseId;
  final String name;
  final String nameEs;
  final String animation;
  final List<MuscleGroup> primaryMuscles;
  final Equipment equipment;
  final Mechanic mechanic;
  final Impact impact;
  final Set<ProgramLocation> locations;
  final bool perSide;
  final int? duplicateOf;

  String displayName({required bool spanish}) => spanish ? nameEs : name;
}

// ---------------------------------------------------------------------------
// Templates (`ProgramTemplates.kt`)
// ---------------------------------------------------------------------------

class ProgramTemplates {
  const ProgramTemplates({
    required this.splits,
    required this.strengthDays,
    required this.prescriptions,
    required this.progression,
    required this.cardio,
    required this.impact,
  });

  final List<ProgramSplit> splits;
  final List<StrengthDayTemplate> strengthDays;
  final List<Prescription> prescriptions;
  final ProgressionRules progression;
  final CardioTemplates cardio;
  final ImpactRules impact;

  ProgramSplit? split(ProgramGoal goal, int daysPerWeek, [ProgramFocus focus = ProgramFocus.defaultFocus]) {
    for (final split in splits) {
      if (split.goal == goal && split.daysPerWeek == daysPerWeek && split.focus == focus) return split;
    }
    return null;
  }

  StrengthDayTemplate? strengthDay(String id) {
    for (final day in strengthDays) {
      if (day.id == id) return day;
    }
    return null;
  }

  Prescription? prescription(ProgramGoal goal, ProgramLevel level) {
    for (final p in prescriptions) {
      if (p.goal == goal && p.level == level) return p;
    }
    return null;
  }
}

class ProgramSplit {
  const ProgramSplit({required this.goal, required this.daysPerWeek, required this.days, required this.focus});

  final ProgramGoal goal;
  final int daysPerWeek;
  final List<SplitDay> days;
  final ProgramFocus focus;
}

sealed class SplitDay {
  const SplitDay();
}

final class StrengthSplitDay extends SplitDay {
  const StrengthSplitDay(this.strengthDayId);

  final String strengthDayId;
}

final class CardioSplitDay extends SplitDay {
  const CardioSplitDay();
}

class StrengthDayTemplate {
  const StrengthDayTemplate({required this.id, required this.title, required this.slots});

  final String id;
  final StrengthDayTitle title;
  final List<SlotPattern> slots;
}

class SlotPattern {
  const SlotPattern({
    required this.muscle,
    required this.mechanic,
    required this.type,
    required this.candidates,
    this.required = false,
  });

  final MuscleGroup muscle;
  final Mechanic mechanic;
  final ProgramSlotType type;
  final List<int> candidates;
  final bool required;
}

class Prescription {
  const Prescription({
    required this.goal,
    required this.level,
    required this.exercisesPerSession,
    required this.setsCompound,
    required this.setsIsolation,
    required this.repsCompound,
    required this.repsIsolation,
    required this.restCompoundSeconds,
    required this.restIsolationSeconds,
    required this.holdSeconds,
  });

  final ProgramGoal goal;
  final ProgramLevel level;
  final IntRange exercisesPerSession;
  final int setsCompound;
  final int setsIsolation;
  final IntRange repsCompound;
  final IntRange repsIsolation;
  final int restCompoundSeconds;
  final int restIsolationSeconds;
  final int holdSeconds;
}

class ProgressionRules {
  const ProgressionRules({
    required this.repStepPerWeek,
    required this.maxSets,
    required this.holdStepSeconds,
    required this.deloadEveryWeeks,
    required this.deloadMinProgramWeeks,
    required this.deloadSetsDrop,
    required this.deloadMinSets,
    required this.deloadCardioFactor,
  });

  final int repStepPerWeek;
  final int maxSets;
  final int holdStepSeconds;
  final int deloadEveryWeeks;
  final int deloadMinProgramWeeks;
  final int deloadSetsDrop;
  final int deloadMinSets;
  final double deloadCardioFactor;
}

class CardioTemplates {
  const CardioTemplates({
    required this.rotation,
    required this.outdoorRun,
    required this.treadmill,
    required this.treadmillLowImpact,
    required this.homeCircuit,
  });

  final Map<ProgramLocation, List<CardioType>> rotation;
  final OutdoorRunTemplate outdoorRun;
  final Map<ProgramLevel, TreadmillTemplate> treadmill;
  final TreadmillLowImpactTemplate treadmillLowImpact;
  final HomeCircuitTemplate homeCircuit;
}

class OutdoorRunTemplate {
  const OutdoorRunTemplate({required this.weeklyIncreasePercent, required this.runStartKm, required this.walkStartKm});

  final int weeklyIncreasePercent;
  final Map<ProgramLevel, double> runStartKm;
  final Map<ProgramLevel, double> walkStartKm;
}

class TreadmillTemplate {
  const TreadmillTemplate({
    required this.warmUpSeconds,
    required this.coolDownSeconds,
    required this.jogSpeedKmh,
    required this.walkSpeedKmh,
    required this.jogSeconds,
    required this.walkSeconds,
    required this.inclines,
    required this.baseIntervals,
    required this.intervalsPerWeek,
    required this.maxIntervals,
  });

  final int warmUpSeconds;
  final int coolDownSeconds;
  final double jogSpeedKmh;
  final double walkSpeedKmh;
  final int jogSeconds;
  final int walkSeconds;
  final List<double> inclines;
  final int baseIntervals;
  final int intervalsPerWeek;
  final int maxIntervals;
}

class TreadmillLowImpactTemplate {
  const TreadmillLowImpactTemplate({
    required this.warmUpSeconds,
    required this.coolDownSeconds,
    required this.speedKmh,
    required this.lowIncline,
    required this.highIncline,
    required this.lowSeconds,
    required this.highSeconds,
    required this.baseIntervals,
    required this.intervalsPerWeek,
    required this.maxIntervals,
  });

  final int warmUpSeconds;
  final int coolDownSeconds;
  final double speedKmh;
  final double lowIncline;
  final double highIncline;
  final int lowSeconds;
  final int highSeconds;
  final Map<ProgramLevel, int> baseIntervals;
  final int intervalsPerWeek;
  final int maxIntervals;
}

class HomeCircuitTemplate {
  const HomeCircuitTemplate({
    required this.levels,
    required this.restBetweenRoundsSeconds,
    required this.candidates,
    required this.lowImpactCandidates,
  });

  final Map<ProgramLevel, HomeCircuitLevel> levels;
  final int restBetweenRoundsSeconds;
  final List<int> candidates;
  final List<int> lowImpactCandidates;
}

class HomeCircuitLevel {
  const HomeCircuitLevel({
    required this.rounds,
    required this.workSeconds,
    required this.restSeconds,
    required this.exercises,
  });

  final int rounds;
  final int workSeconds;
  final int restSeconds;
  final int exercises;
}

class ImpactRules {
  const ImpactRules({
    required this.lowImpactMinBmi,
    required this.lowImpactMinAge,
    required this.seniorMinAge,
    required this.seniorExtraRestSeconds,
    required this.excludedForBeginnerOrLowImpact,
  });

  final double lowImpactMinBmi;
  final int lowImpactMinAge;
  final int seniorMinAge;
  final int seniorExtraRestSeconds;
  final Set<int> excludedForBeginnerOrLowImpact;
}

// ---------------------------------------------------------------------------
// The generated program (`GeneratedProgram.kt`)
// ---------------------------------------------------------------------------

class GeneratedProgram {
  const GeneratedProgram(this.weeks);

  final List<ProgramWeek> weeks;
}

class ProgramWeek {
  const ProgramWeek({required this.weekNumber, required this.isDeload, required this.workouts});

  /// Starts at 1.
  final int weekNumber;
  final bool isDeload;
  final List<ProgramWorkout> workouts;
}

/// One workout of a week; stored as its payload (`toPayload`).
sealed class ProgramWorkout {
  const ProgramWorkout();

  ProgramWorkoutKind get kind;

  /// The payload as Android encodes it: `kind` first, then the fields in declaration order, defaults
  /// left out (`perSide` only when true), doubles as Kotlin prints them ("5.0", also in a browser,
  /// where `jsonEncode(5.0)` would give "5").
  String toPayload() => kotlinJson(_toJson());

  Map<String, Object?> _toJson();

  /// The locale-free name of this workout (`ProgramWorkoutTitleCode.kt`).
  ProgramTitleCode get titleCode;

  /// Decodes a stored payload; null when it can't be read (as Android's `toProgramWorkoutOrNull`).
  static ProgramWorkout? fromPayload(String payload) {
    try {
      return fromJson(jsonDecode(payload));
    } on FormatException {
      return null;
    }
  }

  static ProgramWorkout? fromJson(Object? json) {
    if (json is! Map) return null;
    try {
      return switch (json['kind']) {
        'STRENGTH' => StrengthWorkout(
          title: StrengthDayTitle.fromCode(json['title'])!,
          slots: [for (final slot in json['slots'] as List) StrengthSlot.fromJson(slot as Map)],
        ),
        'OUTDOOR_RUN' => OutdoorRunWorkout(
          distanceKm: (json['distanceKm'] as num).toDouble(),
          walk: json['walk'] as bool,
        ),
        'TREADMILL' => TreadmillWorkout([
          for (final segment in json['segments'] as List) TreadmillSegment.fromJson(segment as Map),
        ]),
        'HOME_CIRCUIT' => HomeCircuitWorkout(
          rounds: json['rounds'] as int,
          workSeconds: json['workSeconds'] as int,
          restSeconds: json['restSeconds'] as int,
          restBetweenRoundsSeconds: json['restBetweenRoundsSeconds'] as int,
          exerciseIds: [for (final id in json['exerciseIds'] as List) id as int],
        ),
        _ => null,
      };
    } on Object {
      // A missing or mistyped field: unreadable, as kotlinx would throw
      return null;
    }
  }
}

final class StrengthWorkout extends ProgramWorkout {
  const StrengthWorkout({required this.title, required this.slots});

  final StrengthDayTitle title;
  final List<StrengthSlot> slots;

  @override
  ProgramWorkoutKind get kind => ProgramWorkoutKind.strength;

  @override
  ProgramTitleCode get titleCode => ProgramTitleCode.fromCode(title.code)!;

  @override
  Map<String, Object?> _toJson() => {
    'kind': kind.code,
    'title': title.code,
    'slots': [for (final slot in slots) slot.toJson()],
  };
}

sealed class CardioWorkout extends ProgramWorkout {
  const CardioWorkout();
}

final class OutdoorRunWorkout extends CardioWorkout {
  const OutdoorRunWorkout({required this.distanceKm, required this.walk});

  final double distanceKm;
  final bool walk;

  @override
  ProgramWorkoutKind get kind => ProgramWorkoutKind.outdoorRun;

  @override
  ProgramTitleCode get titleCode => walk ? ProgramTitleCode.briskWalk : ProgramTitleCode.outdoorRun;

  @override
  Map<String, Object?> _toJson() => {'kind': kind.code, 'distanceKm': KotlinDouble(distanceKm), 'walk': walk};
}

final class TreadmillWorkout extends CardioWorkout {
  const TreadmillWorkout(this.segments);

  final List<TreadmillSegment> segments;

  @override
  ProgramWorkoutKind get kind => ProgramWorkoutKind.treadmill;

  @override
  ProgramTitleCode get titleCode => ProgramTitleCode.treadmill;

  @override
  Map<String, Object?> _toJson() => {
    'kind': kind.code,
    'segments': [for (final segment in segments) segment.toJson()],
  };
}

final class HomeCircuitWorkout extends CardioWorkout {
  const HomeCircuitWorkout({
    required this.rounds,
    required this.workSeconds,
    required this.restSeconds,
    required this.restBetweenRoundsSeconds,
    required this.exerciseIds,
  });

  final int rounds;
  final int workSeconds;
  final int restSeconds;
  final int restBetweenRoundsSeconds;
  final List<int> exerciseIds;

  @override
  ProgramWorkoutKind get kind => ProgramWorkoutKind.homeCircuit;

  @override
  ProgramTitleCode get titleCode => ProgramTitleCode.homeCircuit;

  @override
  Map<String, Object?> _toJson() => {
    'kind': kind.code,
    'rounds': rounds,
    'workSeconds': workSeconds,
    'restSeconds': restSeconds,
    'restBetweenRoundsSeconds': restBetweenRoundsSeconds,
    'exerciseIds': exerciseIds,
  };
}

/// One exercise of a strength workout. REPS: each value of [reps] is a rep count; TIME: seconds.
/// With [perSide] every value is per side.
class StrengthSlot {
  const StrengthSlot({
    required this.position,
    required this.exerciseId,
    required this.type,
    required this.reps,
    required this.restSeconds,
    this.perSide = false,
  });

  final int position;
  final int exerciseId;
  final ProgramSlotType type;
  final List<int> reps;
  final int restSeconds;
  final bool perSide;

  int get sets => reps.length;

  Map<String, Object?> toJson() => {
    'position': position,
    'exerciseId': exerciseId,
    'type': type.code,
    'reps': reps,
    'restSeconds': restSeconds,
    if (perSide) 'perSide': true,
  };

  static StrengthSlot fromJson(Map<Object?, Object?> json) => StrengthSlot(
    position: json['position']! as int,
    exerciseId: json['exerciseId']! as int,
    type: ProgramSlotType.fromCode(json['type'])!,
    reps: [for (final rep in json['reps']! as List) rep as int],
    restSeconds: json['restSeconds']! as int,
    perSide: json['perSide'] as bool? ?? false,
  );
}

/// A double to encode as Kotlin's `Double.toString` does for the payloads' values: a whole number
/// keeps ".0" ("5.0"), others use the shortest form ("1.4").
class KotlinDouble {
  const KotlinDouble(this.value);

  final double value;

  @override
  String toString() {
    if (!value.isFinite) throw ArgumentError('Not a finite double: $value');
    if (value == value.truncateToDouble() && value.abs() < 1e7) return '${value.toInt()}.0';
    return value.toString();
  }
}

/// JSON text with kotlinx-serialization's compact format: no spaces, map order kept, strings
/// escaped by `jsonEncode`, [KotlinDouble] as Kotlin prints it.
String kotlinJson(Object? value) {
  final out = StringBuffer();
  void write(Object? v) {
    switch (v) {
      case null:
        out.write('null');
      case final KotlinDouble number:
        out.write(number);
      case final bool flag:
        out.write(flag);
      case final int whole:
        out.write(whole);
      case final String text:
        out.write(jsonEncode(text));
      case final List<Object?> list:
        out.write('[');
        for (var i = 0; i < list.length; i++) {
          if (i > 0) out.write(',');
          write(list[i]);
        }
        out.write(']');
      case final Map<String, Object?> map:
        out.write('{');
        var first = true;
        for (final MapEntry(:key, value: item) in map.entries) {
          if (!first) out.write(',');
          first = false;
          out
            ..write(jsonEncode(key))
            ..write(':');
          write(item);
        }
        out.write('}');
      default:
        throw ArgumentError('Not encodable: ${v.runtimeType}');
    }
  }

  write(value);
  return out.toString();
}

class TreadmillSegment {
  const TreadmillSegment({
    required this.durationSeconds,
    required this.speedKmh,
    required this.inclinePercent,
    required this.label,
  });

  final int durationSeconds;
  final double speedKmh;
  final double inclinePercent;
  final TreadmillSegmentLabel label;

  Map<String, Object?> toJson() => {
    'durationSeconds': durationSeconds,
    'speedKmh': KotlinDouble(speedKmh),
    'inclinePercent': KotlinDouble(inclinePercent),
    'label': label.code,
  };

  static TreadmillSegment fromJson(Map<Object?, Object?> json) => TreadmillSegment(
    durationSeconds: json['durationSeconds']! as int,
    speedKmh: (json['speedKmh']! as num).toDouble(),
    inclinePercent: (json['inclinePercent']! as num).toDouble(),
    label: TreadmillSegmentLabel.fromCode(json['label'])!,
  );
}
