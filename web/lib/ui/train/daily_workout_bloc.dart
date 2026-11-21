import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/content_repository.dart';
import '../../data/repositories.dart';
import '../../domain/countdown.dart';
import '../../domain/daily.dart';
import '../../domain/daily_progress.dart';
import '../../domain/sessions.dart';
import '../../domain/training.dart';

// ---------- Intents (Android's WorkoutIntent) ----------

sealed class DailyWorkoutIntent {
  const DailyWorkoutIntent();
}

final class DailyWorkoutLoad extends DailyWorkoutIntent {
  const DailyWorkoutLoad();
}

final class DailyStartGetReady extends DailyWorkoutIntent {
  const DailyStartGetReady();
}

final class DailyCompleteReps extends DailyWorkoutIntent {
  const DailyCompleteReps();
}

/// Skip, or Next once a timed exercise is done.
final class DailySkipExercise extends DailyWorkoutIntent {
  const DailySkipExercise();
}

final class DailyBack extends DailyWorkoutIntent {
  const DailyBack();
}

/// "Start again from Day 1" after the last day, once confirmed: restart, then the day list.
final class DailyRestartDays extends DailyWorkoutIntent {
  const DailyRestartDays();
}

final class DailyVisibility extends DailyWorkoutIntent {
  const DailyVisibility(this.visible);

  final bool visible;
}

final class _DailyTick extends DailyWorkoutIntent {
  const _DailyTick(this.update);

  final DailyWorkoutState Function(DailyWorkoutState) update;
}

final class _DailyTimerFinished extends DailyWorkoutIntent {
  const _DailyTimerFinished();
}

final class _DailyReadyFinished extends DailyWorkoutIntent {
  const _DailyReadyFinished();
}

// ---------- State (Android's WorkoutState) ----------

class DailyWorkoutState {
  const DailyWorkoutState({
    this.isLoading = true,
    this.notFound = false,
    this.routine,
    this.exercises = const {},
    this.index = 0,
    this.timerRemaining = 0,
    this.timerTotal = 0,
    this.isTimerRunning = false,
    this.isReadyCountdown = false,
    this.readyRemaining = 0,
    this.isComplete = false,
    this.recorded,
    this.lockedDay,
    this.lastRoutineId,
    this.progress,
  });

  final bool isLoading;
  final bool notFound;
  final DailyRoutine? routine;
  final Map<int, CatalogExercise> exercises;
  final int index;
  final int timerRemaining;
  final int timerTotal;
  final bool isTimerRunning;
  final bool isReadyCountdown;
  final int readyRemaining;
  final bool isComplete;

  /// The entry recorded when the workout finished (the completion view shares it).
  final TrainingEntry? recorded;

  /// This day is locked (opened by its URL): the day that unlocks it ("Complete Day 2 to unlock").
  final int? lockedDay;

  /// The last daily routine of the content, and the progress once the finished day is saved.
  final int? lastRoutineId;
  final DailyProgress? progress;

  /// After the last day of a cycle: "Start again from Day 1".
  bool get offersStartAgain =>
      isComplete &&
      routine?.routineId == lastRoutineId &&
      progress != null &&
      DailyUnlockRules.isCycleComplete(progress!, lastRoutineId);

  int get total => routine?.exercises.length ?? 0;

  /// Null when finished, or on a locked day (nothing to do there).
  RoutineExercise? get current {
    final slots = routine?.exercises;
    return slots == null || index >= slots.length || isComplete || lockedDay != null ? null : slots[index];
  }

  CatalogExercise? get currentExercise => current == null ? null : exercises[current!.exerciseId];

  bool get isReps => current?.type == ExerciseType.reps;

  bool get isTime => current?.type == ExerciseType.time;

  /// Android's `activity`: a timer runs (Get Ready, a TIME exercise, a rest) / a REPS exercise is shown.
  ({bool timerRunning, bool repsOnScreen}) get activity {
    final inProgress = !isComplete && !isLoading && !notFound && lockedDay == null;
    return (
      timerRunning: inProgress && (isTimerRunning || isReadyCountdown),
      repsOnScreen: inProgress && current?.type == ExerciseType.reps,
    );
  }

  DailyWorkoutState copyWith({
    bool? isLoading,
    bool? notFound,
    DailyRoutine? routine,
    Map<int, CatalogExercise>? exercises,
    int? index,
    int? timerRemaining,
    int? timerTotal,
    bool? isTimerRunning,
    bool? isReadyCountdown,
    int? readyRemaining,
    bool? isComplete,
    TrainingEntry? recorded,
    int? lockedDay,
    int? lastRoutineId,
    DailyProgress? progress,
  }) => DailyWorkoutState(
    isLoading: isLoading ?? this.isLoading,
    notFound: notFound ?? this.notFound,
    routine: routine ?? this.routine,
    exercises: exercises ?? this.exercises,
    index: index ?? this.index,
    timerRemaining: timerRemaining ?? this.timerRemaining,
    timerTotal: timerTotal ?? this.timerTotal,
    isTimerRunning: isTimerRunning ?? this.isTimerRunning,
    isReadyCountdown: isReadyCountdown ?? this.isReadyCountdown,
    readyRemaining: readyRemaining ?? this.readyRemaining,
    isComplete: isComplete ?? this.isComplete,
    recorded: recorded ?? this.recorded,
    lockedDay: lockedDay ?? this.lockedDay,
    lastRoutineId: lastRoutineId ?? this.lastRoutineId,
    progress: progress ?? this.progress,
  );
}

// ---------- Effects ----------

sealed class DailyWorkoutEffect {
  const DailyWorkoutEffect();
}

final class DailyNavigateBack extends DailyWorkoutEffect {
  const DailyNavigateBack();
}

/// "Your unfinished workout was saved (…)" for an older session recorded now.
final class DailySessionSaved extends DailyWorkoutEffect {
  const DailySessionSaved(this.durationSeconds);

  final int durationSeconds;
}

/// The daily workout (Android's `WorkoutViewModel`): Get Ready (5 s), TIME exercises with a
/// countdown, REPS exercises, rest slots that start on their own, skip / next, completion. Its
/// active time is kept in a persisted session (localStorage) from the first action; the finished
/// workout is recorded once with it. A page reload resumes the same exercise and session.
class DailyWorkoutBloc extends MviBloc<DailyWorkoutIntent, DailyWorkoutState, DailyWorkoutEffect> {
  DailyWorkoutBloc({required this.routineId, required this.user, required this.content, required TrainingDeps deps})
    : _deps = deps,
      _tracker = deps.service.dailySessions(user.uid),
      super(const DailyWorkoutState()) {
    _countdown = Countdown(deps.scheduler, deps.monotonic);
    _session = ActiveWorkoutSession(deps.scheduler, _tracker, ActiveTimeMeter(deps.monotonic), dailyRefId(routineId));
    on<DailyWorkoutLoad>((_, emit) => _load(emit));
    on<DailyStartGetReady>((_, emit) {
      if (state.lockedDay != null) return;
      _userAction();
      _startReady(emit);
    });
    on<DailyCompleteReps>((_, emit) {
      if (state.lockedDay != null) return;
      _userAction();
      if (state.isReps) _next(emit);
    });
    on<DailySkipExercise>((_, emit) {
      if (state.lockedDay != null) return;
      _userAction();
      _next(emit);
    });
    on<DailyBack>((_, emit) {
      _countdown.cancel();
      _emit(emit, state.copyWith(isTimerRunning: false, isReadyCountdown: false, timerRemaining: 0));
      // Left unfinished: the timers stop and the screen is gone; recorded after 2 hours of inactivity
      _session.setVisible(false);
      unawaited(_deps.wakeLock.release());
      sendEffect(const DailyNavigateBack());
    });
    on<DailyRestartDays>((_, emit) async {
      try {
        await _deps.service.restartDailyDays(user.uid);
      } on Object catch (e) {
        debugPrint('Daily workout: restart failed (${e.runtimeType})');
      }
      sendEffect(const DailyNavigateBack());
    });
    on<DailyVisibility>((intent, _) {
      _session.setVisible(intent.visible);
      if (intent.visible && !state.isComplete && !state.isLoading && state.lockedDay == null) {
        unawaited(_deps.wakeLock.acquire());
      }
    });
    on<_DailyTick>((intent, emit) => _emit(emit, intent.update(state)));
    on<_DailyTimerFinished>((_, emit) {
      if (state.isTime) {
        _deps.feedback
          ..playCompletionSound()
          ..vibrate();
      }
      _next(emit);
    });
    on<_DailyReadyFinished>((_, emit) {
      _emit(emit, state.copyWith(isReadyCountdown: false, readyRemaining: 0));
      _startTimer(emit, state.current?.amount ?? 0);
    });
  }

  final int routineId;
  final WebUser user;
  final ContentRepository content;
  final TrainingDeps _deps;
  final ActiveSessionTracker _tracker;
  late final Countdown _countdown;
  late final ActiveWorkoutSession _session;
  bool _recording = false;

  static const String progressName = 'progress.daily';

  /// Every state change also tells the session what counts as active time.
  void _emit(Emitter<DailyWorkoutState> emit, DailyWorkoutState next) {
    emit(next);
    final activity = next.activity;
    _session
      ..setTimerRunning(activity.timerRunning)
      ..setActiveOnScreen(activity.repsOnScreen);
  }

  /// The first action stores the session (an older unfinished one is recorded and announced).
  void _userAction() {
    _deps.feedback.prepare();
    if (!_session.isStarted) {
      for (final closed in _session.start()) {
        sendEffect(DailySessionSaved(closed.durationSeconds));
      }
    }
    _session.onUserAction();
  }

  Future<void> _load(Emitter<DailyWorkoutState> emit) async {
    final content = await this.content.training();
    final routine = content?.dailyRoutine(routineId);
    if (routine == null || content == null) {
      _emit(emit, state.copyWith(isLoading: false, notFound: true));
      return;
    }
    final lastRoutineId = content.dailyRoutines.fold<int>(0, (last, r) => r.routineId > last ? r.routineId : last);
    // A locked day opened by its URL can't be done (an unreadable progress never blocks a workout)
    try {
      final progress = await _deps.service.dailyProgress(user.uid);
      if (!DailyUnlockRules.isUnlocked(routineId, progress)) {
        _emit(emit, state.copyWith(isLoading: false, routine: routine, lockedDay: routineId - 1));
        return;
      }
    } on Object catch (e) {
      debugPrint('Daily workout: progress unavailable (${e.runtimeType})');
    }
    _deps.service.rememberSessionTitle(
      user.uid,
      dailyRefId(routineId),
      dailyWorkoutTitle(routine.routineDay, routine.title),
    );
    // A reload of this workout: the same exercise, the same session (when still open)
    final saved = _deps.service.progress(user.uid, progressName);
    var index = 0;
    // An inactive session is recorded first (and announced), so only an open one resumes
    _tracker.closeInactive();
    for (final closed in _tracker.takeClosedSessions()) {
      sendEffect(DailySessionSaved(closed.durationSeconds));
    }
    if (saved != null && saved['routineId'] == routineId && saved['index'] is int) {
      if (_session.resume(_tracker.storedSession, _deps.wall())) index = saved['index']! as int;
    }
    if (index >= routine.exercises.length) index = 0;
    _emit(
      emit,
      state.copyWith(
        isLoading: false,
        routine: routine,
        exercises: content.exercises,
        index: index,
        lastRoutineId: lastRoutineId,
      ),
    );
    _loadExercise(emit);
    unawaited(_deps.wakeLock.acquire());
  }

  void _loadExercise(Emitter<DailyWorkoutState> emit) {
    _countdown.cancel();
    final slot = state.current;
    if (slot == null) return;
    _deps.service.saveProgress(user.uid, progressName, {'routineId': routineId, 'index': state.index});
    final amount = slot.type == ExerciseType.time ? slot.amount : 0;
    _emit(
      emit,
      state.copyWith(
        isTimerRunning: false,
        timerRemaining: amount,
        timerTotal: amount,
        isReadyCountdown: false,
        readyRemaining: 0,
      ),
    );
    // Rest slots have no "Get Ready": their countdown starts on load
    if (slot.isRest && slot.type == ExerciseType.time) _startTimer(emit, slot.amount);
  }

  void _next(Emitter<DailyWorkoutState> emit) {
    final nextIndex = state.index + 1;
    if (nextIndex >= state.total) {
      _finish(emit);
      return;
    }
    _countdown.cancel();
    _emit(emit, state.copyWith(index: nextIndex, isTimerRunning: false, isReadyCountdown: false));
    _loadExercise(emit);
  }

  void _startTimer(Emitter<DailyWorkoutState> emit, int seconds) {
    _emit(emit, state.copyWith(timerTotal: seconds, timerRemaining: seconds, isTimerRunning: true));
    _countdown.start(
      seconds,
      onTick: (remaining) => add(_DailyTick((s) => s.copyWith(timerRemaining: remaining))),
      onFinished: () {
        add(_DailyTick((s) => s.copyWith(isTimerRunning: false, timerRemaining: 0)));
        add(const _DailyTimerFinished());
      },
    );
  }

  void _startReady(Emitter<DailyWorkoutState> emit) {
    final slot = state.current;
    if (slot == null || state.currentExercise == null && !slot.isRest) return;
    if (slot.isRest) {
      if (!_countdown.isRunning) _startTimer(emit, slot.amount);
      return;
    }
    if (state.isReadyCountdown || state.isTimerRunning) return;
    _emit(emit, state.copyWith(isReadyCountdown: true, readyRemaining: Countdown.getReadySeconds));
    _countdown.start(
      Countdown.getReadySeconds,
      onTick: (remaining) => add(_DailyTick((s) => s.copyWith(readyRemaining: remaining))),
      onFinished: () => add(const _DailyReadyFinished()),
    );
  }

  /// Records the finished workout once, with its active time; the session ends first.
  void _finish(Emitter<DailyWorkoutState> emit) {
    if (_recording) return;
    _recording = true;
    _countdown.cancel();
    final routine = state.routine!;
    final seconds = _session.finish();
    _deps.service.clearProgress(user.uid, progressName);
    unawaited(_deps.wakeLock.release());
    final pending = _deps.service.recordWorkout(
      user: user,
      source: TrainingSource.daily,
      refId: dailyRefId(routineId),
      title: dailyWorkoutTitle(routine.routineDay, routine.title),
      durationSeconds: seconds,
    );
    emit(state.copyWith(isComplete: true, isTimerRunning: false, isReadyCountdown: false, timerRemaining: 0));
    pending.then((entry) => add(_DailyTick((s) => s.copyWith(recorded: entry)))).ignore();
    // The next day unlocks (a session closed for inactivity never gets here)
    _deps.service
        .finishDailyDay(user.uid, routineId)
        .then((progress) => add(_DailyTick((s) => s.copyWith(progress: progress))))
        .catchError((Object e) => debugPrint('Daily workout: progress not saved (${e.runtimeType})'))
        .ignore();
  }

  @override
  Future<void> close() {
    _countdown.cancel();
    _session.close();
    unawaited(_deps.wakeLock.release());
    return super.close();
  }
}
