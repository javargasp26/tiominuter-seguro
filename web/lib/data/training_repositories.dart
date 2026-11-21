/// What training on the web reads and writes (Phase 5b), as interfaces: Firebase implementations in
/// `firebase_training_repositories.dart`, fakes in test/support/training_fakes.dart. Every write is
/// a document of docs/firestore-model.md built by `write_mappers.dart`; Android pulls them through
/// its normal sync (by `updatedAt`), so the web never needs a rule or model of its own.
///
/// Writes go into Firestore's offline queue (the persistent web cache): their Futures complete when
/// the server acknowledged them, so the screens never wait for them (see [PendingWrites]).
library;

import 'package:flutter/foundation.dart';

import '../domain/daily_progress.dart';
import '../domain/program.dart';
import '../domain/training.dart';
import '../domain/weekly_progress.dart';
import 'write_mappers.dart';

/// A live program with its progress (`users/{uid}/programs/{programId}` + its completions count).
class ProgramSummary {
  const ProgramSummary({
    required this.programId,
    required this.profile,
    required this.createdAt,
    required this.completedWorkouts,
  });

  final String programId;
  final ProgramProfile profile;
  final int createdAt;
  final int completedWorkouts;

  /// One workout per training day of every week (as Android's program_workouts rows).
  int get totalWorkouts => profile.weeks * profile.daysPerWeek;
}

/// One workout of a program, as stored (`…/workouts/{week}-{dayIndex}`), and whether it is completed.
class ProgramWorkoutRow {
  const ProgramWorkoutRow({
    required this.week,
    required this.dayIndex,
    required this.isDeload,
    required this.workout,
    required this.completedAt,
  });

  final int week;
  final int dayIndex;
  final bool isDeload;

  /// Null when the payload can't be read (a newer app's): shown as unavailable.
  final ProgramWorkout? workout;
  final int? completedAt;

  bool get isCompleted => completedAt != null;
}

class ProgramDetail {
  const ProgramDetail({required this.summary, required this.workouts});

  final ProgramSummary summary;

  /// Ordered by week, then day.
  final List<ProgramWorkoutRow> workouts;

  ProgramWorkoutRow? workout(int week, int dayIndex) {
    for (final row in workouts) {
      if (row.week == week && row.dayIndex == dayIndex) return row;
    }
    return null;
  }
}

/// My own data under `users/{uid}`: the writes training needs and the reads that go with them.
abstract interface class TrainingRepository {
  /// Creates or replaces an entry (a `set` without merge, as Android's sync).
  Future<void> saveEntry(String uid, TrainingEntryDoc entry);

  /// The entry's tombstone (`{entryId, deleted: true, updatedAt, schemaVersion}`).
  Future<void> deleteEntry(String uid, String entryId);

  /// One entry with every field; null when it doesn't exist or was deleted.
  Future<TrainingEntryDoc?> entry(String uid, String entryId);

  /// `weeklyGoals/{weekStart}` (the Monday of this week).
  Future<void> setGoal(String uid, String weekStart, int workoutsPerWeek);

  /// Every live weekly goal (one read).
  Future<List<WeeklyGoal>> goals(String uid);

  /// Entries with `dateLocal >= fromDate` (one read).
  Future<List<TrainingEntry>> entriesFrom(String uid, String fromDate);

  /// Entries with `fromDate <= dateLocal < untilDate` (one read; the streak's past weeks).
  Future<List<TrainingEntry>> entriesBetween(String uid, String fromDate, String untilDate);

  /// The latest date before [beforeDate] with an entry that counts toward the goal (pages of 25,
  /// newest first, at most [maxPages]); null when none is found.
  Future<String?> lastCountedDateBefore(String uid, String beforeDate, {int maxPages});

  /// `dailyProgress/state`; null when there is none yet (or it can't be read).
  Future<DailyProgress?> dailyProgress(String uid);

  /// The source and refId of my DAILY entries (`trainingLog where source == "DAILY"`, one read): the
  /// first progress comes from them ([DailyUnlockRules.fromHistory]).
  Future<List<({TrainingSource source, String? refId})>> dailyHistory(String uid);

  /// Writes [change] of the server's current progress (null: none yet) in a transaction, so a
  /// state another device wrote meanwhile is never lost; nothing is written when it doesn't change.
  /// Offline, the change applies to the cached state and is queued. Returns the resulting state.
  Future<DailyProgress> updateDailyProgress(String uid, DailyProgress Function(DailyProgress? current) change);

  /// The program and every workout, in one batch (≤ 61 writes).
  Future<void> createProgram(
    String uid,
    String programId,
    ProgramProfile profile,
    GeneratedProgram program,
    int createdAt,
  );

  /// `…/completions/{week}-{dayIndex}`.
  Future<void> completeProgramWorkout(String uid, String programId, int week, int dayIndex, int completedAt);

  /// My live programs, newest first, each with its completed-workout count.
  Future<List<ProgramSummary>> programs(String uid);

  /// The program with its workouts and completions; null when it doesn't exist or was deleted.
  Future<ProgramDetail?> program(String uid, String programId);
}

/// My member summary and my posts in my groups.
abstract interface class SharingRepository {
  /// The groups of my membership index (one read of ≤ 5 docs).
  Future<List<String>> myGroupIds(String uid);

  /// An `update` of my member doc (it exists: I joined).
  Future<void> updateMemberSummary(String groupId, String uid, Fields fields);

  /// `groups/{groupId}/posts/{postId}`, postId = the entry's uuid.
  Future<void> createPost(String groupId, String postId, Fields fields);

  /// Deleting a missing post is allowed (no read first).
  Future<void> deletePost(String groupId, String postId);

  /// Sets ([thumb]) or removes (null) the thumbnail of my post: the only field the author may
  /// change (an `update`; a post that doesn't exist fails, which is ignored).
  Future<void> setPostThumb(String groupId, String postId, Uint8List? thumb);
}

/// Writes not acknowledged by the server yet (offline, or in flight): the "pending" indicator.
/// [track] counts this page's writes; [watchQueue] also covers writes queued before a reload,
/// which only the SDK knows about (`waitForPendingWrites`).
class PendingWrites extends ValueNotifier<int> {
  PendingWrites() : super(0);

  bool _queuedBeforeLoad = false;

  bool get hasPending => value > 0 || _queuedBeforeLoad;

  /// Counts [write] until it completes (successfully or not; a failure is logged by the caller).
  Future<T> track<T>(Future<T> write) {
    value++;
    return write.whenComplete(() => value--);
  }

  /// [allWritten] completes when every queued write (also from an earlier page load) was sent.
  void watchQueue(Future<void> allWritten) {
    _queuedBeforeLoad = true;
    notifyListeners();
    allWritten.whenComplete(() {
      _queuedBeforeLoad = false;
      notifyListeners();
    }).ignore();
  }
}
