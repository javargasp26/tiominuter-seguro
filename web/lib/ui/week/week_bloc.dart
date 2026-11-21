import 'dart:async';

import '../../core/mvi.dart';
import '../../data/repositories.dart';
import '../../domain/calendar_dates.dart';
import '../../domain/training.dart';
import '../../domain/weekly_progress.dart';

// ---------- Intents ----------

sealed class WeekIntent {
  const WeekIntent();
}

/// The page (and the browser tab) is visible: start the listeners.
final class WeekVisible extends WeekIntent {
  const WeekVisible();
}

/// Hidden: stop the listeners.
final class WeekHidden extends WeekIntent {
  const WeekHidden();
}

final class WeekRetry extends WeekIntent {
  const WeekRetry();
}

final class _WeekData extends WeekIntent {
  const _WeekData({this.entries, this.goals});

  final List<TrainingEntry>? entries;
  final List<WeeklyGoal>? goals;
}

final class _StreakLoaded extends WeekIntent {
  const _StreakLoaded(this.streak, this.generation);

  final int streak;
  final int generation;
}

final class _WeekFailed extends WeekIntent {
  const _WeekFailed(this.error);

  final DataError error;
}

// ---------- State ----------

class WeekState {
  const WeekState({
    this.isLoading = true,
    this.summary,
    this.sourcesPerDay = const {},
    this.weekDates = const [],
    this.streak,
    this.error,
  });

  final bool isLoading;

  /// The week of today (its streak is [streak], read from past weeks).
  final CurrentWeekSummary? summary;

  /// The distinct sources of the counted workouts of each day of this week.
  final Map<String, List<TrainingSource>> sourcesPerDay;
  final List<String> weekDates;

  /// Null while past weeks are still being read.
  final int? streak;
  final DataError? error;

  bool get hasGoal => summary?.progress.goal != null;
}

/// "My week": the weekly goal progress, the streak and this week's days, computed with the same
/// rules as Android's Home card (domain/weekly_progress.dart lists the Kotlin sources).
///
/// Reads while visible: a listener on this week's entries (`dateLocal >= Monday`) and one on the
/// weekly goals; the streak reads past weeks once per visible session, 8 weeks at a time, only as
/// far back as the streak goes.
class WeekBloc extends MviBloc<WeekIntent, WeekState, Never> {
  WeekBloc({required this._uid, required this._data, required this._dates}) : super(const WeekState()) {
    on<WeekVisible>((_, _) => _listen());
    on<WeekRetry>((_, emit) {
      emit(const WeekState());
      _listen();
    });
    on<WeekHidden>((_, _) => _stop());
    on<_WeekData>(_onData);
    on<_StreakLoaded>((intent, emit) {
      if (intent.generation == _generation) emit(_copy(streak: intent.streak));
    });
    on<_WeekFailed>((intent, emit) {
      emit(WeekState(isLoading: false, error: intent.error));
      _stop();
    });
  }

  final String _uid;
  final UserDataRepository _data;
  final LocalDates _dates;
  final List<StreamSubscription<Object?>> _subscriptions = [];
  List<TrainingEntry>? _entries;
  List<WeeklyGoal>? _goals;
  int _generation = 0;
  Map<String, int>? _pastPerDay;
  final Set<String> _loadedRanges = {};
  String? _pastWeekStart;

  void _listen() {
    _stop();
    final monday = _dates.currentWeekStart();
    // Past weeks are read once per week shown (a new week starts the cache again)
    if (_pastWeekStart != monday) {
      _pastPerDay = null;
      _loadedRanges.clear();
      _pastWeekStart = monday;
    }
    _subscriptions
      ..add(
        _data
            .watchEntriesFrom(_uid, monday)
            .listen(
              (entries) => add(_WeekData(entries: entries)),
              onError: (Object e) => add(_WeekFailed(e is DataException ? e.error : DataError.failed)),
            ),
      )
      ..add(
        _data
            .watchGoals(_uid)
            .listen(
              (goals) => add(_WeekData(goals: goals)),
              onError: (Object e) => add(_WeekFailed(e is DataException ? e.error : DataError.failed)),
            ),
      );
  }

  void _stop() {
    for (final subscription in _subscriptions) {
      subscription.cancel();
    }
    _subscriptions.clear();
  }

  void _onData(_WeekData intent, Emitter<WeekState> emit) {
    final goalsChanged = intent.goals != null;
    _entries = intent.entries ?? _entries;
    _goals = intent.goals ?? _goals;
    final entries = _entries;
    final goals = _goals;
    if (entries == null || goals == null) return;
    final today = _dates.today();
    final week = CalendarDates.weekDates(today);
    final thisWeek = entries.where((e) => week.contains(e.dateLocal)).toList();
    final perDay = countedWorkoutsPerDay(thisWeek);
    final progress = WeeklyProgressCalculator.progress(today, goals, perDay);
    final sources = countedSourcesPerDay(thisWeek);
    emit(
      WeekState(
        isLoading: false,
        summary: CurrentWeekSummary(today: today, progress: progress, streak: state.streak ?? 0),
        sourcesPerDay: sources,
        weekDates: week,
        streak: goalsChanged || state.streak == null ? null : state.streak,
      ),
    );
    _computeStreak(today, goals, perDay);
  }

  void _computeStreak(String today, List<WeeklyGoal> goals, Map<String, int> currentPerDay) {
    final generation = ++_generation;
    final cache = _pastPerDay ??= {};
    streakReadingBack(
      today: today,
      goals: goals,
      currentWeekPerDay: currentPerDay,
      loadPast: (from, until) async {
        final key = '$from/$until';
        if (_loadedRanges.contains(key)) return cache;
        final entries = await _data.entriesBetween(_uid, from, until);
        cache.addAll(countedWorkoutsPerDay(entries));
        _loadedRanges.add(key);
        return cache;
      },
    ).then(
      (streak) {
        if (!isClosed) add(_StreakLoaded(streak, generation));
      },
      onError: (Object e) {
        if (!isClosed) add(_WeekFailed(e is DataException ? e.error : DataError.failed));
      },
    );
  }

  WeekState _copy({int? streak}) => WeekState(
    isLoading: state.isLoading,
    summary: state.summary == null
        ? null
        : CurrentWeekSummary(today: state.summary!.today, progress: state.summary!.progress, streak: streak ?? 0),
    sourcesPerDay: state.sourcesPerDay,
    weekDates: state.weekDates,
    streak: streak,
    error: state.error,
  );

  @override
  Future<void> close() {
    _stop();
    return super.close();
  }
}
