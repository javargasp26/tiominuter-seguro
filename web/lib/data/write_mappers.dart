/// The web's writes, as the documents of docs/firestore-model.md, field for field the same as
/// Android's mappers: `data/sync/AppDocMappers.kt` (`TrainingLogDocMapper`, `WeeklyGoalDocMapper`),
/// `data/program/sync/ProgramSyncStore.kt` (`ProgramDocMapper`, `ProgramWorkoutDocMapper`,
/// `ProgramCompletionDocMapper`), `data/sync/FirestoreRemoteUserData.kt` (`deleted`, `updatedAt`,
/// `schemaVersion`, tombstones), `data/groups/FirestoreGroupRepository.kt` (`memberFields`) and
/// `data/groups/FirestoreFeedRepository.kt` (`postFields`). Pure: [serverTimestamp] marks the
/// server time, turned into `FieldValue.serverTimestamp()` when written (tested against
/// shared/fixtures, where it is `{"$serverTimestamp": true}`).
library;

import 'dart:typed_data';

import '../domain/calendar_dates.dart';
import '../domain/daily_progress.dart';
import '../domain/groups.dart';
import '../domain/program.dart';
import '../domain/training.dart';
import 'firestore_mappers.dart' show schemaVersion;

typedef Fields = Map<String, Object?>;

/// The server's time (`FieldValue.serverTimestamp()`): never a browser clock.
final class ServerTimestamp {
  const ServerTimestamp._();
}

const ServerTimestamp serverTimestamp = ServerTimestamp._();

/// A new or edited training entry (`users/{uid}/trainingLog/{entryId}`).
class TrainingEntryDoc {
  const TrainingEntryDoc({
    required this.entryId,
    required this.dateLocal,
    required this.completedAt,
    required this.source,
    required this.refId,
    required this.title,
    required this.durationSeconds,
    required this.createdAt,
    this.activityType,
    this.intensity,
    this.note,
  });

  /// A lowercase uuid.
  final String entryId;
  final String dateLocal;
  final int completedAt;
  final TrainingSource source;

  /// The Firestore refId (`program:<programUuid>:<week>:<day>` for a program workout).
  final String? refId;
  final String title;
  final int? durationSeconds;
  final int createdAt;
  final String? activityType;
  final String? intensity;
  final String? note;

  TrainingEntry toEntry() => TrainingEntry(
    id: entryId,
    dateLocal: dateLocal,
    completedAt: completedAt,
    source: source,
    refId: refId,
    title: title,
    durationSeconds: durationSeconds,
    activityTypeCode: activityType,
  );
}

abstract final class WriteMappers {
  static Fields _synced(Fields fields) => {
    ...fields,
    'deleted': false,
    'updatedAt': serverTimestamp,
    'schemaVersion': schemaVersion,
  };

  /// A tombstone: only the id field, `deleted: true`, `updatedAt`, `schemaVersion` (a `set` without
  /// merge, so the deleted data leaves the server).
  static Fields tombstone(String idField, String id) => {
    idField: id,
    'deleted': true,
    'updatedAt': serverTimestamp,
    'schemaVersion': schemaVersion,
  };

  /// `TrainingLogDocMapper.toFields` + the sync fields. Every field is written, nulls included, as
  /// Android does.
  static Fields trainingEntry(TrainingEntryDoc entry) => _synced({
    'entryId': entry.entryId,
    'dateLocal': entry.dateLocal,
    'completedAt': entry.completedAt,
    'source': entry.source.code,
    'refId': entry.refId,
    'title': entry.title,
    'durationSeconds': entry.durationSeconds,
    'activityType': entry.activityType,
    'intensity': entry.intensity,
    'note': entry.note,
    'createdAt': entry.createdAt,
  });

  /// `weeklyGoals/{effectiveFromWeek}` (the natural id: the Monday it applies from).
  static Fields weeklyGoal(String effectiveFromWeek, int workoutsPerWeek) =>
      _synced({'effectiveFromWeek': effectiveFromWeek, 'workoutsPerWeek': workoutsPerWeek});

  /// `dailyProgress/state` (Android's `DailyProgressDocMapper`): no id field and no `deleted` (never
  /// tombstoned).
  static Fields dailyProgress(DailyProgress progress) => {
    'lastCompletedDay': progress.lastCompletedDay,
    'cycle': progress.cycle,
    'updatedAt': serverTimestamp,
    'schemaVersion': schemaVersion,
  };

  /// `programs/{programId}`: the profile it was generated from.
  static Fields program(String programId, ProgramProfile profile, int createdAt) => _synced({
    'programId': programId,
    'goal': profile.goal.code,
    'level': profile.level.code,
    'location': profile.location.code,
    'focus': profile.focus.code,
    'weeks': profile.weeks,
    'daysPerWeek': profile.daysPerWeek,
    'age': profile.age,
    'weightKg': profile.weightKg,
    'heightCm': profile.heightCm,
    'createdAt': createdAt,
  });

  /// `…/workouts/{week}-{dayIndex}`: the generated workout as Android stores it.
  static Fields programWorkout(int week, int dayIndex, ProgramWorkout workout, {required bool isDeload}) => _synced({
    'workoutId': workoutDocId(week, dayIndex),
    'week': week,
    'dayIndex': dayIndex,
    'kind': workout.kind.code,
    'isDeload': isDeload,
    'payload': workout.toPayload(),
  });

  /// `…/completions/{week}-{dayIndex}`.
  static Fields programCompletion(int week, int dayIndex, int completedAt) => _synced({
    'workoutId': workoutDocId(week, dayIndex),
    'week': week,
    'dayIndex': dayIndex,
    'completedAt': completedAt,
  });

  /// The fields of my member doc a summary update writes (`memberFields`; joinedAt and inviteCode
  /// are written only by the join).
  static Fields memberSummary(String displayName, String? photoUrl, MemberSummary summary) => {
    'displayName': displayName,
    'photoUrl': photoUrl,
    'weekStartLocal': summary.weekStartLocal,
    'workoutsThisWeek': summary.workoutsThisWeek,
    'weeklyGoal': summary.weeklyGoal,
    'streakWeeks': summary.streakWeeks,
    'daysThisWeek': {
      for (final MapEntry(:key, :value) in summary.daysThisWeek.entries)
        '$key': [for (final source in value) source.code],
    },
    'lastTrainedLocal': summary.lastTrainedLocal,
    'updatedAt': serverTimestamp,
    'schemaVersion': schemaVersion,
  };

  /// A post of [entry] (`postFields`): postId = the entry's uuid; [thumb] when this browser has
  /// the entry's photo; the
  /// activity type only for an activity; [titleKey] when known. Android's `newPost`: the title cut
  /// to 500 characters, a negative duration as 0.
  static Fields post({
    required String authorUid,
    required String authorName,
    required String? authorPhotoUrl,
    required TrainingEntry entry,
    required String? titleKey,
    Uint8List? thumb,
  }) {
    final duration = entry.durationSeconds;
    return {
      'authorUid': authorUid,
      'authorName': authorName,
      'authorPhotoUrl': authorPhotoUrl,
      'source': entry.source.code,
      'title': entry.title.length > postTitleMaxLength ? entry.title.substring(0, postTitleMaxLength) : entry.title,
      'durationSeconds': duration == null ? null : (duration < 0 ? 0 : duration),
      'dateLocal': entry.dateLocal,
      'weekStartLocal': CalendarDates.mondayOf(entry.dateLocal),
      'createdAt': serverTimestamp,
      'reactions': const <String, String>{},
      'schemaVersion': schemaVersion,
      if (entry.source == TrainingSource.activity && entry.activity != null) 'activityType': entry.activity!.code,
      'titleKey': ?titleKey,
      // The photo's thumbnail (JPEG ≤ 65,536 bytes, the rules' cap), only when there is one
      'thumb': ?thumb,
    };
  }

  /// Android's `FeedLimits.TITLE_MAX_LENGTH`.
  static const int postTitleMaxLength = 500;
}

/// `{week}-{dayIndex}`, the id of a program's workout and completion docs.
String workoutDocId(int week, int dayIndex) => '$week-$dayIndex';

/// `program:<programUuid>:<week>:<dayIndex>`, the Firestore refId of a program workout's entry.
String programRefId(String programId, int week, int dayIndex) => 'program:$programId:$week:$dayIndex';
