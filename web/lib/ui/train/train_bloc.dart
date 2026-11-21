import 'package:flutter/foundation.dart';

import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/content_repository.dart';
import '../../data/repositories.dart';
import '../../domain/daily.dart';
import '../../domain/daily_progress.dart';
import 'daily_workout_bloc.dart';

sealed class TrainIntent {
  const TrainIntent();
}

final class TrainLoad extends TrainIntent {
  const TrainLoad();
}

/// The hub is shown (and on every return): abandoned sessions are closed lazily, as Android's Home
/// `OnStart`.
final class TrainVisible extends TrainIntent {
  const TrainVisible();
}

class TrainState {
  const TrainState({this.isLoading = true, this.contentMissing = false, this.availableDay, this.inProgressRoutine});

  final bool isLoading;
  final bool contentMissing;

  /// "Day X available" (Android Home's `currentDay`): the next unlocked day
  /// ([DailyUnlockRules.nextAvailableDay]); day 1 when the content or the progress can't be read
  /// (Android's fallback); null when there is no routine.
  final int? availableDay;

  /// The daily workout kept for a reload (its session still open), to continue it.
  final DailyRoutine? inProgressRoutine;

  TrainState copyWith({
    bool? isLoading,
    bool? contentMissing,
    int? availableDay,
    bool clearAvailableDay = false,
    DailyRoutine? inProgressRoutine,
    bool clearInProgress = false,
  }) => TrainState(
    isLoading: isLoading ?? this.isLoading,
    contentMissing: contentMissing ?? this.contentMissing,
    availableDay: clearAvailableDay ? null : (availableDay ?? this.availableDay),
    inProgressRoutine: clearInProgress ? null : (inProgressRoutine ?? this.inProgressRoutine),
  );
}

sealed class TrainEffect {
  const TrainEffect();
}

final class TrainSessionSaved extends TrainEffect {
  const TrainSessionSaved(this.durationSeconds);

  final int durationSeconds;
}

/// The training hub (Android's Home navigation): the available day, the workout in progress, and
/// the lazy close of abandoned sessions. Each entry has its own screen.
class TrainBloc extends MviBloc<TrainIntent, TrainState, TrainEffect> {
  TrainBloc({required this.user, required this.content, required this.deps}) : super(const TrainState()) {
    on<TrainLoad>((_, emit) async {
      emit(state.copyWith(isLoading: true));
      final training = await content.training();
      emit(
        availableDayOf(training, await _progress(), state.copyWith(isLoading: false, contentMissing: training == null)),
      );
      _refreshInProgress(emit, training);
    });
    on<TrainVisible>((_, emit) async {
      for (final closed in deps.service.closeInactiveSessions(user.uid)) {
        sendEffect(TrainSessionSaved(closed.durationSeconds));
      }
      final training = await content.training();
      // A day finished meanwhile (here or on the phone) moves the line
      if (!state.isLoading) emit(availableDayOf(training, await _progress(), state));
      _refreshInProgress(emit, training);
    });
  }

  final WebUser user;
  final ContentRepository content;
  final TrainingDeps deps;

  /// The next unlocked daily routine, as Android's `HomeViewModel.publishCurrentDay`.
  static TrainState availableDayOf(TrainingContent? training, DailyProgress? progress, TrainState state) {
    if (training == null) return state.copyWith(availableDay: 1);
    final ids = training.dailyRoutines.map((r) => r.routineId);
    if (ids.isEmpty) return state.copyWith(clearAvailableDay: true);
    if (progress == null) return state.copyWith(availableDay: 1);
    final day = DailyUnlockRules.nextAvailableDay(progress, ids.reduce((a, b) => a > b ? a : b));
    return day == null ? state.copyWith(clearAvailableDay: true) : state.copyWith(availableDay: day);
  }

  /// My daily progress; null when it can't be read (offline without a cached copy).
  Future<DailyProgress?> _progress() async {
    try {
      return await deps.service.dailyProgress(user.uid);
    } on Object catch (e) {
      debugPrint('Train: daily progress unavailable (${e.runtimeType})');
      return null;
    }
  }

  void _refreshInProgress(Emitter<TrainState> emit, TrainingContent? training) {
    final saved = deps.service.progress(user.uid, DailyWorkoutBloc.progressName);
    final stored = deps.service.dailySessions(user.uid).storedSession;
    final routineId = saved?['routineId'];
    final routine = routineId is int && stored?.key == dailyRefId(routineId) ? training?.dailyRoutine(routineId) : null;
    emit(routine == null ? state.copyWith(clearInProgress: true) : state.copyWith(inProgressRoutine: routine));
  }
}
