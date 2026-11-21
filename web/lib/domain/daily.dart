/// The daily routines as the web trains them, ported from Android's
/// `data/mapper/DailyMappers.kt` (slots in list order, an unknown type skipped, the rest slot by
/// [dailyRestExerciseId]), `domain/model/Routine.kt` / `Exercise.kt`, `toExerciseType`
/// (GymMappers.kt) and `domain/traininglog/DailyRefId.kt`.
library;

import 'kotlin_text.dart';

/// Id of the "Rest" exercise of `exercises.json`: a slot with it is a rest (Android's
/// `DAILY_REST_EXERCISE_ID`).
const int dailyRestExerciseId = 13;

/// `daily:<routineId>`, the refId of a DAILY entry and the key of its session.
String dailyRefId(int routineId) => 'daily:$routineId';

/// Title of a DAILY entry: the routine's day, plus " · title" when it has one (Android's
/// `dailyWorkoutTitle`; the stored title is only the names' fallback).
String dailyWorkoutTitle(String routineDay, String? title) =>
    title == null || isKotlinBlank(title) ? routineDay : '$routineDay · $title';

enum ExerciseType {
  time('TIME'),
  reps('REPS');

  const ExerciseType(this.code);

  final String code;

  /// Android's `toExerciseType`: case-insensitive; null for an unknown type.
  static ExerciseType? fromCode(Object? code) => switch (code is String ? code.toUpperCase() : null) {
    'TIME' => time,
    'REPS' => reps,
    _ => null,
  };
}

/// One slot of a daily routine: [amount] is seconds for TIME and target reps for REPS.
class RoutineExercise {
  const RoutineExercise({required this.exerciseId, required this.type, required this.amount, this.isRest = false});

  final int exerciseId;
  final ExerciseType type;
  final int amount;
  final bool isRest;
}

class DailyRoutine {
  const DailyRoutine({
    required this.routineId,
    required this.routineDay,
    required this.exercises,
    this.title,
    this.titleEs,
  });

  final int routineId;

  /// "Day 3" (English, as the content has it): the stored title.
  final String routineDay;
  final String? title;
  final String? titleEs;
  final List<RoutineExercise> exercises;
}

/// An entry of `exercises.json`: the names (English and the optional Spanish one).
class CatalogExercise {
  const CatalogExercise({required this.exerciseId, required this.name, this.nameEs, required this.animation});

  final int exerciseId;
  final String name;
  final String? nameEs;
  final String animation;
}

abstract final class DailyContent {
  /// `routines.json`: entries without the required fields are skipped; slots of an unknown type too.
  static List<DailyRoutine> routines(Object? decoded) {
    if (decoded is! List) throw const FormatException('routines.json is not a list');
    final routines = <DailyRoutine>[];
    for (final item in decoded) {
      if (item is! Map) continue;
      final id = item['routineId'];
      final day = item['routineDay'];
      final slots = item['exercises'];
      if (id is! int || day is! String || slots is! List) continue;
      final title = item['title'];
      final titleEs = item['titleEs'];
      routines.add(
        DailyRoutine(
          routineId: id,
          routineDay: day,
          title: title is String ? title : null,
          titleEs: titleEs is String ? titleEs : null,
          exercises: [
            for (final slot in slots)
              if (slot is Map && slot['exerciseId'] is int && slot['amount'] is int)
                if (ExerciseType.fromCode(slot['type']) case final type?)
                  RoutineExercise(
                    exerciseId: slot['exerciseId'] as int,
                    type: type,
                    amount: slot['amount'] as int,
                    isRest: slot['exerciseId'] == dailyRestExerciseId,
                  ),
          ],
        ),
      );
    }
    // Android lists them by routineId (DailyRoutineDao)
    routines.sort((a, b) => a.routineId.compareTo(b.routineId));
    return routines;
  }

  /// `exercises.json` by id.
  static Map<int, CatalogExercise> exercises(Object? decoded) {
    if (decoded is! List) throw const FormatException('exercises.json is not a list');
    return {
      for (final item in decoded)
        if (item is Map && item['exerciseId'] is int && item['name'] is String)
          item['exerciseId'] as int: CatalogExercise(
            exerciseId: item['exerciseId'] as int,
            name: item['name'] as String,
            nameEs: item['nameEs'] is String ? item['nameEs'] as String : null,
            animation: item['animation'] is String ? item['animation'] as String : '',
          ),
    };
  }
}
