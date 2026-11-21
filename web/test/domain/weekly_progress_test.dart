import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/weekly_progress.dart';

/// Ports of Android's CalendarDatesTest, WorkoutCountingTest and WeeklyProgressCalculatorTest
/// (app/src/test/.../domain/), case by case, plus the web's read-back streak. 19 October 2026 is a
/// Monday; the current week in most tests is 19–25 October.
void main() {
  Map<String, int> once(List<String> dates) => {for (final date in dates) date: 1};
  WeeklyGoal goal(int workouts, String week) => WeeklyGoal(workoutsPerWeek: workouts, effectiveFromWeek: week);

  group('CalendarDates', () {
    test('mondayOf returns the Monday of the week for every day', () {
      for (final date in CalendarDates.weekDates('2026-10-22')) {
        expect(CalendarDates.mondayOf(date), '2026-10-19');
      }
      expect(CalendarDates.mondayOf('2026-10-25'), '2026-10-19', reason: 'Sunday');
      expect(CalendarDates.mondayOf('2026-10-26'), '2026-10-26', reason: 'Monday');
    });

    test('weeks cross month, year and leap-day boundaries', () {
      expect(CalendarDates.weekDates('2026-12-31'), [
        '2026-12-28', '2026-12-29', '2026-12-30', '2026-12-31', '2027-01-01', '2027-01-02', '2027-01-03', //
      ]);
      expect(CalendarDates.mondayOf('2028-03-01'), '2028-02-28');
      expect(CalendarDates.plusDays('2028-02-28', 1), '2028-02-29');
    });

    test('a week with a daylight saving change still has 7 consecutive dates', () {
      expect(CalendarDates.weekDates('2026-10-25'), [
        '2026-10-19', '2026-10-20', '2026-10-21', '2026-10-22', '2026-10-23', '2026-10-24', '2026-10-25', //
      ]);
    });

    test('plusWeeks moves Monday to Monday from any day', () {
      expect(CalendarDates.plusWeeks('2026-10-22', -1), '2026-10-12');
      expect(CalendarDates.plusWeeks('2026-10-25', 1), '2026-10-26');
    });

    test('an invalid date is rejected', () {
      for (final bad in ['2026-02-30', '2026-13-01', '26-10-19', '2026-10-1', 'today']) {
        expect(() => CalendarDates.mondayOf(bad), throwsArgumentError, reason: bad);
        expect(CalendarDates.isValid(bad), isFalse, reason: bad);
      }
    });

    test("today is the device's local date", () {
      final dates = LocalDates(now: () => DateTime(2026, 10, 25, 23, 59));
      expect(dates.today(), '2026-10-25');
      expect(dates.currentWeekStart(), '2026-10-19');
    });
  });

  group('the 10-minute rule', () {
    TrainingEntry entry(int? seconds, {String date = '2026-10-19'}) => TrainingEntry(
      id: 'e$seconds$date',
      dateLocal: date,
      completedAt: 0,
      source: TrainingSource.gym,
      title: '',
      durationSeconds: seconds,
    );

    test('the minimum is 10 minutes', () => expect(minCountedDurationSeconds, 600));

    test('an entry counts from exactly the minimum', () {
      expect(countsTowardGoal(entry(599)), isFalse);
      expect(countsTowardGoal(entry(600)), isTrue);
      expect(countsTowardGoal(entry(601)), isTrue);
    });

    test('an unknown duration counts', () => expect(countsTowardGoal(entry(null)), isTrue));

    test('workouts per day count every counted entry, also several in one day', () {
      final entries = [entry(600), entry(1200), entry(30), entry(null, date: '2026-10-20')];
      expect(countedWorkoutsPerDay(entries), {'2026-10-19': 2, '2026-10-20': 1});
    });

    test('the sources of a day are distinct and in source order', () {
      TrainingEntry of(TrainingSource source, int seconds) => TrainingEntry(
        id: '$source$seconds',
        dateLocal: '2026-10-19',
        completedAt: 0,
        source: source,
        title: '',
        durationSeconds: seconds,
      );
      expect(
        countedSourcesPerDay([
          of(TrainingSource.activity, 900),
          of(TrainingSource.gym, 900),
          of(TrainingSource.gym, 1200),
          of(TrainingSource.daily, 60),
        ]),
        {
          '2026-10-19': [TrainingSource.gym, TrainingSource.activity],
        },
      );
    });
  });

  group('goal history', () {
    test('no goal before the first one', () {
      expect(WeeklyProgressCalculator.goalForWeek(const [], '2026-10-19'), isNull);
      expect(WeeklyProgressCalculator.goalForWeek([goal(3, '2026-10-19')], '2026-10-12'), isNull);
    });

    test('the latest goal on or before the week applies, in any list order', () {
      final goals = [goal(5, '2026-11-02'), goal(3, '2026-10-05'), goal(4, '2026-10-19')];
      expect(WeeklyProgressCalculator.goalForWeek(goals, '2026-10-05'), 3);
      expect(WeeklyProgressCalculator.goalForWeek(goals, '2026-10-12'), 3);
      expect(WeeklyProgressCalculator.goalForWeek(goals, '2026-10-19'), 4);
      expect(WeeklyProgressCalculator.goalForWeek(goals, '2026-10-26'), 4);
      expect(WeeklyProgressCalculator.goalForWeek(goals, '2026-11-09'), 5);
    });

    test('a goal changed mid-history keeps the old goal for past weeks', () {
      final goals = [goal(2, '2026-10-05'), goal(4, '2026-10-19')];
      final workouts = once(['2026-10-06', '2026-10-08', '2026-10-20', '2026-10-21']);
      expect(WeeklyProgressCalculator.progress('2026-10-07', goals, workouts).isGoalMet, isTrue);
      expect(WeeklyProgressCalculator.progress('2026-10-22', goals, workouts).isGoalMet, isFalse);
      expect(WeeklyProgressCalculator.progress('2026-10-22', goals, workouts).goal, 4);
    });
  });

  group('weekly progress', () {
    test('several workouts on the same day count separately', () {
      final progress = WeeklyProgressCalculator.progress(
        '2026-10-22',
        [goal(3, '2026-10-19')],
        {'2026-10-19': 2, '2026-10-21': 1},
      );
      expect(progress.workoutCount, 3);
      expect(progress.workoutsOn('2026-10-19'), 2);
      expect(progress.workoutsOn('2026-10-20'), 0);
      expect(progress.isGoalMet, isTrue);
    });

    test('progress keeps only the dates of that week', () {
      final workouts = {'2026-10-18': 3, '2026-10-19': 1, '2026-10-25': 2, '2026-10-26': 4};
      final progress = WeeklyProgressCalculator.progress('2026-10-22', [goal(3, '2026-10-19')], workouts);
      expect(progress.weekStart, '2026-10-19');
      expect(progress.workoutsPerDay, {'2026-10-19': 1, '2026-10-25': 2});
      expect(progress.workoutCount, 3);
    });

    test('exceeding the goal is still met and keeps the real count', () {
      final progress = WeeklyProgressCalculator.progress(
        '2026-10-22',
        [goal(4, '2026-10-19')],
        {'2026-10-19': 3, '2026-10-20': 2},
      );
      expect(progress.workoutCount, 5);
      expect(progress.isGoalMet, isTrue);
    });

    test('Sunday belongs to its week and Monday starts the next one', () {
      final goals = [goal(1, '2026-10-12')];
      expect(WeeklyProgressCalculator.progress('2026-10-19', goals, once(['2026-10-25'])).isGoalMet, isTrue);
      expect(WeeklyProgressCalculator.progress('2026-10-19', goals, once(['2026-10-26'])).isGoalMet, isFalse);
      expect(WeeklyProgressCalculator.progress('2026-10-19', goals, once(['2026-10-18'])).isGoalMet, isFalse);
    });

    test('without a goal the week is never met', () {
      final progress = WeeklyProgressCalculator.progress('2026-10-19', const [], {'2026-10-19': 3});
      expect(progress.goal, isNull);
      expect(progress.workoutCount, 3);
      expect(progress.isGoalMet, isFalse);
    });
  });

  group('streak', () {
    test('no goal means no streak', () {
      expect(WeeklyProgressCalculator.streak('2026-10-22', const [], once(['2026-10-19', '2026-10-12'])), 0);
    });

    test('completed weeks with the goal met count back until one is missed', () {
      expect(
        WeeklyProgressCalculator.streak('2026-10-22', [
          goal(1, '2026-09-28'),
        ], once(['2026-09-29', '2026-10-06', '2026-10-13'])),
        3,
      );
    });

    test('two workouts in one day can meet a goal of two', () {
      expect(WeeklyProgressCalculator.streak('2026-10-22', [goal(2, '2026-10-12')], {'2026-10-14': 2}), 1);
    });

    test('a gap breaks the streak', () {
      expect(
        WeeklyProgressCalculator.streak('2026-10-22', [goal(1, '2026-09-28')], once(['2026-09-29', '2026-10-13'])),
        1,
      );
    });

    test('an unfinished current week does not break the streak and adds one once met', () {
      final goals = [goal(2, '2026-10-05')];
      final past = once(['2026-10-05', '2026-10-07', '2026-10-12', '2026-10-14']);
      expect(WeeklyProgressCalculator.streak('2026-10-22', goals, past), 2);
      expect(WeeklyProgressCalculator.streak('2026-10-22', goals, {...past, '2026-10-20': 1}), 2);
      expect(WeeklyProgressCalculator.streak('2026-10-22', goals, {...past, '2026-10-20': 2}), 3);
    });

    test('weeks before the first goal do not count', () {
      expect(
        WeeklyProgressCalculator.streak('2026-10-22', [
          goal(1, '2026-10-12'),
        ], once(['2026-09-29', '2026-10-06', '2026-10-13'])),
        1,
      );
    });

    test('a past week measured against its own higher goal can break the streak', () {
      final goals = [goal(1, '2026-10-05'), goal(3, '2026-10-12'), goal(1, '2026-10-19')];
      expect(WeeklyProgressCalculator.streak('2026-10-22', goals, once(['2026-10-06', '2026-10-13', '2026-10-14'])), 0);
    });

    test('the current week summary says how many workouts are needed', () {
      final summary = WeeklyProgressCalculator.currentWeek('2026-10-22', [goal(5, '2026-10-19')], {'2026-10-19': 2});
      expect(summary.workoutsNeeded, 3);
      expect(summary.streak, 0);
      final exceeded = WeeklyProgressCalculator.currentWeek('2026-10-25', [goal(1, '2026-10-19')], {'2026-10-19': 3});
      expect(exceeded.workoutsNeeded, 0);
      expect(exceeded.streak, 1);
      expect(WeeklyProgressCalculator.currentWeek('2026-10-22', const [], const {}).workoutsNeeded, 0);
    });
  });

  group('the web reads past weeks only as far back as the streak goes', () {
    Future<(int, List<String>)> readBack(String today, List<WeeklyGoal> goals, Map<String, int> all) async {
      final ranges = <String>[];
      final monday = CalendarDates.mondayOf(today);
      final streak = await streakReadingBack(
        today: today,
        goals: goals,
        currentWeekPerDay: {
          for (final MapEntry(:key, :value) in all.entries)
            if (key.compareTo(monday) >= 0) key: value,
        },
        loadPast: (from, until) async {
          ranges.add('$from..$until');
          return {
            for (final MapEntry(:key, :value) in all.entries)
              if (key.compareTo(from) >= 0 && key.compareTo(until) < 0) key: value,
          };
        },
      );
      return (streak, ranges);
    }

    test('it gives the same streak as Android for every case above', () async {
      final cases = <(String, List<WeeklyGoal>, Map<String, int>)>[
        ('2026-10-22', const [], once(['2026-10-19', '2026-10-12'])),
        ('2026-10-22', [goal(1, '2026-09-28')], once(['2026-09-29', '2026-10-06', '2026-10-13'])),
        ('2026-10-22', [goal(2, '2026-10-12')], {'2026-10-14': 2}),
        ('2026-10-22', [goal(1, '2026-09-28')], once(['2026-09-29', '2026-10-13'])),
        (
          '2026-10-22',
          [goal(2, '2026-10-05')],
          {
            ...once(['2026-10-05', '2026-10-07', '2026-10-12', '2026-10-14']),
            '2026-10-20': 2,
          },
        ),
        ('2026-10-22', [goal(1, '2026-10-12')], once(['2026-09-29', '2026-10-06', '2026-10-13'])),
        (
          '2026-10-22',
          [goal(1, '2026-10-05'), goal(3, '2026-10-12'), goal(1, '2026-10-19')],
          once(['2026-10-06', '2026-10-13', '2026-10-14']),
        ),
      ];
      for (final (today, goals, workouts) in cases) {
        final (streak, _) = await readBack(today, goals, workouts);
        expect(streak, WeeklyProgressCalculator.streak(today, goals, workouts));
      }
    });

    test('a long streak is read 8 weeks at a time, never before the first goal', () async {
      final goals = [goal(1, '2026-01-05')];
      // One workout every week from 5 January to 12 October: 41 weeks met, then this week
      final workouts = <String, int>{};
      for (var week = '2026-01-05'; week.compareTo('2026-10-12') <= 0; week = CalendarDates.plusWeeks(week, 1)) {
        workouts[CalendarDates.plusDays(week, 2)] = 1;
      }
      final (streak, ranges) = await readBack('2026-10-22', goals, workouts);
      expect(streak, WeeklyProgressCalculator.streak('2026-10-22', goals, workouts));
      expect(streak, 41);
      expect(ranges.first, '2026-08-24..2026-10-19');
      expect(ranges.last, startsWith('2026-01-05..'));
      expect(ranges, hasLength(6));
    });

    test('a missed week stops the reading: nothing older is read', () async {
      final goals = [goal(1, '2025-01-06')];
      final (streak, ranges) = await readBack('2026-10-22', goals, once(['2026-10-13']));
      expect(streak, 1);
      expect(ranges, ['2026-08-24..2026-10-19']);
    });

    test('without a goal nothing is read', () async {
      final (streak, ranges) = await readBack('2026-10-22', const [], once(['2026-10-13']));
      expect(streak, 0);
      expect(ranges, isEmpty);
    });
  });
}
