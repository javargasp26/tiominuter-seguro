import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/workout_names.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/ui/workout_name_text.dart';

/// Port of Android's WorkoutNameResolverTest (app/src/test/.../domain/traininglog/), with the web's
/// real English and Spanish strings: every source × language × known / unknown / removed / old data,
/// history entries and feed posts. Program refIds are Firestore ones (`program:<uuid>:<week>:<day>`).
void main() {
  final en = lookupAppLocalizations(const Locale('en'));
  final es = lookupAppLocalizations(const Locale('es'));

  const catalog = WorkoutNameCatalog(
    gymRoutines: {64: GymRoutineNames('Pull Day', 'Día de tirón'), 65: GymRoutineNames('Push Day', null)},
    dailyRoutines: {
      5: DailyRoutineNames(),
      6: DailyRoutineNames(title: 'Core', titleEs: 'Abdomen'),
      7: DailyRoutineNames(title: 'Mobility'),
    },
    programWorkouts: {
      'program:9a1b2c3d-4e5f-4a6b-8c7d-112233445566:1:0': ProgramTitleCode.upperA,
      'program:9a1b2c3d-4e5f-4a6b-8c7d-112233445566:1:1': ProgramTitleCode.treadmill,
    },
  );
  const program = 'program:9a1b2c3d-4e5f-4a6b-8c7d-112233445566';

  TrainingEntry entry(TrainingSource source, String? refId, String title, {String? activity}) => TrainingEntry(
    id: 'u',
    dateLocal: '2026-10-19',
    completedAt: 0,
    source: source,
    refId: refId,
    title: title,
    durationSeconds: 1800,
    activityTypeCode: activity,
  );

  (String, String) both(TrainingEntry e) {
    final name = WorkoutNameResolver.nameOf(e, catalog);
    return (name.text(en), name.text(es));
  }

  group('GYM', () {
    test('the catalog routine in each language, recorded in English', () {
      expect(both(entry(TrainingSource.gym, 'gym:64', 'Pull Day')), ('Pull Day', 'Día de tirón'));
    });

    test('a routine without a Spanish name shows its English name in Spanish', () {
      expect(both(entry(TrainingSource.gym, 'gym:65', 'Push Day')), ('Push Day', 'Push Day'));
    });

    test('a removed routine, a malformed refId or old data keep the stored title', () {
      expect(both(entry(TrainingSource.gym, 'gym:99', 'Old routine')), ('Old routine', 'Old routine'));
      expect(both(entry(TrainingSource.gym, 'gym:x', 'Pull Day')), ('Pull Day', 'Pull Day'));
      expect(both(entry(TrainingSource.gym, null, 'Pull Day')), ('Pull Day', 'Pull Day'));
      expect(both(entry(TrainingSource.gym, null, '')), ('Gym routine', 'Rutina de gimnasio'));
    });
  });

  group('DAILY', () {
    test('the day number in each language, never the stored English text', () {
      expect(both(entry(TrainingSource.daily, 'daily:5', 'Day 5')), ('Day 5', 'Día 5'));
    });

    test('a routine with a title shows it, in Spanish only its Spanish title', () {
      expect(both(entry(TrainingSource.daily, 'daily:6', 'Day 6 · Core')), ('Day 6 · Core', 'Día 6 · Abdomen'));
      expect(both(entry(TrainingSource.daily, 'daily:7', 'Day 7 · Mobility')), ('Day 7 · Mobility', 'Día 7'));
    });

    test('a routine not in the catalog is still its day, a malformed refId or old data keep the stored title', () {
      expect(both(entry(TrainingSource.daily, 'daily:30', 'Day 30')), ('Day 30', 'Día 30'));
      expect(both(entry(TrainingSource.daily, null, 'Day 1')), ('Day 1', 'Day 1'));
      expect(both(entry(TrainingSource.daily, 'daily:0', 'Day 0')), ('Day 0', 'Day 0'));
      expect(both(entry(TrainingSource.daily, 'gym:1', 'Day 1')), ('Day 1', 'Day 1'));
    });
  });

  group('PROGRAM', () {
    test('the workout of the refId, whatever language recorded it', () {
      expect(both(entry(TrainingSource.program, '$program:1:0', 'Tren superior A')), ('Upper A', 'Tren superior A'));
      expect(both(entry(TrainingSource.program, '$program:1:0', 'Upper A')), ('Upper A', 'Tren superior A'));
      expect(both(entry(TrainingSource.program, '$program:1:1', 'Treadmill')), ('Treadmill', 'Cinta de correr'));
    });

    test('a deleted program or old data keep the stored title', () {
      expect(both(entry(TrainingSource.program, '$program:2:0', 'Tren superior A')), (
        'Tren superior A',
        'Tren superior A',
      ));
      expect(both(entry(TrainingSource.program, null, 'Full body A')), ('Full body A', 'Full body A'));
    });

    test('the refId parser takes the Firestore form only', () {
      expect(programWorkoutOf('$program:2:1'), (
        programId: '9a1b2c3d-4e5f-4a6b-8c7d-112233445566',
        week: 2,
        dayIndex: 1,
      ));
      expect(programWorkoutOf('program::2:1'), isNull);
      expect(programWorkoutOf('program:x:y:1'), isNull);
      expect(programWorkoutOf('gym:64'), isNull);
      expect(programWorkoutOf(null), isNull);
    });
  });

  group('ACTIVITY', () {
    test('its type in each language, OTHER shows the custom name', () {
      expect(both(entry(TrainingSource.activity, null, '', activity: 'RUNNING')), ('Running', 'Correr'));
      expect(both(entry(TrainingSource.activity, null, 'Padel', activity: 'OTHER')), ('Padel', 'Padel'));
      expect(both(entry(TrainingSource.activity, null, '', activity: 'OTHER')), ('Other', 'Otra'));
    });

    test('an unknown type code keeps the stored title, or the source', () {
      expect(both(entry(TrainingSource.activity, null, 'Climbing', activity: 'FUTURE_TYPE')), ('Climbing', 'Climbing'));
      expect(both(entry(TrainingSource.activity, null, '', activity: 'FUTURE_TYPE')), ('Activity', 'Actividad'));
    });
  });

  group('feed posts', () {
    (String, String) post(String? titleKey, TrainingSource source, String title, {ActivityType? activity}) {
      final name = WorkoutNameResolver.nameOfPost(
        titleKey: titleKey,
        source: source,
        activityType: activity,
        title: title,
        catalog: catalog,
      );
      return (name.text(en), name.text(es));
    }

    test("a post is named in the reader's language from its key, whoever wrote it", () {
      expect(post('gym/64', TrainingSource.gym, 'Pull Day'), ('Pull Day', 'Día de tirón'));
      expect(post('daily/5', TrainingSource.daily, 'Day 5'), ('Day 5', 'Día 5'));
      expect(post('program/UPPER_A', TrainingSource.program, 'Upper A'), ('Upper A', 'Tren superior A'));
      expect(post('gym/64', TrainingSource.gym, 'Día de tirón'), ('Pull Day', 'Día de tirón'));
      expect(post('program/LOWER_B', TrainingSource.program, 'Tren inferior B'), ('Lower B', 'Tren inferior B'));
      expect(post('daily/6', TrainingSource.daily, 'Día 6'), ('Day 6 · Core', 'Día 6 · Abdomen'));
    });

    test('a post without a usable key shows its title', () {
      expect(post(null, TrainingSource.gym, 'Pull Day'), ('Pull Day', 'Pull Day'));
      expect(post('gym/99', TrainingSource.gym, 'Old routine'), ('Old routine', 'Old routine'));
      expect(post('program/HYROX', TrainingSource.program, 'Hyrox'), ('Hyrox', 'Hyrox'));
      expect(post('daily/5', TrainingSource.gym, 'Pull Day'), ('Pull Day', 'Pull Day'));
      expect(post('gym/064', TrainingSource.gym, 'Pull Day'), ('Pull Day', 'Pull Day'));
      expect(post('program/??', TrainingSource.program, ''), ('Program', 'Programa'));
    });

    test('an activity post is named by its type and ignores any key', () {
      expect(post(null, TrainingSource.activity, '', activity: ActivityType.running), ('Running', 'Correr'));
      expect(post('gym/64', TrainingSource.activity, '', activity: ActivityType.running), ('Running', 'Correr'));
      expect(post(null, TrainingSource.activity, 'Padel', activity: ActivityType.other), ('Padel', 'Padel'));
    });
  });

  group('title keys', () {
    test('the key format is as strict as the rules', () {
      final valid = {
        'gym/1': TrainingSource.gym,
        'gym/999999999': TrainingSource.gym,
        'daily/30': TrainingSource.daily,
        'program/UPPER_A': TrainingSource.program,
        'program/${'A' * 72}': TrainingSource.program,
      };
      valid.forEach((key, source) => expect(WorkoutTitleKey.parse(key, source), isNotNull, reason: key));
      for (final key in [
        '', 'gym/', 'gym/0', 'gym/064', 'gym/x', 'gym/1234567890', 'GYM/64', 'gym:64', ' gym/64', 'gym/64 ', //
        'gym/64/extra', 'daily/5', 'program/UPPER_A',
      ]) {
        expect(WorkoutTitleKey.parse(key, TrainingSource.gym), isNull, reason: key);
      }
      for (final key in [
        'program/upper_a',
        'program/',
        'program/1A',
        'program/_A',
        'program/A-B',
        'program/${'A' * 73}',
        'gym/1',
      ]) {
        expect(WorkoutTitleKey.parse(key, TrainingSource.program), isNull, reason: key);
      }
      expect(WorkoutTitleKey.parse('gym/64', TrainingSource.activity), isNull);
      expect(WorkoutTitleKey.parse(null, TrainingSource.gym), isNull);
    });

    test('program codes are the same as Android and never change', () {
      expect(ProgramTitleCode.values.map((code) => code.code), [
        'FULL_BODY_A', 'FULL_BODY_B', 'FULL_BODY_C', 'UPPER_A', 'UPPER_B', 'LOWER_A', 'LOWER_B', 'PUSH', 'PULL', //
        'LEGS', 'FULL_BODY', 'UPPER', 'LOWER', 'UPPER_C', 'LOWER_C', 'OUTDOOR_RUN', 'BRISK_WALK', 'TREADMILL', //
        'HOME_CIRCUIT',
      ]);
      for (final code in ProgramTitleCode.values) {
        expect(ProgramTitleCode.fromCode(code.code), code);
        expect(programTitle(code, en), isNotEmpty);
        expect(programTitle(code, es), isNotEmpty);
      }
      expect(ProgramTitleCode.fromCode('upper_a'), isNull);
    });
  });

  test('durations are whole minutes, rounded down, at least 1', () {
    expect(durationText(590, en), '9 min');
    expect(durationText(30, es), '1 min');
    expect(durationText(2700, en), '45 min');
  });
}
