import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:go_router/go_router.dart';

import '../data/repositories.dart' show WebUser;
import '../l10n/app_localizations.dart';
import '../ui/account/account_page.dart';
import '../ui/groups/group_detail_page.dart';
import '../ui/groups/groups_page.dart';
import '../ui/history/history_page.dart';
import '../ui/landing/landing_page.dart';
import '../ui/session/session_bloc.dart';
import '../ui/shell/home_shell.dart';
import '../ui/train/daily_selection_page.dart';
import '../ui/train/daily_workout_page.dart';
import '../ui/train/gym_pages.dart';
import '../ui/train/log_activity_page.dart';
import '../ui/train/program_create_page.dart';
import '../ui/train/program_detail_page.dart';
import '../ui/train/program_list_page.dart';
import '../ui/train/program_workout_page.dart';
import '../ui/train/strength_exercise_page.dart';
import '../ui/train/train_page.dart';
import '../ui/train/train_widgets.dart';
import '../ui/week/week_page.dart';
import '../ui/week/weekly_goal_page.dart';
import 'services.dart';
import 'theme.dart';
import 'training_service.dart';

/// Routes (path URLs; Hosting serves index.html for every path except /content/**).
abstract final class Routes {
  static const String landing = '/';
  static const String week = '/week';
  static const String history = '/history';
  static const String groups = '/groups';
  static const String account = '/account';
  static const String train = '/train';
  static const String weeklyGoal = '/week/goal';

  /// The weekly goal opened from the Train hub (back and Save return there).
  static const String weeklyGoalFromTrain = '/week/goal?from=train';
  static const String logActivity = '/train/activity';
  static const String programCreate = '/train/programs/new';

  /// The Train hub's entries (Android Home's buttons): the daily selection, gym routines, programs.
  static const String daily = '/train/daily';

  static const String programs = '/train/programs';

  static String dailyWorkout(int routineId) => '/train/daily/$routineId';

  static const String gym = '/train/gym';

  static String gymRoutine(int routineId) => '/train/gym/$routineId';

  static String gymExercise(int routineId, int position) => '/train/gym/$routineId/$position';

  static String editActivity(String entryId) => '/history/activity/$entryId';

  static String program(String programId) => '/train/programs/$programId';

  static String programWorkout(String programId, int week, int dayIndex) =>
      '/train/programs/$programId/$week/$dayIndex';

  static String programExercise(String programId, int week, int dayIndex, int position) =>
      '/train/programs/$programId/$week/$dayIndex/$position';

  static String group(String groupId) => '/groups/$groupId';

  static const String groupCreate = '/groups/create';
  static const String groupJoin = '/groups/join';

  /// The join screen with the code filled in (the link in a shared invite).
  static String groupJoinCode(String code) => '/groups/join?code=${Uri.encodeQueryComponent(code)}';

  /// The weekly goal, then [next] (a group screen: Groups asks for a goal before creating or joining).
  static String weeklyGoalThen(String next) => '/week/goal?then=${Uri.encodeQueryComponent(next)}';

  /// The group's Feed tab (the tab is a query parameter, so switching keeps the page).
  static String groupFeed(String groupId) => '/groups/$groupId?tab=feed';
}

class TioMinuterWebApp extends StatefulWidget {
  const TioMinuterWebApp({required this.services, this.initialLocation = Routes.landing, super.key});

  final AppServices services;

  /// Where the router starts (a deep link or a reload in the browser gives the URL instead).
  final String initialLocation;

  @override
  State<TioMinuterWebApp> createState() => _TioMinuterWebAppState();
}

class _TioMinuterWebAppState extends State<TioMinuterWebApp> {
  late final SessionBloc _session = SessionBloc(widget.services.auth)..add(const SessionStarted());
  late final GoRouter _router = buildRouter(_session, initialLocation: widget.initialLocation);
  late final StreamSubscription<SessionState> _user;

  @override
  void initState() {
    super.initState();
    // Abandoned sessions are recorded for whoever is signed in; a new sign-in writes users/{uid}
    _user = _session.stream.listen((state) {
      setTrainingUser(state.user);
      _writeProfile(state);
    });
  }

  String? _profileWrittenFor;

  /// `users/{uid}` as Android's sync writes it (the name cut to 500, an https photo or none, server
  /// time), once per signed-in account and page load; never while a deletion resumes.
  void _writeProfile(SessionState state) {
    final user = state.user;
    final account = widget.services.account;
    if (user == null) _profileWrittenFor = null;
    if (user == null || account == null || state.isLoading || _profileWrittenFor == user.uid) return;
    if (state.resumeAccountDeletion) return;
    _profileWrittenFor = user.uid;
    final profile = profileOf(user);
    account.writeProfile(user.uid, profile.displayName, profile.photoUrl).catchError((Object _) {
      // Logged by the repository; the next sign-in writes it
    });
  }

  @override
  void dispose() {
    _user.cancel();
    _router.dispose();
    _session.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => RepositoryProvider.value(
    value: widget.services,
    child: BlocProvider.value(
      value: _session,
      child: MaterialApp.router(
        onGenerateTitle: (context) => AppLocalizations.of(context).appName,
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        themeMode: ThemeMode.system,
        localizationsDelegates: const [
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        routerConfig: _router,
        // Exercise animations for every screen (the flag, reduced motion, a hidden tab)
        builder: (context, child) => ExerciseAnimationScope(child: child ?? const SizedBox.shrink()),
      ),
    ),
  );
}

/// Signed out → the landing page only; signed in → the app (the landing redirects to My week).
GoRouter buildRouter(SessionBloc session, {String initialLocation = Routes.landing}) {
  String? redirect(BuildContext context, GoRouterState state) {
    final current = session.state;
    if (current.isLoading) return null;
    final atLanding = state.matchedLocation == Routes.landing;
    if (current.user == null) return atLanding ? null : Routes.landing;
    // Back from the re-authentication of Delete account: it continues on the Account page
    if (current.resumeAccountDeletion || current.accountDeletionFailure != null) {
      return state.matchedLocation == Routes.account ? null : Routes.account;
    }
    return atLanding ? Routes.week : null;
  }

  // The signed-in user, also provided to the pages below (training records as them)
  Widget signedIn(Widget Function(String uid) page) => BlocBuilder<SessionBloc, SessionState>(
    buildWhen: (a, b) => a.user?.uid != b.user?.uid,
    builder: (context, state) {
      final user = state.user;
      return user == null
          ? const SizedBox.shrink()
          : RepositoryProvider.value(value: WebUserHolder(user), child: page(user.uid));
    },
  );

  int intParam(GoRouterState state, String name) => int.tryParse(state.pathParameters[name] ?? '') ?? -1;

  // A link with an id that isn't a number goes to that entry's list instead of a "not found"
  GoRouterRedirect numbersOr(String fallback, List<String> names) =>
      (_, state) => names.every((n) => int.tryParse(state.pathParameters[n] ?? '') != null) ? null : fallback;

  return GoRouter(
    initialLocation: initialLocation,
    refreshListenable: _StreamListenable(session.stream),
    redirect: redirect,
    routes: [
      GoRoute(path: Routes.landing, builder: (_, _) => const LandingPage()),
      // Training: full screen, no navigation bar while a workout runs
      GoRoute(
        path: Routes.weeklyGoal,
        builder: (_, state) => signedIn((_) {
          // Only a group screen may follow the goal (never an arbitrary URL)
          final then = state.uri.queryParameters['then'];
          final next = then != null && (then == Routes.groupCreate || then == Routes.groupJoin) ? then : null;
          return WeeklyGoalPage(
            backTo: next != null
                ? Routes.groups
                : (state.uri.queryParameters['from'] == 'train' ? Routes.train : Routes.week),
            savedTo: next,
          );
        }),
      ),
      GoRoute(path: Routes.logActivity, builder: (_, _) => signedIn((_) => const LogActivityPage())),
      GoRoute(
        path: '/history/activity/:entryId',
        builder: (_, state) => signedIn((_) => LogActivityPage(entryId: state.pathParameters['entryId'])),
      ),
      // Matched before the shell: /train/daily, /train/gym and /train/programs are the hub's
      // children inside it (the navigation bar stays; back returns to the hub)
      GoRoute(
        path: '/train/daily/:routineId',
        redirect: numbersOr(Routes.daily, ['routineId']),
        builder: (_, state) => signedIn((_) => DailyWorkoutPage(routineId: intParam(state, 'routineId'))),
      ),
      GoRoute(
        path: '/train/gym/:routineId',
        redirect: numbersOr(Routes.gym, ['routineId']),
        builder: (_, state) => signedIn((_) => GymRoutinePage(routineId: intParam(state, 'routineId'))),
        routes: [
          GoRoute(
            path: ':position',
            redirect: (context, state) => int.tryParse(state.pathParameters['position'] ?? '') == null
                ? Routes.gymRoutine(intParam(state, 'routineId'))
                : null,
            builder: (_, state) => signedIn(
              (_) => GymExercisePage(routineId: intParam(state, 'routineId'), position: intParam(state, 'position')),
            ),
          ),
        ],
      ),
      GoRoute(path: Routes.programCreate, builder: (_, _) => signedIn((_) => const ProgramCreatePage())),
      GoRoute(
        path: '/train/programs/:programId',
        builder: (_, state) => signedIn((_) => ProgramDetailPage(programId: state.pathParameters['programId']!)),
        routes: [
          GoRoute(
            path: ':week/:day',
            builder: (_, state) => signedIn(
              (_) => ProgramWorkoutPage(
                programId: state.pathParameters['programId']!,
                week: intParam(state, 'week'),
                dayIndex: intParam(state, 'day'),
              ),
            ),
            routes: [
              GoRoute(
                path: ':position',
                builder: (_, state) => signedIn(
                  (_) => StrengthExercisePage(
                    programId: state.pathParameters['programId']!,
                    week: intParam(state, 'week'),
                    dayIndex: intParam(state, 'day'),
                    position: intParam(state, 'position'),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
      ShellRoute(
        builder: (context, state, child) => HomeShell(
          location: state.matchedLocation,
          pending: context.read<AppServices>().trainingDeps?.pending,
          child: child,
        ),
        routes: [
          GoRoute(
            path: Routes.week,
            pageBuilder: (_, _) => NoTransitionPage(child: signedIn((uid) => WeekPage(uid: uid))),
          ),
          GoRoute(
            path: Routes.train,
            pageBuilder: (_, _) => NoTransitionPage(child: signedIn((_) => const TrainPage())),
            routes: [
              GoRoute(path: 'daily', builder: (_, _) => signedIn((_) => const DailySelectionPage())),
              GoRoute(path: 'gym', builder: (_, _) => signedIn((_) => const GymSelectionPage())),
              GoRoute(path: 'programs', builder: (_, _) => signedIn((_) => const ProgramListPage())),
            ],
          ),
          GoRoute(
            path: Routes.history,
            pageBuilder: (_, _) => NoTransitionPage(child: signedIn((uid) => HistoryPage(uid: uid))),
          ),
          GoRoute(
            path: Routes.groups,
            pageBuilder: (_, _) => NoTransitionPage(child: signedIn((uid) => GroupsPage(uid: uid))),
            routes: [
              // Before ':groupId', which would match them too
              GoRoute(path: 'create', builder: (_, _) => signedIn((_) => const GroupCreatePage())),
              GoRoute(
                path: 'join',
                builder: (_, state) => signedIn((_) => GroupJoinPage(code: state.uri.queryParameters['code'] ?? '')),
              ),
              GoRoute(
                path: ':groupId',
                builder: (_, state) => signedIn(
                  (uid) => GroupDetailPage(
                    uid: uid,
                    groupId: state.pathParameters['groupId']!,
                    tab: state.uri.queryParameters['tab'] == 'feed' ? GroupTab.feed : GroupTab.ranking,
                  ),
                ),
              ),
            ],
          ),
          GoRoute(
            path: Routes.account,
            pageBuilder: (_, _) => NoTransitionPage(child: signedIn((_) => const AccountPage())),
          ),
        ],
      ),
    ],
  );
}

class _StreamListenable extends ChangeNotifier {
  _StreamListenable(Stream<Object?> stream) {
    _subscription = stream.listen((_) => notifyListeners());
  }

  late final StreamSubscription<Object?> _subscription;

  @override
  void dispose() {
    _subscription.cancel();
    super.dispose();
  }
}

/// Android's `SyncCoordinator.writeProfile` limits: the Google name cut to 500, an https photo of
/// ≤ 2048 characters or none.
({String? displayName, String? photoUrl}) profileOf(WebUser user) {
  final name = user.displayName;
  final photo = user.photoUrl;
  return (
    displayName: name == null ? null : (name.length > 500 ? name.substring(0, 500) : name),
    photoUrl: photo != null && photo.startsWith('https://') && photo.length <= 2048 ? photo : null,
  );
}
