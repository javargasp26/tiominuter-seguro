import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/workout_names.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/ui/workout_name_text.dart';

import 'golden.dart';

/// shared/golden/workout_names.json: the resolver, the post key and the rendered names in English and
/// Spanish give Android's results (WorkoutNamesGoldenTest.kt; the ARB texts match strings.xml).
void main() {
  final golden = Golden.load('workout_names');
  final en = lookupAppLocalizations(const Locale('en'));
  final es = lookupAppLocalizations(const Locale('es'));

  WorkoutNameCatalog catalogOf(Map<Object?, Object?> json) => WorkoutNameCatalog(
    gymRoutines: {
      for (final MapEntry(:key, :value) in (json['gymRoutines']! as Map).entries)
        int.parse(key as String): GymRoutineNames((value as Map)['name']! as String, value['nameEs'] as String?),
    },
    dailyRoutines: {
      for (final MapEntry(:key, :value) in (json['dailyRoutines']! as Map).entries)
        int.parse(key as String): DailyRoutineNames(
          title: (value as Map)['title'] as String?,
          titleEs: value['titleEs'] as String?,
        ),
    },
    programWorkouts: {
      for (final MapEntry(:key, :value) in (json['programWorkouts']! as Map).entries)
        key as String: ProgramTitleCode.fromCode(value as String)!,
    },
  );

  Map<String, Object?> nameJson(WorkoutName name) => switch (name) {
    GymWorkoutName(:final name, :final nameEs) => {'kind': 'gym', 'name': name, 'nameEs': nameEs},
    DailyWorkoutName(:final dayNumber, :final title, :final titleEs) => {
      'kind': 'daily',
      'dayNumber': dayNumber,
      'title': title,
      'titleEs': titleEs,
    },
    ProgramWorkoutName(:final code) => {'kind': 'program', 'code': code.code},
    ActivityWorkoutName(:final type) => {'kind': 'activity', 'type': type.code},
    StoredWorkoutName(:final text) => {'kind': 'stored', 'text': text},
    SourceOnlyWorkoutName(:final source) => {'kind': 'sourceOnly', 'source': source.code},
  };

  test('every entry and post is named as on Android, in English and Spanish', () {
    final catalog = catalogOf(golden['catalog']! as Map);
    final mismatches = Mismatches();
    final cases = Golden.cases(golden);
    for (final item in cases) {
      final id = item['id']! as String;
      final input = item['input']! as Map;
      final output = item['output']! as Map;
      final source = TrainingSource.fromCode(input['source'])!;
      final title = input['title']! as String;
      final WorkoutName name;
      if (item['kind'] == 'entry') {
        final entry = TrainingEntry(
          id: id,
          dateLocal: '2026-10-21',
          completedAt: 0,
          source: source,
          refId: input['refId'] as String?,
          title: title,
          durationSeconds: 900,
          activityTypeCode: input['activityType'] as String?,
        );
        name = WorkoutNameResolver.nameOf(entry, catalog);
        mismatches.check(
          id,
          'titleKey',
          WorkoutNameResolver.titleKeyOf(entry, catalog.programWorkouts[entry.refId]),
          output['titleKey'],
        );
      } else {
        final titleKey = input['titleKey'] as String?;
        name = WorkoutNameResolver.nameOfPost(
          titleKey: titleKey,
          source: source,
          activityType: ActivityType.fromCode(input['activityType']),
          title: title,
          catalog: catalog,
        );
        mismatches.check(id, 'parsedKey', WorkoutTitleKey.parse(titleKey, source)?.value, output['parsedKey']);
      }
      mismatches
        ..check(id, 'name $input', nameJson(name), output['name'])
        ..check(id, 'English text', name.text(en), output['en'])
        ..check(id, 'Spanish text', name.text(es), output['es']);
    }
    expect(mismatches.count, 0, reason: mismatches.report(25));
    expect(cases, hasLength(golden['caseCount']));
  });
}
