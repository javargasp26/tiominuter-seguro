/// Workout names, ported from Android's `WorkoutNameResolver`
/// (app/src/main/java/com/javargasp/tiominutermvi/domain/traininglog/WorkoutNames.kt) with the same
/// fallback order. The stored `title` (in the recording device's language) is only the fallback;
/// the name is resolved at display time in the viewer's language (`ui/workout_name_text.dart`).
library;

import 'kotlin_text.dart';
import 'training.dart';

/// The locale-free name of a program workout (Android's `ProgramTitleCode`). Its [code] is the
/// code of a feed post's `program/<CODE>` key: never rename or remove a value.
enum ProgramTitleCode {
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
  lowerC('LOWER_C'),
  outdoorRun('OUTDOOR_RUN'),
  briskWalk('BRISK_WALK'),
  treadmill('TREADMILL'),
  homeCircuit('HOME_CIRCUIT');

  const ProgramTitleCode(this.code);

  /// The code in posts and payloads (Android's enum name).
  final String code;

  /// The code of [code]; null for one this app doesn't know (a newer app's).
  static ProgramTitleCode? fromCode(String code) {
    for (final value in values) {
      if (value.code == code) return value;
    }
    return null;
  }
}

/// What a workout is called, without the language.
sealed class WorkoutName {
  const WorkoutName();
}

/// A gym routine of the catalog: its English and Spanish names.
final class GymWorkoutName extends WorkoutName {
  const GymWorkoutName(this.name, this.nameEs);

  final String name;
  final String? nameEs;

  @override
  bool operator ==(Object other) => other is GymWorkoutName && other.name == name && other.nameEs == nameEs;

  @override
  int get hashCode => Object.hash(name, nameEs);

  @override
  String toString() => 'Gym($name, $nameEs)';
}

/// A daily routine: "Day N", plus its content title (and Spanish title) when it has one.
final class DailyWorkoutName extends WorkoutName {
  const DailyWorkoutName(this.dayNumber, {this.title, this.titleEs});

  final int dayNumber;
  final String? title;
  final String? titleEs;

  @override
  bool operator ==(Object other) =>
      other is DailyWorkoutName && other.dayNumber == dayNumber && other.title == title && other.titleEs == titleEs;

  @override
  int get hashCode => Object.hash(dayNumber, title, titleEs);

  @override
  String toString() => 'Daily($dayNumber, $title, $titleEs)';
}

/// A program workout, named by its code.
final class ProgramWorkoutName extends WorkoutName {
  const ProgramWorkoutName(this.code);

  final ProgramTitleCode code;

  @override
  bool operator ==(Object other) => other is ProgramWorkoutName && other.code == code;

  @override
  int get hashCode => code.hashCode;

  @override
  String toString() => 'Program(${code.code})';
}

/// A logged activity of a named type (OTHER with a custom name is [StoredWorkoutName]).
final class ActivityWorkoutName extends WorkoutName {
  const ActivityWorkoutName(this.type);

  final ActivityType type;

  @override
  bool operator ==(Object other) => other is ActivityWorkoutName && other.type == type;

  @override
  int get hashCode => type.hashCode;

  @override
  String toString() => 'Activity(${type.code})';
}

/// A text shown as it is: an activity's custom name, or the stored title as the fallback.
final class StoredWorkoutName extends WorkoutName {
  const StoredWorkoutName(this.text);

  final String text;

  @override
  bool operator ==(Object other) => other is StoredWorkoutName && other.text == text;

  @override
  int get hashCode => text.hashCode;

  @override
  String toString() => 'Stored($text)';
}

/// Nothing to name it by: the source label ("Gym routine").
final class SourceOnlyWorkoutName extends WorkoutName {
  const SourceOnlyWorkoutName(this.source);

  final TrainingSource source;

  @override
  bool operator ==(Object other) => other is SourceOnlyWorkoutName && other.source == source;

  @override
  int get hashCode => source.hashCode;

  @override
  String toString() => 'SourceOnly(${source.code})';
}

class GymRoutineNames {
  const GymRoutineNames(this.name, this.nameEs);

  final String name;
  final String? nameEs;
}

class DailyRoutineNames {
  const DailyRoutineNames({this.title, this.titleEs});

  final String? title;
  final String? titleEs;
}

/// The names the viewer's browser knows: the gym and daily catalogs of /content/v1 (by routineId)
/// and the program workouts of the viewer's own history (by the Firestore refId
/// `program:<programUuid>:<week>:<day>`).
class WorkoutNameCatalog {
  const WorkoutNameCatalog({
    this.gymRoutines = const {},
    this.dailyRoutines = const {},
    this.programWorkouts = const {},
  });

  static const empty = WorkoutNameCatalog();

  final Map<int, GymRoutineNames> gymRoutines;
  final Map<int, DailyRoutineNames> dailyRoutines;
  final Map<String, ProgramTitleCode> programWorkouts;

  WorkoutNameCatalog withProgramWorkouts(Map<String, ProgramTitleCode> programWorkouts) =>
      WorkoutNameCatalog(gymRoutines: gymRoutines, dailyRoutines: dailyRoutines, programWorkouts: programWorkouts);
}

/// A feed post's locale-free name (`titleKey`, docs/firestore-model.md "Title keys"): the same
/// strict format as Android's `WorkoutTitleKey` and the rules' `isPostTitleKey`.
sealed class WorkoutTitleKey {
  const WorkoutTitleKey();

  static const int maxLength = 80;
  static final RegExp _number = RegExp(r'^[1-9][0-9]{0,8}$');
  static final RegExp _code = RegExp(r'^[A-Z][A-Z0-9_]*$');

  TrainingSource get source;

  /// The stored text of the key (`gym/64`, `daily/3`, `program/FULL_BODY_A`).
  String get value;

  /// [key]'s value when it is valid (a key the rules would refuse is never sent).
  static String? valueOrNull(WorkoutTitleKey key) {
    final value = key.value;
    final parsed = parse(value, key.source);
    return parsed != null && parsed.value == value ? value : null;
  }

  /// The key of [value] when it is valid for a post of [source]; null otherwise.
  static WorkoutTitleKey? parse(String? value, TrainingSource source) {
    if (value == null || value.length > maxLength) return null;
    WorkoutTitleKey? key;
    if (value.startsWith('gym/')) {
      final rest = value.substring(4);
      if (_number.hasMatch(rest)) key = GymTitleKey(int.parse(rest));
    } else if (value.startsWith('daily/')) {
      final rest = value.substring(6);
      if (_number.hasMatch(rest)) key = DailyTitleKey(int.parse(rest));
    } else if (value.startsWith('program/')) {
      final rest = value.substring(8);
      if (_code.hasMatch(rest)) key = ProgramTitleKey(rest);
    }
    return key != null && key.source == source ? key : null;
  }
}

final class GymTitleKey extends WorkoutTitleKey {
  const GymTitleKey(this.routineId);

  final int routineId;

  @override
  TrainingSource get source => TrainingSource.gym;

  @override
  String get value => 'gym/$routineId';
}

final class DailyTitleKey extends WorkoutTitleKey {
  const DailyTitleKey(this.dayNumber);

  final int dayNumber;

  @override
  TrainingSource get source => TrainingSource.daily;

  @override
  String get value => 'daily/$dayNumber';
}

final class ProgramTitleKey extends WorkoutTitleKey {
  const ProgramTitleKey(this.code);

  final String code;

  @override
  TrainingSource get source => TrainingSource.program;

  @override
  String get value => 'program/$code';
}

/// The routine id of a `gym:<id>` refId; null for any other refId.
int? gymRoutineIdOf(String? refId) => _idAfter(refId, 'gym:');

/// The routine id (day number) of a `daily:<id>` refId; null for any other refId.
int? dailyRoutineIdOf(String? refId) => _idAfter(refId, 'daily:');

int? _idAfter(String? refId, String prefix) {
  if (refId == null || !refId.startsWith(prefix)) return null;
  // Kotlin's toIntOrNull: no hex, no spaces, Int range only
  return kotlinToIntOrNull(refId.substring(prefix.length));
}

/// (programUuid, week, dayIndex) of a Firestore PROGRAM refId; null for any other refId.
({String programId, int week, int dayIndex})? programWorkoutOf(String? refId) {
  if (refId == null) return null;
  final parts = refId.split(':');
  if (parts.length != 4 || parts[0] != 'program' || parts[1].isEmpty) return null;
  final week = int.tryParse(parts[2]);
  final dayIndex = int.tryParse(parts[3]);
  if (week == null || dayIndex == null) return null;
  return (programId: parts[1], week: week, dayIndex: dayIndex);
}

/// The single rule for what a workout is called (Android's `WorkoutNameResolver`).
abstract final class WorkoutNameResolver {
  /// A history entry. GYM: the routine of `gym:<id>` in the catalog, else the stored title. DAILY:
  /// "Day N" from `daily:<N>` (with the routine's title if the catalog has one), else the stored
  /// title. PROGRAM: the workout of its refId, else the stored title. ACTIVITY: OTHER's custom
  /// name, else its type. A blank stored title shows the source.
  static WorkoutName nameOf(TrainingEntry entry, WorkoutNameCatalog catalog) {
    final WorkoutName? resolved = switch (entry.source) {
      TrainingSource.gym => _gymName(gymRoutineIdOf(entry.refId), catalog),
      TrainingSource.daily => switch (dailyRoutineIdOf(entry.refId)) {
        final int day when day >= 1 => _dailyName(day, catalog),
        _ => null,
      },
      TrainingSource.program => switch (catalog.programWorkouts[entry.refId]) {
        final ProgramTitleCode code => ProgramWorkoutName(code),
        null => null,
      },
      TrainingSource.activity => _activityName(entry.activity, entry.title),
    };
    return resolved ?? _stored(entry.title, entry.source);
  }

  /// A feed post: its [titleKey] when it is valid for its [source] and the reader knows what it
  /// names (a gym routine in the catalog, a known program code; a day number always), else the
  /// author's [title]. An activity is named by its [activityType].
  static WorkoutName nameOfPost({
    required String? titleKey,
    required TrainingSource source,
    required ActivityType? activityType,
    required String title,
    required WorkoutNameCatalog catalog,
  }) {
    if (source == TrainingSource.activity) {
      return _activityName(activityType, title) ?? _stored(title, source);
    }
    final WorkoutName? resolved = switch (WorkoutTitleKey.parse(titleKey, source)) {
      GymTitleKey(:final routineId) => _gymName(routineId, catalog),
      DailyTitleKey(:final dayNumber) => _dailyName(dayNumber, catalog),
      ProgramTitleKey(:final code) => switch (ProgramTitleCode.fromCode(code)) {
        final ProgramTitleCode known => ProgramWorkoutName(known),
        null => null,
      },
      null => null,
    };
    return resolved ?? _stored(title, source);
  }

  /// The `titleKey` of [entry]'s post: GYM and DAILY from their refId; PROGRAM from its workout
  /// ([programCode], looked up by the caller); none for an activity or when nothing is known.
  static String? titleKeyOf(TrainingEntry entry, ProgramTitleCode? programCode) {
    final WorkoutTitleKey? key = switch (entry.source) {
      TrainingSource.gym => switch (gymRoutineIdOf(entry.refId)) {
        final int id => GymTitleKey(id),
        null => null,
      },
      TrainingSource.daily => switch (dailyRoutineIdOf(entry.refId)) {
        final int day => DailyTitleKey(day),
        null => null,
      },
      TrainingSource.program => programCode == null ? null : ProgramTitleKey(programCode.code),
      TrainingSource.activity => null,
    };
    return key == null ? null : WorkoutTitleKey.valueOrNull(key);
  }

  static WorkoutName? _gymName(int? routineId, WorkoutNameCatalog catalog) {
    final names = catalog.gymRoutines[routineId];
    return names == null ? null : GymWorkoutName(names.name, names.nameEs);
  }

  static WorkoutName _dailyName(int dayNumber, WorkoutNameCatalog catalog) {
    final names = catalog.dailyRoutines[dayNumber];
    return DailyWorkoutName(dayNumber, title: names?.title, titleEs: names?.titleEs);
  }

  static WorkoutName? _activityName(ActivityType? type, String title) {
    if (type == null) return null;
    if (type == ActivityType.other && !isKotlinBlank(title)) return StoredWorkoutName(title);
    return ActivityWorkoutName(type);
  }

  static WorkoutName _stored(String title, TrainingSource source) =>
      isKotlinBlank(title) ? SourceOnlyWorkoutName(source) : StoredWorkoutName(title);
}

/// The title code of a synced program workout, from its `payload` JSON (Android's
/// `ProgramWorkout.titleCode()`, domain/program/model/ProgramWorkoutTitleCode.kt; the payload's
/// discriminator is `kind`). Null for a payload this app can't read.
ProgramTitleCode? programTitleCodeOfPayload(Object? decoded) {
  if (decoded is! Map) return null;
  return switch (decoded['kind']) {
    'STRENGTH' => switch (decoded['title']) {
      final String title => ProgramTitleCode.fromCode(title),
      _ => null,
    },
    'OUTDOOR_RUN' => decoded['walk'] == true ? ProgramTitleCode.briskWalk : ProgramTitleCode.outdoorRun,
    'TREADMILL' => ProgramTitleCode.treadmill,
    'HOME_CIRCUIT' => ProgramTitleCode.homeCircuit,
    _ => null,
  };
}
