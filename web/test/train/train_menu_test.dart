import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:tiominuter_web/app/app.dart';
import 'package:tiominuter_web/app/services.dart';
import 'package:tiominuter_web/app/theme.dart';
import 'package:tiominuter_web/app/training_service.dart';
import 'package:tiominuter_web/data/content_repository.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/data/training_repositories.dart';
import 'package:tiominuter_web/domain/daily.dart';
import 'package:tiominuter_web/domain/daily_progress.dart';
import 'package:tiominuter_web/domain/program.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/workout_names.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/ui/train/daily_selection_page.dart';
import 'package:tiominuter_web/ui/train/daily_workout_bloc.dart';
import 'package:tiominuter_web/ui/train/program_list_page.dart';
import 'package:tiominuter_web/ui/train/train_bloc.dart';
import 'package:tiominuter_web/ui/train/train_page.dart';

import '../support/fakes.dart';
import '../support/training_fakes.dart';

/// The Train tab as Android Home's navigation: the hub (labels, the day line, the entries, the
/// layout), the daily selection, the program list, and the routes (each entry, back to the hub,
/// redirects, a reload on a workout).
void main() {
  Widget app(Widget child, {Locale locale = const Locale('en')}) => MaterialApp(
    locale: locale,
    theme: AppTheme.light,
    localizationsDelegates: const [
      AppLocalizations.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: AppLocalizations.supportedLocales,
    home: child,
  );

  void size(WidgetTester tester, double width, [double height = 2000]) {
    tester.view.physicalSize = Size(width, height);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  final content = trainingContent();
  final day3 = content.dailyRoutine(3)!;

  group('the hub', () {
    Future<List<String>> show(WidgetTester tester, TrainState state, {Locale locale = const Locale('en')}) async {
      final opened = <String>[];
      await tester.pumpWidget(
        app(
          Scaffold(
            body: TrainContent(
              state: state,
              onDaily: () => opened.add('daily'),
              onContinue: (r) => opened.add('continue ${r.routineId}'),
              onGym: () => opened.add('gym'),
              onPrograms: () => opened.add('programs'),
              onLogActivity: () => opened.add('activity'),
              onWeeklyGoal: () => opened.add('goal'),
            ),
          ),
          locale: locale,
        ),
      );
      return opened;
    }

    testWidgets('Android Home\'s entries in its order and labels, the day line, each entry opens', (tester) async {
      size(tester, 400);
      final opened = await show(tester, const TrainState(isLoading: false, availableDay: 30));
      final labels = ['Start Today\'s Routine', 'Gym routines', 'Programs'];
      final tops = [for (final label in labels) tester.getTopLeft(find.text(label)).dy];
      expect(tops, orderedEquals([...tops]..sort()), reason: 'Daily, Gym, Programs from top to bottom');
      expect(find.text('Day 30 available'), findsOneWidget);
      expect(find.textContaining('stays in this browser'), findsOneWidget, reason: 'only on the gym entry');
      expect(find.text('Log activity'), findsOneWidget);
      expect(find.text('Weekly goal'), findsOneWidget);
      for (final label in [...labels, 'Log activity', 'Weekly goal']) {
        await tester.tap(find.text(label));
      }
      expect(opened, ['daily', 'gym', 'programs', 'activity', 'goal']);
    });

    testWidgets('Spanish, with Android\'s strings', (tester) async {
      size(tester, 400);
      await show(tester, const TrainState(isLoading: false, availableDay: 5), locale: const Locale('es'));
      for (final text in [
        'Comenzar la rutina de hoy',
        'Día 5 disponible',
        'Rutinas de gimnasio',
        'Programas',
        'Registrar actividad',
        'Meta semanal',
      ]) {
        expect(find.text(text), findsOneWidget, reason: text);
      }
      expect(find.textContaining('app de Android'), findsNothing);
    });

    testWidgets('no day line while loading or without routines; Day 1 when the content failed', (tester) async {
      await show(tester, const TrainState());
      expect(find.textContaining('available'), findsNothing);
      await show(tester, const TrainState(isLoading: false));
      expect(find.textContaining('available'), findsNothing);
      expect(TrainBloc.availableDayOf(null, const DailyProgress(), const TrainState()).availableDay, 1);
      expect(TrainBloc.availableDayOf(content, null, const TrainState()).availableDay, 1, reason: 'progress unknown');
    });

    test('the day line is the next unlocked day, capped at the last routine (Android Home\'s rule)', () {
      final thirty = TrainingContent(
        dailyRoutines: [
          for (var d = 1; d <= 30; d++) DailyRoutine(routineId: d, routineDay: 'Day $d', exercises: const []),
        ],
        exercises: const {},
        programCatalog: const [],
        programTemplates: content.programTemplates,
      );
      int? day(DailyProgress progress) => TrainBloc.availableDayOf(thirty, progress, const TrainState()).availableDay;
      expect(day(const DailyProgress()), 1);
      expect(day(const DailyProgress(lastCompletedDay: 6)), 7);
      expect(day(const DailyProgress(lastCompletedDay: 30)), 30);
      expect(day(const DailyProgress(lastCompletedDay: 30, cycle: 1)), 30);
      expect(day(const DailyProgress(cycle: 4)), 1, reason: 'restarted');
    });

    test('the hub reads my progress, and again on every return (a day finished meanwhile)', () async {
      final kit = TrainingKit();
      kit.training.storedDailyProgress = const DailyProgress(lastCompletedDay: 2);
      final hub = TrainBloc(user: alex, content: FakeContentRepository()..trainingContent = content, deps: kit.deps)
        ..add(const TrainLoad());
      await settle();
      await settle();
      expect(hub.state.availableDay, 3);
      kit.training.storedDailyProgress = const DailyProgress(cycle: 1);
      hub.add(const TrainVisible());
      await settle();
      await settle();
      expect(hub.state.availableDay, 1, reason: 'restarted on the phone');
      await hub.close();
    });

    testWidgets('a workout in progress: Continue opens it', (tester) async {
      final opened = await show(tester, TrainState(isLoading: false, availableDay: 3, inProgressRoutine: day3));
      await tester.tap(find.text('Workout in progress: Day 3'));
      expect(opened, ['continue 3']);
    });

    testWidgets('one column on phones, the three entries in a row on wide screens', (tester) async {
      size(tester, 400);
      await show(tester, const TrainState(isLoading: false, availableDay: 3));
      final daily = tester.getTopLeft(find.text('Start Today\'s Routine'));
      final programs = tester.getTopLeft(find.text('Programs'));
      expect(programs.dy, greaterThan(daily.dy));
      expect(programs.dx, daily.dx);

      size(tester, 1000);
      await show(tester, const TrainState(isLoading: false, availableDay: 3));
      Offset card(String title) =>
          tester.getTopLeft(find.ancestor(of: find.text(title), matching: find.byType(TrainEntryCard)));
      final wideDaily = card('Start Today\'s Routine');
      final wideGym = card('Gym routines');
      final widePrograms = card('Programs');
      expect(wideGym.dy, wideDaily.dy);
      expect(widePrograms.dy, wideDaily.dy);
      expect(wideGym.dx, greaterThan(wideDaily.dx));
      expect(widePrograms.dx, greaterThan(wideGym.dx));
    });

    testWidgets('keyboard: Tab reaches the entries in order and Enter opens one', (tester) async {
      size(tester, 400);
      final opened = await show(tester, const TrainState(isLoading: false, availableDay: 3));
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(opened, ['gym']);
      // Every entry is a button for screen readers, at least 48 px tall
      for (final card in tester.widgetList<TrainEntryCard>(find.byType(TrainEntryCard))) {
        expect(tester.getSize(find.widgetWithText(InkWell, card.title)).height, greaterThanOrEqualTo(48));
      }
    });
  });

  group('the daily selection', () {
    const routines = [
      DailyRoutine(routineId: 1, routineDay: 'Day 1', exercises: []),
      DailyRoutine(routineId: 2, routineDay: 'Day 2', exercises: []),
      DailyRoutine(routineId: 3, routineDay: 'Day 3', exercises: []),
    ];

    Future<({List<int> started, List<String> restarts})> showSelection(
      WidgetTester tester,
      DailyProgress progress, {
      Locale locale = const Locale('en'),
    }) async {
      size(tester, 400);
      final started = <int>[];
      final restarts = <String>[];
      await tester.pumpWidget(
        app(
          DailySelectionContent(
            state: DailySelectionState(isLoading: false, routines: routines, progress: progress),
            onBack: () {},
            onRetry: () {},
            onStart: (r) => started.add(r.routineId),
            onRestart: () => restarts.add('restart'),
          ),
          locale: locale,
        ),
      );
      return (started: started, restarts: restarts);
    }

    testWidgets('completed, available and locked days; only unlocked days start', (tester) async {
      final semantics = tester.ensureSemantics();
      final opened = await showSelection(tester, const DailyProgress(lastCompletedDay: 1), locale: const Locale('es'));
      expect(find.text('Seleccionar rutina'), findsOneWidget);
      expect(find.text('Día 1'), findsOneWidget);
      expect(find.text('Día 2'), findsOneWidget);
      expect(find.text('Día 3'), findsOneWidget);
      expect(find.text('Disponible hoy'), findsOneWidget);
      expect(find.text('Completa el Día 2 para desbloquearlo'), findsOneWidget);
      expect(find.byIcon(Icons.lock_outline), findsOneWidget);
      expect(find.byIcon(Icons.check_circle), findsOneWidget);
      // Day 2 is the next one (filled), Day 1 can be repeated (outlined), Day 3 has no button
      expect(find.widgetWithText(FilledButton, 'Iniciar'), findsOneWidget);
      expect(find.widgetWithText(OutlinedButton, 'Iniciar'), findsOneWidget);
      expect(
        tester.getSemantics(find.widgetWithText(FilledButton, 'Iniciar')),
        isSemantics(label: 'Iniciar, Día 2', isButton: true, hasTapAction: true),
      );
      // The state is read with each day ("Bloqueado", "Completado", "Disponible")
      expect(tester.getSemantics(find.text('Día 3')), isSemantics(label: 'Día 3', value: 'Bloqueado', isHeader: true));
      expect(tester.getSemantics(find.text('Día 1')), isSemantics(label: 'Día 1', value: 'Completado', isHeader: true));
      expect(tester.getSemantics(find.text('Día 2')), isSemantics(label: 'Día 2', value: 'Disponible', isHeader: true));
      await tester.tap(find.widgetWithText(FilledButton, 'Iniciar'));
      await tester.tap(find.widgetWithText(OutlinedButton, 'Iniciar'));
      expect(opened.started, [2, 1]);
      // "Reiniciar días" once a day is done
      expect(find.widgetWithText(TextButton, 'Reiniciar días'), findsOneWidget);
      semantics.dispose();
    });

    testWidgets('a new person: only Day 1, no restart', (tester) async {
      await showSelection(tester, const DailyProgress());
      expect(find.widgetWithText(FilledButton, 'Start'), findsOneWidget);
      expect(find.widgetWithText(OutlinedButton, 'Start'), findsNothing);
      expect(find.text('Complete Day 1 to unlock'), findsOneWidget);
      expect(find.text('Complete Day 2 to unlock'), findsOneWidget);
      expect(find.text('Restart days'), findsNothing);
    });

    testWidgets('after the last day: every day completed and "Start again from Day 1"', (tester) async {
      final opened = await showSelection(tester, const DailyProgress(lastCompletedDay: 3));
      expect(find.text('You completed every day!'), findsOneWidget);
      expect(find.byIcon(Icons.check_circle), findsNWidgets(3));
      expect(find.text('Available today'), findsNothing);
      await tester.tap(find.text('Start again from Day 1'));
      await tester.tap(find.text('Restart days'));
      expect(opened.restarts, ['restart', 'restart']);
    });

    testWidgets('the restart dialog says what changes and what doesn\'t; Cancel keeps everything', (tester) async {
      bool? answer;
      await tester.pumpWidget(
        app(
          Builder(
            builder: (context) => TextButton(
              onPressed: () async => answer = await confirmRestartDays(context, const DailyWorkoutName(1)),
              child: const Text('open'),
            ),
          ),
          locale: const Locale('es'),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('¿Reiniciar los días?'), findsOneWidget);
      expect(
        find.text(
          'Todos los días se bloquearán de nuevo y empezarás desde el Día 1. '
          'Tu historial, tu meta semanal y tus grupos no cambian.',
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(answer, isFalse);
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Reiniciar'));
      await tester.pumpAndSettle();
      expect(answer, isTrue);
    });

    test('the bloc: the progress created from the history, read again on every visit, the restart', () async {
      final kit = TrainingKit();
      await kit.service.recordWorkout(
        user: alex,
        source: TrainingSource.daily,
        refId: 'daily:2',
        title: 'Day 2',
        durationSeconds: 700,
      );
      final bloc = DailySelectionBloc(
        user: alex,
        content: FakeContentRepository()..trainingContent = content,
        deps: kit.deps,
      )..add(const DailySelectionVisible());
      await settle();
      await settle();
      expect(bloc.state.routines.map((r) => r.routineId), [3]);
      expect(bloc.state.progress, const DailyProgress(lastCompletedDay: 2));
      expect(bloc.state.statusOf(3), DailyDayStatus.available);
      expect(bloc.state.nextDay, 3);

      kit.training.storedDailyProgress = const DailyProgress(lastCompletedDay: 3);
      bloc.add(const DailySelectionVisible());
      await settle();
      await settle();
      expect(bloc.state.isCycleComplete, isTrue, reason: 'a day finished on the phone meanwhile');

      bloc.add(const DailySelectionRestart());
      await settle();
      await settle();
      expect(bloc.state.progress, const DailyProgress(cycle: 1));
      expect(kit.training.storedDailyProgress, const DailyProgress(cycle: 1));
      expect(bloc.state.statusOf(3), DailyDayStatus.locked);

      final missing = DailySelectionBloc(user: alex, content: FakeContentRepository(), deps: kit.deps)
        ..add(const DailySelectionVisible());
      await settle();
      await settle();
      expect(missing.state.contentMissing, isTrue);
      await bloc.close();
      await missing.close();
    });

    test('the bloc: an unreadable progress shows Retry', () async {
      final kit = TrainingKit();
      kit.training.failWith = DataError.network;
      final bloc = DailySelectionBloc(
        user: alex,
        content: FakeContentRepository()..trainingContent = content,
        deps: kit.deps,
      )..add(const DailySelectionVisible());
      await settle();
      await settle();
      expect(bloc.state.progressFailed, isTrue);
      await bloc.close();
    });
  });

  group('the program list', () {
    test('my programs from the hub\'s old section; Create needs the content', () async {
      final kit = TrainingKit();
      final bloc = ProgramListBloc(user: alex, content: FakeContentRepository(), deps: kit.deps)
        ..add(const ProgramListLoad());
      await settle();
      await settle();
      expect(bloc.state.isLoading, isFalse);
      expect(bloc.state.contentMissing, isTrue);
      expect(bloc.state.programs, isEmpty);
      await bloc.close();
    });

    testWidgets('cards with goal, focus and progress', (tester) async {
      const profile = ProgramProfile(
        age: 30,
        weightKg: 70,
        heightCm: 175,
        level: ProgramLevel.beginner,
        goal: ProgramGoal.loseFat,
        location: ProgramLocation.home,
        weeks: 6,
        daysPerWeek: 3,
        focus: ProgramFocus.core,
      );
      final opened = <String>[];
      await tester.pumpWidget(
        app(
          ProgramListContent(
            state: const ProgramListState(
              isLoading: false,
              programs: [ProgramSummary(programId: 'p1', profile: profile, createdAt: 0, completedWorkouts: 3)],
            ),
            onBack: () {},
            onRetry: () {},
            onCreate: () => opened.add('create'),
            onProgram: (p) => opened.add(p.programId),
          ),
          locale: const Locale('es'),
        ),
      );
      expect(find.text('Perder grasa'), findsOneWidget);
      expect(find.textContaining('Enfoque: Core y estabilidad · Progreso: 16,7 %'), findsOneWidget);
      await tester.tap(find.text('Perder grasa'));
      await tester.tap(find.text('Crear programa'));
      expect(opened, ['p1', 'create']);
    });
  });

  group('routes', () {
    late TrainingKit kit;

    setUp(() {
      kit = TrainingKit();
      // Day 3 (the test content's only routine) is unlocked
      kit.training.storedDailyProgress = const DailyProgress(lastCompletedDay: 2);
      setTrainingUser(alex);
    });

    Future<GoRouter> open(WidgetTester tester, String location) async {
      size(tester, 400, 1600);
      final services = AppServices(
        auth: FakeAuthRepository(user: alex),
        userData: FakeUserDataRepository(),
        groups: FakeGroupsRepository(),
        content: FakeContentRepository(WorkoutNameCatalog.empty, content),
        dates: kit.dates,
        trainingDeps: kit.deps,
      );
      await tester.pumpWidget(TioMinuterWebApp(services: services, initialLocation: location));
      await tester.pumpAndSettle();
      return GoRouter.of(tester.element(find.byType(Scaffold).first));
    }

    String at(GoRouter router) => router.routerDelegate.currentConfiguration.uri.toString();

    testWidgets('the hub opens each entry on its own route; the selection\'s back returns to the hub', (tester) async {
      final router = await open(tester, Routes.train);
      expect(find.text('Day 3 available'), findsOneWidget);
      for (final (label, route, shown) in [
        ('Start Today\'s Routine', Routes.daily, 'Select Routine'),
        ('Gym routines', Routes.gym, 'Pull Day'),
        ('Programs', Routes.programs, 'My programs'),
      ]) {
        await tester.tap(find.text(label));
        await tester.pumpAndSettle();
        expect(at(router), route);
        expect(find.text(shown), findsWidgets);
        // The app bar's back (the browser's back pops the same page)
        await tester.tap(find.byTooltip('Back').last);
        await tester.pumpAndSettle();
        expect(at(router), Routes.train);
      }
      // The navigation bar stays on the hub's screens: Train is selected
      router.go(Routes.daily);
      await tester.pumpAndSettle();
      expect(find.byType(NavigationBar), findsOneWidget);
      router.pop();
      await tester.pumpAndSettle();
      expect(at(router), Routes.train);
    });

    testWidgets('a deep link to the selection still has the hub below it', (tester) async {
      final router = await open(tester, Routes.daily);
      expect(find.text('Select Routine'), findsOneWidget);
      expect(router.canPop(), isTrue);
      router.pop();
      await tester.pumpAndSettle();
      expect(at(router), Routes.train);
    });

    testWidgets('Start opens the workout full screen; its back returns to the selection', (tester) async {
      final router = await open(tester, Routes.daily);
      await tester.tap(find.text('Start'));
      await tester.pumpAndSettle();
      expect(at(router), Routes.dailyWorkout(3));
      expect(find.byType(NavigationBar), findsNothing);
      await tester.tap(find.byTooltip('Back'));
      await tester.pumpAndSettle();
      expect(at(router), Routes.daily);
    });

    testWidgets('Log activity and Weekly goal come back to the hub', (tester) async {
      final router = await open(tester, Routes.train);
      await tester.tap(find.text('Weekly goal'));
      await tester.pumpAndSettle();
      expect(at(router), Routes.weeklyGoalFromTrain);
      await tester.tap(find.byTooltip('Back'));
      await tester.pumpAndSettle();
      expect(at(router), Routes.train);
      await tester.tap(find.text('Log activity'));
      await tester.pumpAndSettle();
      expect(at(router), Routes.logActivity);
      // From My week the goal still returns there
      router.go(Routes.weeklyGoal);
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Back'));
      await tester.pumpAndSettle();
      expect(at(router), Routes.week);
    });

    testWidgets('old and odd links: every earlier path still opens; an id that isn\'t a number redirects', (
      tester,
    ) async {
      final router = await open(tester, Routes.train);
      for (final (link, expected) in [
        ('/train/daily/3', '/train/daily/3'),
        ('/train/gym/64', '/train/gym/64'),
        ('/train/gym/64/0', '/train/gym/64/0'),
        ('/train/programs/new', '/train/programs/new'),
        ('/train/activity', '/train/activity'),
        ('/train/daily/abc', Routes.daily),
        ('/train/gym/x', Routes.gym),
        ('/train/gym/64/x', '/train/gym/64'),
      ]) {
        router.go(link);
        await tester.pumpAndSettle();
        expect(at(router), expected, reason: link);
      }
    });

    testWidgets('a reload on a daily workout route resumes it there; the hub offers Continue', (tester) async {
      // A workout started before the reload: its exercise and session are in this browser's storage
      final before = DailyWorkoutBloc(
        routineId: 3,
        user: alex,
        content: FakeContentRepository()..trainingContent = content,
        deps: kit.deps,
      )..add(const DailyWorkoutLoad());
      for (var i = 0; i < 5; i++) {
        await tester.runAsync(settle);
        kit.scheduler.runCurrent();
      }
      before.add(const DailyStartGetReady());
      for (var ms = 0; ms < 36000; ms += 250) {
        kit.scheduler.advance(250);
        await tester.runAsync(settle);
      }
      expect(before.state.index, 1);
      await tester.runAsync(before.close);

      await open(tester, Routes.dailyWorkout(3));
      expect(find.text('Squats'), findsOneWidget, reason: 'the REPS exercise where it was');
      final router = GoRouter.of(tester.element(find.byType(Scaffold).first));
      router.go(Routes.train);
      await tester.pumpAndSettle();
      expect(find.text('Workout in progress: Day 3'), findsOneWidget);
    });
  });
}
