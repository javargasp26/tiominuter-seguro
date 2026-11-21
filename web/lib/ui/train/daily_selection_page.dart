import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../app/theme.dart';
import '../../core/mvi.dart';
import '../../data/content_repository.dart';
import '../../data/repositories.dart';
import '../../domain/daily.dart';
import '../../domain/daily_progress.dart';
import '../../domain/workout_names.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import '../workout_name_text.dart';
import 'train_widgets.dart';

sealed class DailySelectionIntent {
  const DailySelectionIntent();
}

/// Shown (and on every return): abandoned sessions are closed, the routines are read once and the
/// progress again (a day finished meanwhile, or on the phone).
final class DailySelectionVisible extends DailySelectionIntent {
  const DailySelectionVisible();
}

final class DailySelectionRetry extends DailySelectionIntent {
  const DailySelectionRetry();
}

/// "Restart days" (or "Start again from Day 1"), once confirmed.
final class DailySelectionRestart extends DailySelectionIntent {
  const DailySelectionRestart();
}

/// How a day shows in the list (Android's `DailyDayStatus`).
enum DailyDayStatus { completed, available, locked }

class DailySelectionState {
  const DailySelectionState({
    this.isLoading = true,
    this.contentMissing = false,
    this.progressFailed = false,
    this.routines = const [],
    this.progress,
  });

  final bool isLoading;
  final bool contentMissing;

  /// The progress couldn't be read (offline without a cached copy): Retry.
  final bool progressFailed;
  final List<DailyRoutine> routines;
  final DailyProgress? progress;

  int? get _lastRoutineId => routines.isEmpty ? null : routines.last.routineId;

  /// The day the hub calls available, highlighted ("Available today").
  int? get nextDay => progress == null ? null : DailyUnlockRules.nextAvailableDay(progress!, _lastRoutineId);

  bool get isCycleComplete => progress != null && DailyUnlockRules.isCycleComplete(progress!, _lastRoutineId);

  /// "Restart days" only once a day was finished.
  bool get canRestart => (progress?.lastCompletedDay ?? 0) > 0;

  DailyDayStatus statusOf(int day) {
    final current = progress;
    if (current == null) return DailyDayStatus.locked;
    if (DailyUnlockRules.isCompleted(day, current)) return DailyDayStatus.completed;
    if (DailyUnlockRules.isUnlocked(day, current)) return DailyDayStatus.available;
    return DailyDayStatus.locked;
  }

  /// The name of [day] (its routine's title when the content has one).
  DailyWorkoutName dayName(int day) {
    for (final routine in routines) {
      if (routine.routineId == day) {
        return DailyWorkoutName(day, title: routine.title, titleEs: routine.titleEs);
      }
    }
    return DailyWorkoutName(day);
  }
}

/// Android's `DailyRoutineSelectionViewModel`: the daily routines by id with their unlock state
/// (domain/daily_progress.dart), starting an unlocked day, and "Restart days".
class DailySelectionBloc extends MviBloc<DailySelectionIntent, DailySelectionState, int> {
  DailySelectionBloc({required this.user, required this.content, required this.deps})
    : super(const DailySelectionState()) {
    on<DailySelectionVisible>((_, emit) async {
      // The effect is a closed session's duration ("…was saved")
      for (final closed in deps.service.closeInactiveSessions(user.uid)) {
        sendEffect(closed.durationSeconds);
      }
      if (_loaded) {
        await _loadProgress(emit);
      } else {
        await _load(emit);
      }
    });
    on<DailySelectionRetry>((_, emit) => _load(emit));
    on<DailySelectionRestart>((_, emit) async {
      try {
        final progress = await deps.service.restartDailyDays(user.uid);
        emit(_copy(progress: progress));
      } on Object catch (e) {
        debugPrint('Daily selection: restart failed (${e.runtimeType})');
      }
    });
  }

  final WebUser user;
  final ContentRepository content;
  final TrainingDeps deps;
  bool _loaded = false;

  DailySelectionState _copy({DailyProgress? progress, bool? progressFailed}) => DailySelectionState(
    isLoading: false,
    routines: state.routines,
    progress: progress ?? state.progress,
    progressFailed: progressFailed ?? false,
  );

  Future<void> _load(Emitter<DailySelectionState> emit) async {
    emit(const DailySelectionState());
    final training = await content.training();
    if (training == null) {
      emit(const DailySelectionState(isLoading: false, contentMissing: true));
      return;
    }
    _loaded = true;
    final routines = [...training.dailyRoutines]..sort((a, b) => a.routineId.compareTo(b.routineId));
    emit(DailySelectionState(routines: routines));
    await _loadProgress(emit);
  }

  Future<void> _loadProgress(Emitter<DailySelectionState> emit) async {
    try {
      emit(_copy(progress: await deps.service.dailyProgress(user.uid)));
    } on Object catch (e) {
      debugPrint('Daily selection: progress unavailable (${e.runtimeType})');
      if (state.progress == null) emit(_copy(progressFailed: true));
    }
  }
}

/// "Restart days?" (Android's `RestartDaysDialog`): true to restart.
Future<bool> confirmRestartDays(BuildContext context, DailyWorkoutName firstDay) async {
  final l10n = AppLocalizations.of(context);
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(l10n.dailyRestartTitle),
      content: Text(l10n.dailyRestartMessage(firstDay.text(l10n))),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
        TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.dailyRestartConfirm)),
      ],
    ),
  );
  return confirmed == true;
}

class DailySelectionPage extends StatelessWidget {
  const DailySelectionPage({super.key});

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final user = context.read<WebUserHolder>().user;
    return BlocProvider(
      create: (_) => DailySelectionBloc(user: user, content: services.content, deps: services.training),
      child: EffectListener<DailySelectionBloc, int>(
        onEffect: showSessionSaved,
        child: Builder(
          builder: (context) => VisibilityScope(
            onVisible: () => context.read<DailySelectionBloc>().add(const DailySelectionVisible()),
            onHidden: () {},
            child: BlocBuilder<DailySelectionBloc, DailySelectionState>(
              builder: (context, state) => DailySelectionContent(
                state: state,
                onBack: () => backToTrain(context),
                onRetry: () => context.read<DailySelectionBloc>().add(const DailySelectionRetry()),
                onStart: (routine) => context.go(Routes.dailyWorkout(routine.routineId)),
                onRestart: () async {
                  final bloc = context.read<DailySelectionBloc>();
                  if (await confirmRestartDays(context, state.dayName(1))) bloc.add(const DailySelectionRestart());
                },
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class DailySelectionContent extends StatelessWidget {
  const DailySelectionContent({
    required this.state,
    required this.onBack,
    required this.onRetry,
    required this.onStart,
    required this.onRestart,
    super.key,
  });

  final DailySelectionState state;
  final VoidCallback onBack;
  final VoidCallback onRetry;
  final void Function(DailyRoutine routine) onStart;
  final VoidCallback onRestart;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final success = SuccessColors.of(context);
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(tooltip: l10n.back, icon: const Icon(Icons.arrow_back), onPressed: onBack),
        title: Text(l10n.dailySelectTitle),
        actions: [
          if (state.canRestart && !state.isLoading)
            TextButton(
              onPressed: onRestart,
              style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
              child: Text(l10n.dailyRestartAction),
            ),
        ],
      ),
      body: PageBody(
        children: [
          if (state.isLoading || (state.progress == null && !state.progressFailed && !state.contentMissing))
            const LoadingView()
          else if (state.contentMissing || state.progressFailed)
            ErrorView(error: DataError.failed, onRetry: onRetry)
          else if (state.routines.isEmpty)
            EmptyView(text: l10n.trainNoRoutines)
          else ...[
            if (state.isCycleComplete)
              Card(
                color: success.successContainer,
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Semantics(
                        header: true,
                        child: Text(
                          l10n.dailyCycleComplete,
                          style: theme.textTheme.titleMedium?.copyWith(color: success.onSuccessContainer),
                        ),
                      ),
                      const SizedBox(height: 12),
                      FilledButton(
                        onPressed: onRestart,
                        style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                        child: Text(l10n.dailyStartAgain(state.dayName(1).text(l10n))),
                      ),
                    ],
                  ),
                ),
              ),
            for (final routine in state.routines) _dayCard(context, l10n, routine),
          ],
        ],
      ),
    );
  }

  Widget _dayCard(BuildContext context, AppLocalizations l10n, DailyRoutine routine) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = state.statusOf(routine.routineId);
    final locked = status == DailyDayStatus.locked;
    final next = routine.routineId == state.nextDay && !state.isCycleComplete;
    final name = state.dayName(routine.routineId).text(l10n);
    final stateText = switch (status) {
      DailyDayStatus.completed => l10n.dailyStateCompleted,
      DailyDayStatus.available => l10n.dailyStateAvailable,
      DailyDayStatus.locked => l10n.dailyStateLocked,
    };
    final success = SuccessColors.of(context).success;
    return Opacity(
      opacity: locked ? 0.6 : 1,
      child: Card(
        // Its own nodes: the day as a header (with its state), then its Start button
        semanticContainer: false,
        color: next ? scheme.primaryContainer : null,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Semantics(
                      header: true,
                      value: stateText,
                      child: Text(name, style: theme.textTheme.titleLarge),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      l10n.trainExercisesCount(routine.exercises.length),
                      style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
                    ),
                    if (locked) ...[
                      const SizedBox(height: 4),
                      Text(
                        l10n.dailyLockedHint(state.dayName(routine.routineId - 1).text(l10n)),
                        style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
                      ),
                    ] else if (next) ...[
                      const SizedBox(height: 4),
                      Text(
                        l10n.dailyAvailableToday,
                        style: theme.textTheme.labelLarge?.copyWith(color: scheme.onPrimaryContainer),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 12),
              if (locked)
                ExcludeSemantics(child: Icon(Icons.lock_outline, color: scheme.onSurfaceVariant))
              else ...[
                if (status == DailyDayStatus.completed)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ExcludeSemantics(child: Icon(Icons.check_circle, color: success)),
                  ),
                Semantics(
                  // "Start Day 3", not three identical "Start" buttons
                  label: '${l10n.dailyStart}, $name',
                  excludeSemantics: true,
                  button: true,
                  onTap: () => onStart(routine),
                  child: next
                      ? FilledButton(
                          onPressed: () => onStart(routine),
                          style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                          child: Text(l10n.dailyStart),
                        )
                      : OutlinedButton(
                          onPressed: () => onStart(routine),
                          style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
                          child: Text(l10n.dailyStart),
                        ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
