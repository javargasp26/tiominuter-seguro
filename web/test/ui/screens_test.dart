import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/app/theme.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/weekly_progress.dart';
import 'package:tiominuter_web/domain/workout_names.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/ui/account/account_page.dart';
import 'package:tiominuter_web/ui/groups/feed_bloc.dart';
import 'package:tiominuter_web/ui/groups/group_detail_page.dart';
import 'package:tiominuter_web/ui/groups/groups_bloc.dart';
import 'package:tiominuter_web/ui/groups/groups_page.dart';
import 'package:tiominuter_web/ui/history/history_bloc.dart';
import 'package:tiominuter_web/ui/history/history_page.dart';
import 'package:tiominuter_web/ui/landing/landing_page.dart';
import 'package:tiominuter_web/ui/session/session_bloc.dart';
import 'package:tiominuter_web/ui/shell/home_shell.dart';
import 'package:tiominuter_web/ui/week/week_bloc.dart';
import 'package:tiominuter_web/ui/week/week_page.dart';

import '../support/fakes.dart';

/// Every screen from plain states (the stateless *Content widgets): loading, empty, data and error,
/// in English and in Spanish.
void main() {
  Widget app(Widget child, {Locale locale = const Locale('en'), Size? size}) => MaterialApp(
    locale: locale,
    theme: AppTheme.light,
    localizationsDelegates: const [
      AppLocalizations.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(body: child),
  );

  const weekDates = ['2026-10-19', '2026-10-20', '2026-10-21', '2026-10-22', '2026-10-23', '2026-10-24', '2026-10-25'];

  group('My week', () {
    WeekState data({
      int? goal = 4,
      Map<String, int> perDay = const {'2026-10-19': 1, '2026-10-21': 2},
      int? streak = 3,
    }) => WeekState(
      isLoading: false,
      summary: CurrentWeekSummary(
        today: '2026-10-22',
        progress: WeeklyProgress(weekStart: '2026-10-19', goal: goal, workoutsPerDay: perDay),
        streak: streak ?? 0,
      ),
      sourcesPerDay: const {
        '2026-10-19': [TrainingSource.gym],
        '2026-10-21': [TrainingSource.daily, TrainingSource.activity],
      },
      weekDates: weekDates,
      streak: streak,
    );

    testWidgets('loading', (tester) async {
      await tester.pumpWidget(app(WeekContent(state: const WeekState(), onRetry: () {})));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });

    testWidgets('data: progress, workouts left and the streak', (tester) async {
      await tester.pumpWidget(app(WeekContent(state: data(), onRetry: () {})));
      expect(find.text('3 of 4 workouts'), findsOneWidget);
      expect(find.text('1 workout left to reach your goal · Streak: 3 weeks'), findsOneWidget);
      expect(find.byIcon(Icons.check), findsOneWidget, reason: 'one workout on Monday');
      expect(find.text('2'), findsOneWidget, reason: 'two on Wednesday');
    });

    testWidgets('data in Spanish, goal met', (tester) async {
      await tester.pumpWidget(
        app(
          WeekContent(
            state: data(perDay: const {'2026-10-19': 4}),
            onRetry: () {},
          ),
          locale: const Locale('es'),
        ),
      );
      expect(find.text('4 de 4 entrenos'), findsOneWidget);
      expect(find.text('¡Meta de la semana cumplida! · Racha: 3 semanas'), findsOneWidget);
      expect(find.text('L'), findsOneWidget);
      expect(find.text('X'), findsOneWidget);
    });

    testWidgets('empty: no goal yet', (tester) async {
      await tester.pumpWidget(
        app(
          WeekContent(
            state: data(goal: null, perDay: const {}, streak: 0),
            onRetry: () {},
          ),
        ),
      );
      expect(find.text('Set your weekly goal'), findsOneWidget);
      expect(find.textContaining('Android app'), findsOneWidget);
    });

    testWidgets('error with retry', (tester) async {
      var retried = false;
      await tester.pumpWidget(
        app(
          WeekContent(
            state: const WeekState(isLoading: false, error: DataError.network),
            onRetry: () => retried = true,
          ),
        ),
      );
      expect(find.text('No internet connection. Check it and try again.'), findsOneWidget);
      await tester.tap(find.text('Retry'));
      expect(retried, isTrue);
    });

    testWidgets('the day strip is one sentence for screen readers', (tester) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(app(WeekContent(state: data(), onRetry: () {})));
      expect(
        find.bySemanticsLabel(
          RegExp(
            r'^Monday: Gym routine\. Tuesday: no workouts\. Wednesday: Daily routine, Activity\. Thursday \(today\): no workouts',
          ),
        ),
        findsOneWidget,
      );
      semantics.dispose();
    });
  });

  group('My history', () {
    final entries = [
      trainingEntry('a', date: '2026-10-22', completedAt: DateTime(2026, 10, 22, 9).millisecondsSinceEpoch),
      trainingEntry(
        'b',
        date: '2026-10-21',
        completedAt: DateTime(2026, 10, 21, 18).millisecondsSinceEpoch,
        source: TrainingSource.daily,
        refId: 'daily:5',
        title: 'Day 5',
        durationSeconds: 300,
      ),
      trainingEntry(
        'c',
        date: '2026-10-13',
        completedAt: DateTime(2026, 10, 13, 7).millisecondsSinceEpoch,
        source: TrainingSource.activity,
        refId: null,
        title: '',
        activity: 'RUNNING',
      ),
    ];
    final state = HistoryState(
      isLoading: false,
      entries: entries,
      hasMore: true,
      names: const WorkoutNameCatalog(gymRoutines: {64: GymRoutineNames('Pull Day', 'Día de tirón')}),
    );

    Widget history(HistoryState state, {Locale locale = const Locale('en'), VoidCallback? onLoadMore}) => app(
      HistoryContent(state: state, today: '2026-10-22', onRetry: () {}, onLoadMore: onLoadMore ?? () {}),
      locale: locale,
    );

    testWidgets('loading', (tester) async {
      await tester.pumpWidget(history(const HistoryState()));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });

    testWidgets('empty', (tester) async {
      await tester.pumpWidget(history(const HistoryState(isLoading: false)));
      expect(find.textContaining('Completed workouts appear here'), findsOneWidget);
    });

    testWidgets('data: days, names, source, duration and the 10-minute note', (tester) async {
      var more = false;
      await tester.pumpWidget(history(state, onLoadMore: () => more = true));
      expect(find.text('Today'), findsOneWidget);
      expect(find.text('Yesterday'), findsOneWidget);
      expect(find.text('Pull Day'), findsOneWidget);
      expect(find.text('Gym routine · 30 min'), findsOneWidget);
      expect(find.text('Day 5'), findsOneWidget);
      expect(find.text("Doesn't count toward the goal"), findsOneWidget);
      expect(find.text('Running'), findsOneWidget);
      expect(find.text('Tuesday, October 13'), findsOneWidget);
      await tester.tap(find.text('Load more'));
      expect(more, isTrue);
    });

    testWidgets('data in Spanish: names in Spanish', (tester) async {
      await tester.pumpWidget(history(state, locale: const Locale('es')));
      expect(find.text('Hoy'), findsOneWidget);
      expect(find.text('Día de tirón'), findsOneWidget);
      expect(find.text('Día 5'), findsOneWidget);
      expect(find.text('Correr'), findsOneWidget);
      expect(find.text('martes, 13 de octubre'), findsOneWidget);
    });

    testWidgets('error', (tester) async {
      await tester.pumpWidget(history(const HistoryState(isLoading: false, error: DataError.failed)));
      expect(find.textContaining("Couldn't load this"), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
    });
  });

  group('Groups', () {
    Widget groups(GroupsState state) => app(GroupsContent(state: state, uid: 'me', onRetry: () {}, onOpen: (_) {}));

    testWidgets('loading, empty, error', (tester) async {
      await tester.pumpWidget(groups(const GroupsState()));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      await tester.pumpWidget(groups(const GroupsState(isLoading: false)));
      expect(find.text('No groups yet'), findsOneWidget);
      await tester.pumpWidget(groups(const GroupsState(isLoading: false, error: DataError.network)));
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('data: name, members, owner, my position, the privacy note', (tester) async {
      await tester.pumpWidget(
        groups(
          const GroupsState(
            isLoading: false,
            groups: [
              MyGroup(
                group: Group(id: 'g', name: 'Crew', ownerUid: 'me', memberCount: 3),
                myPosition: 2,
                isOwner: true,
              ),
              MyGroup(
                group: Group(id: 'h', name: 'Runners', ownerUid: 'ana', memberCount: 1),
                myPosition: null,
                isOwner: false,
              ),
            ],
          ),
        ),
      );
      expect(find.text('Crew'), findsOneWidget);
      expect(find.text('1 member'), findsOneWidget);
      expect(find.byIcon(Icons.star), findsOneWidget);
      expect(find.text('3 members · This week: #2'), findsOneWidget);
      expect(find.textContaining('Groups see your name'), findsOneWidget);
      expect(find.textContaining('Android app'), findsNothing);
    });
  });

  group('Group ranking', () {
    final ranking = GroupRanking.rank(
      [
        groupMember(
          'ana',
          'Ana',
          done: 3,
          goal: 3,
          streak: 2,
          days: const {
            1: [TrainingSource.gym],
          },
        ),
        groupMember('me', 'Me', done: 1, goal: 3),
        groupMember('old', 'Old', done: 5, goal: 3, week: '2026-10-12'),
      ],
      '2026-10-19',
      'me',
    );

    Widget detail(GroupDetailState state, {GroupTab tab = GroupTab.ranking, Widget? feed}) => MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: GroupDetailContent(
        state: state,
        uid: 'me',
        tab: tab,
        onTab: (_) {},
        onBack: () {},
        onRetry: () {},
        feed: feed,
      ),
    );

    testWidgets('loading, error, gone', (tester) async {
      await tester.pumpWidget(detail(const GroupDetailState()));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      await tester.pumpWidget(detail(const GroupDetailState(isLoading: false, error: DataError.failed)));
      expect(find.text('Retry'), findsOneWidget);
      await tester.pumpWidget(detail(const GroupDetailState(isLoading: false, isGone: true)));
      expect(find.text('This group is no longer available.'), findsOneWidget);
    });

    testWidgets('data: positions, me, progress, stale week and streak', (tester) async {
      await tester.pumpWidget(
        detail(
          GroupDetailState(
            isLoading: false,
            group: const Group(id: 'g', name: 'Crew', ownerUid: 'ana', memberCount: 3),
            ranking: ranking,
            weekStart: '2026-10-19',
          ),
        ),
      );
      expect(find.text('Crew'), findsOneWidget);
      expect(find.text('#1'), findsOneWidget);
      expect(find.text('Me (You)'), findsOneWidget);
      expect(find.text('3/3 · Streak: 2 weeks'), findsOneWidget);
      expect(find.text('Not updated this week'), findsOneWidget);
      expect(find.text('Ranking'), findsOneWidget);
      expect(find.text('Feed'), findsOneWidget);
    });

    testWidgets('the Feed tab shows the feed slot only', (tester) async {
      await tester.pumpWidget(
        detail(
          GroupDetailState(isLoading: false, ranking: ranking, weekStart: '2026-10-19'),
          tab: GroupTab.feed,
          feed: const Text('feed here'),
        ),
      );
      expect(find.text('feed here'), findsOneWidget);
      expect(find.text('#1'), findsNothing);
    });
  });

  group('Feed', () {
    Widget feed(FeedState state, {Locale locale = const Locale('en'), void Function(FeedPost, Reaction)? onReaction}) =>
        app(
          FeedContent(state: state, uid: 'me', onRetry: () {}, onLoadMore: () {}, onReaction: onReaction ?? (_, _) {}),
          locale: locale,
        );

    final posts = [
      feedPost(
        'p1',
        'ana',
        titleKey: 'gym/64',
        title: 'Pull Day',
        reactions: {'me': Reaction.fire, 'luis': Reaction.fire},
      ),
      feedPost('p2', 'luis', source: TrainingSource.program, titleKey: 'program/UPPER_A', title: 'Tren superior A'),
      feedPost('p3', 'me', title: 'Old routine'),
    ];
    final state = FeedState(
      isLoading: false,
      posts: posts,
      weekStart: '2026-10-19',
      names: const WorkoutNameCatalog(gymRoutines: {64: GymRoutineNames('Pull Day', 'Día de tirón')}),
    );

    testWidgets('loading, error', (tester) async {
      await tester.pumpWidget(feed(const FeedState()));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      await tester.pumpWidget(feed(const FeedState(isLoading: false, error: DataError.network)));
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('empty, and the Monday text', (tester) async {
      await tester.pumpWidget(feed(const FeedState(isLoading: false)));
      expect(find.text('No workouts shared this week yet.'), findsOneWidget);
      await tester.pumpWidget(feed(const FeedState(isLoading: false, isFirstDayOfWeek: true)));
      expect(find.textContaining('A new week starts today'), findsOneWidget);
    });

    testWidgets("data: names by titleKey in the reader's language, reactions with counts", (tester) async {
      Reaction? tapped;
      await tester.pumpWidget(feed(state, locale: const Locale('es'), onReaction: (_, reaction) => tapped = reaction));
      expect(find.text('Día de tirón'), findsOneWidget);
      expect(find.text('Tren superior A'), findsOneWidget);
      expect(find.text('Old routine'), findsOneWidget, reason: 'no key: the title');
      expect(find.text('Me (Tú)'), findsOneWidget);
      expect(find.text('🔥 2'), findsOneWidget);
      await tester.tap(find.text('👏').first);
      expect(tapped, Reaction.clap);
    });

    testWidgets('reactions are described for screen readers, mine selected', (tester) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(feed(state));
      expect(find.bySemanticsLabel('On fire, 2 reactions'), findsOneWidget);
      expect(
        tester.getSemantics(find.bySemanticsLabel('On fire, 2 reactions')),
        matchesSemantics(
          label: 'On fire, 2 reactions',
          value: 'Your reaction',
          isSelected: true,
          hasSelectedState: true,
          isButton: true,
          hasTapAction: true,
        ),
      );
      semantics.dispose();
    });
  });

  group('Account and landing', () {
    testWidgets('account: name, email, sign out, delete account', (tester) async {
      var signedOut = false;
      var deleting = false;
      await tester.pumpWidget(
        app(
          AccountContent(
            user: const WebUser(uid: 'me', displayName: 'Alex Rivera', email: 'alex@example.test'),
            onSignOut: () => signedOut = true,
            onDelete: () => deleting = true,
          ),
        ),
      );
      expect(find.text('Alex Rivera'), findsOneWidget);
      expect(find.text('alex@example.test'), findsOneWidget);
      expect(find.textContaining('Android app'), findsNothing);
      await tester.tap(find.text('Sign out'));
      expect(signedOut, isTrue);
      await tester.tap(find.text('Delete account'));
      expect(deleting, isTrue);
    });

    // The bloc runs on real async (its auth stream), so the frames are pumped by hand
    Future<SessionBloc> landing(
      WidgetTester tester,
      FakeAuthRepository auth, {
      Locale locale = const Locale('en'),
    }) async {
      final session = SessionBloc(auth);
      await tester.runAsync(() async {
        session.add(const SessionStarted());
        await Future<void>.delayed(const Duration(milliseconds: 10));
      });
      await tester.pumpWidget(
        BlocProvider.value(
          value: session,
          child: app(const LandingPage(), locale: locale),
        ),
      );
      await tester.pump();
      return session;
    }

    testWidgets('landing: sign in; a failure shows a message', (tester) async {
      final auth = FakeAuthRepository()..nextSignIn = SignInOutcome.failed;
      final session = await landing(tester, auth);
      expect(find.text('Sign in with Google'), findsOneWidget);
      // The landing scrolls on a short screen
      await tester.ensureVisible(find.text('Sign in with Google'));
      await tester.tap(find.text('Sign in with Google'));
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.text("Couldn't sign in with Google. Please try again."), findsOneWidget);
      expect(auth.signIns, 1);
      // Not awaited: under the test's fake clock a bloc's close waits for its listeners' frames
      unawaited(session.close());
    });

    testWidgets('landing in Spanish', (tester) async {
      final session = await landing(tester, FakeAuthRepository(), locale: const Locale('es'));
      expect(find.text('Iniciar sesión con Google'), findsOneWidget);
      expect(find.text('Sin excusas — solo 10 minutos al día.'), findsOneWidget);
      // Not awaited: under the test's fake clock a bloc's close waits for its listeners' frames
      unawaited(session.close());
    });
  });

  group('responsive shell', () {
    Future<void> shellAt(WidgetTester tester, double width) async {
      tester.view.physicalSize = Size(width, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const HomeShell(location: '/history', child: Text('page')),
        ),
      );
    }

    testWidgets('a navigation bar at phone width', (tester) async {
      await shellAt(tester, 390);
      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.byType(NavigationRail), findsNothing);
      expect(tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex, 2);
      expect(find.text('Train'), findsOneWidget);
    });

    testWidgets('a rail on desktop', (tester) async {
      await shellAt(tester, 1280);
      expect(find.byType(NavigationRail), findsOneWidget);
      expect(find.byType(NavigationBar), findsNothing);
    });

    testWidgets('the destinations are keyboard focusable', (tester) async {
      await shellAt(tester, 390);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(FocusManager.instance.primaryFocus, isNotNull);
    });
  });
}
