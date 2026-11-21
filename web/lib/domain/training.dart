/// The training history as the web reads it from `users/{uid}/trainingLog` (docs/firestore-model.md).
/// Mirrors Android's `TrainingLogEntry`, `TrainingSource` and `ActivityType`
/// (app/src/main/java/com/javargasp/tiominutermvi/domain/traininglog/).
library;

/// Where a workout came from; the order is Android's enum order (day strips sort by it).
enum TrainingSource {
  daily('DAILY'),
  gym('GYM'),
  program('PROGRAM'),
  activity('ACTIVITY');

  const TrainingSource(this.code);

  final String code;

  static TrainingSource? fromCode(Object? code) {
    for (final source in values) {
      if (source.code == code) return source;
    }
    return null;
  }
}

/// The 14 activity types of a logged activity, stored by [code].
enum ActivityType {
  ownGymRoutine('OWN_GYM_ROUTINE'),
  running('RUNNING'),
  walking('WALKING'),
  cycling('CYCLING'),
  swimming('SWIMMING'),
  yoga('YOGA'),
  pilates('PILATES'),
  dance('DANCE'),
  teamSport('TEAM_SPORT'),
  racketSport('RACKET_SPORT'),
  martialArts('MARTIAL_ARTS'),
  hiit('HIIT'),
  stretching('STRETCHING'),
  other('OTHER');

  const ActivityType(this.code);

  final String code;

  /// The type of [code]; null for an unknown one (a newer app's).
  static ActivityType? fromCode(Object? code) {
    for (final type in values) {
      if (type.code == code) return type;
    }
    return null;
  }
}

/// One live training entry (tombstones never become entries).
class TrainingEntry {
  const TrainingEntry({
    required this.id,
    required this.dateLocal,
    required this.completedAt,
    required this.source,
    required this.title,
    this.refId,
    this.durationSeconds,
    this.activityTypeCode,
  });

  /// The entry's uuid (`entryId`, also the document id).
  final String id;
  final String dateLocal;
  final int completedAt;
  final TrainingSource source;

  /// `gym:64`, `daily:3`, `program:<programUuid>:<week>:<day>` in Firestore; null for an activity.
  final String? refId;

  /// The stored title, in the recording device's language: only the fallback of the name.
  final String title;
  final int? durationSeconds;

  /// The raw `activityType` code (an unknown code stays here and [activity] is null).
  final String? activityTypeCode;

  ActivityType? get activity => ActivityType.fromCode(activityTypeCode);

  bool get isActivity => source == TrainingSource.activity;
}
