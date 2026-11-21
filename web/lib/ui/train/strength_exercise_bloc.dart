import 'dart:async';

import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/repositories.dart';
import '../../domain/countdown.dart';
import '../../domain/program.dart';
import '../../domain/sessions.dart';

/// Where the person is within the sets of one exercise (Android's `ProgramExercisePhase`).
sealed class ExercisePhase {
  const ExercisePhase(this.setIndex);

  final int setIndex;
}

final class PerformingReps extends ExercisePhase {
  const PerformingReps(super.setIndex);
}

final class ReadyToStartTimed extends ExercisePhase {
  const ReadyToStartTimed(super.setIndex);
}

final class GetReadyPhase extends ExercisePhase {
  const GetReadyPhase(super.setIndex, this.remaining);

  final int remaining;
}

enum ProgramSide { left, right }

final class PerformingTimed extends ExercisePhase {
  const PerformingTimed(super.setIndex, this.remaining, this.total, [this.side]);

  final int remaining;
  final int total;
  final ProgramSide? side;
}

final class SwitchingSides extends ExercisePhase {
  const SwitchingSides(super.setIndex, this.remaining);

  final int remaining;
}

/// Rest before set [setIndex] (the next one).
final class Resting extends ExercisePhase {
  const Resting(super.setIndex, this.remaining, this.total);

  final int remaining;
  final int total;
}

sealed class StrengthExerciseIntent {
  const StrengthExerciseIntent();
}

final class StrengthSetDone extends StrengthExerciseIntent {
  const StrengthSetDone();
}

final class StrengthStartTimed extends StrengthExerciseIntent {
  const StrengthStartTimed();
}

final class StrengthSkipRest extends StrengthExerciseIntent {
  const StrengthSkipRest();
}

final class StrengthBack extends StrengthExerciseIntent {
  const StrengthBack();
}

final class StrengthConfirmExit extends StrengthExerciseIntent {
  const StrengthConfirmExit();
}

final class StrengthDismissExit extends StrengthExerciseIntent {
  const StrengthDismissExit();
}

final class _StrengthPhase extends StrengthExerciseIntent {
  const _StrengthPhase(this.phase);

  final ExercisePhase phase;
}

final class _StrengthStep extends StrengthExerciseIntent {
  const _StrengthStep(this.step);

  final FutureOr<void> Function(Emitter<StrengthExerciseState> emit) step;
}

class StrengthExerciseState {
  const StrengthExerciseState({
    required this.slot,
    required this.exercise,
    required this.exerciseNumber,
    required this.totalExercises,
    required this.phase,
    this.showExitConfirmation = false,
    this.finished = false,
  });

  final StrengthSlot slot;
  final ProgramExercise? exercise;
  final int exerciseNumber;
  final int totalExercises;
  final ExercisePhase phase;
  final bool showExitConfirmation;
  final bool finished;

  int get totalSets => slot.sets;

  int get completedSets => phase.setIndex;

  StrengthExerciseState copyWith({ExercisePhase? phase, bool? showExitConfirmation, bool? finished}) =>
      StrengthExerciseState(
        slot: slot,
        exercise: exercise,
        exerciseNumber: exerciseNumber,
        totalExercises: totalExercises,
        phase: phase ?? this.phase,
        showExitConfirmation: showExitConfirmation ?? this.showExitConfirmation,
        finished: finished ?? this.finished,
      );
}

sealed class StrengthExerciseEffect {
  const StrengthExerciseEffect();
}

final class StrengthNavigateBack extends StrengthExerciseEffect {
  const StrengthNavigateBack();
}

/// One exercise of a strength workout (Android's `ProgramStrengthExerciseViewModel`): REPS set →
/// rest → next set; TIME sets start with a 5-second get-ready; a per-side TIME set runs left → 5-s
/// switch → right. No rest after the last set; the last set marks the exercise done (and the workout
/// when it was the last one), with sound and vibration. Every set extends the workout's session.
class StrengthExerciseBloc extends MviBloc<StrengthExerciseIntent, StrengthExerciseState, StrengthExerciseEffect> {
  StrengthExerciseBloc({
    required this.user,
    required this.programId,
    required this.week,
    required this.dayIndex,
    required this.workout,
    required StrengthSlot slot,
    required ProgramExercise? exercise,
    required this.deps,
  }) : _countdown = Countdown(deps.scheduler, deps.monotonic),
       _sessions = deps.service.strengthSessions(user.uid),
       super(
         StrengthExerciseState(
           slot: slot,
           exercise: exercise,
           exerciseNumber: workout.slots.indexOf(slot) + 1,
           totalExercises: workout.slots.length,
           phase: slot.type == ProgramSlotType.reps ? const PerformingReps(0) : const ReadyToStartTimed(0),
         ),
       ) {
    on<_StrengthPhase>((i, emit) => emit(state.copyWith(phase: i.phase)));
    on<_StrengthStep>((i, emit) async => i.step(emit));
    on<StrengthSetDone>((_, emit) async {
      deps.feedback.prepare();
      if (state.phase is PerformingReps) await _finishSet(emit, state.phase.setIndex);
    });
    on<StrengthStartTimed>((_, emit) {
      deps.feedback.prepare();
      if (state.phase is ReadyToStartTimed) _startGetReady(state.phase.setIndex);
    });
    on<StrengthSkipRest>((_, emit) {
      if (state.phase is Resting) _startSet(emit, state.phase.setIndex);
    });
    on<StrengthBack>((_, emit) {
      if (state.finished) return;
      if (state.completedSets > 0) {
        emit(state.copyWith(showExitConfirmation: true));
      } else {
        _countdown.cancel();
        sendEffect(const StrengthNavigateBack());
      }
    });
    on<StrengthConfirmExit>((_, emit) {
      _countdown.cancel();
      emit(state.copyWith(showExitConfirmation: false));
      sendEffect(const StrengthNavigateBack());
    });
    on<StrengthDismissExit>((_, emit) => emit(state.copyWith(showExitConfirmation: false)));
    unawaited(deps.wakeLock.acquire());
  }

  /// Android's `SWITCH_SIDES_SECONDS`.
  static const int switchSidesSeconds = 5;

  final WebUser user;
  final String programId;
  final int week;
  final int dayIndex;
  final StrengthWorkout workout;
  final TrainingDeps deps;
  final Countdown _countdown;
  final StrengthSessions _sessions;
  bool _finishing = false;

  bool _isLastSet(int setIndex) => setIndex >= state.totalSets - 1;

  void _startSet(Emitter<StrengthExerciseState> emit, int setIndex) {
    _countdown.cancel();
    emit(
      state.copyWith(
        phase: state.slot.type == ProgramSlotType.reps ? PerformingReps(setIndex) : ReadyToStartTimed(setIndex),
      ),
    );
  }

  void _startGetReady(int setIndex) => _countdown.start(
    Countdown.getReadySeconds,
    onTick: (remaining) => add(_StrengthPhase(GetReadyPhase(setIndex, remaining))),
    onFinished: () => _startTimedSide(setIndex, state.slot.perSide ? ProgramSide.left : null),
  );

  /// One TIME set, or one side of it when [side] is not null.
  void _startTimedSide(int setIndex, ProgramSide? side) {
    final duration = state.slot.reps[setIndex];
    _countdown.start(
      duration,
      onTick: (remaining) => add(_StrengthPhase(PerformingTimed(setIndex, remaining, duration, side))),
      onFinished: () {
        if (side == ProgramSide.left) {
          _feedback();
          _countdown.start(
            switchSidesSeconds,
            onTick: (remaining) => add(_StrengthPhase(SwitchingSides(setIndex, remaining))),
            onFinished: () => _startTimedSide(setIndex, ProgramSide.right),
          );
        } else {
          // The last set gets its feedback once, on completion
          if (!_isLastSet(setIndex)) _feedback();
          add(_StrengthStep((emit) => _finishSet(emit, setIndex)));
        }
      },
    );
  }

  /// After a set: rest (if any) before the next one, or complete the exercise. The workout's session
  /// notes the set; the last one is noted before the slot is marked.
  Future<void> _finishSet(Emitter<StrengthExerciseState> emit, int setIndex) async {
    if (_isLastSet(setIndex)) {
      await _completeExercise(emit);
      return;
    }
    _sessions.onSetCompleted(programId, week, dayIndex);
    final next = setIndex + 1;
    final rest = state.slot.restSeconds;
    if (rest > 0) {
      _countdown.start(
        rest,
        onTick: (remaining) => add(_StrengthPhase(Resting(next, remaining, rest))),
        onFinished: () {
          _feedback();
          add(_StrengthStep((emit) => _startSet(emit, next)));
        },
      );
    } else {
      _startSet(emit, next);
    }
  }

  Future<void> _completeExercise(Emitter<StrengthExerciseState> emit) async {
    if (_finishing) return;
    _finishing = true;
    _countdown.cancel();
    // Before the slot: completing the workout reads the session's last set
    _sessions.onSetCompleted(programId, week, dayIndex);
    emit(state.copyWith(finished: true));
    await deps.service.markSlotCompleted(
      user: user,
      programId: programId,
      week: week,
      dayIndex: dayIndex,
      workout: workout,
      position: state.slot.position,
    );
    _feedback();
    sendEffect(const StrengthNavigateBack());
  }

  void _feedback() {
    deps.feedback
      ..playCompletionSound()
      ..vibrate();
  }

  @override
  Future<void> close() {
    _countdown.cancel();
    unawaited(deps.wakeLock.release());
    return super.close();
  }
}
