import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/app/training_service.dart';
import 'package:tiominuter_web/data/jsonc.dart';
import 'package:tiominuter_web/data/write_mappers.dart';
import 'package:tiominuter_web/domain/daily.dart';
import 'package:tiominuter_web/domain/gym.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/ui/train/gym_blocs.dart';
import 'package:tiominuter_web/ui/train/gym_pages.dart';

import '../parity/golden.dart';
import '../support/fakes.dart';
import '../support/fixtures.dart';
import '../support/training_fakes.dart';

/// Gym routines on the web (Android's gym screens and GymSessionTracker): the catalog, the summary
/// and the names; every exercise phase on a fake clock; the progress and the session in this
/// browser (reset, the completion recorded once, the 2-hour close, a reload); and the GYM entry,
/// post and summary written as Android writes them.
void main() {
  final en = lookupAppLocalizations(const Locale('en'));
  final es = lookupAppLocalizations(const Locale('es'));
  late TrainingKit kit;
  late FakeContentRepository content;

  setUp(() {
    kit = TrainingKit();
    kit.training.storedGoals[kit.monday] = 3;
    content = FakeContentRepository()..trainingContent = trainingContent();
    setTrainingUser(alex);
  });

  GymRoutine pullDay() => trainingContent().gymRoutine(64)!;

  /// Moves the fake time in small steps, letting the blocs' events run (as the daily tests).
  Future<void> elapse(int millis) async {
    for (var i = 0; i < 5; i++) {
      await settle();
      kit.scheduler.runCurrent();
    }
    var left = millis;
    while (left > 0) {
      final step = left < 250 ? left : 250;
      kit.scheduler.advance(step);
      left -= step;
      for (var i = 0; i < 5; i++) {
        await settle();
        kit.scheduler.runCurrent();
      }
    }
  }

  GymExerciseBloc exercise(int position) {
    final routine = pullDay();
    return GymExerciseBloc(
      user: alex,
      routine: routine,
      slot: routine.exercises[position],
      exercise: trainingContent().exercises[routine.exercises[position].exerciseId],
      deps: kit.deps,
    );
  }

  GymRoutineBloc routine(TrainingKit kit, {int id = 64}) =>
      GymRoutineBloc(user: alex, routineId: id, content: content, deps: kit.deps)..add(const GymRoutineVisible());

  /// Does every set of [position] through its screen (REPS: "Set done" and the rest skipped;
  /// TIME: Start and the timers).
  Future<void> doExercise(int position) async {
    final bloc = exercise(position);
    final slot = pullDay().exercises[position];
    for (var set = 0; set < slot.sets; set++) {
      if (slot.type == ExerciseType.reps) {
        bloc.add(const GymSetDone());
        await elapse(0);
        if (bloc.state.phase is GymResting) bloc.add(const GymSkipRest());
      } else {
        bloc.add(const GymStartTimed());
        await elapse((5 + slot.reps[set]) * 1000);
      }
      await elapse(0);
    }
    await bloc.close();
  }

  List<TrainingEntry> gymEntries() => [
    for (final e in kit.training.live)
      if (e.source == TrainingSource.gym) e,
  ];

  group('the catalog', () {
    test('the real routines_for_gym.jsonc: the 11 routines by id, slots in order, sets = reps', () {
      final routines = GymContent.routines(parseJsonc(Golden.contentText('routines_for_gym.jsonc')));
      expect(routines.map((r) => r.routineId), [64, 65, 66, 67, 68, 69, 71, 72, 73, 74, 75]);
      final pull = routines.first;
      expect(pull.routineName, 'Pull Day');
      expect(pull.routineNameEs, 'Día de tirón');
      expect(pull.exercises.map((s) => s.position), List.generate(11, (i) => i));
      expect(pull.exercises.first.exerciseId, 18);
      expect(pull.exercises.first.reps, [10, 10, 10, 10]);
      expect(pull.exercises.first.restSeconds, 45);
      for (final routine in routines) {
        expect(routine.routineNameEs, isNotNull, reason: '${routine.routineId}');
        expect(routine.exercises, isNotEmpty);
      }
    });

    test('Android\'s lenient parsing: comments, trailing commas; bad entries and unknown types skipped', () {
      const text = '''
[
  // a comment
  { "routineId": 9, "routineName": "B", "exercises": [
      { "exerciseId": 1, "type": "time", "set": 2, "reps": [30, 45], "rest": 0, },
      { "exerciseId": 2, "type": "JUMP", "set": 1, "reps": [5], "rest": 10 },
      { "exerciseId": 3, "type": "REPS", "set": 1, "reps": [8], "rest": 10, "extra": true },
  ], },
  /* no id */ { "routineName": "X", "exercises": [] },
  { "routineId": 2, "routineName": "A // not a comment", "exercises": [] },
]''';
      final routines = GymContent.routines(parseJsonc(text));
      expect(routines.map((r) => r.routineId), [2, 9]);
      expect(routines.first.routineName, 'A // not a comment');
      final slots = routines.last.exercises;
      // The slot of an unknown type is skipped; positions stay the indices in the file
      expect(slots.map((s) => (s.position, s.exerciseId, s.type)), [
        (0, 1, ExerciseType.time),
        (2, 3, ExerciseType.reps),
      ]);
      expect(slots.first.sets, 2);
    });

    test('the summary in Android\'s format, English and Spanish', () {
      expect(
        exerciseSummary(ExerciseType.reps, [10, 10, 10, 10], gymSummaryLabels(en)),
        'Sets: 4 · Reps: 10, 10, 10, 10',
      );
      expect(exerciseSummary(ExerciseType.time, [30, 45], gymSummaryLabels(en)), 'Sets: 2 · Time: 30s, 45s');
      expect(exerciseSummary(ExerciseType.reps, [12, 10], gymSummaryLabels(es)), 'Series: 2 · Repeticiones: 12, 10');
      expect(exerciseSummary(ExerciseType.time, [30, 45], gymSummaryLabels(es)), 'Series: 2 · Tiempo: 30 s, 45 s');
    });

    test('localized names: the routine and its exercises, English as the fallback', () {
      final content = trainingContent();
      expect(gymRoutineName(pullDay(), en), 'Pull Day');
      expect(gymRoutineName(pullDay(), es), 'Día de tirón');
      // No Spanish name: the English one
      expect(gymRoutineName(content.gymRoutine(99)!, es), 'Empty');
      expect(gymExerciseName(content.exercises[2], es), 'Sentadillas');
      expect(gymExerciseName(content.exercises[2], en), 'Squats');
      expect(gymExerciseName(null, es), es.unknownExercise);
    });

    test('the selection: the catalog once, cards opened independently, sessions closed on every visit', () async {
      final bloc = GymSelectionBloc(user: alex, content: content, deps: kit.deps)..add(const GymSelectionVisible());
      await elapse(0);
      expect(bloc.state.routines.map((r) => r.routineId), [64, 99]);
      bloc
        ..add(const GymToggleExpanded(64))
        ..add(const GymToggleExpanded(99))
        ..add(const GymToggleExpanded(99));
      await elapse(0);
      expect(bloc.state.expandedRoutineIds, {64});
      // Content unavailable: the error, then a retry
      final missing = FakeContentRepository();
      final failing = GymSelectionBloc(user: alex, content: missing, deps: kit.deps)..add(const GymSelectionVisible());
      await elapse(0);
      expect(failing.state.contentMissing, isTrue);
      missing.trainingContent = trainingContent();
      failing.add(const GymSelectionRetry());
      await elapse(0);
      expect(failing.state.routines, hasLength(2));
      await bloc.close();
      await failing.close();
    });
  });

  group('the exercise', () {
    test('REPS: Set done → rest between sets → the last set marks it, feedback once, back', () async {
      final bloc = exercise(0);
      final effects = <GymExerciseEffect>[];
      final sub = bloc.effects.listen(effects.add);
      expect(bloc.state.phase, isA<GymPerformingReps>());
      bloc.add(const GymSetDone());
      await elapse(0);
      expect(bloc.state.phase, isA<GymResting>().having((p) => p.remaining, 'remaining', 45));
      expect(bloc.state.completedSets, 1);
      await elapse(45000);
      // The rest's end beeps once, then the next set
      expect(kit.feedback.sounds, 1);
      expect(bloc.state.phase, isA<GymPerformingReps>().having((p) => p.setIndex, 'set', 1));
      bloc.add(const GymSetDone());
      await elapse(0);
      // No rest after the last set
      expect(bloc.state.finished, isTrue);
      expect(kit.service.gymCompletedPositions(alex.uid, 64), {0});
      expect(kit.feedback.sounds, 2);
      expect(kit.feedback.vibrations, 2);
      expect(effects, [isA<GymNavigateBack>()]);
      expect(kit.wakeLock.held, isTrue);
      await sub.cancel();
      await bloc.close();
      expect(kit.wakeLock.held, isFalse);
    });

    test('Skip rest goes to the next set at once', () async {
      final bloc = exercise(0)..add(const GymSetDone());
      await elapse(10000);
      bloc.add(const GymSkipRest());
      await elapse(0);
      expect(bloc.state.phase, isA<GymPerformingReps>().having((p) => p.setIndex, 'set', 1));
      expect(kit.feedback.sounds, 0);
      await bloc.close();
    });

    test('TIME: Start → 5-s get-ready → the timer; no rest when rest is 0; the last set completes', () async {
      final bloc = exercise(1);
      expect(bloc.state.phase, isA<GymReadyToStartTimed>());
      bloc.add(const GymStartTimed());
      await elapse(0);
      expect(bloc.state.phase, isA<GymGetReady>().having((p) => p.remaining, 'remaining', 5));
      await elapse(5000);
      expect(bloc.state.phase, isA<GymPerformingTimed>().having((p) => p.remaining, 'remaining', 30));
      await elapse(29000);
      expect(bloc.state.phase, isA<GymPerformingTimed>().having((p) => p.remaining, 'remaining', 1));
      await elapse(1000);
      // The set's end beeps; straight to the next set (rest 0), waiting for Start
      expect(kit.feedback.sounds, 1);
      expect(bloc.state.phase, isA<GymReadyToStartTimed>().having((p) => p.setIndex, 'set', 1));
      bloc.add(const GymStartTimed());
      await elapse(5000 + 45000);
      expect(bloc.state.finished, isTrue);
      expect(kit.feedback.sounds, 2);
      expect(kit.service.gymCompletedPositions(alex.uid, 64), {1});
      await bloc.close();
    });

    test('Back: leaves at once before a set; asks after one, and leaving does not mark it', () async {
      final fresh = exercise(0);
      final freshEffects = <GymExerciseEffect>[];
      final freshSub = fresh.effects.listen(freshEffects.add);
      fresh.add(const GymBack());
      await elapse(0);
      expect(freshEffects, [isA<GymNavigateBack>()]);
      await freshSub.cancel();
      await fresh.close();

      final bloc = exercise(0);
      final effects = <GymExerciseEffect>[];
      final sub = bloc.effects.listen(effects.add);
      bloc.add(const GymSetDone());
      await elapse(0);
      bloc.add(const GymBack());
      await elapse(0);
      expect(bloc.state.showExitConfirmation, isTrue);
      bloc.add(const GymDismissExit());
      await elapse(0);
      expect(bloc.state.showExitConfirmation, isFalse);
      expect(effects, isEmpty);
      bloc
        ..add(const GymBack())
        ..add(const GymConfirmExit());
      await elapse(0);
      expect(effects, [isA<GymNavigateBack>()]);
      expect(kit.service.gymCompletedPositions(alex.uid, 64), isEmpty);
      await sub.cancel();
      await bloc.close();
    });
  });

  group('the routine', () {
    test('progress line, reset only with progress; reset discards the session without an entry', () async {
      final bloc = routine(kit);
      await elapse(0);
      expect(bloc.state.completedCount, 0);
      bloc.add(const GymResetClicked());
      await elapse(0);
      expect(bloc.state.showResetConfirmation, isFalse);
      await doExercise(0);
      bloc.add(const GymRoutineVisible());
      await elapse(0);
      expect(bloc.state.completedCount, 1);
      expect(en.gymCompletedCount(bloc.state.completedCount, 2), '1 of 2 exercises completed');
      expect(es.gymCompletedCount(1, 2), '1 de 2 ejercicios completados');
      bloc.add(const GymResetClicked());
      await elapse(0);
      expect(bloc.state.showResetConfirmation, isTrue);
      bloc.add(const GymResetConfirmed());
      await elapse(0);
      expect(bloc.state.completedCount, 0);
      expect(kit.service.gymCompletedPositions(alex.uid, 64), isEmpty);
      expect(kit.service.gymSessions(alex.uid).takeClosedSessions(), isEmpty);
      // Even 3 hours later nothing is recorded: the session was discarded
      kit.wallOffset += const Duration(hours: 3).inMilliseconds;
      expect(kit.service.closeInactiveSessions(alex.uid), isEmpty);
      expect(gymEntries(), isEmpty);
      await bloc.close();
    });

    test('an empty routine never completes; an unknown one is not found', () async {
      final empty = routine(kit, id: 99);
      final unknown = routine(kit, id: 12345);
      await elapse(0);
      expect(empty.state.routine?.exercises, isEmpty);
      expect(empty.state.showRoutineCompleted, isFalse);
      expect(unknown.state.isNotFound, isTrue);
      await empty.close();
      await unknown.close();
    });

    test('completed: the dialog, ONE entry (first → last set, dated now), Finish starts over', () async {
      final firstSetAt = kit.wall();
      await doExercise(0);
      await elapse(60000);
      await doExercise(1);
      final lastSetAt = kit.wall();
      final bloc = routine(kit);
      await elapse(0);
      expect(bloc.state.showRoutineCompleted, isTrue);
      final entry = bloc.state.completedEntry!;
      expect(gymEntries().map((e) => e.id), [entry.id]);
      final doc = kit.training.entries[entry.id]!;
      expect(doc.refId, 'gym:64');
      expect(doc.title, 'Pull Day');
      expect(doc.durationSeconds, (lastSetAt - firstSetAt) ~/ 1000);
      expect(doc.completedAt, kit.wall());
      expect(doc.dateLocal, kit.today);
      // Shown again (another visit before Finish, a reload): nothing more is recorded
      bloc.add(const GymRoutineVisible());
      final again = routine(kit);
      await elapse(0);
      expect(again.state.showRoutineCompleted, isTrue);
      expect(again.state.completedEntry, isNull);
      expect(gymEntries(), hasLength(1));
      bloc.add(const GymCompletedDismissed());
      await elapse(0);
      expect(bloc.state.showRoutineCompleted, isFalse);
      expect(kit.service.gymCompletedPositions(alex.uid, 64), isEmpty);
      await bloc.close();
      await again.close();
    });

    test('a reload mid-routine keeps the progress and the session (a new service over the same storage)', () async {
      final firstSetAt = kit.wall();
      await doExercise(0);
      await elapse(10 * 60000);
      // The page reloads: nothing in memory survives, only the browser's storage
      final reloaded = TrainingService(
        training: kit.training,
        sharing: kit.sharing,
        store: kit.store,
        dates: kit.dates,
        texts: EnglishTrainingTexts(),
        wallClock: kit.wall,
        uuid: () => 'reloaded-1',
      );
      expect(reloaded.gymCompletedPositions(alex.uid, 64), {0});
      reloaded.gymSessions(alex.uid).onSetCompleted(64);
      reloaded.markGymCompleted(alex.uid, 64, 1);
      final entry = await reloaded.completeGymRoutine(alex, pullDay());
      expect(entry!.durationSeconds, (kit.wall() - firstSetAt) ~/ 1000);
      expect(await reloaded.completeGymRoutine(alex, pullDay()), isNull);
      expect(gymEntries(), hasLength(1));
    });
  });

  group('the 2-hour close', () {
    Future<void> twoSets() async {
      // As the exercise screen does: the routine's English name is kept for a later close
      kit.service.rememberGymRoutine(alex.uid, pullDay());
      final sessions = kit.service.gymSessions(alex.uid);
      sessions.onSetCompleted(64);
      kit.service.markGymCompleted(alex.uid, 64, 0);
      kit.wallOffset += 20 * 60000;
      sessions.onSetCompleted(64);
    }

    Future<List<int>> visitSelection() async {
      final bloc = GymSelectionBloc(user: alex, content: content, deps: kit.deps);
      final saved = <int>[];
      final sub = bloc.effects.listen((e) => saved.add(e.durationSeconds));
      bloc.add(const GymSelectionVisible());
      await elapse(0);
      await sub.cancel();
      await bloc.close();
      return saved;
    }

    test('1 h 59 min and exactly 2 h after the last set: still open', () async {
      await twoSets();
      kit.wallOffset += const Duration(hours: 1, minutes: 59).inMilliseconds;
      expect(await visitSelection(), isEmpty);
      kit.wallOffset += const Duration(minutes: 1).inMilliseconds;
      expect(await visitSelection(), isEmpty);
      expect(gymEntries(), isEmpty);
      expect(kit.service.gymCompletedPositions(alex.uid, 64), {0});
    });

    test('2 h 1 min: ONE entry with the real duration, dated on the session start, progress reset', () async {
      kit = TrainingKit(start: DateTime(2026, 10, 21, 23, 30));
      kit.training.storedGoals[kit.monday] = 3;
      final startedAt = kit.wall();
      await twoSets();
      final lastSetAt = kit.wall();
      kit.wallOffset += const Duration(hours: 2, minutes: 1).inMilliseconds;
      expect(await visitSelection(), [1200]);
      final doc = kit.training.entries[gymEntries().single.id]!;
      expect(doc.durationSeconds, 1200);
      expect(doc.completedAt, lastSetAt);
      expect(doc.dateLocal, '2026-10-21');
      expect(DateTime.fromMillisecondsSinceEpoch(startedAt).day, 21);
      expect(doc.refId, 'gym:64');
      expect(doc.title, 'Pull Day');
      expect(kit.service.gymCompletedPositions(alex.uid, 64), isEmpty);
      // Closed once: later visits record nothing
      expect(await visitSelection(), isEmpty);
      expect(gymEntries(), hasLength(1));
    });

    test('the routine screen closes it too (resume), and the completion no longer records', () async {
      await twoSets();
      kit.wallOffset += const Duration(hours: 2, minutes: 1).inMilliseconds;
      final bloc = routine(kit);
      final saved = <int>[];
      final sub = bloc.effects.listen((e) => saved.add(e.durationSeconds));
      await elapse(0);
      expect(saved, [1200]);
      expect(bloc.state.completedCount, 0);
      await sub.cancel();
      await bloc.close();
    });

    test('a late set (2 h + 1 ms later) records the old session and starts a new one', () async {
      await twoSets();
      kit.wallOffset += const Duration(hours: 2).inMilliseconds + 1;
      final newStart = kit.wall();
      kit.service.gymSessions(alex.uid).onSetCompleted(64);
      expect(gymEntries().single.durationSeconds, 1200);
      kit.wallOffset += 5 * 60000;
      kit.service.markGymCompleted(alex.uid, 64, 0);
      kit.service.gymSessions(alex.uid).onSetCompleted(64);
      kit.service.markGymCompleted(alex.uid, 64, 1);
      final entry = await kit.service.completeGymRoutine(alex, pullDay());
      expect(entry!.durationSeconds, (kit.wall() - newStart) ~/ 1000);
      expect(gymEntries(), hasLength(2));
    });

    test('a clock moved back never closes', () async {
      await twoSets();
      kit.wallOffset -= const Duration(hours: 5).inMilliseconds;
      expect(await visitSelection(), isEmpty);
      expect(gymEntries(), isEmpty);
    });

    test('Home / the hub closes gym sessions with the others, and sign-out clears gym storage', () async {
      await twoSets();
      kit.wallOffset += const Duration(hours: 3).inMilliseconds;
      expect(kit.service.closeInactiveSessions(alex.uid).map((c) => c.durationSeconds), [1200]);
      await twoSets();
      await kit.service.clearLocalData(alex.uid);
      expect(kit.service.gymCompletedPositions(alex.uid, 64), isEmpty);
      kit.wallOffset += const Duration(hours: 3).inMilliseconds;
      expect(kit.service.closeInactiveSessions(alex.uid), isEmpty);
    });
  });

  group('what is written, as Android writes it', () {
    Future<TrainingEntry> completeRoutine() async {
      await doExercise(0);
      await doExercise(1);
      final bloc = routine(kit);
      await elapse(0);
      final entry = bloc.state.completedEntry!;
      await bloc.close();
      await elapse(0);
      return entry;
    }

    test('the entry has the shared fixture\'s fields (gymEntry)', () async {
      final entry = await completeRoutine();
      final fields = WriteMappers.trainingEntry(kit.training.entries[entry.id]!);
      final fixture = SharedFixtures.raw('gymEntry');
      expect(fields.keys.toSet(), {...fixture.keys, 'activityType', 'intensity', 'note'});
      expect(fields['source'], fixture['source']);
      expect(fields['refId'], fixture['refId']);
      expect(fields['title'], fixture['title']);
      expect(fields['activityType'], isNull);
    });

    test('a short workout (under 10 minutes): the summary counts nothing and nothing is posted', () async {
      kit.sharing.groupIds = ['g1'];
      kit.service.setFeedIntroSeen(alex.uid);
      await completeRoutine();
      expect(kit.sharing.summaries['g1']?['workoutsThisWeek'], 0);
      expect(kit.sharing.posts['g1'], isNull);
    });

    test('a counted gym workout: the summary shows GYM today, the post is written once', () async {
      kit.sharing.groupIds = ['g1'];
      kit.service.setFeedIntroSeen(alex.uid);
      await doExercise(0);
      await elapse(15 * 60000);
      await doExercise(1);
      final bloc = routine(kit);
      await elapse(0);
      final entry = bloc.state.completedEntry!;
      expect(entry.durationSeconds, greaterThanOrEqualTo(600));
      final again = routine(kit);
      await elapse(0);
      final summary = kit.sharing.summaries['g1']!;
      expect(summary['workoutsThisWeek'], 1);
      final day = DateTime(2026, 10, 21).weekday.toString();
      expect((summary['daysThisWeek']! as Map)[day], ['GYM']);
      expect(kit.sharing.writes.where((w) => w.startsWith('post ')), ['post g1 ${entry.id}']);
      // The post: the shared fixture's fields plus the key readers name it by, no thumbnail
      final post = kit.sharing.posts['g1']![entry.id]!;
      final fixture = SharedFixtures.raw('post');
      expect(post.keys.toSet(), {...fixture.keys, 'titleKey'});
      expect(post['source'], 'GYM');
      expect(post['title'], 'Pull Day');
      expect(post['titleKey'], 'gym/64');
      expect(post['durationSeconds'], entry.durationSeconds);
      expect(post.containsKey('thumb'), isFalse);
      await bloc.close();
      await again.close();
    });
  });
}
