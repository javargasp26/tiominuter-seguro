/// My weekly summary for my groups, ported from Android's
/// `domain/groups/GroupSummaryCalculator.kt` (and the caps of `GroupLimits` in `GroupModels.kt`):
/// the 10-minute rule, the goal in force this week and the same streak as "My week". The week is
/// Monday–Sunday of [today] (the browser's local date). Checked against shared/golden/weekly.json.
library;

import 'calendar_dates.dart';
import 'groups.dart';
import 'training.dart';
import 'weekly_progress.dart';

/// Android's `GroupLimits`, for what the member doc may hold (firebase/firestore.rules checks them).
abstract final class GroupLimits {
  static const int maxWorkouts = 100;
  static const int maxStreak = 1000;
  static const int displayNameMaxLength = 100;
  static const int photoUrlMaxLength = 2048;
}

abstract final class GroupSummaryCalculator {
  /// Null without a goal this week (a group member always has one; the Android app asks first).
  static MemberSummary? summary(String today, List<TrainingEntry> entries, List<WeeklyGoal> goals) {
    final weekStart = CalendarDates.mondayOf(today);
    final goal = WeeklyProgressCalculator.goalForWeek(goals, weekStart);
    if (goal == null) return null;
    final counted = entries.where(countsTowardGoal).toList();
    final perDay = countedWorkoutsPerDay(counted);
    final week = CalendarDates.weekDates(today);
    final days = <int, List<TrainingSource>>{};
    for (var index = 0; index < week.length; index++) {
      final sources = counted.where((e) => e.dateLocal == week[index]).map((e) => e.source).toSet().toList()
        ..sort((a, b) => a.index.compareTo(b.index));
      if (sources.isNotEmpty) days[index + 1] = sources;
    }
    final workouts = WeeklyProgressCalculator.progress(today, goals, perDay).workoutCount;
    final streak = WeeklyProgressCalculator.streak(today, goals, perDay);
    // A date after today only comes from a wrong clock: never "trained in the future"
    String? lastTrained;
    for (final entry in counted) {
      final date = entry.dateLocal;
      if (date.compareTo(today) <= 0 && (lastTrained == null || date.compareTo(lastTrained) > 0)) lastTrained = date;
    }
    return MemberSummary(
      weekStartLocal: weekStart,
      workoutsThisWeek: workouts > GroupLimits.maxWorkouts ? GroupLimits.maxWorkouts : workouts,
      weeklyGoal: goal,
      streakWeeks: streak > GroupLimits.maxStreak ? GroupLimits.maxStreak : streak,
      daysThisWeek: days,
      lastTrainedLocal: lastTrained,
    );
  }
}
