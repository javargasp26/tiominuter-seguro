/// The daily routines' unlock, ported from Android's `domain/daily/DailyProgress.kt`
/// (`DailyProgress`, `DailyUnlockRules`) and `DailyProgressSync.kt`; checked by
/// shared/golden/daily_unlock.json. A day is a routineId (the content pins `Day <routineId>`).
///
/// - Day 1 is always available; day N + 1 unlocks when day N is finished (the workout reached its
///   completion view). A session closed for inactivity unlocks nothing.
/// - Unlocked days stay available and can be repeated.
/// - Restarting locks every day again from Day 1 and starts a new cycle.
/// - Two states meet: the higher cycle wins; in the same cycle, the further day.
library;

import 'training.dart';
import 'workout_names.dart';

/// Every day up to [lastCompletedDay] was finished in this [cycle] (0 = none yet).
/// `users/{uid}/dailyProgress/state` (docs/firestore-model.md).
class DailyProgress {
  const DailyProgress({this.lastCompletedDay = 0, this.cycle = 0});

  final int lastCompletedDay;
  final int cycle;

  @override
  bool operator ==(Object other) =>
      other is DailyProgress && other.lastCompletedDay == lastCompletedDay && other.cycle == cycle;

  @override
  int get hashCode => Object.hash(lastCompletedDay, cycle);

  @override
  String toString() => 'DailyProgress($lastCompletedDay, cycle $cycle)';
}

abstract final class DailyUnlockRules {
  /// The highest day the model stores (`lastCompletedDay` 0..999 in the rules).
  static const int maxDay = 999;

  /// "Day X available": the next day to do, capped at the last routine; null without routines.
  static int? nextAvailableDay(DailyProgress progress, int? lastRoutineId) {
    if (lastRoutineId == null || lastRoutineId < 1) return null;
    final next = progress.lastCompletedDay + 1;
    return next > lastRoutineId ? lastRoutineId : next;
  }

  static bool isUnlocked(int day, DailyProgress progress) => day >= 1 && day <= progress.lastCompletedDay + 1;

  static bool isCompleted(int day, DailyProgress progress) => day >= 1 && day <= progress.lastCompletedDay;

  /// Every routine was finished in this cycle: "Start again from Day 1" is offered.
  static bool isCycleComplete(DailyProgress progress, int? lastRoutineId) =>
      lastRoutineId != null && lastRoutineId >= 1 && progress.lastCompletedDay >= lastRoutineId;

  /// After finishing [day]: the next day unlocks only when [day] is the newest unlocked one.
  static DailyProgress afterFinishing(DailyProgress progress, int day) =>
      day == progress.lastCompletedDay + 1 && day <= maxDay
      ? DailyProgress(lastCompletedDay: day, cycle: progress.cycle)
      : progress;

  /// "Restart days": back to Day 1, in a new cycle (the history is not touched).
  static DailyProgress restart(DailyProgress progress) => DailyProgress(cycle: progress.cycle + 1);

  /// The first state of a person who trained before the unlock existed: the highest day among their
  /// DAILY entries (`daily:<N>`, Kotlin's `toIntOrNull`), so nobody loses access; 0 without one.
  static DailyProgress fromHistory(Iterable<({TrainingSource source, String? refId})> entries) {
    var highest = 0;
    for (final entry in entries) {
      if (entry.source != TrainingSource.daily) continue;
      final day = dailyRoutineIdOf(entry.refId);
      if (day != null && day >= 1 && day > highest) highest = day;
    }
    return DailyProgress(lastCompletedDay: highest > maxDay ? maxDay : highest);
  }

  /// > 0 when [a] is more advanced than [b]: the higher cycle, then the further day.
  static int compare(DailyProgress a, DailyProgress b) {
    final byCycle = a.cycle.compareTo(b.cycle);
    return byCycle != 0 ? byCycle : a.lastCompletedDay.compareTo(b.lastCompletedDay);
  }

  /// The more advanced of two states (the first one when they are equal).
  static DailyProgress merge(DailyProgress a, DailyProgress b) => compare(a, b) >= 0 ? a : b;
}

/// What a client does when its state meets the server's ([DailyProgressSync.reconcile]).
sealed class DailyProgressOutcome {
  const DailyProgressOutcome();
}

/// The server is ahead: take its state, nothing to upload.
final class ApplyRemoteProgress extends DailyProgressOutcome {
  const ApplyRemoteProgress(this.progress);

  final DailyProgress progress;
}

/// This side is ahead, or the server has no document yet: upload [progress].
final class PushLocalProgress extends DailyProgressOutcome {
  const PushLocalProgress(this.progress);

  final DailyProgress progress;
}

/// Both say the same.
final class ProgressInSync extends DailyProgressOutcome {
  const ProgressInSync();
}

abstract final class DailyProgressSync {
  /// [remote] null: the account has no document yet; the local state is uploaded, raised in cycle 0
  /// to the [history] ([DailyUnlockRules.fromHistory]).
  static DailyProgressOutcome reconcile(DailyProgress local, DailyProgress? remote, DailyProgress history) {
    if (remote == null) {
      return PushLocalProgress(local.cycle == 0 ? DailyUnlockRules.merge(local, history) : local);
    }
    final order = DailyUnlockRules.compare(remote, local);
    if (order > 0) return ApplyRemoteProgress(remote);
    if (order < 0) return PushLocalProgress(local);
    return const ProgressInSync();
  }
}
