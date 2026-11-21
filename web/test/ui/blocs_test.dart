import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/weekly_progress.dart';
import 'package:tiominuter_web/domain/workout_names.dart';
import 'package:tiominuter_web/ui/groups/feed_bloc.dart';
import 'package:tiominuter_web/ui/groups/groups_bloc.dart';
import 'package:tiominuter_web/ui/history/history_bloc.dart';
import 'package:tiominuter_web/ui/session/session_bloc.dart';
import 'package:tiominuter_web/ui/week/week_bloc.dart';

import '../support/fakes.dart';

/// The screens' blocs over in-memory repositories: what they read, when their listeners run, and
/// the states and effects they produce. "Today" is Thursday 22 October 2026 (week of the 19th).
void main() {
  final dates = LocalDates(now: () => DateTime(2026, 10, 22, 10));
  Future<void> settle() => Future<void>.delayed(Duration.zero);

  group('WeekBloc', () {
    late FakeUserDataRepository data;
    late WeekBloc bloc;

    setUp(() {
      data = FakeUserDataRepository()
        ..goals = [const WeeklyGoal(workoutsPerWeek: 2, effectiveFromWeek: '2026-10-05')]
        ..entries = [
          trainingEntry('a', date: '2026-10-06'),
          trainingEntry('b', date: '2026-10-07', source: TrainingSource.daily),
          trainingEntry('c', date: '2026-10-13'),
          trainingEntry('d', date: '2026-10-14'),
          trainingEntry('e', date: '2026-10-20'),
          trainingEntry('f', date: '2026-10-20', source: TrainingSource.activity, refId: null),
          trainingEntry('short', date: '2026-10-21', durationSeconds: 300),
        ];
      bloc = WeekBloc(uid: 'me', data: data, dates: dates);
    });

    tearDown(() => bloc.close());

    test('this week, the 10-minute rule, the sources of each day and the streak read back', () async {
      bloc.add(const WeekVisible());
      await settle();
      await settle();
      final state = bloc.state;
      expect(state.isLoading, isFalse);
      expect(state.summary!.progress.workoutCount, 2, reason: 'the 5-minute workout does not count');
      expect(state.summary!.progress.isGoalMet, isTrue);
      expect(state.sourcesPerDay, {
        '2026-10-20': [TrainingSource.gym, TrainingSource.activity],
      });
      expect(state.weekDates.first, '2026-10-19');
      expect(state.streak, 3);
      expect(data.calls, contains('watchEntriesFrom:2026-10-19'));
      expect(data.calls.where((c) => c.startsWith('entriesBetween')), ['entriesBetween:2026-10-05..2026-10-19']);
    });

    test('listeners run only while visible', () async {
      bloc.add(const WeekVisible());
      await settle();
      expect(data.activeListeners, 2);
      bloc.add(const WeekHidden());
      await settle();
      expect(data.activeListeners, 0);
      data.entries = [...data.entries, trainingEntry('g', date: '2026-10-22')];
      data.changed();
      await settle();
      expect(bloc.state.summary!.progress.workoutCount, 2, reason: 'nothing read while hidden');
      bloc.add(const WeekVisible());
      await settle();
      await settle();
      expect(bloc.state.summary!.progress.workoutCount, 3);
    });

    test('a new workout updates the week live', () async {
      bloc.add(const WeekVisible());
      await settle();
      data.entries = [...data.entries, trainingEntry('g', date: '2026-10-22', durationSeconds: null)];
      data.changed();
      await settle();
      expect(bloc.state.summary!.progress.workoutCount, 3);
    });

    test('without a goal there is no streak and no goal', () async {
      data.goals = [];
      bloc.add(const WeekVisible());
      await settle();
      await settle();
      expect(bloc.state.hasGoal, isFalse);
      expect(bloc.state.streak, 0);
    });

    test('a failed read shows the error; retry reads again', () async {
      data.failWith = DataError.network;
      bloc.add(const WeekVisible());
      await settle();
      expect(bloc.state.error, DataError.network);
      data.failWith = null;
      bloc.add(const WeekRetry());
      await settle();
      await settle();
      expect(bloc.state.error, isNull);
      expect(bloc.state.summary, isNotNull);
    });
  });

  group('HistoryBloc', () {
    const uuid = '9a1b2c3d-4e5f-4a6b-8c7d-112233445566';
    late FakeUserDataRepository data;

    HistoryBloc historyBloc({int pageSize = 20}) => HistoryBloc(
      uid: 'me',
      data: data,
      content: FakeContentRepository(
        const WorkoutNameCatalog(gymRoutines: {64: GymRoutineNames('Pull Day', 'Día de tirón')}),
      ),
      pageSize: pageSize,
    );

    setUp(() {
      data = FakeUserDataRepository()
        ..entries = [
          for (var i = 0; i < 25; i++) trainingEntry('e$i', date: '2026-10-${(i % 9 + 10)}', completedAt: i),
          trainingEntry(
            'p',
            date: '2026-10-21',
            completedAt: 100,
            source: TrainingSource.program,
            refId: 'program:$uuid:1:0',
            title: 'Tren superior A',
          ),
        ]
        ..programCodes = {'program:$uuid:1:0': ProgramTitleCode.upperA};
    });

    test('pages of 20 newest first, then load more', () async {
      final bloc = historyBloc()..add(const HistoryLoad());
      await settle();
      await settle();
      expect(bloc.state.entries, hasLength(20));
      expect(bloc.state.entries.first.id, 'p');
      expect(bloc.state.hasMore, isTrue);
      bloc.add(const HistoryLoadMore());
      await settle();
      await settle();
      expect(bloc.state.entries, hasLength(26));
      expect(bloc.state.hasMore, isFalse);
      await bloc.close();
    });

    test('names come from the content and the synced program workouts', () async {
      final bloc = historyBloc()..add(const HistoryLoad());
      await settle();
      await settle();
      final program = bloc.state.entries.firstWhere((e) => e.id == 'p');
      expect(bloc.state.nameOf(program), const ProgramWorkoutName(ProgramTitleCode.upperA));
      expect(bloc.state.nameOf(bloc.state.entries[1]), const GymWorkoutName('Pull Day', 'Día de tirón'));
      await bloc.close();
    });

    test('an empty history and a failure', () async {
      data.entries = [];
      final empty = historyBloc()..add(const HistoryLoad());
      await settle();
      await settle();
      expect(empty.state.isEmpty, isTrue);
      await empty.close();

      data.failWith = DataError.permissionDenied;
      final failing = historyBloc()..add(const HistoryLoad());
      await settle();
      await settle();
      expect(failing.state.error, DataError.permissionDenied);
      await failing.close();
    });
  });

  group('GroupDetailBloc', () {
    late FakeGroupsRepository groups;

    setUp(() {
      groups = FakeGroupsRepository()
        ..addGroup(const Group(id: 'g', name: 'Crew', ownerUid: 'ana', memberCount: 3), [
          groupMember('ana', 'Ana', done: 3, goal: 3),
          groupMember('me', 'Me', done: 1, goal: 3),
          groupMember('old', 'Old', done: 5, goal: 3, week: '2026-10-12'),
        ]);
    });

    test('the ranking of this week, live while visible', () async {
      final bloc = GroupDetailBloc(groupId: 'g', uid: 'me', groups: groups, dates: dates)..add(const GroupVisible());
      await settle();
      await settle();
      expect(bloc.state.ranking.map((r) => r.member.uid), ['ana', 'me', 'old']);
      expect(bloc.state.ranking[1].isMe, isTrue);
      expect(bloc.state.ranking[2].isCurrentWeek, isFalse);
      expect(groups.activeListeners, 2);
      bloc.add(const GroupHidden());
      await settle();
      expect(groups.activeListeners, 0);
      await bloc.close();
    });

    test('a group I lost access to is gone', () async {
      groups.readable.clear();
      final bloc = GroupDetailBloc(groupId: 'g', uid: 'me', groups: groups, dates: dates)..add(const GroupVisible());
      await settle();
      await settle();
      expect(bloc.state.isGone, isTrue);
      await bloc.close();
    });
  });

  group('GroupsBloc', () {
    test('my readable groups; an empty list; a failure', () async {
      final groups = FakeGroupsRepository()
        ..addGroup(const Group(id: 'g', name: 'Crew', ownerUid: 'me', memberCount: 2), const [])
        ..addGroup(const Group(id: 'gone', name: 'Old', ownerUid: 'x', memberCount: 1), const [], member: false);
      final bloc = GroupsBloc(uid: 'me', groups: groups)..add(const GroupsLoad());
      await settle();
      expect(bloc.state.groups.map((g) => g.group.id), ['g']);
      groups.readable.clear();
      bloc.add(const GroupsLoad());
      await settle();
      expect(bloc.state.isEmpty, isTrue);
      groups.failWith = DataError.network;
      bloc.add(const GroupsLoad());
      await settle();
      expect(bloc.state.error, DataError.network);
      await bloc.close();
    });
  });

  group('FeedBloc', () {
    late FakeGroupsRepository groups;
    late FeedBloc bloc;
    late List<FeedEffect> effects;

    setUp(() {
      groups = FakeGroupsRepository()
        ..addGroup(const Group(id: 'g', name: 'Crew', ownerUid: 'ana', memberCount: 3), const [])
        ..posts['g'] = [
          feedPost('older', 'luis', createdAt: 1),
          feedPost('newest', 'ana', createdAt: 3, titleKey: 'gym/64', reactions: {'luis': Reaction.fire}),
          feedPost('lastWeek', 'ana', createdAt: 5, week: '2026-10-12'),
        ];
      bloc = FeedBloc(
        groupId: 'g',
        uid: 'me',
        groups: groups,
        content: FakeContentRepository(
          const WorkoutNameCatalog(gymRoutines: {64: GymRoutineNames('Pull Day', 'Día de tirón')}),
        ),
        dates: dates,
      );
      effects = [];
      bloc.effects.listen(effects.add);
    });

    tearDown(() => bloc.close());

    test("this week's posts newest first, named by their key, live only while visible", () async {
      bloc.add(const FeedVisible());
      await settle();
      await settle();
      expect(bloc.state.posts.map((p) => p.id), ['newest', 'older']);
      expect(bloc.state.nameOf(bloc.state.posts.first), const GymWorkoutName('Pull Day', 'Día de tirón'));
      expect(groups.calls, contains('watchWeekPosts:g:2026-10-19:20'));
      bloc.add(const FeedHidden());
      await settle();
      expect(groups.activeListeners, 0);
    });

    test('a reaction is set, changed and removed, only my key', () async {
      bloc.add(const FeedVisible());
      await settle();
      Reaction? mine() => bloc.state.posts.firstWhere((p) => p.id == 'newest').reactions['me'];
      bloc.add(const FeedReactionTapped('newest', Reaction.strong));
      await settle();
      await settle();
      expect(mine(), Reaction.strong);
      bloc.add(const FeedReactionTapped('newest', Reaction.clap));
      await settle();
      await settle();
      expect(mine(), Reaction.clap);
      bloc.add(const FeedReactionTapped('newest', Reaction.clap));
      await settle();
      await settle();
      expect(mine(), isNull);
      expect(groups.posts['g']!.firstWhere((p) => p.id == 'newest').reactions, {'luis': Reaction.fire});
      expect(groups.calls.where((c) => c.startsWith('setReaction')), [
        'setReaction:g:newest:strong',
        'setReaction:g:newest:clap',
        'setReaction:g:newest:none',
      ]);
    });

    test('a reaction is optimistic and rolled back when the write fails', () async {
      bloc.add(const FeedVisible());
      await settle();
      groups
        ..failReactionsWith = DataError.permissionDenied
        ..reactionGate = Completer<void>();
      bloc.add(const FeedReactionTapped('newest', Reaction.raised));
      await settle();
      expect(bloc.state.posts.firstWhere((p) => p.id == 'newest').reactions['me'], Reaction.raised);
      groups.reactionGate!.complete();
      await settle();
      await settle();
      expect(bloc.state.posts.firstWhere((p) => p.id == 'newest').reactions['me'], isNull);
      expect(effects.single, isA<FeedReactionFailed>());
    });

    test('load more raises the listener limit', () async {
      groups.posts['g'] = [for (var i = 0; i < 25; i++) feedPost('p$i', 'ana', createdAt: i)];
      bloc.add(const FeedVisible());
      await settle();
      expect(bloc.state.posts, hasLength(20));
      expect(bloc.state.canLoadMore, isTrue);
      bloc.add(const FeedLoadMore());
      await settle();
      expect(bloc.state.posts, hasLength(25));
      expect(groups.calls, contains('watchWeekPosts:g:2026-10-19:40'));
    });

    test('an empty feed on Monday says a new week started', () async {
      final monday = FeedBloc(
        groupId: 'g',
        uid: 'me',
        groups: groups..posts['g'] = [],
        content: FakeContentRepository(),
        dates: LocalDates(now: () => DateTime(2026, 10, 19, 9)),
      )..add(const FeedVisible());
      await settle();
      expect(monday.state.isEmpty, isTrue);
      expect(monday.state.isFirstDayOfWeek, isTrue);
      await monday.close();
    });
  });

  group('SessionBloc', () {
    test('signs in; a cancelled popup is silent; a failure is an effect', () async {
      final auth = FakeAuthRepository();
      final bloc = SessionBloc(auth)..add(const SessionStarted());
      final effects = <SessionEffect>[];
      bloc.effects.listen(effects.add);
      await settle();
      expect(bloc.state.isLoading, isFalse);
      expect(bloc.state.user, isNull);

      auth.nextSignIn = SignInOutcome.cancelled;
      bloc.add(const SignInClicked());
      await settle();
      expect(effects, isEmpty);

      auth.nextSignIn = SignInOutcome.popupBlocked;
      bloc.add(const SignInClicked());
      await settle();
      expect((effects.single as SignInFailed).outcome, SignInOutcome.popupBlocked);

      auth.nextSignIn = SignInOutcome.signedIn;
      bloc.add(const SignInClicked());
      await settle();
      await settle();
      expect(bloc.state.user?.uid, 'me');

      bloc.add(const SignOutClicked());
      await settle();
      await settle();
      expect(bloc.state.user, isNull);
      expect(effects.last, isA<SignedOut>());
      await bloc.close();
    });
  });

  test('the week dates used by the blocs are Monday to Sunday', () {
    expect(CalendarDates.weekDates(dates.today()).first, '2026-10-19');
  });
}
