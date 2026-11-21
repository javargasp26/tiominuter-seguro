import 'dart:typed_data';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/data/firestore_mappers.dart';
import 'package:tiominuter_web/domain/daily_progress.dart';
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/weekly_progress.dart';
import 'package:tiominuter_web/domain/workout_names.dart';

import '../support/fixtures.dart';

/// The Dart models against the documents the rules tests write (shared/fixtures): every type maps,
/// tombstones and other schema versions don't, unknown fields are ignored.
void main() {
  final entryId = SharedFixtures.constant('ENTRY_ID');
  final programId = SharedFixtures.constant('PROGRAM_ID');

  group('training entries', () {
    test('a gym entry maps every field the model has', () {
      final entry = FirestoreMappers.trainingEntry(entryId, SharedFixtures.document('gymEntry'))!;
      expect(entry.id, entryId);
      expect(entry.dateLocal, '2026-10-20');
      expect(entry.completedAt, 1760990400000);
      expect(entry.source, TrainingSource.gym);
      expect(entry.refId, 'gym:64');
      expect(entry.title, 'Pull Day');
      expect(entry.durationSeconds, 2700);
      expect(entry.activity, isNull);
    });

    test('an activity entry keeps its type; the note and intensity are not read', () {
      final entry = FirestoreMappers.trainingEntry(entryId, SharedFixtures.document('activityEntry'))!;
      expect(entry.source, TrainingSource.activity);
      expect(entry.activity, ActivityType.running);
      expect(entry.refId, isNull);
      expect(entry.title, '');
    });

    test('a tombstone, another schemaVersion or a missing required field is not an entry', () {
      expect(FirestoreMappers.trainingEntry(entryId, SharedFixtures.tombstone('entryId', entryId)), isNull);
      expect(
        FirestoreMappers.trainingEntry(entryId, SharedFixtures.document('gymEntry', overrides: {'schemaVersion': 2})),
        isNull,
      );
      for (final field in ['dateLocal', 'completedAt', 'source', 'title']) {
        expect(
          FirestoreMappers.trainingEntry(
            entryId,
            SharedFixtures.document('gymEntry', overrides: {field: SharedFixtures.remove}),
          ),
          isNull,
          reason: field,
        );
      }
      expect(
        FirestoreMappers.trainingEntry(entryId, SharedFixtures.document('gymEntry', overrides: {'source': 'FUTURE'})),
        isNull,
      );
      expect(
        FirestoreMappers.trainingEntry(
          entryId,
          SharedFixtures.document('gymEntry', overrides: {'dateLocal': '2026-02-30'}),
        ),
        isNull,
      );
    });

    test('optional fields may be absent or null (old rows), unknown fields are ignored', () {
      final old = SharedFixtures.document(
        'gymEntry',
        overrides: {'refId': SharedFixtures.remove, 'durationSeconds': null, 'futureField': 'x'},
      );
      final entry = FirestoreMappers.trainingEntry(entryId, old)!;
      expect(entry.refId, isNull);
      expect(entry.durationSeconds, isNull);
    });
  });

  group('weekly goals and programs', () {
    test('a weekly goal maps; its tombstone does not', () {
      final week = SharedFixtures.constant('WEEK');
      expect(
        FirestoreMappers.weeklyGoal(week, SharedFixtures.document('weeklyGoal')),
        const WeeklyGoal(workoutsPerWeek: 4, effectiveFromWeek: '2026-10-19'),
      );
      expect(FirestoreMappers.weeklyGoal(week, SharedFixtures.tombstone('effectiveFromWeek', week)), isNull);
    });

    test('the daily progress maps; another version or a value out of range does not', () {
      final document = SharedFixtures.document('dailyProgress');
      expect(FirestoreMappers.dailyProgress(document), const DailyProgress(lastCompletedDay: 7, cycle: 1));
      expect(FirestoreMappers.dailyProgress({...document, 'schemaVersion': 2}), isNull);
      expect(FirestoreMappers.dailyProgress({...document, 'lastCompletedDay': 1000}), isNull);
      expect(FirestoreMappers.dailyProgress({...document, 'lastCompletedDay': -1}), isNull);
      expect(FirestoreMappers.dailyProgress({...document, 'cycle': -1}), isNull);
      expect(FirestoreMappers.dailyProgress({...document, 'cycle': '1'}), isNull);
      expect(FirestoreMappers.dailyProgress({...document}..remove('cycle')), isNull);
    });

    test('a program is live until tombstoned; its workout payload gives the title code', () {
      expect(FirestoreMappers.isLiveProgram(SharedFixtures.document('program')), isTrue);
      expect(FirestoreMappers.isLiveProgram(SharedFixtures.tombstone('programId', programId)), isFalse);
      expect(FirestoreMappers.isLiveProgram(null), isFalse);
      expect(
        FirestoreMappers.programWorkoutTitleCode(SharedFixtures.document('programWorkout')),
        ProgramTitleCode.fullBodyA,
      );
    });

    test('every payload kind maps to its code, an unreadable one to null', () {
      String payload(String json) => json;
      Map<String, Object?> workout(String json) =>
          SharedFixtures.document('programWorkout', overrides: {'payload': payload(json)});
      expect(
        FirestoreMappers.programWorkoutTitleCode(workout('{"kind":"OUTDOOR_RUN","distanceKm":3.0,"walk":false}')),
        ProgramTitleCode.outdoorRun,
      );
      expect(
        FirestoreMappers.programWorkoutTitleCode(workout('{"kind":"OUTDOOR_RUN","distanceKm":3.0,"walk":true}')),
        ProgramTitleCode.briskWalk,
      );
      expect(
        FirestoreMappers.programWorkoutTitleCode(workout('{"kind":"TREADMILL","segments":[]}')),
        ProgramTitleCode.treadmill,
      );
      expect(
        FirestoreMappers.programWorkoutTitleCode(workout('{"kind":"HOME_CIRCUIT"}')),
        ProgramTitleCode.homeCircuit,
      );
      expect(
        FirestoreMappers.programWorkoutTitleCode(workout('{"kind":"STRENGTH","title":"LOWER_C"}')),
        ProgramTitleCode.lowerC,
      );
      expect(FirestoreMappers.programWorkoutTitleCode(workout('{"kind":"STRENGTH","title":"NEW_ONE"}')), isNull);
      expect(FirestoreMappers.programWorkoutTitleCode(workout('not json')), isNull);
      expect(FirestoreMappers.programWorkoutTitleCode(SharedFixtures.tombstone('workoutId', '2-1')), isNull);
    });
  });

  group('groups', () {
    final groupId = SharedFixtures.constant('GROUP_ID');

    test('a membership names its group; a group maps', () {
      expect(
        FirestoreMappers.membershipGroupId(groupId, SharedFixtures.document('membership', args: {'groupId': groupId})),
        groupId,
      );
      final group = FirestoreMappers.group(groupId, SharedFixtures.document('group', args: {'ownerUid': 'ana'}))!;
      expect(group.name, 'Morning crew');
      expect(group.ownerUid, 'ana');
      expect(group.memberCount, 1);
    });

    test('a member summary maps with its days (Monday = 1) and only https photos', () {
      final member = FirestoreMappers.member('ana', SharedFixtures.document('member'))!;
      expect(member.displayName, 'Alex Rivera');
      expect(member.photoUrl, 'https://lh3.googleusercontent.com/a/photo');
      expect(member.summary.weekStartLocal, '2026-10-19');
      expect(member.summary.workoutsThisWeek, 2);
      expect(member.summary.weeklyGoal, 4);
      expect(member.summary.streakWeeks, 3);
      expect(member.summary.daysThisWeek, {
        1: [TrainingSource.gym],
        3: [TrainingSource.daily, TrainingSource.activity],
      });
      expect(member.summary.lastTrainedLocal, '2026-10-21');
      final http = FirestoreMappers.member(
        'ana',
        SharedFixtures.document('member', overrides: {'photoUrl': 'http://x', 'daysThisWeek': <String, Object?>{}}),
      )!;
      expect(http.photoUrl, isNull);
      expect(http.summary.daysThisWeek, isEmpty);
    });

    test('a post maps with its reactions, titleKey, thumbnail and server time', () {
      final post = FirestoreMappers.post(
        'p1',
        SharedFixtures.document(
          'post',
          args: {'authorUid': 'ana'},
          overrides: {
            'titleKey': 'gym/64',
            'reactions': {'bob': 'fire', 'cara': 'unknown'},
            'thumb': Blob(Uint8List.fromList([1, 2, 3])),
          },
        ),
      )!;
      expect(post.authorUid, 'ana');
      expect(post.source, TrainingSource.gym);
      expect(post.title, 'Pull Day');
      expect(post.titleKey, 'gym/64');
      expect(post.reactions, {'bob': Reaction.fire});
      expect(post.thumb, [1, 2, 3]);
      expect(post.weekStartLocal, '2026-10-19');
      expect(post.createdAtMillis, SharedFixtures.serverTime.millisecondsSinceEpoch);
    });

    test('a post without the optional fields still maps (older apps)', () {
      final post = FirestoreMappers.post('p1', SharedFixtures.document('post', args: {'authorUid': 'ana'}))!;
      expect(post.titleKey, isNull);
      expect(post.thumb, isNull);
      expect(post.activityType, isNull);
      expect(post.reactions, isEmpty);
    });
  });
}
