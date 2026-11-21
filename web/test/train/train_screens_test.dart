import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/app/theme.dart';
import 'package:tiominuter_web/data/training_repositories.dart';
import 'package:tiominuter_web/domain/activity_entry.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/ui/shell/home_shell.dart';
import 'package:tiominuter_web/ui/train/daily_workout_bloc.dart';
import 'package:tiominuter_web/ui/train/daily_workout_page.dart';
import 'package:tiominuter_web/ui/train/log_activity_bloc.dart';
import 'package:tiominuter_web/ui/train/log_activity_page.dart';
import 'package:tiominuter_web/ui/train/train_widgets.dart';
import 'package:tiominuter_web/ui/week/weekly_goal_page.dart';

import '../support/training_fakes.dart';

/// The training screens from plain states (their stateless *Content), in English and Spanish.
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

  final content = trainingContent();
  final routine = content.dailyRoutines.single;

  group('without an animation scope (a list, a screen test)', () {
    testWidgets('an icon per exercise type, described; no image is loaded', (tester) async {
      await tester.pumpWidget(
        app(
          const Scaffold(
            body: Column(
              children: [
                ExerciseVisual(kind: ExerciseVisualKind.time, animation: '1_jumping_jack.gif'),
                ExerciseVisual(kind: ExerciseVisualKind.reps, animation: '2_squat.gif'),
                ExerciseVisual(kind: ExerciseVisualKind.rest, animation: '13_rest.gif'),
              ],
            ),
          ),
        ),
      );
      expect(find.byType(Image), findsNothing);
      expect(find.byIcon(Icons.timer_outlined), findsOneWidget);
      expect(find.byIcon(Icons.repeat), findsOneWidget);
      expect(find.bySemanticsLabel('Timed exercise'), findsOneWidget);
      expect(find.bySemanticsLabel('Repetition exercise'), findsOneWidget);
    });
  });

  group('the pending indicator', () {
    testWidgets('hidden with nothing pending, shown while a write waits, hidden again once sent', (tester) async {
      final pending = PendingWrites();
      await tester.pumpWidget(app(Scaffold(body: PendingBanner(pending: pending))));
      expect(find.text('Not synced yet'), findsNothing);
      final write = Completer<void>();
      unawaited(pending.track(write.future));
      await tester.pump();
      expect(find.text('Not synced yet'), findsOneWidget);
      write.complete();
      await tester.pump();
      await tester.pump();
      expect(find.text('Not synced yet'), findsNothing);
    });

    testWidgets('also for writes queued before a reload, until the SDK has sent them', (tester) async {
      final pending = PendingWrites();
      final queue = Completer<void>();
      pending.watchQueue(queue.future);
      await tester.pumpWidget(
        app(
          Scaffold(body: PendingBanner(pending: pending)),
          locale: const Locale('es'),
        ),
      );
      expect(find.text('Pendiente de sincronizar'), findsOneWidget);
      queue.complete();
      await tester.pump();
      await tester.pump();
      expect(find.text('Pendiente de sincronizar'), findsNothing);
    });
  });

  group('the daily workout', () {
    DailyWorkoutState at(int index, {bool running = false, bool ready = false}) => DailyWorkoutState(
      isLoading: false,
      routine: routine,
      exercises: content.exercises,
      index: index,
      timerTotal: routine.exercises[index].amount,
      timerRemaining: routine.exercises[index].amount,
      isTimerRunning: running,
      isReadyCountdown: ready,
      readyRemaining: 5,
    );

    testWidgets('a TIME exercise waiting for Get Ready', (tester) async {
      final intents = <DailyWorkoutIntent>[];
      await tester.pumpWidget(app(DailyWorkoutContent(state: at(0), onIntent: intents.add)));
      expect(find.text('Day 3'), findsOneWidget);
      expect(find.text('Exercise 1 of 4'), findsOneWidget);
      expect(find.text('Jumping Jacks'), findsOneWidget);
      expect(find.text('00:30'), findsOneWidget);
      await tester.tap(find.text('Get Ready'));
      expect(intents.single, isA<DailyStartGetReady>());
    });

    testWidgets('a REPS exercise in Spanish', (tester) async {
      await tester.pumpWidget(
        app(
          DailyWorkoutContent(state: at(1), onIntent: (_) {}),
          locale: const Locale('es'),
        ),
      );
      expect(find.text('Día 3'), findsOneWidget);
      expect(find.text('Sentadillas'), findsOneWidget);
      expect(find.text('10 repeticiones'), findsOneWidget);
      expect(find.text('Completar repeticiones'), findsOneWidget);
      expect(find.text('Saltar ejercicio'), findsOneWidget);
    });

    testWidgets('a running rest has no Get Ready', (tester) async {
      await tester.pumpWidget(app(DailyWorkoutContent(state: at(2, running: true), onIntent: (_) {})));
      expect(find.text('Rest'), findsOneWidget);
      expect(find.text('Timer running…'), findsOneWidget);
      expect(find.text('Get Ready'), findsNothing);
    });

    testWidgets('the completion view', (tester) async {
      await tester.pumpWidget(app(DailyWorkoutContent(state: at(3).copyWith(isComplete: true), onIntent: (_) {})));
      expect(find.text('Workout Complete!'), findsOneWidget);
      expect(find.text('Done'), findsOneWidget);
    });
  });

  group('log activity', () {
    testWidgets('the errors after a save, with Android\'s texts', (tester) async {
      // Tall enough for the whole form (the list builds only what is visible)
      tester.view.physicalSize = const Size(800, 4000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final form = LogActivityForm(date: '2026-10-21', hour: 18, minute: 0, durationText: '9');
      await tester.pumpWidget(
        app(
          LogActivityContent(
            state: LogActivityState(
              isLoading: false,
              form: form,
              initialForm: form,
              today: '2026-10-21',
              dayOptions: const ['2026-10-21', '2026-10-20'],
              showErrors: true,
              errors: const {ActivityEntryError.typeMissing, ActivityEntryError.intensityMissing},
            ),
            onIntent: (_) {},
          ),
        ),
      );
      expect(find.text('Choose an activity'), findsOneWidget);
      expect(find.text('Choose an intensity'), findsOneWidget);
      expect(find.text("It won't count toward your weekly goal (minimum 10 minutes)"), findsOneWidget);
      expect(find.text('Today'), findsOneWidget);
      expect(find.text('Yesterday'), findsOneWidget);
      expect(find.text('🏃'), findsOneWidget);
      expect(find.bySemanticsLabel('Easy: You can talk normally'), findsOneWidget);
    });
  });

  group('the weekly goal', () {
    testWidgets('the stepper stops at 14 and 1', (tester) async {
      final intents = <WeeklyGoalIntent>[];
      await tester.pumpWidget(
        app(
          WeeklyGoalContent(
            state: const WeeklyGoalState(isLoading: false, workouts: 14),
            onIntent: intents.add,
            onBack: () {},
          ),
        ),
      );
      expect(find.text('14 workouts per week'), findsOneWidget);
      expect(tester.widget<IconButton>(find.widgetWithIcon(IconButton, Icons.add)).onPressed, isNull);
      await tester.tap(find.byTooltip('Fewer workouts'));
      expect((intents.single as WeeklyGoalStep).delta, -1);
    });
  });

  test('the training source of a recorded daily workout is DAILY', () {
    expect(TrainingSource.daily.code, 'DAILY');
  });
}
