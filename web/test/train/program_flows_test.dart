import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/domain/program.dart';
import 'package:tiominuter_web/domain/program_content.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/ui/train/cardio_blocs.dart';
import 'package:tiominuter_web/ui/train/program_create_bloc.dart';
import 'package:tiominuter_web/ui/train/strength_exercise_bloc.dart';

import '../parity/golden.dart';
import '../support/fakes.dart';
import '../support/training_fakes.dart';

/// Programs on the web: creation (a spot check against Android's golden), the strength sets and the
/// workout's completion, the treadmill and the circuit.
void main() {
  late TrainingKit kit;
  late FakeContentRepository content;
  final catalog = ProgramContent.catalog(jsonDecode(Golden.contentText('program_exercises.json')));
  final templates = ProgramContent.templates(jsonDecode(Golden.contentText('program_templates.json')));

  setUp(() {
    kit = TrainingKit();
    content = FakeContentRepository()..trainingContent = trainingContent(templates: templates, catalog: catalog);
  });

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

  test('created from the form: the same program as Android (golden), the program and every workout written', () async {
    final bloc = ProgramCreateBloc(uid: alex.uid, content: content, deps: kit.deps)..add(const ProgramCreateLoad());
    final created = <ProgramCreateEffect>[];
    final sub = bloc.effects.listen(created.add);
    await elapse(0);
    bloc
      ..add(ProgramFormChanged((f) => f.copyWith(ageText: '30', weightText: '70', heightText: '175')))
      ..add(ProgramFormChanged((f) => f.copyWith(level: ProgramLevel.beginner, location: ProgramLocation.home)))
      ..add(const ProgramPreviewClicked());
    await elapse(0);
    expect(bloc.state.preview, hasLength(3));
    bloc.add(const ProgramCreateClicked());
    await elapse(0);
    final id = (created.single as ProgramCreated).programId;
    final stored = kit.training.storedPrograms[id]!;
    final golden = Golden.load('program_generator');
    final payloads = [for (final p in golden['payloads']! as List) p as String];
    final goldenCase = Golden.cases(golden)
        .firstWhere((c) => c['id'] == 'adult/GAIN_MUSCLE/HOME/BEGINNER/3d/BALANCED/6w');
    final goldenWeeks = (goldenCase['output']! as Map)['weeks']! as List;
    expect(stored.program.weeks, hasLength(goldenWeeks.length));
    for (final (w, week) in stored.program.weeks.indexed) {
      final expected = (goldenWeeks[w] as Map)['workouts']! as List;
      expect(
        [for (final workout in week.workouts) workout.toPayload()],
        [for (final item in expected) payloads[(item as Map)['payload']! as int]],
      );
    }
    expect(kit.training.writes, ['program $id']);
    await sub.cancel();
    await bloc.close();
  });

  test('the form refuses an incomplete profile and shows every error', () async {
    final bloc = ProgramCreateBloc(uid: alex.uid, content: content, deps: kit.deps)..add(const ProgramCreateLoad());
    await elapse(0);
    bloc.add(const ProgramCreateClicked());
    await elapse(0);
    expect(bloc.state.form.showAllErrors, isTrue);
    expect(kit.training.storedPrograms, isEmpty);
    await bloc.close();
  });

  group('strength', () {
    const workout = StrengthWorkout(
      title: StrengthDayTitle.fullBodyA,
      slots: [
        StrengthSlot(position: 0, exerciseId: 1, type: ProgramSlotType.reps, reps: [10, 10], restSeconds: 60),
        StrengthSlot(position: 1, exerciseId: 4, type: ProgramSlotType.time, reps: [20], restSeconds: 0, perSide: true),
      ],
    );

    StrengthExerciseBloc exercise(int position) => StrengthExerciseBloc(
      user: alex,
      programId: 'p1',
      week: 1,
      dayIndex: 0,
      workout: workout,
      slot: workout.slots[position],
      exercise: null,
      deps: kit.deps,
    );

    test('sets, rest, a per-side hold; the last exercise completes the workout once, timed first → last set', () async {
      kit.sharing.groupIds = ['g1'];
      kit.service.setFeedIntroSeen(alex.uid);
      final reps = exercise(0);
      expect(reps.state.phase, isA<PerformingReps>());
      reps.add(const StrengthSetDone());
      await elapse(0);
      expect(reps.state.phase, isA<Resting>());
      await elapse(30000);
      reps.add(const StrengthSkipRest());
      await elapse(0);
      expect(reps.state.phase, isA<PerformingReps>());
      expect(reps.state.phase.setIndex, 1);
      await elapse(40000);
      reps.add(const StrengthSetDone());
      await elapse(0);
      expect(kit.service.completedSlots(alex.uid, 'p1', 1, 0), {0});
      expect(kit.training.completions, isEmpty, reason: 'one exercise is left');
      await reps.close();

      final hold = exercise(1);
      hold.add(const StrengthStartTimed());
      await elapse(0);
      expect(hold.state.phase, isA<GetReadyPhase>());
      await elapse(5000);
      expect((hold.state.phase as PerformingTimed).side, ProgramSide.left);
      await elapse(20000);
      expect(hold.state.phase, isA<SwitchingSides>());
      await elapse(5000);
      expect((hold.state.phase as PerformingTimed).side, ProgramSide.right);
      await elapse(20000);
      expect(kit.training.completions['p1'], contains('1-0'));
      final entry = kit.training.live.single;
      expect(entry.source, TrainingSource.program);
      expect(entry.refId, 'program:p1:1:0');
      expect(entry.title, 'Full body A');
      // First set (t = 0) → last set (the hold's end, 120 s later)
      expect(entry.durationSeconds, 120);
      expect(kit.sharing.posts['g1'], isNull, reason: '2 minutes: under the 10-minute rule, never posted');
      expect(kit.service.takeJustCompleted('p1', 1, 0)?.id, entry.id);
      await hold.close();

      // Done again without a reset: the completion is written again, nothing more is recorded
      final again = exercise(1);
      again.add(const StrengthStartTimed());
      await elapse(60000);
      expect(kit.training.live, hasLength(1));
      await again.close();
    });

    test('leaving with sets done asks first', () async {
      final bloc = exercise(0);
      bloc.add(const StrengthBack());
      await elapse(0);
      expect(bloc.state.showExitConfirmation, isFalse, reason: 'nothing done yet: it just leaves');
      bloc.add(const StrengthSetDone());
      await elapse(0);
      bloc.add(const StrengthBack());
      await elapse(0);
      expect(bloc.state.showExitConfirmation, isTrue);
      await bloc.close();
    });
  });

  test('the treadmill: segments, pause (not counted), skip, completed with its active time', () async {
    const treadmill = TreadmillWorkout([
      TreadmillSegment(durationSeconds: 60, speedKmh: 5, inclinePercent: 1, label: TreadmillSegmentLabel.warmUp),
      TreadmillSegment(durationSeconds: 30, speedKmh: 8, inclinePercent: 2, label: TreadmillSegmentLabel.jog),
      TreadmillSegment(durationSeconds: 60, speedKmh: 5, inclinePercent: 0, label: TreadmillSegmentLabel.coolDown),
    ]);
    final bloc = TreadmillBloc(deps: kit.deps, user: alex, programId: 'p1', week: 2, dayIndex: 1, treadmill: treadmill);
    bloc.add(const CardioStart());
    await elapse(20000);
    expect(bloc.state.remainingSeconds, 40);
    bloc.add(const CardioPause());
    await elapse(100000);
    expect(bloc.state.remainingSeconds, 40);
    bloc.add(const CardioResume());
    await elapse(40000);
    expect(bloc.state.currentIndex, 1);
    bloc.add(const CardioSkip());
    await elapse(60000);
    expect(bloc.state.isFinished, isTrue);
    expect(kit.feedback.sounds, 3);
    final entry = kit.training.live.single;
    expect(entry.durationSeconds, 120, reason: '20 + 40 + 60 running; the pause excluded');
    expect(entry.title, 'Treadmill');
    expect(kit.training.completions['p1'], contains('2-1'));
    await bloc.close();
  });

  test('the circuit: work and rest, rounds, completed after the last work', () async {
    const circuit = HomeCircuitWorkout(
      rounds: 2,
      workSeconds: 20,
      restSeconds: 10,
      restBetweenRoundsSeconds: 30,
      exerciseIds: [1, 2],
    );
    final bloc = CircuitBloc(deps: kit.deps, user: alex, programId: 'p1', week: 1, dayIndex: 2, circuit: circuit);
    bloc.add(const CardioStart());
    await elapse(0);
    expect(bloc.state.phase, isA<CircuitWork>());
    await elapse(20000);
    expect(bloc.state.phase, isA<CircuitRest>());
    await elapse(10000);
    expect((bloc.state.phase! as CircuitWork).exerciseIndex, 1);
    await elapse(20000);
    expect(bloc.state.phase, isA<CircuitRoundRest>());
    expect(bloc.state.currentRound, 2);
    bloc.add(const CardioSkip());
    // Round 2: work 20 s, rest 10 s, work 20 s
    await elapse(50000);
    expect(bloc.state.isFinished, isTrue);
    expect(kit.training.live.single.title, 'Home circuit');
    await bloc.close();
  });
}
