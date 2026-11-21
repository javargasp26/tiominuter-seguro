import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/app/training_service.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/data/write_mappers.dart';
import 'package:tiominuter_web/domain/activity_entry.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/domain/program.dart';
import 'package:tiominuter_web/domain/training.dart';

import '../support/training_fakes.dart';

/// What the web writes after training (Android's repositories, GroupSummaryPublisher and
/// FeedPublisher), over in-memory repositories.
void main() {
  late TrainingKit kit;

  setUp(() {
    kit = TrainingKit();
    kit.training.storedGoals[kit.monday] = 3;
  });

  Future<TrainingEntry> record({
    int? duration = 1200,
    String refId = 'daily:3',
    TrainingSource source = TrainingSource.daily,
  }) async {
    final entry = await kit.service.recordWorkout(
      user: alex,
      source: source,
      refId: refId,
      title: 'Day 3',
      durationSeconds: duration,
    );
    await settle();
    return entry;
  }

  group('recording', () {
    test('an entry with a lowercase uuid, today\'s date, now, the stored title', () async {
      final entry = await record();
      final doc = kit.training.entries[entry.id]!;
      expect(doc.entryId, 'uuid-1');
      expect(doc.dateLocal, kit.today);
      expect(doc.completedAt, kit.wall());
      expect(doc.createdAt, kit.wall());
      expect(doc.source, TrainingSource.daily);
      expect(doc.refId, 'daily:3');
      expect(newUuid(), matches(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')));
    });

    test('an abandoned session is dated on its start and completed at its last activity', () async {
      final yesterdayNight = DateTime(2026, 10, 20, 23, 30).millisecondsSinceEpoch;
      final entry = await kit.service.recordWorkout(
        user: alex,
        source: TrainingSource.daily,
        refId: 'daily:3',
        title: 'Day 3',
        durationSeconds: 900,
        completedAt: yesterdayNight + 1000,
        dateOfMillis: yesterdayNight,
      );
      expect(entry.dateLocal, '2026-10-20');
      expect(entry.completedAt, yesterdayNight + 1000);
    });

    test('the tombstone, and the post deleted in every group of my index', () async {
      kit.sharing.groupIds = ['g1', 'g2'];
      kit.service.setFeedIntroSeen(alex.uid);
      final entry = await record();
      await settle();
      expect(kit.sharing.posts['g1'], contains(entry.id));
      await kit.service.deleteEntry(alex, entry);
      await settle();
      expect(kit.training.entries[entry.id], isNull, reason: 'a tombstone');
      expect(kit.training.writes.last, 'tombstone ${entry.id}');
      expect(kit.sharing.writes, containsAll(['delete g1 ${entry.id}', 'delete g2 ${entry.id}']));
      expect(kit.service.isShared(alex.uid, entry.id), isFalse);
    });
  });

  group('my summary for my groups', () {
    test('published to every group of my index, then skipped while unchanged', () async {
      kit.sharing.groupIds = ['g1', 'g2'];
      await record();
      await settle();
      expect(kit.sharing.writes.where((w) => w.startsWith('summary')), ['summary g1', 'summary g2']);
      final fields = kit.sharing.summaries['g1']!;
      expect(fields['displayName'], 'Alex Rivera', reason: 'trimmed');
      expect(fields['workoutsThisWeek'], 1);
      expect(fields['weeklyGoal'], 3);
      expect(fields['weekStartLocal'], kit.monday);
      expect(fields['lastTrainedLocal'], kit.today);
      expect(fields['updatedAt'], same(serverTimestamp));
      expect(fields.containsKey('joinedAt'), isFalse);

      kit.sharing.writes.clear();
      await kit.service.publishSummary(alex);
      expect(kit.sharing.writes, isEmpty, reason: 'unchanged: nothing written');

      await record(duration: 300);
      expect(kit.sharing.writes, isEmpty, reason: 'a short workout changes nothing in the summary');
      await record();
      expect(kit.sharing.writes.where((w) => w.startsWith('summary')), hasLength(2));
      expect(kit.sharing.summaries['g2']!['workoutsThisWeek'], 2);
    });

    test('a group that refuses (I was removed) does not block the others', () async {
      kit.sharing
        ..groupIds = ['gone', 'g2']
        ..refusing.add('gone');
      await record();
      await settle();
      expect(kit.sharing.summaries.keys, ['g2']);
    });

    test('without a goal this week nothing is published; without groups nothing is read', () async {
      kit.training.storedGoals.clear();
      kit.sharing.groupIds = ['g1'];
      await record();
      expect(kit.sharing.summaries, isEmpty);
    });

    test(
      'the same summary as GroupSummaryCalculator: the streak read back, the last trained date before this week',
      () async {
        kit.sharing.groupIds = ['g1'];
        final lastMonday = CalendarDates.plusWeeks(kit.today, -1);
        kit.training.storedGoals
          ..clear()
          ..[CalendarDates.plusWeeks(kit.today, -3)] = 1;
        for (final (i, date) in [lastMonday, CalendarDates.plusWeeks(kit.today, -2)].indexed) {
          await kit.training.saveEntry(
            alex.uid,
            TrainingEntryDoc(
              entryId: 'old-$i',
              dateLocal: date,
              completedAt: 0,
              source: TrainingSource.gym,
              refId: 'gym:64',
              title: 'Pull Day',
              durationSeconds: 1800,
              createdAt: 0,
            ),
          );
        }
        final summary = await kit.service.currentSummary(alex.uid);
        expect(summary!.streakWeeks, 2);
        expect(summary.lastTrainedLocal, lastMonday);
        expect(summary.workoutsThisWeek, 0);
      },
    );

    test('a goal change publishes the new goal', () async {
      kit.sharing.groupIds = ['g1'];
      await kit.service.setGoal(alex, 5);
      await settle();
      expect(kit.training.writes, contains('goal ${kit.monday} 5'));
      expect(kit.sharing.summaries['g1']!['weeklyGoal'], 5);
    });
  });

  group('the feed', () {
    test('nothing is posted until the explanation was answered', () async {
      kit.sharing.groupIds = ['g1'];
      await record();
      expect(kit.sharing.posts, isEmpty);
    });

    test('one post per group with the daily titleKey, the author profile, no thumbnail', () async {
      kit.sharing.groupIds = ['g1', 'g2'];
      kit.service.setFeedIntroSeen(alex.uid);
      final entry = await record();
      await settle();
      for (final group in ['g1', 'g2']) {
        final post = kit.sharing.posts[group]![entry.id]!;
        expect(post['titleKey'], 'daily/3');
        expect(post['authorName'], 'Alex Rivera');
        expect(post['authorPhotoUrl'], 'https://lh3.googleusercontent.com/a/photo');
        expect(post['weekStartLocal'], kit.monday);
        expect(post.containsKey('thumb'), isFalse);
      }
    });

    test('a program workout posts its program/<CODE> key; a short workout and a past week post nothing', () async {
      kit.sharing.groupIds = ['g1'];
      kit.service.setFeedIntroSeen(alex.uid);
      final entry = await kit.service.completeProgramWorkout(
        user: alex,
        programId: 'p1',
        week: 1,
        dayIndex: 0,
        workout: const StrengthWorkout(title: StrengthDayTitle.lowerC, slots: []),
        durationSeconds: 1500,
      );
      await settle();
      expect(kit.sharing.posts['g1']![entry.id]!['titleKey'], 'program/LOWER_C');
      expect(entry.refId, 'program:p1:1:0');
      expect(entry.title, 'Lower C');
      expect(kit.training.completions['p1'], contains('1-0'));

      final short = await record(duration: 599);
      await settle();
      expect(kit.sharing.posts['g1']!.containsKey(short.id), isFalse);

      final lastWeek = await kit.service.recordWorkout(
        user: alex,
        source: TrainingSource.daily,
        refId: 'daily:3',
        title: 'Day 3',
        durationSeconds: 1200,
        completedAt: DateTime.parse('${CalendarDates.plusWeeks(kit.today, -1)} 10:00').millisecondsSinceEpoch,
      );
      await settle();
      expect(kit.sharing.posts['g1']!.containsKey(lastWeek.id), isFalse);
    });

    test('turning the switch off deletes the posts made', () async {
      kit.sharing.groupIds = ['g1', 'g2'];
      kit.service.setFeedIntroSeen(alex.uid);
      final entry = await record();
      await settle();
      kit.service.unshareEntry(alex.uid, entry.id);
      await settle();
      expect(kit.sharing.posts['g1'], isEmpty);
      expect(kit.sharing.posts['g2'], isEmpty);
    });
  });

  group('logged activities', () {
    ActivityEntryInput input({ActivityType? type = ActivityType.running, int? minutes = 30, int? at}) =>
        ActivityEntryInput(
          type: type,
          customName: '',
          durationMinutes: minutes,
          intensity: Intensity.moderate,
          completedAt: at ?? kit.wall() - 60000,
          note: '  Easy run  ',
        );

    test('invalid input writes nothing', () async {
      final result = await kit.service.saveActivity(user: alex, input: input(type: null, minutes: 601));
      expect(result.errors, {ActivityEntryError.typeMissing, ActivityEntryError.durationOutOfRange});
      expect(kit.training.writes, isEmpty);
    });

    test(
      'saved as Android: minutes as seconds, the trimmed note, an empty title; an edit keeps its id and createdAt',
      () async {
        final saved = await kit.service.saveActivity(user: alex, input: input());
        final doc = kit.training.entries[saved.entry!.id]!;
        expect(doc.durationSeconds, 1800);
        expect(doc.note, 'Easy run');
        expect(doc.title, '');
        expect(doc.activityType, 'RUNNING');
        expect(doc.intensity, 'MODERATE');
        kit.scheduler.advance(60000);
        final edited = await kit.service.saveActivity(user: alex, input: input(minutes: 45), existing: doc);
        final after = kit.training.entries[edited.entry!.id]!;
        expect(after.entryId, doc.entryId);
        expect(after.createdAt, doc.createdAt);
        expect(after.durationSeconds, 2700);
      },
    );

    test('the future and more than 6 days back are refused; an edit keeps its own older day', () async {
      expect(
        (await kit.service.saveActivity(
          user: alex,
          input: input(at: kit.wall() + 60000),
        )).errors,
        {ActivityEntryError.dateInFuture},
      );
      final eightDaysAgo = ActivityEntryRules.epochMillisAt(CalendarDates.plusDays(kit.today, -8), 18, 0);
      expect(
        (await kit.service.saveActivity(
          user: alex,
          input: input(at: eightDaysAgo),
        )).errors,
        {ActivityEntryError.dateTooEarly},
      );
      final old = TrainingEntryDoc(
        entryId: 'old',
        dateLocal: CalendarDates.plusDays(kit.today, -8),
        completedAt: eightDaysAgo,
        source: TrainingSource.activity,
        refId: null,
        title: '',
        durationSeconds: 1800,
        createdAt: eightDaysAgo,
        activityType: 'RUNNING',
        intensity: 'EASY',
      );
      expect(
        (await kit.service.saveActivity(
          user: alex,
          input: input(at: eightDaysAgo),
          existing: old,
        )).errors,
        isEmpty,
      );
    });
  });

  test('the member profile: trimmed, at most 100 characters, the fallback name, https photos only', () {
    expect(memberProfileOf(const WebUser(uid: 'u', displayName: '  Ana  '), 'Member').displayName, 'Ana');
    expect(memberProfileOf(const WebUser(uid: 'u', displayName: '   '), 'Member').displayName, 'Member');
    expect(memberProfileOf(const WebUser(uid: 'u'), 'Miembro').displayName, 'Miembro');
    expect(memberProfileOf(WebUser(uid: 'u', displayName: 'x' * 150), 'Member').displayName.length, 100);
    expect(memberProfileOf(const WebUser(uid: 'u', photoUrl: 'http://x/p.png'), 'Member').photoUrl, isNull);
    expect(memberProfileOf(const WebUser(uid: 'u', photoUrl: 'https://x/p.png'), 'Member').photoUrl, 'https://x/p.png');
  });

  test('sign-out clears every training key of the account', () async {
    kit.service.setFeedIntroSeen(alex.uid);
    kit.service.dailySessions(alex.uid).start('daily:3');
    await kit.service.clearLocalData(alex.uid);
    expect(kit.store.values.keys.where((k) => k.startsWith('tm.${alex.uid}.')), isEmpty);
  });
}
