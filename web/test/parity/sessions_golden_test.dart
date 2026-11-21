import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/domain/gym.dart';
import 'package:tiominuter_web/domain/sessions.dart';

import '../support/fake_scheduler.dart';
import 'golden.dart';

/// shared/golden/sessions.json: the session rules and the same event sequences give Android's stored
/// sessions, recorded sessions and finished seconds (SessionGoldenTest.kt).
class _MemoryActiveStore implements ActiveSessionStore {
  ActiveSession? session;
  final List<ActiveSession> abandoned = [];
  int saves = 0;

  @override
  ActiveSession? get() => session;

  @override
  void save(ActiveSession session) {
    this.session = session;
    saves++;
  }

  @override
  void delete() => session = null;

  @override
  void recordAbandoned(ActiveSession session) => abandoned.add(session);
}

class _MemoryGymStore implements GymSessionStore {
  final Map<int, GymSession> sessions = {};
  final List<int> cleared = [];
  final List<Map<String, Object?>> recorded = [];

  @override
  GymSession? get(int routineId) => sessions[routineId];

  @override
  List<GymSession> getAll() => sessions.values.toList();

  @override
  void save(GymSession session) => sessions[session.routineId] = session;

  @override
  void delete(int routineId) => sessions.remove(routineId);

  @override
  void clearProgress(int routineId) => cleared.add(routineId);

  // As Android's recorder: a GYM entry titled with the routine's name
  @override
  void recordSession(int routineId, int durationSeconds, {required int completedAt, required int dateOfMillis}) =>
      recorded.add({
        'source': 'GYM',
        'refId': gymRefId(routineId),
        'title': 'Routine $routineId',
        'durationSeconds': durationSeconds,
        'completedAt': completedAt,
        'dateOfMillis': dateOfMillis,
      });
}

class _MemoryStrengthStore implements StrengthSessionStore {
  final Map<String, StrengthSession> sessions = {};
  final List<StrengthSession> abandoned = [];

  String _key(String programId, int week, int dayIndex) => '$programId/$week/$dayIndex';

  @override
  StrengthSession? get(String programId, int week, int dayIndex) => sessions[_key(programId, week, dayIndex)];

  @override
  List<StrengthSession> getAll() => sessions.values.toList();

  @override
  void save(StrengthSession session) => sessions[_key(session.programId, session.week, session.dayIndex)] = session;

  @override
  void delete(String programId, int week, int dayIndex) => sessions.remove(_key(programId, week, dayIndex));

  @override
  void recordAbandoned(StrengthSession session) => abandoned.add(session);
}

Object? _active(ActiveSession? session) => session == null
    ? null
    : {
        'key': session.key,
        'startedAt': session.startedAt,
        'activeSeconds': session.activeSeconds,
        'lastActivityAt': session.lastActivityAt,
      };

Object? _strength(StrengthSession session) => {
  'dayIndex': session.dayIndex,
  'startedAt': session.startedAt,
  'lastActivityAt': session.lastActivityAt,
  'durationSeconds': session.durationSeconds,
};

void main() {
  final golden = Golden.load('sessions');
  final baseWall = golden['baseWall']! as int;

  test('the session rules and constants are Android\'s', () {
    final rules = golden['rules']! as Map;
    expect(sessionInactivityTimeoutMillis, rules['inactivityTimeoutMillis']);
    expect(ActiveWorkoutSession.checkpointMillis, rules['checkpointMillis']);
    for (final c in (rules['isInactive']! as List).cast<Map<Object?, Object?>>()) {
      expect(SessionRules.isInactive(c['lastActivityAt'] as int, c['now'] as int), c['inactive'], reason: '$c');
    }
    for (final c in (rules['durationSeconds']! as List).cast<Map<Object?, Object?>>()) {
      expect(SessionRules.durationSeconds(c['startedAt'] as int, c['endedAt'] as int), c['seconds'], reason: '$c');
    }
    for (final c in (rules['latestRun']! as List).cast<Map<Object?, Object?>>()) {
      final run = SessionRules.latestRun([for (final t in c['times'] as List) t as int]);
      expect(run == null ? null : [run.$1, run.$2], c['run'], reason: '$c');
    }
  });

  test('every event sequence gives Android\'s sessions', () {
    final mismatches = Mismatches();
    final cases = Golden.cases(golden);
    for (final item in cases) {
      final id = item['id']! as String;
      final run = item['run']! as Map;
      final events = run['events']! as List;
      final expectedSteps = run['steps']! as List;
      final result = run['result']! as Map;
      final scheduler = FakeScheduler();
      var wallOffset = 0;
      int wall() => baseWall + scheduler.now + wallOffset;
      final steps = <Object?>[];

      if (item['kind'] == 'active') {
        final store = _MemoryActiveStore();
        final tracker = ActiveSessionTracker(store, wall);
        var meter = ActiveTimeMeter(() => scheduler.now);
        var session = ActiveWorkoutSession(scheduler, tracker, meter, 'daily:3');
        final finished = <int>[];
        final announced = <int>[];
        for (final event in events) {
          final e = event as Map;
          switch (e['type']) {
            case 'advance':
              scheduler.advance(e['ms'] as int);
            case 'clock':
              wallOffset += e['ms'] as int;
            case 'start':
              announced.addAll(session.start().map((closed) => closed.durationSeconds));
            case 'timer':
              session.setTimerRunning(e['on'] as bool);
            case 'onScreen':
              session.setActiveOnScreen(e['on'] as bool);
            case 'visible':
              session.setVisible(e['on'] as bool);
            case 'action':
              session.onUserAction();
            case 'finish':
              finished.add(session.finish());
            case 'processDeath':
              session.close();
              meter = ActiveTimeMeter(() => scheduler.now);
              session = ActiveWorkoutSession(scheduler, tracker, meter, 'daily:3');
            case 'closeInactive':
              tracker.closeInactive();
            case 'open':
              tracker.start(e['key'] as String);
          }
          scheduler.runCurrent();
          steps.add({'event': e['type'], 'meterSeconds': meter.seconds, 'stored': _active(store.session)});
        }
        session.close();
        mismatches
          ..check(id, 'steps', steps, expectedSteps)
          ..check(id, 'recorded', [for (final s in store.abandoned) _active(s)], result['recorded'])
          ..check(id, 'finishedSeconds', finished, result['finishedSeconds'])
          ..check(id, 'announcedSeconds', announced, result['announcedSeconds'])
          ..check(id, 'saves', store.saves, result['saves']);
      } else if (item['kind'] == 'gym') {
        // Kotlin stores sessions by routineId (insertion order); the Dart map keeps it too
        final store = _MemoryGymStore();
        final sessions = GymSessions(store, wall);
        final completed = <bool>[];
        for (final event in events) {
          final e = event as Map;
          final routineId = e['ms'] as int?;
          switch (e['type']) {
            case 'advance':
              scheduler.advance(routineId!);
            case 'clock':
              wallOffset += routineId!;
            case 'set':
              sessions.onSetCompleted(routineId!);
            case 'complete':
              completed.add(sessions.completeSession(routineId!));
            case 'discard':
              sessions.discardSession(routineId!);
            case 'closeInactive':
              sessions.closeInactive();
          }
          scheduler.runCurrent();
          steps.add({
            'event': e['type'],
            'stored': [
              for (final s in store.sessions.values)
                {'routineId': s.routineId, 'startedAt': s.startedAt, 'lastSetAt': s.lastSetAt},
            ],
          });
        }
        mismatches
          ..check(id, 'steps', steps, expectedSteps)
          ..check(id, 'recorded', store.recorded, result['recorded'])
          ..check(id, 'completed', completed, result['completed'])
          ..check(id, 'progressCleared', store.cleared, result['progressCleared'])
          ..check(id, 'announcedSeconds', [
            for (final closed in sessions.takeClosedSessions()) closed.durationSeconds,
          ], result['announcedSeconds']);
      } else {
        final store = _MemoryStrengthStore();
        final sessions = StrengthSessions(store, wall);
        for (final event in events) {
          final e = event as Map;
          switch (e['type']) {
            case 'advance':
              scheduler.advance(e['ms'] as int);
            case 'clock':
              wallOffset += e['ms'] as int;
            case 'set':
              sessions.onSetCompleted('p7', 1, e['ms'] as int);
            case 'closeInactive':
              sessions.closeInactive();
          }
          scheduler.runCurrent();
          steps.add({
            'event': e['type'],
            'stored': [for (final s in store.sessions.values) _strength(s)],
          });
        }
        mismatches
          ..check(id, 'steps', steps, expectedSteps)
          ..check(id, 'recorded', [for (final s in store.abandoned) _strength(s)], result['recorded'])
          ..check(id, 'announcedSeconds', [
            for (final closed in sessions.takeClosedSessions()) closed.durationSeconds,
          ], result['announcedSeconds']);
      }
    }
    expect(mismatches.count, 0, reason: mismatches.report());
    expect(cases, hasLength(golden['caseCount']));
  });
}
