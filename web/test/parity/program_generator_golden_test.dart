import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/domain/program.dart';
import 'package:tiominuter_web/domain/program_content.dart';
import 'package:tiominuter_web/domain/program_generator.dart';

import 'golden.dart';

/// shared/golden/program_generator.json: the Dart generator gives Android's programs, payload
/// strings byte for byte (ProgramGeneratorGoldenTest.kt).
void main() {
  final golden = Golden.load('program_generator');
  final payloads = [for (final p in golden['payloads']! as List) p as String];
  const exercisesFile = 'program_exercises.json';
  const templatesFile = 'program_templates.json';

  test('the golden was made from the current content/v1 program files', () {
    final content = golden['content']! as Map;
    expect(Golden.sha256Of(exercisesFile), content[exercisesFile], reason: 'run scripts/update_golden.sh');
    expect(Golden.sha256Of(templatesFile), content[templatesFile], reason: 'run scripts/update_golden.sh');
  });

  test('every profile of the matrix gives the same program as Android', () {
    final catalog = ProgramContent.catalog(jsonDecode(Golden.contentText(exercisesFile)));
    final templates = ProgramContent.templates(jsonDecode(Golden.contentText(templatesFile)));
    final mismatches = Mismatches();
    final cases = Golden.cases(golden);
    for (final item in cases) {
      final id = item['id']! as String;
      final input = item['input']! as Map;
      final output = item['output']! as Map;
      final profile = ProgramProfile(
        age: input['age']! as int,
        weightKg: (input['weightKg']! as num).toDouble(),
        heightCm: input['heightCm']! as int,
        level: ProgramLevel.fromCode(input['level'])!,
        goal: ProgramGoal.fromCode(input['goal'])!,
        location: ProgramLocation.fromCode(input['location'])!,
        weeks: input['weeks']! as int,
        daysPerWeek: input['daysPerWeek']! as int,
        focus: ProgramFocus.fromCode(input['focus'])!,
      );
      mismatches
        ..check(id, 'bmi', computeBmi(profile.weightKg, profile.heightCm), output['bmi'])
        ..check(id, 'lowImpact', isLowImpact(profile, templates.impact), output['lowImpact'])
        ..check(id, 'senior', isSenior(profile, templates.impact), output['senior']);
      final program = ProgramGenerator.generate(profile, catalog, templates);
      final expectedWeeks = output['weeks']! as List;
      mismatches.check(id, 'week count', program.weeks.length, expectedWeeks.length);
      for (var w = 0; w < program.weeks.length && w < expectedWeeks.length; w++) {
        final week = program.weeks[w];
        final expected = expectedWeeks[w] as Map;
        mismatches
          ..check(id, 'week ${w + 1} number', week.weekNumber, expected['weekNumber'])
          ..check(id, 'week ${w + 1} deload', week.isDeload, expected['isDeload']);
        final expectedWorkouts = expected['workouts']! as List;
        mismatches.check(id, 'week ${w + 1} workouts', week.workouts.length, expectedWorkouts.length);
        for (var d = 0; d < week.workouts.length && d < expectedWorkouts.length; d++) {
          final workout = week.workouts[d];
          final expectedWorkout = expectedWorkouts[d] as Map;
          final expectedPayload = payloads[expectedWorkout['payload']! as int];
          mismatches
            ..check(id, 'week ${w + 1} day $d kind', workout.kind.code, expectedWorkout['kind'])
            ..check(id, 'week ${w + 1} day $d payload', workout.toPayload(), expectedPayload);
        }
      }
    }
    expect(mismatches.count, 0, reason: mismatches.report());
    expect(cases, hasLength(golden['caseCount']));
  });

  test('every golden payload decodes and encodes back to the same string', () {
    for (final payload in payloads) {
      final workout = ProgramWorkout.fromPayload(payload);
      expect(workout, isNotNull, reason: payload);
      expect(workout!.toPayload(), payload);
    }
  });
}
