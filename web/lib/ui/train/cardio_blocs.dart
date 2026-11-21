/// Program cardio on the web: the treadmill (Android's `ProgramTreadmillViewModel`) and the home
/// circuit (`ProgramCircuitViewModel`), each with its active-time session (the cardio kind, one for
/// all program cardio, as Android's PROGRAM_CARDIO). The outdoor run is Android-only.
library;

import 'dart:async';

import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/repositories.dart';
import '../../data/write_mappers.dart';
import '../../domain/countdown.dart';
import '../../domain/program.dart';
import '../../domain/sessions.dart';
import '../../domain/training.dart';

// ---------------------------------------------------------------------------
// Shared intents and effects
// ---------------------------------------------------------------------------

sealed class CardioIntent {
  const CardioIntent();
}

final class CardioStart extends CardioIntent {
  const CardioStart();
}

final class CardioPause extends CardioIntent {
  const CardioPause();
}

final class CardioResume extends CardioIntent {
  const CardioResume();
}

/// Skip a segment (treadmill) or a phase (circuit).
final class CardioSkip extends CardioIntent {
  const CardioSkip();
}

final class CardioBack extends CardioIntent {
  const CardioBack();
}

final class CardioConfirmExit extends CardioIntent {
  const CardioConfirmExit();
}

final class CardioDismissExit extends CardioIntent {
  const CardioDismissExit();
}

final class CardioVisibility extends CardioIntent {
  const CardioVisibility(this.visible);

  final bool visible;
}

final class _CardioUpdate<S> extends CardioIntent {
  const _CardioUpdate(this.update);

  final FutureOr<void> Function(Emitter<S> emit) update;
}

sealed class CardioEffect {
  const CardioEffect();
}

final class CardioNavigateBack extends CardioEffect {
  const CardioNavigateBack();
}

final class CardioSessionSaved extends CardioEffect {
  const CardioSessionSaved(this.durationSeconds);

  final int durationSeconds;
}

/// What both cardio blocs share: the session, the countdown, recording the finish.
mixin _CardioWorkout<S> on MviBloc<CardioIntent, S, CardioEffect> {
  late final Countdown countdown;
  late final ActiveWorkoutSession session;
  late final TrainingDeps deps;
  late final WebUser user;
  late final String programId;
  late final int week;
  late final int dayIndex;
  late final ProgramWorkout workout;

  void initCardio(TrainingDeps deps, WebUser user, String programId, int week, int dayIndex, ProgramWorkout workout) {
    this.deps = deps;
    this.user = user;
    this.programId = programId;
    this.week = week;
    this.dayIndex = dayIndex;
    this.workout = workout;
    countdown = Countdown(deps.scheduler, deps.monotonic);
    final refId = programRefId(programId, week, dayIndex);
    session = ActiveWorkoutSession(
      deps.scheduler,
      deps.service.cardioSessions(user.uid),
      ActiveTimeMeter(deps.monotonic),
      refId,
    );
    deps.service.rememberSessionTitle(
      user.uid,
      refId,
      deps.service.texts.programTitle(workout),
      titleKey: 'program/${workout.titleCode.code}',
    );
  }

  void startSession() {
    deps.feedback.prepare();
    for (final closed in session.start()) {
      sendEffect(CardioSessionSaved(closed.durationSeconds));
    }
    unawaited(deps.wakeLock.acquire());
  }

  void feedback() {
    deps.feedback
      ..playCompletionSound()
      ..vibrate();
  }

  /// Marks the workout completed with its active time and records it (Android's `markWorkoutCompleted`).
  Future<TrainingEntry> recordFinish() {
    final seconds = session.finish();
    unawaited(deps.wakeLock.release());
    return deps.service.completeProgramWorkout(
      user: user,
      programId: programId,
      week: week,
      dayIndex: dayIndex,
      workout: workout,
      durationSeconds: seconds,
    );
  }

  Future<void> closeCardio() {
    countdown.cancel();
    session.close();
    return deps.wakeLock.release();
  }
}

// ---------------------------------------------------------------------------
// Treadmill
// ---------------------------------------------------------------------------

class TreadmillState {
  const TreadmillState({
    required this.segments,
    this.currentIndex = 0,
    required this.remainingSeconds,
    this.isStarted = false,
    this.isRunning = false,
    this.isFinished = false,
    this.showExitConfirmation = false,
    this.recorded,
  });

  final List<TreadmillSegment> segments;
  final int currentIndex;
  final int remainingSeconds;
  final bool isStarted;
  final bool isRunning;
  final bool isFinished;
  final bool showExitConfirmation;
  final TrainingEntry? recorded;

  TreadmillSegment? get currentSegment => currentIndex < segments.length ? segments[currentIndex] : null;

  TreadmillSegment? get nextSegment => currentIndex + 1 < segments.length ? segments[currentIndex + 1] : null;

  /// The current segment's remaining time plus every later segment.
  int get totalRemainingSeconds => isFinished
      ? 0
      : remainingSeconds + segments.skip(currentIndex + 1).fold<int>(0, (sum, s) => sum + s.durationSeconds);

  TreadmillState copyWith({
    int? currentIndex,
    int? remainingSeconds,
    bool? isStarted,
    bool? isRunning,
    bool? isFinished,
    bool? showExitConfirmation,
    TrainingEntry? recorded,
  }) => TreadmillState(
    segments: segments,
    currentIndex: currentIndex ?? this.currentIndex,
    remainingSeconds: remainingSeconds ?? this.remainingSeconds,
    isStarted: isStarted ?? this.isStarted,
    isRunning: isRunning ?? this.isRunning,
    isFinished: isFinished ?? this.isFinished,
    showExitConfirmation: showExitConfirmation ?? this.showExitConfirmation,
    recorded: recorded ?? this.recorded,
  );
}

/// One countdown per segment, Start / Pause / Resume and Skip; sound + vibration at every segment
/// change; completed after the last segment. Active time: while running.
class TreadmillBloc extends MviBloc<CardioIntent, TreadmillState, CardioEffect> with _CardioWorkout<TreadmillState> {
  TreadmillBloc({
    required TrainingDeps deps,
    required WebUser user,
    required String programId,
    required int week,
    required int dayIndex,
    required TreadmillWorkout treadmill,
  }) : super(TreadmillState(segments: treadmill.segments, remainingSeconds: treadmill.segments.first.durationSeconds)) {
    initCardio(deps, user, programId, week, dayIndex, treadmill);
    on<_CardioUpdate<TreadmillState>>((i, emit) async => i.update(emit));
    on<CardioStart>((_, emit) {
      if (state.isStarted) return;
      _emit(emit, state.copyWith(isStarted: true));
      startSession();
      _run(emit);
    });
    on<CardioPause>((_, emit) {
      session.onUserAction();
      if (!state.isRunning) return;
      countdown.cancel();
      _emit(emit, state.copyWith(isRunning: false));
    });
    on<CardioResume>((_, emit) {
      session.onUserAction();
      if (state.isStarted && !state.isRunning && !state.isFinished) _run(emit);
    });
    on<CardioSkip>((_, emit) async {
      session.onUserAction();
      if (state.isStarted && !state.isFinished) await _nextSegment(emit);
    });
    on<CardioBack>((_, emit) {
      if (state.isStarted && !state.isFinished) {
        session.onUserAction();
        emit(state.copyWith(showExitConfirmation: true));
      } else {
        countdown.cancel();
        sendEffect(const CardioNavigateBack());
      }
    });
    on<CardioConfirmExit>((_, emit) {
      countdown.cancel();
      _emit(emit, state.copyWith(showExitConfirmation: false, isRunning: false));
      sendEffect(const CardioNavigateBack());
    });
    on<CardioDismissExit>((_, emit) {
      session.onUserAction();
      emit(state.copyWith(showExitConfirmation: false));
    });
    on<CardioVisibility>((i, _) => session.setVisible(i.visible));
  }

  /// Every change tells the session whether the countdown runs (counted also in the background).
  void _emit(Emitter<TreadmillState> emit, TreadmillState next) {
    emit(next);
    session.setTimerRunning(next.isRunning);
  }

  void _run(Emitter<TreadmillState> emit) {
    _emit(emit, state.copyWith(isRunning: true));
    countdown.start(
      state.remainingSeconds,
      onTick: (remaining) =>
          add(_CardioUpdate<TreadmillState>((emit) => emit(state.copyWith(remainingSeconds: remaining)))),
      onFinished: () => add(_CardioUpdate<TreadmillState>(_nextSegment)),
    );
  }

  Future<void> _nextSegment(Emitter<TreadmillState> emit) async {
    countdown.cancel();
    feedback();
    final next = state.currentIndex + 1;
    if (next >= state.segments.length) {
      _emit(emit, state.copyWith(isRunning: false, isFinished: true, remainingSeconds: 0));
      final entry = await recordFinish();
      emit(state.copyWith(recorded: entry));
      return;
    }
    final wasRunning = state.isRunning;
    emit(state.copyWith(currentIndex: next, remainingSeconds: state.segments[next].durationSeconds));
    if (wasRunning) _run(emit);
  }

  @override
  Future<void> close() async {
    await closeCardio();
    return super.close();
  }
}

// ---------------------------------------------------------------------------
// Home circuit
// ---------------------------------------------------------------------------

/// Where the person is in the circuit ([round] 1-based, exercise indices 0-based).
sealed class CircuitPhase {
  const CircuitPhase(this.remaining, this.total);

  final int remaining;
  final int total;
}

final class CircuitWork extends CircuitPhase {
  const CircuitWork(this.round, this.exerciseIndex, super.remaining, super.total);

  final int round;
  final int exerciseIndex;
}

/// Rest between two exercises of a round; shows the next one.
final class CircuitRest extends CircuitPhase {
  const CircuitRest(this.round, this.nextExerciseIndex, super.remaining, super.total);

  final int round;
  final int nextExerciseIndex;
}

/// Rest between rounds, before [nextRound].
final class CircuitRoundRest extends CircuitPhase {
  const CircuitRoundRest(this.nextRound, super.remaining, super.total);

  final int nextRound;
}

class CircuitState {
  const CircuitState({
    required this.workout,
    this.phase,
    this.isFinished = false,
    this.showExitConfirmation = false,
    this.recorded,
  });

  final HomeCircuitWorkout workout;

  /// Null before Start.
  final CircuitPhase? phase;
  final bool isFinished;
  final bool showExitConfirmation;
  final TrainingEntry? recorded;

  int get currentRound => switch (phase) {
    CircuitWork(:final round) => round,
    CircuitRest(:final round) => round,
    CircuitRoundRest(:final nextRound) => nextRound,
    null => 1,
  };

  /// The exercise being worked, or the next one during a rest.
  int? get shownExerciseId {
    final ids = workout.exerciseIds;
    final index = switch (phase) {
      CircuitWork(:final exerciseIndex) => exerciseIndex,
      CircuitRest(:final nextExerciseIndex) => nextExerciseIndex,
      CircuitRoundRest() || null => 0,
    };
    return index < ids.length ? ids[index] : null;
  }

  CircuitState copyWith({
    CircuitPhase? phase,
    bool clearPhase = false,
    bool? isFinished,
    bool? showExitConfirmation,
    TrainingEntry? recorded,
  }) => CircuitState(
    workout: workout,
    phase: clearPhase ? null : (phase ?? this.phase),
    isFinished: isFinished ?? this.isFinished,
    showExitConfirmation: showExitConfirmation ?? this.showExitConfirmation,
    recorded: recorded ?? this.recorded,
  );
}

/// Rounds of work / rest, rest between rounds, Skip; completed after the last work of the last round.
/// Active time: while a phase runs.
class CircuitBloc extends MviBloc<CardioIntent, CircuitState, CardioEffect> with _CardioWorkout<CircuitState> {
  CircuitBloc({
    required TrainingDeps deps,
    required WebUser user,
    required String programId,
    required int week,
    required int dayIndex,
    required HomeCircuitWorkout circuit,
  }) : super(CircuitState(workout: circuit)) {
    initCardio(deps, user, programId, week, dayIndex, circuit);
    on<_CardioUpdate<CircuitState>>((i, emit) async => i.update(emit));
    on<CardioStart>((_, emit) {
      if (state.phase != null || state.isFinished) return;
      startSession();
      _startWork(1, 0);
    });
    on<CardioSkip>((_, emit) async {
      session.onUserAction();
      if (state.phase != null && !state.isFinished) await _endPhase(emit);
    });
    on<CardioBack>((_, emit) {
      if (state.phase != null && !state.isFinished) {
        session.onUserAction();
        emit(state.copyWith(showExitConfirmation: true));
      } else {
        countdown.cancel();
        sendEffect(const CardioNavigateBack());
      }
    });
    on<CardioConfirmExit>((_, emit) {
      countdown.cancel();
      // The timer stops here (recorded after 2 hours of inactivity)
      session.setTimerRunning(false);
      emit(state.copyWith(showExitConfirmation: false));
      sendEffect(const CardioNavigateBack());
    });
    on<CardioDismissExit>((_, emit) {
      session.onUserAction();
      emit(state.copyWith(showExitConfirmation: false));
    });
    on<CardioVisibility>((i, _) => session.setVisible(i.visible));
    on<CardioPause>((_, _) {});
    on<CardioResume>((_, _) {});
  }

  void _emit(Emitter<CircuitState> emit, CircuitState next) {
    emit(next);
    session.setTimerRunning(next.phase != null && !next.isFinished);
  }

  void _startWork(int round, int index) {
    final seconds = state.workout.workSeconds;
    countdown.start(
      seconds,
      onTick: (remaining) => add(
        _CardioUpdate<CircuitState>(
          (emit) => _emit(emit, state.copyWith(phase: CircuitWork(round, index, remaining, seconds))),
        ),
      ),
      onFinished: () => add(_CardioUpdate<CircuitState>(_endPhase)),
    );
  }

  /// The end of the current phase (time is up, or Skip): feedback, then the next phase.
  Future<void> _endPhase(Emitter<CircuitState> emit) async {
    final workout = state.workout;
    countdown.cancel();
    feedback();
    switch (state.phase) {
      case CircuitWork(:final round, :final exerciseIndex):
        final lastInRound = exerciseIndex >= workout.exerciseIds.length - 1;
        if (lastInRound && round >= workout.rounds) {
          _emit(emit, state.copyWith(isFinished: true, clearPhase: true));
          final entry = await recordFinish();
          emit(state.copyWith(recorded: entry));
        } else if (lastInRound) {
          _startRest(
            workout.restBetweenRoundsSeconds,
            (r, t) => CircuitRoundRest(round + 1, r, t),
            () => _startWork(round + 1, 0),
          );
        } else {
          _startRest(
            workout.restSeconds,
            (r, t) => CircuitRest(round, exerciseIndex + 1, r, t),
            () => _startWork(round, exerciseIndex + 1),
          );
        }
      case CircuitRest(:final round, :final nextExerciseIndex):
        _startWork(round, nextExerciseIndex);
      case CircuitRoundRest(:final nextRound):
        _startWork(nextRound, 0);
      case null:
        break;
    }
  }

  void _startRest(int seconds, CircuitPhase Function(int remaining, int total) phaseFor, void Function() then) {
    if (seconds <= 0) {
      then();
      return;
    }
    countdown.start(
      seconds,
      onTick: (remaining) =>
          add(_CardioUpdate<CircuitState>((emit) => _emit(emit, state.copyWith(phase: phaseFor(remaining, seconds))))),
      onFinished: () => add(_CardioUpdate<CircuitState>(_endPhase)),
    );
  }

  @override
  Future<void> close() async {
    await closeCardio();
    return super.close();
  }
}
