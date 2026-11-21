/// Gym routines on the web, ported from Android: the catalog (`data/mapper/GymMappers.kt` and the
/// DTOs: `"set"` is ignored, a slot's sets are its `reps`; an unknown type is skipped; slots in list
/// order), `ui/components/ExerciseSummary.kt` ([exerciseSummary]) and the gym session
/// (`domain/gymsession/GymSessionTracker.kt`): the same set-to-set rule as program strength, so it
/// reuses [StrengthSessions] (golden-checked), with what the gym adds — a close resets the routine's
/// progress, a completion is dated now (shared/golden/sessions.json, kind `gym`).
///
/// Gym progress and sessions are device-local on Android and not synced (docs/firestore-model.md):
/// on the web they live in the browser; only the finished workout (a GYM entry) is synced.
library;

import 'daily.dart';
import 'sessions.dart';

/// `gym:<routineId>`, the refId of a GYM entry.
String gymRefId(int routineId) => 'gym:$routineId';

/// One slot of a gym routine: [position] is its index in the routine; [reps] holds one value per
/// set (reps for REPS, seconds for TIME).
class GymSlot {
  const GymSlot({
    required this.position,
    required this.exerciseId,
    required this.type,
    required this.reps,
    required this.restSeconds,
  });

  final int position;
  final int exerciseId;
  final ExerciseType type;
  final List<int> reps;
  final int restSeconds;

  int get sets => reps.length;
}

class GymRoutine {
  const GymRoutine({required this.routineId, required this.routineName, required this.exercises, this.routineNameEs});

  final int routineId;

  /// The English name: the stored title of a GYM entry (as Android stores it).
  final String routineName;
  final String? routineNameEs;
  final List<GymSlot> exercises;
}

abstract final class GymContent {
  /// `routines_for_gym.jsonc` (already decoded, comments and trailing commas removed), ordered by
  /// routineId as Android's catalog.
  static List<GymRoutine> routines(Object? decoded) {
    if (decoded is! List) throw const FormatException('routines_for_gym.jsonc is not a list');
    final routines = <GymRoutine>[];
    for (final item in decoded) {
      if (item is! Map) continue;
      final id = item['routineId'];
      final name = item['routineName'];
      final slots = item['exercises'];
      if (id is! int || name is! String || slots is! List) continue;
      final nameEs = item['routineNameEs'];
      routines.add(
        GymRoutine(
          routineId: id,
          routineName: name,
          routineNameEs: nameEs is String ? nameEs : null,
          exercises: [
            for (final (position, slot) in slots.indexed)
              if (slot is Map && slot['exerciseId'] is int && slot['reps'] is List && slot['rest'] is int)
                if (ExerciseType.fromCode(slot['type']) case final type?)
                  GymSlot(
                    position: position,
                    exerciseId: slot['exerciseId'] as int,
                    type: type,
                    reps: [for (final rep in slot['reps'] as List) rep as int],
                    restSeconds: slot['rest'] as int,
                  ),
          ],
        ),
      );
    }
    routines.sort((a, b) => a.routineId.compareTo(b.routineId));
    return routines;
  }
}

/// The localized pieces of a summary (Android's `ExerciseSummaryLabels`).
class ExerciseSummaryLabels {
  const ExerciseSummaryLabels({
    required this.sets,
    required this.reps,
    required this.time,
    required this.seconds,
    required this.join,
  });

  final String Function(int count) sets;
  final String Function(String values) reps;
  final String Function(String values) time;
  final String Function(int seconds) seconds;
  final String Function(String sets, String values) join;
}

/// "Sets: 4 · Reps: 10, 10, 10, 10" or "Sets: 2 · Time: 30s, 45s" (Android's `exerciseSummary`).
String exerciseSummary(ExerciseType type, List<int> reps, ExerciseSummaryLabels labels) {
  final values = switch (type) {
    ExerciseType.reps => labels.reps(reps.join(', ')),
    ExerciseType.time => labels.time(reps.map(labels.seconds).join(', ')),
  };
  return labels.join(labels.sets(reps.length), values);
}

/// A gym session as stored (Android's `GymSession`).
class GymSession {
  const GymSession({required this.routineId, required this.startedAt, required this.lastSetAt});

  final int routineId;
  final int startedAt;
  final int lastSetAt;

  int get durationSeconds => SessionRules.durationSeconds(startedAt, lastSetAt);
}

/// Where the gym sessions live and what closing one touches (Android's `GymSessionStore`).
abstract interface class GymSessionStore {
  GymSession? get(int routineId);
  List<GymSession> getAll();
  void save(GymSession session);
  void delete(int routineId);

  /// Clears the routine's completed positions.
  void clearProgress(int routineId);

  /// One GYM entry: [completedAt], dated on [dateOfMillis] (the session's start for an abandoned one).
  void recordSession(int routineId, int durationSeconds, {required int completedAt, required int dateOfMillis});
}

/// Android's `GymSessionTracker` on the shared set-to-set rule ([StrengthSessions]).
class GymSessions {
  GymSessions(GymSessionStore store, WallClock clock)
    : _store = store,
      _clock = clock,
      _sessions = StrengthSessions(_GymAdapter(store), clock);

  static const String _key = 'gym';

  final GymSessionStore _store;
  final WallClock _clock;
  final StrengthSessions _sessions;

  /// A set of [routineId] was completed now: an inactive session is closed first (recorded, progress
  /// reset), then this set starts or extends one.
  void onSetCompleted(int routineId) => _sessions.onSetCompleted(_key, routineId, 0);

  /// The routine was completed: its entry (first → last set, dated now) and the session ends. False
  /// when there is no session (already recorded: a completion shown again after a reload).
  bool completeSession(int routineId) {
    final session = _sessions.take(_key, routineId, 0);
    if (session == null) return false;
    final now = _clock();
    _store.recordSession(routineId, session.durationSeconds, completedAt: now, dateOfMillis: now);
    return true;
  }

  /// Closes every session inactive for more than 2 hours.
  void closeInactive() => _sessions.closeInactive();

  /// The progress was reset by hand: the session ends without being recorded.
  void discardSession(int routineId) => _sessions.take(_key, routineId, 0);

  List<ClosedSession> takeClosedSessions() => _sessions.takeClosedSessions();
}

/// [StrengthSessions]' store over a [GymSessionStore]: the routine is the "week" of the strength key.
class _GymAdapter implements StrengthSessionStore {
  _GymAdapter(this._store);

  final GymSessionStore _store;

  StrengthSession _strength(GymSession s) => StrengthSession(
    programId: GymSessions._key,
    week: s.routineId,
    dayIndex: 0,
    startedAt: s.startedAt,
    lastActivityAt: s.lastSetAt,
  );

  @override
  StrengthSession? get(String programId, int week, int dayIndex) {
    final session = _store.get(week);
    return session == null ? null : _strength(session);
  }

  @override
  List<StrengthSession> getAll() => [for (final s in _store.getAll()) _strength(s)];

  @override
  void save(StrengthSession session) =>
      _store.save(GymSession(routineId: session.week, startedAt: session.startedAt, lastSetAt: session.lastActivityAt));

  @override
  void delete(String programId, int week, int dayIndex) => _store.delete(week);

  /// An abandoned session: one entry dated on its start, completed at its last set; then the
  /// routine's progress is reset.
  @override
  void recordAbandoned(StrengthSession session) {
    _store.recordSession(
      session.week,
      session.durationSeconds,
      completedAt: session.lastActivityAt,
      dateOfMillis: session.startedAt,
    );
    _store.clearProgress(session.week);
  }
}
