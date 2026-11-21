import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/data/write_mappers.dart';
import 'package:tiominuter_web/domain/daily_progress.dart';
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/domain/program.dart';
import 'package:tiominuter_web/domain/training.dart';

import '../support/fixtures.dart';

/// The web's writes are exactly the shared fixtures (shared/fixtures/firestore_documents.json): the
/// documents the rules tests prove the deployed rules accept.
Object? _json(Object? value) => switch (value) {
  ServerTimestamp() => {r'$serverTimestamp': true},
  final Map<String, Object?> map => {for (final MapEntry(:key, :value) in map.entries) key: _json(value)},
  final List<Object?> list => [for (final item in list) _json(item)],
  _ => value,
};

void main() {
  final entryId = SharedFixtures.constant('ENTRY_ID');
  final programId = SharedFixtures.constant('PROGRAM_ID');
  final week = SharedFixtures.constant('WEEK');

  test('a gym entry', () {
    final fields = WriteMappers.trainingEntry(
      TrainingEntryDoc(
        entryId: entryId,
        dateLocal: '2026-10-20',
        completedAt: 1760990400000,
        source: TrainingSource.gym,
        refId: 'gym:64',
        title: 'Pull Day',
        durationSeconds: 2700,
        createdAt: 1760990400000,
      ),
    );
    final fixture = SharedFixtures.raw('gymEntry');
    // Android writes every field, nulls included; the fixture leaves the activity fields out
    expect(_json(fields), {...fixture, 'activityType': null, 'intensity': null, 'note': null});
  });

  test('an activity entry', () {
    final fields = WriteMappers.trainingEntry(
      TrainingEntryDoc(
        entryId: entryId,
        dateLocal: '2026-10-20',
        completedAt: 1760990400000,
        source: TrainingSource.activity,
        refId: null,
        title: '',
        durationSeconds: 1800,
        createdAt: 1760990400000,
        activityType: 'RUNNING',
        intensity: 'MODERATE',
        note: 'Easy run by the river',
      ),
    );
    expect(_json(fields), SharedFixtures.raw('activityEntry'));
  });

  test('a tombstone: only the id field, deleted, updatedAt, schemaVersion', () {
    expect(_json(WriteMappers.tombstone('entryId', entryId)), {'entryId': entryId, ...SharedFixtures.raw('tombstone')});
  });

  test('a weekly goal (the natural id: the Monday)', () {
    expect(_json(WriteMappers.weeklyGoal(week, 4)), SharedFixtures.raw('weeklyGoal'));
  });

  test('the daily progress: the day and the cycle, no id field and no deleted field', () {
    expect(
      _json(WriteMappers.dailyProgress(const DailyProgress(lastCompletedDay: 7, cycle: 1))),
      SharedFixtures.raw('dailyProgress'),
    );
  });

  test('a program, its workout and its completion', () {
    const profile = ProgramProfile(
      age: 30,
      weightKg: 70.5,
      heightCm: 175,
      level: ProgramLevel.beginner,
      goal: ProgramGoal.gainMuscle,
      location: ProgramLocation.home,
      weeks: 6,
      daysPerWeek: 3,
    );
    expect(_json(WriteMappers.program(programId, profile, 1760990400000)), SharedFixtures.raw('program'));
    expect(
      _json(
        WriteMappers.programWorkout(
          2,
          1,
          const StrengthWorkout(title: StrengthDayTitle.fullBodyA, slots: []),
          isDeload: false,
        ),
      ),
      SharedFixtures.raw('programWorkout'),
    );
    expect(_json(WriteMappers.programCompletion(2, 1, 1760990400000)), SharedFixtures.raw('programCompletion'));
  });

  test('my member summary: the member fixture without the join-only fields', () {
    final fields = WriteMappers.memberSummary(
      'Alex Rivera',
      'https://lh3.googleusercontent.com/a/photo',
      const MemberSummary(
        weekStartLocal: '2026-10-19',
        workoutsThisWeek: 2,
        weeklyGoal: 4,
        streakWeeks: 3,
        daysThisWeek: {
          1: [TrainingSource.gym],
          3: [TrainingSource.daily, TrainingSource.activity],
        },
        lastTrainedLocal: '2026-10-21',
      ),
    );
    final fixture = SharedFixtures.raw('member')
      ..remove('joinedAt')
      ..remove('inviteCode');
    expect(_json(fields), fixture);
  });

  test('a post: no thumbnail, the activity type only for an activity, the titleKey when known', () {
    final fields = WriteMappers.post(
      authorUid: 'author',
      authorName: 'Alex Rivera',
      authorPhotoUrl: 'https://lh3.googleusercontent.com/a/photo',
      entry: const TrainingEntry(
        id: 'e1',
        dateLocal: '2026-10-21',
        completedAt: 0,
        source: TrainingSource.gym,
        refId: 'gym:64',
        title: 'Pull Day',
        durationSeconds: 2700,
      ),
      titleKey: null,
    );
    expect(_json(fields), {...SharedFixtures.raw('post'), 'authorUid': 'author'});
    final keyed = WriteMappers.post(
      authorUid: 'author',
      authorName: 'Alex Rivera',
      authorPhotoUrl: null,
      entry: TrainingEntry(
        id: 'e2',
        dateLocal: '2026-10-21',
        completedAt: 0,
        source: TrainingSource.activity,
        title: 'x' * 600,
        durationSeconds: -5,
        activityTypeCode: 'RUNNING',
      ),
      titleKey: 'daily/3',
    );
    expect(keyed['activityType'], 'RUNNING');
    expect(keyed['titleKey'], 'daily/3');
    expect((keyed['title']! as String).length, 500);
    expect(keyed['durationSeconds'], 0);
    expect(keyed.containsKey('thumb'), isFalse);
  });
}
