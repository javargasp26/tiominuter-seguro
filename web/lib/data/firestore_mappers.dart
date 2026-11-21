/// Firestore documents → the web's models, exactly as docs/firestore-model.md defines them
/// (schemaVersion 1). Unknown fields are ignored; a tombstone (`deleted: true`), a document of
/// another schemaVersion (one this app can't read) or a document missing a required field maps
/// to null, as Android's sync mappers skip what they can't read (data/sync/AppDocMappers.kt).
library;

import 'dart:convert';
import 'dart:typed_data';

import 'package:cloud_firestore/cloud_firestore.dart';

import '../domain/calendar_dates.dart';
import '../domain/daily_progress.dart';
import '../domain/program.dart';
import '../domain/groups.dart';
import '../domain/training.dart';
import '../domain/weekly_progress.dart';
import '../domain/workout_names.dart';
import 'write_mappers.dart';

typedef DocData = Map<String, Object?>;

/// The schemaVersion this app reads and writes.
const int schemaVersion = 1;

abstract final class FirestoreMappers {
  /// `users/{uid}/trainingLog/{entryId}`.
  static TrainingEntry? trainingEntry(String id, DocData data) {
    if (!_isLiveSyncedDoc(data)) return null;
    final dateLocal = _string(data['dateLocal']);
    final completedAt = _int(data['completedAt']);
    final source = TrainingSource.fromCode(data['source']);
    final title = _string(data['title']);
    if (dateLocal == null ||
        !CalendarDates.isValid(dateLocal) ||
        completedAt == null ||
        source == null ||
        title == null) {
      return null;
    }
    return TrainingEntry(
      id: _string(data['entryId']) ?? id,
      dateLocal: dateLocal,
      completedAt: completedAt,
      source: source,
      refId: _string(data['refId']),
      title: title,
      durationSeconds: _int(data['durationSeconds']),
      activityTypeCode: _string(data['activityType']),
    );
  }

  /// `users/{uid}/trainingLog/{entryId}` with every field (the edit form: intensity, note, createdAt).
  static TrainingEntryDoc? trainingEntryDoc(String id, DocData data) {
    final entry = trainingEntry(id, data);
    final createdAt = _int(data['createdAt']);
    if (entry == null || createdAt == null) return null;
    return TrainingEntryDoc(
      entryId: entry.id,
      dateLocal: entry.dateLocal,
      completedAt: entry.completedAt,
      source: entry.source,
      refId: entry.refId,
      title: entry.title,
      durationSeconds: entry.durationSeconds,
      createdAt: createdAt,
      activityType: entry.activityTypeCode,
      intensity: _string(data['intensity']),
      note: _string(data['note']),
    );
  }

  /// `users/{uid}/programs/{programId}`: the profile (null for a tombstone or an unknown value).
  static ({ProgramProfile profile, int createdAt})? program(DocData data) {
    if (!_isLiveSyncedDoc(data)) return null;
    final goal = ProgramGoal.fromCode(data['goal']);
    final level = ProgramLevel.fromCode(data['level']);
    final location = ProgramLocation.fromCode(data['location']);
    final focus = ProgramFocus.fromCode(data['focus']);
    final weeks = _int(data['weeks']);
    final days = _int(data['daysPerWeek']);
    final age = _int(data['age']);
    final weight = data['weightKg'];
    final height = _int(data['heightCm']);
    final createdAt = _int(data['createdAt']);
    if (goal == null ||
        level == null ||
        location == null ||
        focus == null ||
        weeks == null ||
        days == null ||
        age == null ||
        weight is! num ||
        height == null ||
        createdAt == null) {
      return null;
    }
    return (
      profile: ProgramProfile(
        age: age,
        weightKg: weight.toDouble(),
        heightCm: height,
        level: level,
        goal: goal,
        location: location,
        weeks: weeks,
        daysPerWeek: days,
        focus: focus,
      ),
      createdAt: createdAt,
    );
  }

  /// `…/workouts/{workoutId}`: week, day, deload and the decoded payload (null when unreadable).
  static ({int week, int dayIndex, bool isDeload, ProgramWorkout? workout})? programWorkout(DocData data) {
    if (!_isLiveSyncedDoc(data)) return null;
    final week = _int(data['week']);
    final day = _int(data['dayIndex']);
    final payload = _string(data['payload']);
    if (week == null || day == null || payload == null) return null;
    return (
      week: week,
      dayIndex: day,
      isDeload: data['isDeload'] == true,
      workout: ProgramWorkout.fromPayload(payload),
    );
  }

  /// `…/completions/{workoutId}`: when it was completed (null for a tombstone).
  static ({int week, int dayIndex, int completedAt})? programCompletion(DocData data) {
    if (!_isLiveSyncedDoc(data)) return null;
    final week = _int(data['week']);
    final day = _int(data['dayIndex']);
    final completedAt = _int(data['completedAt']);
    if (week == null || day == null || completedAt == null) return null;
    return (week: week, dayIndex: day, completedAt: completedAt);
  }

  /// `users/{uid}/weeklyGoals/{effectiveFromWeek}`.
  static WeeklyGoal? weeklyGoal(String id, DocData data) {
    if (!_isLiveSyncedDoc(data)) return null;
    final workouts = _int(data['workoutsPerWeek']);
    final week = _string(data['effectiveFromWeek']) ?? id;
    if (workouts == null || !CalendarDates.isValid(week)) return null;
    return WeeklyGoal(workoutsPerWeek: workouts, effectiveFromWeek: week);
  }

  /// `users/{uid}/dailyProgress/state` (no `deleted` field); null when unreadable or out of the
  /// model's range (Android's `DailyProgressDocMapper.toDomain`).
  static DailyProgress? dailyProgress(DocData data) {
    if (!_isVersion1(data)) return null;
    final day = _int(data['lastCompletedDay']);
    final cycle = _int(data['cycle']);
    if (day == null || day < 0 || day > DailyUnlockRules.maxDay || cycle == null || cycle < 0) return null;
    return DailyProgress(lastCompletedDay: day, cycle: cycle);
  }

  /// Whether `users/{uid}/programs/{programId}` is a live program (a tombstoned one makes its
  /// workouts dead: docs/firestore-model.md "Tombstones").
  static bool isLiveProgram(DocData? data) => data != null && _isLiveSyncedDoc(data);

  /// The title code of `…/programs/{programId}/workouts/{workoutId}`, from its payload.
  static ProgramTitleCode? programWorkoutTitleCode(DocData data) {
    if (!_isLiveSyncedDoc(data)) return null;
    final payload = _string(data['payload']);
    if (payload == null) return null;
    try {
      return programTitleCodeOfPayload(jsonDecode(payload));
    } on FormatException {
      return null;
    }
  }

  /// `users/{uid}/groups/{groupId}`: the group id of my membership index entry.
  static String? membershipGroupId(String id, DocData data) {
    if (!_isVersion1(data)) return null;
    return _string(data['groupId']) ?? id;
  }

  /// `groups/{groupId}`.
  static Group? group(String id, DocData data) {
    if (!_isVersion1(data)) return null;
    final name = _string(data['name']);
    final ownerUid = _string(data['ownerUid']);
    final memberCount = _int(data['memberCount']);
    if (name == null || ownerUid == null || memberCount == null) return null;
    return Group(
      id: id,
      name: name,
      ownerUid: ownerUid,
      memberCount: memberCount,
      inviteCode: _string(data['inviteCode']) ?? '',
    );
  }

  /// `groups/{groupId}/members/{uid}`.
  static GroupMember? member(String uid, DocData data) {
    if (!_isVersion1(data)) return null;
    final displayName = _string(data['displayName']);
    final weekStart = _string(data['weekStartLocal']);
    final workouts = _int(data['workoutsThisWeek']);
    final goal = _int(data['weeklyGoal']);
    final streak = _int(data['streakWeeks']);
    if (displayName == null || weekStart == null || workouts == null || goal == null || streak == null) return null;
    final days = <int, List<TrainingSource>>{};
    final rawDays = data['daysThisWeek'];
    if (rawDays is Map) {
      for (final MapEntry(:key, :value) in rawDays.entries) {
        final day = int.tryParse('$key');
        if (day == null || day < 1 || day > 7 || value is! List) continue;
        final sources = value.map(TrainingSource.fromCode).whereType<TrainingSource>().toSet().toList()
          ..sort((a, b) => a.index.compareTo(b.index));
        if (sources.isNotEmpty) days[day] = sources;
      }
    }
    return GroupMember(
      uid: uid,
      displayName: displayName,
      photoUrl: _https(data['photoUrl']),
      summary: MemberSummary(
        weekStartLocal: weekStart,
        workoutsThisWeek: workouts,
        weeklyGoal: goal,
        streakWeeks: streak,
        daysThisWeek: days,
        lastTrainedLocal: _string(data['lastTrainedLocal']),
      ),
      joinedAtMillis: _millis(data['joinedAt']),
    );
  }

  /// `inviteCodes/{code}`: the group a code points to, or null.
  static InvitePreview? invite(String code, DocData data) {
    if (!_isVersion1(data)) return null;
    final groupId = _string(data['groupId']);
    if (groupId == null) return null;
    return InvitePreview(code: code, groupId: groupId, groupName: _string(data['groupName']) ?? '');
  }

  /// `groups/{groupId}/posts/{postId}`.
  static FeedPost? post(String id, DocData data) {
    if (!_isVersion1(data)) return null;
    final authorUid = _string(data['authorUid']);
    final source = TrainingSource.fromCode(data['source']);
    final weekStart = _string(data['weekStartLocal']);
    final dateLocal = _string(data['dateLocal']);
    if (authorUid == null || source == null || weekStart == null || dateLocal == null) return null;
    final reactions = <String, Reaction>{};
    final rawReactions = data['reactions'];
    if (rawReactions is Map) {
      for (final MapEntry(:key, :value) in rawReactions.entries) {
        final reaction = Reaction.fromCode(value);
        if (key is String && reaction != null) reactions[key] = reaction;
      }
    }
    return FeedPost(
      id: id,
      authorUid: authorUid,
      authorName: _string(data['authorName']) ?? '',
      authorPhotoUrl: _https(data['authorPhotoUrl']),
      source: source,
      title: _string(data['title']) ?? '',
      titleKey: _string(data['titleKey']),
      activityType: ActivityType.fromCode(data['activityType']),
      durationSeconds: _int(data['durationSeconds']),
      dateLocal: dateLocal,
      weekStartLocal: weekStart,
      createdAtMillis: _millis(data['createdAt']),
      reactions: reactions,
      thumb: _bytes(data['thumb']),
    );
  }

  static bool _isVersion1(DocData data) => _int(data['schemaVersion']) == schemaVersion;

  static bool _isLiveSyncedDoc(DocData data) => _isVersion1(data) && data['deleted'] != true;

  static String? _string(Object? value) => value is String ? value : null;

  // Firestore integers arrive as int; a whole double (a number written by another client) is accepted
  static int? _int(Object? value) => switch (value) {
    final int whole => whole,
    final double number when number == number.roundToDouble() && number.isFinite => number.toInt(),
    _ => null,
  };

  static String? _https(Object? value) => value is String && value.startsWith('https://') ? value : null;

  static int? _millis(Object? value) => switch (value) {
    final Timestamp timestamp => timestamp.millisecondsSinceEpoch,
    final DateTime time => time.millisecondsSinceEpoch,
    _ => null,
  };

  static Uint8List? _bytes(Object? value) => switch (value) {
    final Blob blob => blob.bytes,
    final Uint8List bytes => bytes,
    _ => null,
  };
}
