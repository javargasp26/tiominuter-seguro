/// Gym routines on the web (Android's `ui/gymroutineselection`, `ui/gymroutine`, `ui/gymexercise`):
/// the catalog from the content, the progress and the session in this browser (as on Android they
/// stay on the device), and one GYM entry per finished or abandoned session. Kept apart from the
/// program screens, as on Android.
library;

import 'dart:async';

import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/content_repository.dart';
import '../../data/repositories.dart';
import '../../domain/countdown.dart';
import '../../domain/daily.dart';
import '../../domain/gym.dart';
import '../../domain/training.dart';

/// A session closed for inactivity while a gym screen was opened (Android's `ShowSessionSaved`).
final class GymSessionSaved {
  const GymSessionSaved(this.durationSeconds);

  final int durationSeconds;
}

// ---------------------------------------------------------------------------
// Selection (Android's GymRoutineSelectionViewModel)
// ---------------------------------------------------------------------------

sealed class GymSelectionIntent {
  const GymSelectionIntent();
}

/// Shown (and on every return): inactive sessions are closed, then the catalog is read once.
final class GymSelectionVisible extends GymSelectionIntent {
  const GymSelectionVisible();
}

final class GymSelectionRetry extends GymSelectionIntent {
  const GymSelectionRetry();
}

final class GymToggleExpanded extends GymSelectionIntent {
  const GymToggleExpanded(this.routineId);

  final int routineId;
}

class GymSelectionState {
  const GymSelectionState({
    this.isLoading = true,
    this.contentMissing = false,
    this.routines = const [],
    this.exercises = const {},
    this.expandedRoutineIds = const {},
  });

  final bool isLoading;
  final bool contentMissing;
  final List<GymRoutine> routines;
  final Map<int, CatalogExercise> exercises;

  /// Several cards can be open at once.
  final Set<int> expandedRoutineIds;

  GymSelectionState copyWith({
    bool? isLoading,
    bool? contentMissing,
    List<GymRoutine>? routines,
    Map<int, CatalogExercise>? exercises,
    Set<int>? expandedRoutineIds,
  }) => GymSelectionState(
    isLoading: isLoading ?? this.isLoading,
    contentMissing: contentMissing ?? this.contentMissing,
    routines: routines ?? this.routines,
    exercises: exercises ?? this.exercises,
    expandedRoutineIds: expandedRoutineIds ?? this.expandedRoutineIds,
  );
}

class GymSelectionBloc extends MviBloc<GymSelectionIntent, GymSelectionState, GymSessionSaved> {
  GymSelectionBloc({required this.user, required this.content, required this.deps}) : super(const GymSelectionState()) {
    on<GymSelectionVisible>((_, emit) async {
      for (final closed in deps.service.closeInactiveSessions(user.uid)) {
        sendEffect(GymSessionSaved(closed.durationSeconds));
      }
      if (!_loaded) await _load(emit);
    });
    on<GymSelectionRetry>((_, emit) => _load(emit));
    on<GymToggleExpanded>((i, emit) {
      final open = {...state.expandedRoutineIds};
      if (!open.remove(i.routineId)) open.add(i.routineId);
      emit(state.copyWith(expandedRoutineIds: open));
    });
  }

  final WebUser user;
  final ContentRepository content;
  final TrainingDeps deps;
  bool _loaded = false;

  Future<void> _load(Emitter<GymSelectionState> emit) async {
    emit(state.copyWith(isLoading: true));
    final training = await content.training();
    if (training == null) {
      emit(state.copyWith(isLoading: false, contentMissing: true));
      return;
    }
    _loaded = true;
    emit(
      state.copyWith(
        isLoading: false,
        contentMissing: false,
        routines: training.gymRoutines,
        exercises: training.exercises,
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Routine (Android's GymRoutineViewModel)
// ---------------------------------------------------------------------------

sealed class GymRoutineIntent {
  const GymRoutineIntent();
}

/// Shown (and on every return from an exercise): inactive sessions are closed, the progress is read
/// again, and a fully done routine shows its completion (recorded once).
final class GymRoutineVisible extends GymRoutineIntent {
  const GymRoutineVisible();
}

final class GymResetClicked extends GymRoutineIntent {
  const GymResetClicked();
}

final class GymResetConfirmed extends GymRoutineIntent {
  const GymResetConfirmed();
}

final class GymResetDismissed extends GymRoutineIntent {
  const GymResetDismissed();
}

/// "Finish" on the completion: the routine starts over (Android's `OnRoutineCompletedDismissed`).
final class GymCompletedDismissed extends GymRoutineIntent {
  const GymCompletedDismissed();
}

class GymRoutineState {
  const GymRoutineState({
    this.isLoading = true,
    this.contentMissing = false,
    this.routine,
    this.exercises = const {},
    this.completedPositions = const {},
    this.showResetConfirmation = false,
    this.showRoutineCompleted = false,
    this.completedEntry,
  });

  final bool isLoading;
  final bool contentMissing;

  /// Null once loaded: the routine isn't in the catalog (not found).
  final GymRoutine? routine;
  final Map<int, CatalogExercise> exercises;
  final Set<int> completedPositions;
  final bool showResetConfirmation;
  final bool showRoutineCompleted;

  /// The entry recorded by this completion; null when the completion was already recorded (shown
  /// again after a reload), so it isn't offered for sharing twice.
  final TrainingEntry? completedEntry;

  bool get isNotFound => !isLoading && !contentMissing && routine == null;

  /// Completed positions that exist in the routine (others are ignored, as on Android).
  int get completedCount => routine?.exercises.where((slot) => completedPositions.contains(slot.position)).length ?? 0;

  bool get canReset => completedCount > 0;

  bool get isFullyCompleted {
    final slots = routine?.exercises ?? const [];
    return slots.isNotEmpty && completedCount == slots.length;
  }

  GymRoutineState copyWith({
    bool? isLoading,
    bool? contentMissing,
    GymRoutine? routine,
    Map<int, CatalogExercise>? exercises,
    Set<int>? completedPositions,
    bool? showResetConfirmation,
    bool? showRoutineCompleted,
    TrainingEntry? completedEntry,
    bool clearCompletedEntry = false,
  }) => GymRoutineState(
    isLoading: isLoading ?? this.isLoading,
    contentMissing: contentMissing ?? this.contentMissing,
    routine: routine ?? this.routine,
    exercises: exercises ?? this.exercises,
    completedPositions: completedPositions ?? this.completedPositions,
    showResetConfirmation: showResetConfirmation ?? this.showResetConfirmation,
    showRoutineCompleted: showRoutineCompleted ?? this.showRoutineCompleted,
    completedEntry: clearCompletedEntry ? null : (completedEntry ?? this.completedEntry),
  );
}

class GymRoutineBloc extends MviBloc<GymRoutineIntent, GymRoutineState, GymSessionSaved> {
  GymRoutineBloc({required this.user, required this.routineId, required this.content, required this.deps})
    : super(const GymRoutineState()) {
    on<GymRoutineVisible>((_, emit) async {
      for (final closed in deps.service.closeInactiveSessions(user.uid)) {
        sendEffect(GymSessionSaved(closed.durationSeconds));
      }
      if (state.isLoading || state.contentMissing) {
        final training = await content.training();
        if (training == null) {
          emit(state.copyWith(isLoading: false, contentMissing: true));
          return;
        }
        final routine = training.gymRoutine(routineId);
        emit(
          GymRoutineState(
            isLoading: false,
            routine: routine,
            exercises: training.exercises,
            completedPositions: state.completedPositions,
          ),
        );
        if (routine != null) deps.service.rememberGymRoutine(user.uid, routine);
      }
      await _refresh(emit);
    });
    on<GymResetClicked>((_, emit) {
      if (state.canReset) emit(state.copyWith(showResetConfirmation: true));
    });
    on<GymResetDismissed>((_, emit) => emit(state.copyWith(showResetConfirmation: false)));
    on<GymResetConfirmed>((_, emit) {
      // The work is not saved: the session is discarded without an entry
      deps.service.resetGymRoutine(user.uid, routineId);
      emit(state.copyWith(showResetConfirmation: false, completedPositions: const {}));
    });
    on<GymCompletedDismissed>((_, emit) {
      deps.service.clearGymProgress(user.uid, routineId);
      emit(state.copyWith(showRoutineCompleted: false, completedPositions: const {}, clearCompletedEntry: true));
    });
  }

  final WebUser user;
  final int routineId;
  final ContentRepository content;
  final TrainingDeps deps;

  Future<void> _refresh(Emitter<GymRoutineState> emit) async {
    final routine = state.routine;
    if (routine == null) return;
    emit(state.copyWith(completedPositions: deps.service.gymCompletedPositions(user.uid, routineId)));
    if (state.isFullyCompleted && !state.showRoutineCompleted) {
      emit(state.copyWith(showRoutineCompleted: true, showResetConfirmation: false));
      // Records only while the session exists, and ends it: shown again, it records nothing
      final entry = await deps.service.completeGymRoutine(user, routine);
      if (entry != null) emit(state.copyWith(completedEntry: entry));
    }
  }
}

// ---------------------------------------------------------------------------
// Exercise (Android's GymExerciseViewModel)
// ---------------------------------------------------------------------------

/// Where the person is within the sets of one gym exercise (Android's `GymExercisePhase`).
sealed class GymPhase {
  const GymPhase(this.setIndex);

  final int setIndex;
}

final class GymPerformingReps extends GymPhase {
  const GymPerformingReps(super.setIndex);
}

final class GymReadyToStartTimed extends GymPhase {
  const GymReadyToStartTimed(super.setIndex);
}

final class GymGetReady extends GymPhase {
  const GymGetReady(super.setIndex, this.remaining);

  final int remaining;
}

final class GymPerformingTimed extends GymPhase {
  const GymPerformingTimed(super.setIndex, this.remaining, this.total);

  final int remaining;
  final int total;
}

/// Rest before set [setIndex] (the next one).
final class GymResting extends GymPhase {
  const GymResting(super.setIndex, this.remaining, this.total);

  final int remaining;
  final int total;
}

sealed class GymExerciseIntent {
  const GymExerciseIntent();
}

final class GymSetDone extends GymExerciseIntent {
  const GymSetDone();
}

final class GymStartTimed extends GymExerciseIntent {
  const GymStartTimed();
}

final class GymSkipRest extends GymExerciseIntent {
  const GymSkipRest();
}

final class GymBack extends GymExerciseIntent {
  const GymBack();
}

final class GymConfirmExit extends GymExerciseIntent {
  const GymConfirmExit();
}

final class GymDismissExit extends GymExerciseIntent {
  const GymDismissExit();
}

final class _GymPhaseChanged extends GymExerciseIntent {
  const _GymPhaseChanged(this.phase);

  final GymPhase phase;
}

final class _GymStep extends GymExerciseIntent {
  const _GymStep(this.step);

  final FutureOr<void> Function(Emitter<GymExerciseState> emit) step;
}

class GymExerciseState {
  const GymExerciseState({
    required this.routine,
    required this.slot,
    required this.exercise,
    required this.phase,
    this.showExitConfirmation = false,
    this.finished = false,
  });

  final GymRoutine routine;
  final GymSlot slot;
  final CatalogExercise? exercise;
  final GymPhase phase;
  final bool showExitConfirmation;
  final bool finished;

  int get exerciseNumber => routine.exercises.indexOf(slot) + 1;

  int get totalExercises => routine.exercises.length;

  int get totalSets => slot.sets;

  int get completedSets => phase.setIndex;

  GymExerciseState copyWith({GymPhase? phase, bool? showExitConfirmation, bool? finished}) => GymExerciseState(
    routine: routine,
    slot: slot,
    exercise: exercise,
    phase: phase ?? this.phase,
    showExitConfirmation: showExitConfirmation ?? this.showExitConfirmation,
    finished: finished ?? this.finished,
  );
}

sealed class GymExerciseEffect {
  const GymExerciseEffect();
}

final class GymNavigateBack extends GymExerciseEffect {
  const GymNavigateBack();
}

/// Every set of one slot: REPS → "Set done"; TIME → "Start", 5-s get-ready, the timer. Rest between
/// sets only (when `rest` > 0). Every set extends the routine's session; the last one is noted
/// before the exercise is marked done, then sound + vibration and back to the routine.
class GymExerciseBloc extends MviBloc<GymExerciseIntent, GymExerciseState, GymExerciseEffect> {
  GymExerciseBloc({
    required this.user,
    required GymRoutine routine,
    required GymSlot slot,
    required CatalogExercise? exercise,
    required this.deps,
  }) : _countdown = Countdown(deps.scheduler, deps.monotonic),
       _sessions = deps.service.gymSessions(user.uid),
       super(GymExerciseState(routine: routine, slot: slot, exercise: exercise, phase: _firstPhase(slot, 0))) {
    deps.service.rememberGymRoutine(user.uid, routine);
    on<_GymPhaseChanged>((i, emit) => emit(state.copyWith(phase: i.phase)));
    on<_GymStep>((i, emit) async => i.step(emit));
    on<GymSetDone>((_, emit) async {
      deps.feedback.prepare();
      if (state.phase is GymPerformingReps) await _finishSet(emit, state.phase.setIndex);
    });
    on<GymStartTimed>((_, emit) {
      deps.feedback.prepare();
      if (state.phase is GymReadyToStartTimed) _startGetReady(state.phase.setIndex);
    });
    on<GymSkipRest>((_, emit) {
      if (state.phase is GymResting) _startSet(emit, state.phase.setIndex);
    });
    on<GymBack>((_, emit) {
      if (state.finished) return;
      if (state.completedSets > 0) {
        emit(state.copyWith(showExitConfirmation: true));
      } else {
        _countdown.cancel();
        sendEffect(const GymNavigateBack());
      }
    });
    on<GymConfirmExit>((_, emit) {
      // Leaves without marking the exercise (the sets done stay in the session, as on Android)
      _countdown.cancel();
      emit(state.copyWith(showExitConfirmation: false));
      sendEffect(const GymNavigateBack());
    });
    on<GymDismissExit>((_, emit) => emit(state.copyWith(showExitConfirmation: false)));
    unawaited(deps.wakeLock.acquire());
  }

  final WebUser user;
  final TrainingDeps deps;
  final Countdown _countdown;
  final GymSessions _sessions;
  bool _finishing = false;

  GymRoutine get _routine => state.routine;

  static GymPhase _firstPhase(GymSlot slot, int setIndex) =>
      slot.type == ExerciseType.reps ? GymPerformingReps(setIndex) : GymReadyToStartTimed(setIndex);

  bool _isLastSet(int setIndex) => setIndex >= state.totalSets - 1;

  void _startSet(Emitter<GymExerciseState> emit, int setIndex) {
    _countdown.cancel();
    emit(state.copyWith(phase: _firstPhase(state.slot, setIndex)));
  }

  void _startGetReady(int setIndex) => _countdown.start(
    Countdown.getReadySeconds,
    onTick: (remaining) => add(_GymPhaseChanged(GymGetReady(setIndex, remaining))),
    onFinished: () => _startTimed(setIndex),
  );

  void _startTimed(int setIndex) {
    final duration = state.slot.reps[setIndex];
    _countdown.start(
      duration,
      onTick: (remaining) => add(_GymPhaseChanged(GymPerformingTimed(setIndex, remaining, duration))),
      onFinished: () {
        // The last set gets its feedback once, on completion
        if (!_isLastSet(setIndex)) _feedback();
        add(_GymStep((emit) => _finishSet(emit, setIndex)));
      },
    );
  }

  Future<void> _finishSet(Emitter<GymExerciseState> emit, int setIndex) async {
    if (_isLastSet(setIndex)) {
      await _completeExercise(emit);
      return;
    }
    _sessions.onSetCompleted(_routine.routineId);
    final next = setIndex + 1;
    final rest = state.slot.restSeconds;
    if (rest > 0) {
      _countdown.start(
        rest,
        onTick: (remaining) => add(_GymPhaseChanged(GymResting(next, remaining, rest))),
        onFinished: () {
          _feedback();
          add(_GymStep((emit) => _startSet(emit, next)));
        },
      );
    } else {
      _startSet(emit, next);
    }
  }

  Future<void> _completeExercise(Emitter<GymExerciseState> emit) async {
    if (_finishing) return;
    _finishing = true;
    _countdown.cancel();
    // Before the mark: completing the routine reads the session's last set
    _sessions.onSetCompleted(_routine.routineId);
    deps.service.markGymCompleted(user.uid, _routine.routineId, state.slot.position);
    emit(state.copyWith(finished: true));
    _feedback();
    sendEffect(const GymNavigateBack());
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
