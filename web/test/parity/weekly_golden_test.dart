import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/domain/group_summary.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/weekly_progress.dart';

import 'golden.dart';

/// shared/golden/weekly.json: counting, the goal in force, progress, streak and the member summary
/// give Android's results (WeeklyGoldenTest.kt).
void main() {
  final golden = Golden.load('weekly');

  test('every weekly case gives the same result as Android', () {
    final mismatches = Mismatches();
    final cases = Golden.cases(golden);
    for (final item in cases) {
      final id = item['id']! as String;
      final input = item['input']! as Map;
      final output = item['output']! as Map;
      final today = input['today']! as String;
      final goals = [
        for (final goal in (input['goals']! as List).cast<Map<Object?, Object?>>())
          WeeklyGoal(
            workoutsPerWeek: goal['workoutsPerWeek']! as int,
            effectiveFromWeek: goal['effectiveFromWeek']! as String,
          ),
      ];
      final rawEntries = (input['entries']! as List).cast<Map<Object?, Object?>>();
      final entries = [
        for (var i = 0; i < rawEntries.length; i++)
          TrainingEntry(
            id: 'entry-$i',
            dateLocal: rawEntries[i]['dateLocal']! as String,
            completedAt: 0,
            source: TrainingSource.fromCode(rawEntries[i]['source'])!,
            title: '',
            durationSeconds: rawEntries[i]['durationSeconds'] as int?,
          ),
      ];
      final perDay = countedWorkoutsPerDay(entries);
      final week = WeeklyProgressCalculator.currentWeek(today, goals, perDay);
      final summary = GroupSummaryCalculator.summary(today, entries, goals);
      final progress = output['progress']! as Map;
      mismatches
        ..check(id, 'counts', [for (final e in entries) countsTowardGoal(e)], output['counts'])
        ..check(id, 'countedPerDay', perDay, output['countedPerDay'])
        ..check(
          id,
          'goalForWeek',
          WeeklyProgressCalculator.goalForWeek(goals, CalendarDates.mondayOf(today)),
          output['goalForWeek'],
        )
        ..check(id, 'progress.weekStart', week.progress.weekStart, progress['weekStart'])
        ..check(id, 'progress.goal', week.progress.goal, progress['goal'])
        ..check(id, 'progress.workoutCount', week.progress.workoutCount, progress['workoutCount'])
        ..check(id, 'progress.workoutsPerDay', week.progress.workoutsPerDay, progress['workoutsPerDay'])
        ..check(
          id,
          'progress.trainedDates',
          [
            for (final MapEntry(:key, :value) in week.progress.workoutsPerDay.entries)
              if (value > 0) key,
          ]..sort(),
          progress['trainedDates'],
        )
        ..check(id, 'progress.isGoalMet', week.progress.isGoalMet, progress['isGoalMet'])
        ..check(id, 'streak', week.streak, output['streak'])
        ..check(id, 'workoutsNeeded', week.workoutsNeeded, output['workoutsNeeded'])
        ..check(
          id,
          'summary',
          summary == null
              ? null
              : {
                  'weekStartLocal': summary.weekStartLocal,
                  'workoutsThisWeek': summary.workoutsThisWeek,
                  'weeklyGoal': summary.weeklyGoal,
                  'streakWeeks': summary.streakWeeks,
                  'daysThisWeek': {
                    for (final MapEntry(:key, :value) in summary.daysThisWeek.entries)
                      '$key': [for (final source in value) source.code],
                  },
                  'lastTrainedLocal': summary.lastTrainedLocal,
                },
          output['summary'],
        );
    }
    expect(mismatches.count, 0, reason: mismatches.report());
    expect(cases, hasLength(golden['caseCount']));
  });

  test('the read-back streak equals the full streak on every case', () async {
    for (final item in Golden.cases(golden)) {
      final input = item['input']! as Map;
      final today = input['today']! as String;
      final goals = [
        for (final goal in (input['goals']! as List).cast<Map<Object?, Object?>>())
          WeeklyGoal(
            workoutsPerWeek: goal['workoutsPerWeek']! as int,
            effectiveFromWeek: goal['effectiveFromWeek']! as String,
          ),
      ];
      final entries = [
        for (final (i, raw) in (input['entries']! as List).cast<Map<Object?, Object?>>().indexed)
          TrainingEntry(
            id: 'e$i',
            dateLocal: raw['dateLocal']! as String,
            completedAt: 0,
            source: TrainingSource.fromCode(raw['source'])!,
            title: '',
            durationSeconds: raw['durationSeconds'] as int?,
          ),
      ];
      final perDay = countedWorkoutsPerDay(entries);
      final monday = CalendarDates.mondayOf(today);
      final streak = await streakReadingBack(
        today: today,
        goals: goals,
        currentWeekPerDay: {
          for (final MapEntry(:key, :value) in perDay.entries)
            if (key.compareTo(monday) >= 0) key: value,
        },
        loadPast: (from, until) async => {
          for (final MapEntry(:key, :value) in perDay.entries)
            if (key.compareTo(from) >= 0 && key.compareTo(until) < 0) key: value,
        },
      );
      expect(streak, (item['output']! as Map)['streak'], reason: item['id'] as String);
    }
  });
}
