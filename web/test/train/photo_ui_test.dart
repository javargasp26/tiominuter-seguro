import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/app/services.dart';
import 'package:tiominuter_web/app/theme.dart';
import 'package:tiominuter_web/app/training_service.dart';
import 'package:tiominuter_web/domain/activity_entry.dart';
import 'package:tiominuter_web/domain/photos.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/workout_names.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/ui/history/history_bloc.dart';
import 'package:tiominuter_web/ui/history/history_page.dart';
import 'package:tiominuter_web/ui/train/daily_workout_bloc.dart';
import 'package:tiominuter_web/ui/train/daily_workout_page.dart';
import 'package:tiominuter_web/ui/train/gym_blocs.dart';
import 'package:tiominuter_web/ui/train/gym_pages.dart';
import 'package:tiominuter_web/ui/train/log_activity_bloc.dart';
import 'package:tiominuter_web/ui/train/log_activity_page.dart';
import 'package:tiominuter_web/ui/train/photo_widgets.dart';
import 'package:tiominuter_web/ui/train/train_widgets.dart';

import '../support/fakes.dart';
import '../support/photo_fakes.dart';
import '../support/training_fakes.dart';

/// The photo on each screen: the completion views (daily, gym; the program views use the same
/// CompletionView), Log activity (create and edit), the history's thumbnail and viewer, errors, drops
/// and the iOS Home Screen hint.
void main() {
  const iphone = DeviceInfo(
    userAgent:
        'Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) '
        'Version/18.5 Mobile/15E148 Safari/604.1',
    platform: 'iPhone',
    maxTouchPoints: 5,
  );

  late TrainingKit kit;

  setUp(() {
    kit = TrainingKit();
    kit.training.storedGoals[kit.monday] = 3;
    setTrainingUser(alex);
  });

  Widget app(Widget child, {Locale locale = const Locale('en')}) => RepositoryProvider<AppServices>.value(
    value: AppServices(
      auth: FakeAuthRepository(),
      userData: FakeUserDataRepository(),
      groups: FakeGroupsRepository(),
      content: FakeContentRepository(WorkoutNameCatalog.empty, trainingContent()),
      dates: kit.dates,
      trainingDeps: kit.deps,
    ),
    child: RepositoryProvider.value(
      value: const WebUserHolder(alex),
      child: MaterialApp(
        locale: locale,
        theme: AppTheme.light,
        localizationsDelegates: const [
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: SingleChildScrollView(child: child)),
      ),
    ),
  );

  void tall(WidgetTester tester) {
    tester.view.physicalSize = const Size(800, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  /// Lets the codec, the store and the dialogs finish (real async work runs outside fake time).
  Future<void> work(WidgetTester tester) async {
    for (var i = 0; i < 3; i++) {
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
      await tester.pumpAndSettle();
    }
  }

  Future<TrainingEntry> recorded() => kit.service.recordWorkout(
    user: alex,
    source: TrainingSource.daily,
    refId: 'daily:3',
    title: 'Day 3',
    durationSeconds: 1200,
  );

  group('the completion views', () {
    testWidgets('Add photo → processed and kept here → its thumbnail; Change; Remove asks first', (tester) async {
      tall(tester);
      final entry = await tester.runAsync(recorded);
      await tester.pumpWidget(app(CompletionView(title: 'Done!', text: '', entry: entry, onDone: () {})));
      await work(tester);
      expect(find.text('Add photo'), findsOneWidget);
      expect(find.textContaining('Photos stay in this browser'), findsOneWidget);
      expect(find.text('You can also drop a photo here.'), findsOneWidget, reason: 'a desktop browser');

      kit.picker.next = PickedPhoto(bytes: cameraJpeg(orientation: 6), mimeType: 'image/jpeg');
      await tester.tap(find.text('Add photo'));
      await work(tester);
      expect(kit.picker.picks, 1);
      final stored = (await tester.runAsync(() => kit.service.photoOf(alex.uid, entry!.id)))!;
      expect(jpegSize(stored.full), (200, 300), reason: 'upright');
      expect(find.text('Change photo'), findsOneWidget);
      expect(find.bySemanticsLabel(RegExp('^Workout photo, ')), findsOneWidget);
      expect(kit.persistenceRequests, 1, reason: 'the browser is asked to keep the storage');

      kit.picker.next = PickedPhoto(bytes: cameraJpeg(width: 400, height: 400));
      await tester.tap(find.text('Change photo'));
      await work(tester);
      expect(jpegSize((await tester.runAsync(() => kit.service.photoOf(alex.uid, entry!.id)))!.full), (400, 400));

      await tester.tap(find.text('Remove photo'));
      await tester.pumpAndSettle();
      expect(find.text('Remove the photo?'), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(await tester.runAsync(() => kit.service.photoOf(alex.uid, entry!.id)), isNotNull);
      await tester.tap(find.text('Remove photo'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Remove'));
      await work(tester);
      expect(await tester.runAsync(() => kit.service.photoOf(alex.uid, entry!.id)), isNull);
      expect(find.text('Add photo'), findsOneWidget);
    });

    testWidgets('an unreadable file, a HEIC, a too large one: the message, nothing kept (Spanish)', (tester) async {
      tall(tester);
      final entry = await tester.runAsync(recorded);
      await tester.pumpWidget(
        app(
          CompletionView(title: '', text: '', entry: entry, onDone: () {}),
          locale: const Locale('es'),
        ),
      );
      await work(tester);
      for (final (picked, message) in [
        (PickedPhoto(bytes: Uint8List.fromList('nope'.codeUnits)), 'No se pudo leer esta imagen. Prueba con otra.'),
        (
          PickedPhoto(
            bytes: Uint8List.fromList([0, 0, 0, 24, ...'ftypheic'.codeUnits, ...List.filled(40, 0)]),
            mimeType: 'image/heic',
          ),
          'Este navegador no puede leer fotos HEIC',
        ),
        (PickedPhoto(bytes: Uint8List(PhotoLimits.maxInputBytes + 1)), 'demasiado grande (máximo 25 MB)'),
      ]) {
        kit.picker.next = picked;
        await tester.tap(find.text('Agregar foto'));
        await work(tester);
        expect(find.textContaining(message), findsOneWidget, reason: message);
        ScaffoldMessenger.of(tester.element(find.byType(CompletionView))).clearSnackBars();
        await tester.pumpAndSettle();
      }
      expect(kit.photos.photos, isEmpty);
    });

    testWidgets('a photo dropped on the page (desktop) is added too', (tester) async {
      tall(tester);
      final entry = await tester.runAsync(recorded);
      await tester.pumpWidget(app(CompletionView(title: '', text: '', entry: entry, onDone: () {})));
      await work(tester);
      expect(kit.picker.hasDropListener, isTrue);
      kit.picker.drop(PickedPhoto(bytes: cameraJpeg()));
      await work(tester);
      expect(find.text('Change photo'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      expect(kit.picker.hasDropListener, isFalse, reason: 'only while the screen is open');
    });

    testWidgets('daily and gym completions show the photo (the program views use the same view)', (tester) async {
      tall(tester);
      final entry = (await tester.runAsync(recorded))!;
      final routine = trainingContent().dailyRoutine(3)!;
      await tester.pumpWidget(
        app(
          SizedBox(
            height: 2000,
            child: DailyWorkoutContent(
              state: DailyWorkoutState(
                isLoading: false,
                routine: routine,
                exercises: trainingContent().exercises,
                index: 3,
                isComplete: true,
                recorded: entry,
              ),
              onIntent: (_) {},
            ),
          ),
        ),
      );
      await work(tester);
      expect(find.byType(EntryPhotoSection), findsOneWidget);
      await tester.pumpWidget(
        app(
          SizedBox(
            height: 2000,
            child: GymRoutineContent(
              state: GymRoutineState(
                isLoading: false,
                routine: trainingContent().gymRoutine(64),
                completedPositions: const {0, 1},
                showRoutineCompleted: true,
                completedEntry: entry,
              ),
              onIntent: (_) {},
              onBack: () {},
              onExercise: (_) {},
            ),
          ),
        ),
      );
      await work(tester);
      expect(find.byType(EntryPhotoSection), findsOneWidget);
      // Shown again without an entry (already recorded): no photo to add
      await tester.pumpWidget(app(CompletionView(title: '', text: '', entry: null, onDone: () {})));
      expect(find.byType(EntryPhotoSection), findsNothing);
    });
  });

  group('log activity', () {
    LogActivityBloc bloc({String? entryId}) =>
        LogActivityBloc(user: alex, deps: kit.deps, dates: kit.dates, entryId: entryId)..add(const LogActivityLoad());

    Future<void> run(WidgetTester tester) => tester.runAsync(() async {
      for (var i = 0; i < 10; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 5));
      }
    });

    void fill(LogActivityBloc b) => b
      ..add(const LogActivityTypeSelected(ActivityType.running))
      ..add(const LogActivityQuickDuration(30))
      ..add(const LogActivityIntensitySelected(Intensity.moderate));

    testWidgets('create: the photo is staged (an unsaved change), kept on Save, its thumbnail on the post', (
      tester,
    ) async {
      kit.sharing.groupIds = ['g1'];
      kit.service.setFeedIntroSeen(alex.uid);
      final b = bloc();
      await run(tester);
      fill(b);
      await run(tester);
      final before = b.state.hasUnsavedChanges;
      b.add(LogActivityPhotoPicked(PickedPhoto(bytes: cameraJpeg())));
      await run(tester);
      expect(b.state.form!.photo, isA<StagedFormPhoto>());
      expect(b.state.hasUnsavedChanges, isTrue);
      expect(before, isTrue);
      expect(kit.photos.photos, isEmpty, reason: 'nothing kept before Save');
      b.add(const LogActivitySave());
      await run(tester);
      final entryId = kit.training.entries.keys.single;
      final stored = (await tester.runAsync(() => kit.service.photoOf(alex.uid, entryId)))!;
      expect(stored.thumb, (b.state.form!.photo as StagedFormPhoto).photo.thumb);
      expect(kit.sharing.posts['g1']![entryId]!['thumb'], stored.thumb);
      await tester.runAsync(b.close);
    });

    testWidgets('edit: the stored photo is shown; removing it on Save deletes it and the post thumbnail', (
      tester,
    ) async {
      kit.sharing.groupIds = ['g1'];
      kit.service.setFeedIntroSeen(alex.uid);
      final create = bloc();
      await run(tester);
      fill(create);
      await run(tester);
      create.add(LogActivityPhotoPicked(PickedPhoto(bytes: cameraJpeg())));
      await run(tester);
      create.add(const LogActivitySave());
      await run(tester);
      await tester.runAsync(create.close);
      final entryId = kit.training.entries.keys.single;
      expect(kit.sharing.posts['g1']![entryId]!['thumb'], isNotNull);

      final edit = bloc(entryId: entryId);
      await run(tester);
      expect(edit.state.form!.photo, isA<StoredFormPhoto>());
      expect(edit.state.hasUnsavedChanges, isFalse);
      edit.add(const LogActivityPhotoRemoved());
      await run(tester);
      expect(edit.state.hasUnsavedChanges, isTrue);
      edit.add(const LogActivitySave());
      await run(tester);
      expect(await tester.runAsync(() => kit.service.photoOf(alex.uid, entryId)), isNull);
      expect(kit.sharing.posts['g1']![entryId]!.containsKey('thumb'), isFalse);
      await tester.runAsync(edit.close);
    });

    testWidgets('a photo that can\'t be read: the message, the form unchanged; discarding keeps nothing', (
      tester,
    ) async {
      final b = bloc();
      final effects = <LogActivityEffect>[];
      final sub = b.effects.listen(effects.add);
      await run(tester);
      b.add(LogActivityPhotoPicked(PickedPhoto(bytes: Uint8List.fromList([1, 2, 3]))));
      await run(tester);
      expect(effects.whereType<LogActivityPhotoFailed>().single.error, PhotoError.unreadable);
      expect(b.state.form!.photo, isA<NoFormPhoto>());
      expect(b.state.isProcessingPhoto, isFalse);
      b.add(LogActivityPhotoPicked(PickedPhoto(bytes: cameraJpeg())));
      await run(tester);
      b.add(const LogActivityBack());
      await run(tester);
      expect(b.state.dialog, LogActivityDialog.discard);
      b.add(const LogActivityConfirmDiscard());
      await run(tester);
      expect(kit.photos.photos, isEmpty);
      await tester.runAsync(sub.cancel);
      await tester.runAsync(b.close);
    });

    testWidgets('the form shows Add photo (the file chooser) and, staged, the thumbnail', (tester) async {
      tall(tester);
      var opened = 0;
      final processed = (await tester.runAsync(
        () => kit.deps.photoProcessor.process(PickedPhoto(bytes: cameraJpeg())),
      ))!;
      final form = LogActivityForm(date: kit.today, hour: 10, minute: 0);
      Widget content(LogActivityForm f) => app(
        SizedBox(
          height: 2400,
          child: LogActivityContent(
            state: LogActivityState(
              isLoading: false,
              form: f,
              initialForm: form,
              dayOptions: [kit.today],
              today: kit.today,
            ),
            onIntent: (_) {},
            onAddPhoto: () => opened++,
          ),
        ),
      );
      await tester.pumpWidget(content(form));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Add photo'));
      expect(opened, 1);
      await tester.pumpWidget(content(form.copyWith(photo: StagedFormPhoto(processed))));
      await tester.pumpAndSettle();
      expect(find.text('Change photo'), findsOneWidget);
      expect(find.text('Remove photo'), findsOneWidget);
    });
  });

  group('history', () {
    testWidgets('the thumbnail when this browser has the photo; full screen with the line about other devices', (
      tester,
    ) async {
      tall(tester);
      final withPhoto = (await tester.runAsync(recorded))!;
      final withoutPhoto = (await tester.runAsync(recorded))!;
      final photo = (await tester.runAsync(() => kit.deps.photoProcessor.process(PickedPhoto(bytes: cameraJpeg()))))!;
      await tester.runAsync(() => kit.service.attachPhoto(alex, withPhoto.id, photo));
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(
        app(
          SizedBox(
            height: 2000,
            child: HistoryContent(
              state: HistoryState(isLoading: false, entries: [withPhoto, withoutPhoto]),
              today: kit.today,
              onRetry: () {},
              onLoadMore: () {},
              photoOwner: alex.uid,
            ),
          ),
        ),
      );
      await work(tester);
      expect(find.byType(PhotoThumbnail), findsOneWidget);
      expect(
        tester.getSemantics(find.byType(PhotoThumbnail)),
        isSemantics(label: 'Workout photo, Oct 21, 2026', isButton: true, hasTapAction: true),
      );
      expect(tester.getSize(find.byType(PhotoThumbnail)).height, greaterThanOrEqualTo(48));
      await tester.tap(find.byType(PhotoThumbnail));
      await tester.pumpAndSettle();
      expect(find.byType(PhotoViewer), findsOneWidget);
      expect(find.text('Photos added on another device stay on that device.'), findsOneWidget);
      await tester.tap(find.byTooltip('Close photo'));
      await tester.pumpAndSettle();
      expect(find.byType(PhotoViewer), findsNothing);
      handle.dispose();
    });

    testWidgets('evicted: the row simply has no photo', (tester) async {
      final entry = (await tester.runAsync(recorded))!;
      await tester.runAsync(
        () async => kit.service.attachPhoto(
          alex,
          entry.id,
          (await kit.deps.photoProcessor.process(PickedPhoto(bytes: cameraJpeg()))),
        ),
      );
      kit.photos.evict();
      await tester.pumpWidget(
        app(
          SizedBox(
            height: 1200,
            child: HistoryContent(
              state: HistoryState(isLoading: false, entries: [entry]),
              today: kit.today,
              onRetry: () {},
              onLoadMore: () {},
              photoOwner: alex.uid,
            ),
          ),
        ),
      );
      await work(tester);
      expect(find.byType(PhotoThumbnail), findsNothing);
    });
  });

  group('the iOS Home Screen hint', () {
    testWidgets('in Safari on iPhone: shown once with the photo panel, dismissed for good', (tester) async {
      tall(tester);
      kit = TrainingKit(device: iphone);
      final entry = await tester.runAsync(recorded);
      Widget view() => app(CompletionView(title: '', text: '', entry: entry, onDone: () {}));
      await tester.pumpWidget(view());
      await work(tester);
      expect(find.text('Add Tio Minuter to your Home Screen so your photos stay saved.'), findsOneWidget);
      expect(find.text('You can also drop a photo here.'), findsNothing, reason: 'no drag and drop on a phone');
      await tester.tap(find.byTooltip('Dismiss'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Home Screen'), findsNothing);
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(view());
      await work(tester);
      expect(find.textContaining('Home Screen'), findsNothing);
    });

    testWidgets('Spanish; never on a desktop browser', (tester) async {
      tall(tester);
      kit = TrainingKit(device: iphone);
      final entry = await tester.runAsync(recorded);
      await tester.pumpWidget(
        app(
          CompletionView(title: '', text: '', entry: entry, onDone: () {}),
          locale: const Locale('es'),
        ),
      );
      await work(tester);
      expect(
        find.text('Agrega Tio Minuter a tu pantalla de inicio para que tus fotos se mantengan guardadas.'),
        findsOneWidget,
      );
      kit = TrainingKit();
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(app(CompletionView(title: '', text: '', entry: entry, onDone: () {})));
      await work(tester);
      expect(find.textContaining('Home Screen'), findsNothing);
    });
  });
}
