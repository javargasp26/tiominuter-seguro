import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/app/theme.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/ui/train/gym_blocs.dart';
import 'package:tiominuter_web/ui/train/gym_pages.dart';
import 'package:tiominuter_web/ui/train/train_widgets.dart';

import '../support/training_fakes.dart';

/// The gym screens from plain states (their stateless *Content), in English and Spanish.
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
  final pullDay = content.gymRoutine(64)!;

  void tall(WidgetTester tester) {
    tester.view.physicalSize = const Size(800, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  group('the selection', () {
    testWidgets('cards with the localized names; the toggle shows the summaries; Start routine', (tester) async {
      tall(tester);
      final intents = <GymSelectionIntent>[];
      final started = <int>[];
      Future<void> show(Set<int> expanded) => tester.pumpWidget(
        app(
          GymSelectionContent(
            state: GymSelectionState(
              isLoading: false,
              routines: content.gymRoutines,
              exercises: content.exercises,
              expandedRoutineIds: expanded,
            ),
            onIntent: intents.add,
            onBack: () {},
            onStart: (r) => started.add(r.routineId),
          ),
          locale: const Locale('es'),
        ),
      );
      await show(const {});
      expect(find.text('Rutinas de gimnasio'), findsOneWidget);
      expect(find.text('Día de tirón'), findsOneWidget);
      expect(find.textContaining('se guarda en este navegador'), findsOneWidget);
      expect(find.text('Sentadillas'), findsNothing);
      await tester.tap(find.text('Mostrar ejercicios').first);
      expect(intents.single, isA<GymToggleExpanded>().having((i) => i.routineId, 'id', 64));
      await show(const {64, 99});
      expect(find.text('Sentadillas'), findsOneWidget);
      expect(find.text('Series: 2 · Repeticiones: 10, 8'), findsOneWidget);
      expect(find.text('Series: 2 · Tiempo: 30 s, 45 s'), findsOneWidget);
      expect(find.text('Esta rutina todavía no tiene ejercicios.'), findsOneWidget);
      await tester.tap(find.text('Iniciar rutina').first);
      expect(started, [64]);
    });

    testWidgets('the toggle is a 48-px button with its state for screen readers', (tester) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(
        app(
          GymSelectionContent(
            state: GymSelectionState(isLoading: false, routines: [pullDay], exercises: content.exercises),
            onIntent: (_) {},
            onBack: () {},
            onStart: (_) {},
          ),
        ),
      );
      final toggle = find.bySemanticsLabel('Show exercises');
      expect(
        tester.getSemantics(toggle),
        matchesSemantics(
          label: 'Show exercises',
          value: 'Collapsed',
          isButton: true,
          hasTapAction: true,
          onTapHint: 'Show exercises',
        ),
      );
      expect(tester.getSize(find.byType(InkWell).first).height, greaterThanOrEqualTo(48));
      handle.dispose();
    });

    testWidgets('empty catalog and missing content', (tester) async {
      await tester.pumpWidget(
        app(
          GymSelectionContent(
            state: const GymSelectionState(isLoading: false),
            onIntent: (_) {},
            onBack: () {},
            onStart: (_) {},
          ),
        ),
      );
      expect(find.text('No gym routines available'), findsOneWidget);
    });
  });

  group('the routine', () {
    testWidgets('slots with their state, the progress line, reset enabled only with progress', (tester) async {
      tall(tester);
      final intents = <GymRoutineIntent>[];
      final opened = <int>[];
      Future<void> show(Set<int> done) => tester.pumpWidget(
        app(
          GymRoutineContent(
            state: GymRoutineState(
              isLoading: false,
              routine: pullDay,
              exercises: content.exercises,
              completedPositions: done,
            ),
            onIntent: intents.add,
            onBack: () {},
            onExercise: (slot) => opened.add(slot.position),
          ),
        ),
      );
      await show(const {});
      expect(find.text('Pull Day'), findsOneWidget);
      expect(find.text('0 of 2 exercises completed'), findsOneWidget);
      expect(tester.widget<IconButton>(find.widgetWithIcon(IconButton, Icons.restart_alt)).onPressed, isNull);
      await show(const {0});
      expect(find.text('1 of 2 exercises completed'), findsOneWidget);
      expect(find.bySemanticsLabel(RegExp('Squats')), findsOneWidget);
      await tester.tap(find.byTooltip('Reset progress'));
      expect(intents.single, isA<GymResetClicked>());
      await tester.tap(find.text('Plank'));
      expect(opened, [1]);
    });

    testWidgets('the completion: title, text, Finish', (tester) async {
      final intents = <GymRoutineIntent>[];
      await tester.pumpWidget(
        app(
          GymRoutineContent(
            state: GymRoutineState(
              isLoading: false,
              routine: pullDay,
              completedPositions: const {0, 1},
              showRoutineCompleted: true,
            ),
            onIntent: intents.add,
            onBack: () {},
            onExercise: (_) {},
          ),
          locale: const Locale('es'),
        ),
      );
      expect(find.text('¡Rutina completada!'), findsOneWidget);
      expect(find.text('¡Buen trabajo! Terminaste todos los ejercicios de esta rutina.'), findsOneWidget);
      await tester.tap(find.text('Terminar'));
      expect(intents.single, isA<GymCompletedDismissed>());
    });

    testWidgets('not found and empty', (tester) async {
      Future<void> show(GymRoutineState state) =>
          tester.pumpWidget(app(GymRoutineContent(state: state, onIntent: (_) {}, onBack: () {}, onExercise: (_) {})));
      await show(const GymRoutineState(isLoading: false));
      expect(find.text('Routine not found'), findsOneWidget);
      await show(GymRoutineState(isLoading: false, routine: content.gymRoutine(99)));
      expect(find.text('This routine has no exercises yet.'), findsOneWidget);
    });
  });

  group('the exercise', () {
    GymExerciseState state(int position, GymPhase phase) => GymExerciseState(
      routine: pullDay,
      slot: pullDay.exercises[position],
      exercise: content.exercises[pullDay.exercises[position].exerciseId],
      phase: phase,
    );

    Future<List<GymExerciseIntent>> show(WidgetTester tester, GymExerciseState s, {Locale? locale}) async {
      final intents = <GymExerciseIntent>[];
      await tester.pumpWidget(
        app(
          GymExerciseContent(state: s, onIntent: intents.add),
          locale: locale ?? const Locale('en'),
        ),
      );
      return intents;
    }

    testWidgets('every phase with Android\'s texts; the type icon, no animation', (tester) async {
      tall(tester);
      var intents = await show(tester, state(0, const GymPerformingReps(0)));
      expect(find.text('Exercise 1 of 2'), findsOneWidget);
      expect(find.text('Squats'), findsOneWidget);
      expect(find.text('Set 1 of 2'), findsOneWidget);
      expect(find.text('10 reps'), findsOneWidget);
      expect(find.byType(Image), findsNothing);
      expect(find.byType(ExerciseVisual), findsOneWidget);
      await tester.tap(find.text('Set done'));
      expect(intents.single, isA<GymSetDone>());

      intents = await show(tester, state(0, const GymResting(1, 40, 45)));
      expect(find.text('Rest'), findsOneWidget);
      expect(find.text('00:40'), findsOneWidget);
      expect(find.text('Next: set 2 of 2'), findsOneWidget);
      await tester.tap(find.text('Skip rest'));
      expect(intents.single, isA<GymSkipRest>());

      intents = await show(tester, state(1, const GymReadyToStartTimed(0)));
      expect(find.text('00:30'), findsOneWidget);
      await tester.tap(find.text('Start'));
      expect(intents.single, isA<GymStartTimed>());

      await show(tester, state(1, const GymGetReady(0, 3)));
      expect(find.text('Get ready'), findsOneWidget);
      expect(find.text('00:03'), findsOneWidget);

      await show(tester, state(1, const GymPerformingTimed(1, 12, 45)));
      expect(find.text('Set 2 of 2'), findsOneWidget);
      expect(find.text('00:12'), findsOneWidget);
    });

    testWidgets('Spanish, and the exit dialog text', (tester) async {
      await show(tester, state(0, const GymPerformingReps(1)), locale: const Locale('es'));
      expect(find.text('Día de tirón'), findsOneWidget);
      expect(find.text('Serie 2 de 2'), findsOneWidget);
      expect(find.text('Serie completada'), findsOneWidget);
      final es = lookupAppLocalizations(const Locale('es'));
      expect(es.gymExitText(1, 4), startsWith('Progreso: 1 de 4 serie completada.'));
      expect(es.gymExitText(2, 4), startsWith('Progreso: 2 de 4 series completadas.'));
    });
  });
}
