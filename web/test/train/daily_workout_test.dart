import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/app/training_service.dart';
import 'package:tiominuter_web/domain/daily_progress.dart';
import 'package:tiominuter_web/domain/sessions.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/ui/train/daily_workout_bloc.dart';
import 'package:tiominuter_web/ui/train/train_bloc.dart';

import '../support/fakes.dart';
import '../support/training_fakes.dart';

/// The daily workout on a fake clock (the scheduler's virtual time is the monotonic clock and moves
/// the wall clock): Android's flow, the active time recorded, a reload resumed, an abandoned workout
/// closed after 2 hours.
void main() {
  late TrainingKit kit;
  late FakeContentRepository content;

  setUp(() {
    kit = TrainingKit();
    // Day 3 (the test content's only routine) is the next unlocked day
    kit.training.storedDailyProgress = const DailyProgress(lastCompletedDay: 2);
    content = FakeContentRepository()..trainingContent = trainingContent();
    setTrainingUser(alex);
  });

  DailyWorkoutBloc open() =>
      DailyWorkoutBloc(routineId: 3, user: alex, content: content, deps: kit.deps)..add(const DailyWorkoutLoad());

  /// Moves the fake time in small steps, letting the bloc's events and the session's work run.
  Future<void> elapse(int millis) async {
    // The events already added run first, at the current time
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
    for (var i = 0; i < 5; i++) {
      await settle();
      kit.scheduler.runCurrent();
    }
  }

  List<TrainingEntry> daily() => kit.training.live.where((e) => e.source == TrainingSource.daily).toList();

  test('the whole flow: Get Ready, TIME, REPS, the rest that starts on its own, skip, finish', () async {
    final bloc = open();
    await elapse(0);
    expect(bloc.state.index, 0);
    expect(bloc.state.isTime, isTrue);
    expect(bloc.state.timerRemaining, 30);
    expect(bloc.state.isTimerRunning, isFalse, reason: 'a TIME exercise waits for Get Ready');

    bloc.add(const DailyStartGetReady());
    await elapse(0);
    expect(bloc.state.isReadyCountdown, isTrue);
    expect(bloc.state.readyRemaining, 5);
    await elapse(5000);
    expect(bloc.state.isReadyCountdown, isFalse);
    expect(bloc.state.isTimerRunning, isTrue);
    await elapse(15000);
    expect(bloc.state.timerRemaining, 15);
    await elapse(15000);
    // The TIME exercise ended: sound + vibration, then the REPS one
    expect(kit.feedback.sounds, 1);
    expect(kit.feedback.vibrations, 1);
    expect(bloc.state.index, 1);
    expect(bloc.state.isReps, isTrue);

    await elapse(10000);
    bloc.add(const DailyCompleteReps());
    await elapse(0);
    // The rest starts on its own
    expect(bloc.state.index, 2);
    expect(bloc.state.current!.isRest, isTrue);
    expect(bloc.state.isTimerRunning, isTrue);
    await elapse(15000);
    expect(bloc.state.index, 3);
    expect(bloc.state.isTimerRunning, isFalse);
    // A rest is a TIME slot: its end plays the sound too (Android's OnTimerFinished)
    expect(kit.feedback.sounds, 2);

    // The last TIME exercise waits: that time doesn't count
    await elapse(7000);
    bloc.add(const DailySkipExercise());
    await elapse(0);
    expect(bloc.state.isComplete, isTrue);
    final entries = daily();
    expect(entries, hasLength(1));
    expect(entries.single.refId, 'daily:3');
    expect(entries.single.title, 'Day 3');
    expect(entries.single.dateLocal, kit.today);
    // Get Ready 5 + TIME 30 + REPS on screen 10 + rest 15; not the 7 s waiting
    expect(entries.single.durationSeconds, 60);
    expect(bloc.state.recorded?.id, entries.single.id);
    expect(kit.service.progress(alex.uid, DailyWorkoutBloc.progressName), isNull);
    expect(kit.service.dailySessions(alex.uid).storedSession, isNull);
    await bloc.close();
  });

  test('a REPS exercise counts only while visible', () async {
    final bloc = open();
    await elapse(0);
    bloc.add(const DailySkipExercise());
    await elapse(0);
    expect(bloc.state.isReps, isTrue);
    await elapse(4000);
    bloc.add(const DailyVisibility(false));
    await elapse(60000);
    bloc.add(const DailyVisibility(true));
    await elapse(6000);
    bloc
      ..add(const DailySkipExercise())
      ..add(const DailySkipExercise())
      ..add(const DailySkipExercise());
    await elapse(0);
    expect(daily().single.durationSeconds, 10);
    await bloc.close();
  });

  test('a running timer counts in the background', () async {
    final bloc = open();
    await elapse(0);
    bloc.add(const DailyStartGetReady());
    await elapse(0);
    bloc.add(const DailyVisibility(false));
    await elapse(35000);
    bloc.add(const DailyVisibility(true));
    for (var i = 0; i < 3; i++) {
      bloc.add(const DailySkipExercise());
    }
    await elapse(0);
    expect(daily().single.durationSeconds, 35);
    await bloc.close();
  });

  test('a page reload resumes the same exercise and the same session', () async {
    final first = open();
    await elapse(0);
    first.add(const DailyStartGetReady());
    await elapse(35000);
    expect(first.state.index, 1);
    await elapse(3000);
    // The page goes away (a reload): the bloc is gone, localStorage stays
    await first.close();
    await elapse(60000);

    final second = open();
    await elapse(0);
    expect(second.state.index, 1, reason: 'the REPS exercise where it was');
    expect(daily(), isEmpty, reason: 'nothing recorded: the session goes on');
    await elapse(2000);
    second
      ..add(const DailyCompleteReps())
      ..add(const DailySkipExercise())
      ..add(const DailySkipExercise());
    await elapse(0);
    final entry = daily().single;
    // 35 s timed + 3 s of REPS saved before the reload (saved every 5 s and on changes) + 2 s after
    expect(entry.durationSeconds, inInclusiveRange(35, 40));
    await second.close();
  });

  test('an abandoned workout is recorded after 2 hours (dated on its start) and announced on the hub', () async {
    final bloc = open();
    await elapse(0);
    bloc.add(const DailyStartGetReady());
    await elapse(12000);
    bloc.add(const DailyBack());
    await elapse(0);
    await bloc.close();
    await elapse(sessionInactivityTimeoutMillis);
    final hub = TrainBloc(user: alex, content: content, deps: kit.deps);
    final announced = <TrainEffect>[];
    final sub = hub.effects.listen(announced.add);
    hub.add(const TrainVisible());
    await elapse(0);
    expect(daily(), isEmpty, reason: 'exactly 2 hours: still open');
    await elapse(1000);
    hub.add(const TrainVisible());
    await elapse(0);
    final entry = daily().single;
    expect(entry.durationSeconds, 12, reason: 'Back stops the timers and saves the active time');
    expect(entry.dateLocal, kit.today);
    expect(entry.title, 'Day 3');
    expect(announced.whereType<TrainSessionSaved>().single.durationSeconds, 12);
    await sub.cancel();
    await hub.close();
  });

  test('a workout left open in another routine is recorded when this one starts', () async {
    kit.service.dailySessions(alex.uid).start('daily:1');
    kit.service.rememberSessionTitle(alex.uid, 'daily:1', 'Day 1');
    final bloc = open();
    final effects = <DailyWorkoutEffect>[];
    final sub = bloc.effects.listen(effects.add);
    await elapse(0);
    bloc.add(const DailyStartGetReady());
    await elapse(0);
    expect(daily().single.refId, 'daily:1');
    expect(effects.whereType<DailySessionSaved>(), hasLength(1));
    await sub.cancel();
    await bloc.close();
  });

  // ---------------------------------------------------------------------------
  // Daily unlock (domain/daily_progress.dart)
  // ---------------------------------------------------------------------------

  test('finishing the next day unlocks the following one, after the last day it offers to start again', () async {
    final bloc = open();
    await elapse(0);
    for (var i = 0; i < 4; i++) {
      bloc.add(const DailySkipExercise());
    }
    await elapse(0);
    expect(bloc.state.isComplete, isTrue);
    expect(kit.training.storedDailyProgress, const DailyProgress(lastCompletedDay: 3));
    // Day 3 is the content's last routine: every day is done
    expect(bloc.state.offersStartAgain, isTrue);

    final effects = <DailyWorkoutEffect>[];
    final sub = bloc.effects.listen(effects.add);
    bloc.add(const DailyRestartDays());
    await elapse(0);
    expect(kit.training.storedDailyProgress, const DailyProgress(cycle: 1));
    expect(effects.whereType<DailyNavigateBack>(), hasLength(1), reason: 'back to the days, locked again');
    await sub.cancel();
    await bloc.close();
  });

  test('repeating a completed day changes nothing and offers nothing', () async {
    kit.training.storedDailyProgress = const DailyProgress(lastCompletedDay: 3, cycle: 1);
    content.trainingContent = trainingContent().withExtraDailyRoutine(4);
    final bloc = open();
    await elapse(0);
    for (var i = 0; i < 4; i++) {
      bloc.add(const DailySkipExercise());
    }
    await elapse(0);
    expect(bloc.state.isComplete, isTrue);
    expect(kit.training.storedDailyProgress, const DailyProgress(lastCompletedDay: 3, cycle: 1));
    expect(kit.training.writes.where((w) => w.startsWith('dailyProgress')), isEmpty);
    expect(bloc.state.offersStartAgain, isFalse, reason: 'day 4 is the last one now');
    await bloc.close();
  });

  test("a locked day opened by its URL can't be done", () async {
    kit.training.storedDailyProgress = const DailyProgress(lastCompletedDay: 1);
    final bloc = open();
    await elapse(0);
    expect(bloc.state.lockedDay, 2);
    expect(bloc.state.current, isNull);
    bloc
      ..add(const DailyStartGetReady())
      ..add(const DailyCompleteReps())
      ..add(const DailySkipExercise())
      ..add(const DailySkipExercise())
      ..add(const DailySkipExercise())
      ..add(const DailySkipExercise());
    await elapse(10000);
    expect(bloc.state.isComplete, isFalse);
    expect(daily(), isEmpty);
    expect(kit.service.dailySessions(alex.uid).storedSession, isNull);
    expect(kit.training.storedDailyProgress, const DailyProgress(lastCompletedDay: 1));
    await bloc.close();
  });

  test('a session closed for inactivity unlocks nothing', () async {
    final bloc = open();
    await elapse(0);
    bloc.add(const DailyStartGetReady());
    await elapse(12000);
    bloc.add(const DailyBack());
    await elapse(0);
    await bloc.close();
    await elapse(sessionInactivityTimeoutMillis + 1000);
    kit.service.closeInactiveSessions(alex.uid);
    expect(daily(), hasLength(1));
    expect(kit.training.storedDailyProgress, const DailyProgress(lastCompletedDay: 2));
  });

  test('a new person: the first read creates the progress from the history, once', () async {
    kit.training.storedDailyProgress = null;
    await kit.service.recordWorkout(
      user: alex,
      source: TrainingSource.daily,
      refId: 'daily:7',
      title: 'Day 7',
      durationSeconds: 700,
    );
    expect(await kit.service.dailyProgress(alex.uid), const DailyProgress(lastCompletedDay: 7));
    expect(await kit.service.dailyProgress(alex.uid), const DailyProgress(lastCompletedDay: 7));
    expect(kit.training.dailyHistoryReads, 1);
    expect(kit.training.writes.where((w) => w.startsWith('dailyProgress')), ['dailyProgress 7/0']);
  });

  test('an unknown routine is not found', () async {
    final bloc = DailyWorkoutBloc(routineId: 99, user: alex, content: content, deps: kit.deps)
      ..add(const DailyWorkoutLoad());
    await elapse(0);
    expect(bloc.state.notFound, isTrue);
    await bloc.close();
  });
}
