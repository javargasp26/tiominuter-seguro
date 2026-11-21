/// The weekly goal rules, ported from Android:
/// - `domain/goal/WorkoutCounting.kt` (the 10-minute rule, counted workouts per day),
/// - `domain/goal/WeeklyProgressCalculator.kt` (goal in force, progress, streak),
/// - `domain/goal/WeeklyGoal.kt` (`WeeklyGoal`, `WeeklyProgress`, `CurrentWeekSummary`),
/// - `domain/groups/GroupSummaryCalculator.kt` (the distinct sources of each day).
library;

import 'calendar_dates.dart';
import 'training.dart';

/// Shortest workout that counts toward the weekly goal: 10 minutes.
const int minCountedDurationSeconds = 10 * 60;

/// The single counting rule: at least 10 minutes, or an unknown duration.
bool countsTowardGoal(TrainingEntry entry) {
  final seconds = entry.durationSeconds;
  return seconds == null || seconds >= minCountedDurationSeconds;
}

/// Counted workouts per local date; dates without one are absent.
Map<String, int> countedWorkoutsPerDay(Iterable<TrainingEntry> entries) {
  final counts = <String, int>{};
  for (final entry in entries.where(countsTowardGoal)) {
    counts[entry.dateLocal] = (counts[entry.dateLocal] ?? 0) + 1;
  }
  return counts;
}

/// The distinct sources of the counted workouts of each date, in source order.
Map<String, List<TrainingSource>> countedSourcesPerDay(Iterable<TrainingEntry> entries) {
  final sources = <String, Set<TrainingSource>>{};
  for (final entry in entries.where(countsTowardGoal)) {
    (sources[entry.dateLocal] ??= <TrainingSource>{}).add(entry.source);
  }
  return {
    for (final MapEntry(:key, :value) in sources.entries)
      key: value.toList()..sort((a, b) => a.index.compareTo(b.index)),
  };
}

/// A goal of [workoutsPerWeek] (1–14) in force from the week starting [effectiveFromWeek].
class WeeklyGoal {
  const WeeklyGoal({required this.workoutsPerWeek, required this.effectiveFromWeek});

  final int workoutsPerWeek;
  final String effectiveFromWeek;

  @override
  bool operator ==(Object other) =>
      other is WeeklyGoal && other.workoutsPerWeek == workoutsPerWeek && other.effectiveFromWeek == effectiveFromWeek;

  @override
  int get hashCode => Object.hash(workoutsPerWeek, effectiveFromWeek);
}

/// One week's counted workouts per day and the goal in force (null before the first goal).
class WeeklyProgress {
  const WeeklyProgress({required this.weekStart, required this.goal, required this.workoutsPerDay});

  final String weekStart;
  final int? goal;
  final Map<String, int> workoutsPerDay;

  int get workoutCount => workoutsPerDay.values.fold(0, (sum, count) => sum + count);

  int workoutsOn(String date) => workoutsPerDay[date] ?? 0;

  bool get isGoalMet => goal != null && workoutCount >= goal!;
}

/// Everything "My week" shows about the week of [today].
class CurrentWeekSummary {
  const CurrentWeekSummary({required this.today, required this.progress, required this.streak});

  final String today;
  final WeeklyProgress progress;
  final int streak;

  int get workoutsNeeded {
    final goal = progress.goal;
    if (goal == null) return 0;
    final left = goal - progress.workoutCount;
    return left < 0 ? 0 : left;
  }
}

/// Pure weekly-goal rules (`WeeklyProgressCalculator`). Weeks start on Monday; dates are local
/// "yyyy-MM-dd" strings, so they compare correctly as text.
abstract final class WeeklyProgressCalculator {
  /// The goal in force in the week starting [weekStart]: the latest goal whose effectiveFromWeek is
  /// on or before it; null before the first goal.
  static int? goalForWeek(List<WeeklyGoal> goals, String weekStart) {
    WeeklyGoal? latest;
    for (final goal in goals) {
      if (goal.effectiveFromWeek.compareTo(weekStart) <= 0 &&
          (latest == null || goal.effectiveFromWeek.compareTo(latest.effectiveFromWeek) > 0)) {
        latest = goal;
      }
    }
    return latest?.workoutsPerWeek;
  }

  /// Progress of the week that contains [date]: its counted workouts, per day.
  static WeeklyProgress progress(String date, List<WeeklyGoal> goals, Map<String, int> workoutsPerDay) {
    final week = CalendarDates.weekDates(date);
    return WeeklyProgress(
      weekStart: week.first,
      goal: goalForWeek(goals, week.first),
      workoutsPerDay: {
        for (final MapEntry(:key, :value) in workoutsPerDay.entries)
          if (week.contains(key) && value > 0) key: value,
      },
    );
  }

  /// Consecutive weeks with the goal met, ending at the week of [today]. Completed weeks count
  /// back from the previous week until one without its goal met or without a goal; the current
  /// week adds 1 only once its goal is met, and never breaks the streak.
  static int streak(String today, List<WeeklyGoal> goals, Map<String, int> workoutsPerDay) {
    final firstGoalWeek = firstGoalWeekOf(goals);
    if (firstGoalWeek == null) return 0;
    var streak = progress(today, goals, workoutsPerDay).isGoalMet ? 1 : 0;
    var week = CalendarDates.plusWeeks(today, -1);
    while (week.compareTo(firstGoalWeek) >= 0 && progress(week, goals, workoutsPerDay).isGoalMet) {
      streak++;
      week = CalendarDates.plusWeeks(week, -1);
    }
    return streak;
  }

  static CurrentWeekSummary currentWeek(String today, List<WeeklyGoal> goals, Map<String, int> workoutsPerDay) =>
      CurrentWeekSummary(
        today: today,
        progress: progress(today, goals, workoutsPerDay),
        streak: streak(today, goals, workoutsPerDay),
      );

  static String? firstGoalWeekOf(List<WeeklyGoal> goals) {
    String? first;
    for (final goal in goals) {
      if (first == null || goal.effectiveFromWeek.compareTo(first) < 0) first = goal.effectiveFromWeek;
    }
    return first;
  }
}

/// The same streak as [WeeklyProgressCalculator.streak], reading past weeks only as far back as
/// the streak goes (the web has no local copy of the history): [loadPast] gives the counted
/// workouts per day of `[from, until)` and is called with chunks of [chunkWeeks] weeks, never before
/// the first goal's week. The weeks it walks are exactly the ones Android's loop walks.
Future<int> streakReadingBack({
  required String today,
  required List<WeeklyGoal> goals,
  required Map<String, int> currentWeekPerDay,
  required Future<Map<String, int>> Function(String from, String until) loadPast,
  int chunkWeeks = 8,
}) async {
  final firstGoalWeek = WeeklyProgressCalculator.firstGoalWeekOf(goals);
  if (firstGoalWeek == null) return 0;
  final perDay = Map<String, int>.of(currentWeekPerDay);
  var streak = WeeklyProgressCalculator.progress(today, goals, perDay).isGoalMet ? 1 : 0;
  var loadedFrom = CalendarDates.mondayOf(today);
  var week = CalendarDates.plusWeeks(today, -1);
  while (week.compareTo(firstGoalWeek) >= 0) {
    if (week.compareTo(loadedFrom) < 0) {
      final chunkStart = CalendarDates.plusWeeks(loadedFrom, -chunkWeeks);
      final from = chunkStart.compareTo(firstGoalWeek) < 0 ? firstGoalWeek : chunkStart;
      perDay.addAll(await loadPast(from, loadedFrom));
      loadedFrom = from;
    }
    if (!WeeklyProgressCalculator.progress(week, goals, perDay).isGoalMet) break;
    streak++;
    week = CalendarDates.plusWeeks(week, -1);
  }
  return streak;
}
