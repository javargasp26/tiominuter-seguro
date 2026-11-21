import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:tiominuter_web/app/services.dart';
import 'package:tiominuter_web/app/theme.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/ui/train/daily_workout_bloc.dart';
import 'package:tiominuter_web/ui/train/daily_workout_page.dart';
import 'package:tiominuter_web/ui/train/exercise_animation.dart';

import '../support/training_fakes.dart';

/// Exercise animations ON (the product owner's decision for the private test group): the GIF from
/// /content/v1/animations/, the icon in the same box while loading, for a missing file and with
/// reduced motion, the localized name as alt text, a hidden tab paused, the next one preloaded.
void main() {
  /// A real two-frame GIF, decoded by the test engine.
  final gif = () {
    final first = img.Image(width: 8, height: 8)..clear(img.ColorRgb8(255, 0, 0));
    final second = img.Image(width: 8, height: 8)..clear(img.ColorRgb8(0, 0, 255));
    first.addFrame(second);
    return Uint8List.fromList(img.encodeGif(first));
  }();

  late List<String> requested;

  setUp(() => requested = []);

  /// GIFs from memory; a name containing "missing" is a broken file (as a 404 page would be).
  ImageProvider images(String file) {
    requested.add(file);
    return MemoryImage(file.contains('missing') ? Uint8List.fromList('<html>Not found</html>'.codeUnits) : gif);
  }

  Widget app(
    Widget child, {
    Locale locale = const Locale('en'),
    bool enabled = true,
    bool reducedMotion = false,
    bool tabVisible = true,
    bool osDisablesAnimations = false,
  }) => MaterialApp(
    locale: locale,
    theme: AppTheme.light,
    localizationsDelegates: const [
      AppLocalizations.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: AppLocalizations.supportedLocales,
    home: Builder(
      builder: (context) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: osDisablesAnimations),
        child: ExerciseAnimationSettings(
          enabled: enabled,
          imageFor: images,
          reducedMotion: reducedMotion,
          tabVisible: tabVisible,
          child: Scaffold(body: Center(child: child)),
        ),
      ),
    ),
  );

  /// Lets the engine decode the image (real async work).
  Future<void> decode(WidgetTester tester) async {
    for (var i = 0; i < 3; i++) {
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 50)));
      await tester.pump();
    }
  }

  test('the flag is ON, and the GIFs come from the same origin\'s /content/v1/animations/', () {
    expect(AppConfig.showExerciseAnimations, isTrue);
    final url = ExerciseAnimationSettings.urlOf('1_jumping_jack.gif');
    expect(url.path, endsWith('/content/v1/animations/1_jumping_jack.gif'));
    // Resolved against the page's own URL: the same origin as the app
    expect(url, Uri.base.resolve('/content/v1/animations/1_jumping_jack.gif'));
    expect(url.query, isEmpty, reason: 'no cache-busting: the year-long immutable cache applies');
  });

  testWidgets('the animation, described by the exercise\'s name; the icon until its first frame', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(
      app(const ExerciseVisual(kind: ExerciseVisualKind.time, animation: '1_jumping_jack.gif', name: 'Jumping Jacks')),
    );
    // Not decoded yet: the icon holds the box
    expect(find.byIcon(Icons.timer_outlined), findsOneWidget);
    expect(tester.getSize(find.byType(ExerciseVisual)), const Size(120, 120));
    await decode(tester);
    expect(find.byIcon(Icons.timer_outlined), findsNothing);
    expect(find.byType(RawImage), findsOneWidget);
    expect(tester.getSize(find.byType(ExerciseVisual)), const Size(120, 120), reason: 'no layout jump');
    expect(find.bySemanticsLabel('Jumping Jacks'), findsOneWidget);
    expect(requested, ['1_jumping_jack.gif']);
    handle.dispose();
  });

  testWidgets('a missing or broken file: the icon in the same box, no broken image', (tester) async {
    await tester.pumpWidget(
      app(const ExerciseVisual(kind: ExerciseVisualKind.reps, animation: 'missing.gif', name: 'Squats')),
    );
    await decode(tester);
    expect(find.byIcon(Icons.repeat), findsOneWidget);
    expect(find.byType(RawImage), findsNothing);
    expect(tester.getSize(find.byType(ExerciseVisual)), const Size(120, 120));
    expect(tester.takeException(), isNull);
  });

  testWidgets('reduced motion (the browser\'s preference, or the OS through Flutter): the icon, nothing loaded', (
    tester,
  ) async {
    for (final reduced in [
      app(const ExerciseVisual(kind: ExerciseVisualKind.time, animation: 'a.gif', name: 'Plank'), reducedMotion: true),
      app(
        const ExerciseVisual(kind: ExerciseVisualKind.time, animation: 'a.gif', name: 'Plank'),
        osDisablesAnimations: true,
      ),
    ]) {
      await tester.pumpWidget(reduced);
      await decode(tester);
      expect(find.byIcon(Icons.timer_outlined), findsOneWidget);
      expect(find.bySemanticsLabel('Plank'), findsOneWidget);
    }
    expect(requested, isEmpty);
  });

  testWidgets('a hidden tab pauses the animation; lists and the flag off show icons only', (tester) async {
    await tester.pumpWidget(
      app(const ExerciseVisual(kind: ExerciseVisualKind.time, animation: 'a.gif'), tabVisible: false),
    );
    final ticker = tester.widget<TickerMode>(
      find.descendant(of: find.byType(ExerciseVisual), matching: find.byType(TickerMode)),
    );
    expect(ticker.enabled, isFalse);
    await tester.pumpWidget(
      app(const ExerciseVisual(kind: ExerciseVisualKind.time, animation: 'b.gif', animate: false)),
    );
    await tester.pumpWidget(
      app(const ExerciseVisual(kind: ExerciseVisualKind.time, animation: 'c.gif'), enabled: false),
    );
    expect(find.byType(Image), findsNothing);
    expect(requested, ['a.gif']);
  });

  testWidgets('a rest shows the rest animation (Android\'s REST_ANIMATION)', (tester) async {
    await tester.pumpWidget(app(const ExerciseVisual(kind: ExerciseVisualKind.rest, name: 'Rest')));
    expect(requested, [restAnimation]);
  });

  group('on the daily workout', () {
    final content = trainingContent();
    final routine = content.dailyRoutines.single;
    DailyWorkoutState at(int index) => DailyWorkoutState(
      isLoading: false,
      routine: routine,
      exercises: content.exercises,
      index: index,
      timerTotal: routine.exercises[index].amount,
      timerRemaining: routine.exercises[index].amount,
    );

    testWidgets('only the current GIF, the next one preloaded; alt text in English and Spanish', (tester) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(
        app(
          SizedBox(
            height: 900,
            child: DailyWorkoutContent(state: at(0), onIntent: (_) {}),
          ),
        ),
      );
      await decode(tester);
      // Exercise 1 shown, exercise 2 fetched (the image cache), nothing else
      expect(requested.toSet(), {'1_jumping_jack.gif', '2_squat.gif'});
      expect(find.byType(RawImage), findsOneWidget);
      expect(find.bySemanticsLabel('Jumping Jacks'), findsWidgets);
      await tester.pumpWidget(
        app(
          SizedBox(
            height: 900,
            child: DailyWorkoutContent(state: at(1), onIntent: (_) {}),
          ),
          locale: const Locale('es'),
        ),
      );
      await decode(tester);
      expect(
        find.descendant(of: find.byType(ExerciseVisual), matching: find.bySemanticsLabel('Sentadillas')),
        findsOneWidget,
      );
      handle.dispose();
    });
  });
}
