import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/repositories.dart';
import '../../domain/calendar_dates.dart';
import '../../domain/weekly_progress.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import '../train/train_widgets.dart';

sealed class WeeklyGoalIntent {
  const WeeklyGoalIntent();
}

final class WeeklyGoalLoad extends WeeklyGoalIntent {
  const WeeklyGoalLoad();
}

final class WeeklyGoalStep extends WeeklyGoalIntent {
  const WeeklyGoalStep(this.delta);

  final int delta;
}

final class WeeklyGoalSave extends WeeklyGoalIntent {
  const WeeklyGoalSave();
}

class WeeklyGoalState {
  const WeeklyGoalState({this.isLoading = true, this.workouts = defaultWorkouts});

  /// Android's `WeeklyGoalState.DEFAULT_WORKOUTS` without a goal.
  static const int defaultWorkouts = 3;
  static const int min = 1;
  static const int max = 14;

  final bool isLoading;
  final int workouts;

  bool get canDecrement => workouts > min;

  bool get canIncrement => workouts < max;
}

sealed class WeeklyGoalEffect {
  const WeeklyGoalEffect();
}

final class WeeklyGoalSaved extends WeeklyGoalEffect {
  const WeeklyGoalSaved();
}

/// The weekly goal (Android's `WeeklyGoalViewModel`): starts from the goal in force this week (3
/// without one); 1–14; saving writes `weeklyGoals/{this Monday}` (the natural id) and my summary.
class WeeklyGoalBloc extends MviBloc<WeeklyGoalIntent, WeeklyGoalState, WeeklyGoalEffect> {
  WeeklyGoalBloc({required this.user, required this.deps, required this.dates}) : super(const WeeklyGoalState()) {
    on<WeeklyGoalLoad>((_, emit) async {
      var workouts = WeeklyGoalState.defaultWorkouts;
      try {
        final goals = await deps.service.training.goals(user.uid);
        workouts = WeeklyProgressCalculator.goalForWeek(goals, CalendarDates.mondayOf(dates.today())) ?? workouts;
      } on DataException {
        // Offline without a cached copy: the default
      }
      emit(WeeklyGoalState(isLoading: false, workouts: workouts));
    });
    on<WeeklyGoalStep>((i, emit) {
      final next = (state.workouts + i.delta).clamp(WeeklyGoalState.min, WeeklyGoalState.max);
      emit(WeeklyGoalState(isLoading: false, workouts: next));
    });
    on<WeeklyGoalSave>((_, emit) async {
      await deps.service.setGoal(user, state.workouts);
      sendEffect(const WeeklyGoalSaved());
    });
  }

  final WebUser user;
  final TrainingDeps deps;
  final LocalDates dates;
}

class WeeklyGoalPage extends StatelessWidget {
  const WeeklyGoalPage({this.backTo = Routes.week, this.savedTo, super.key});

  /// Where back and Save return: My week, or the Train hub when opened from it.
  final String backTo;

  /// Where Save continues instead (creating or joining a group, which needs a goal).
  final String? savedTo;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    return BlocProvider(
      create: (_) =>
          WeeklyGoalBloc(user: context.read<WebUserHolder>().user, deps: services.training, dates: services.dates)
            ..add(const WeeklyGoalLoad()),
      child: EffectListener<WeeklyGoalBloc, WeeklyGoalEffect>(
        onEffect: (context, effect) => switch (effect) {
          WeeklyGoalSaved() => context.go(savedTo ?? backTo),
        },
        child: BlocBuilder<WeeklyGoalBloc, WeeklyGoalState>(
          builder: (context, state) => WeeklyGoalContent(
            state: state,
            onIntent: context.read<WeeklyGoalBloc>().add,
            onBack: () => context.go(backTo),
          ),
        ),
      ),
    );
  }
}

class WeeklyGoalContent extends StatelessWidget {
  const WeeklyGoalContent({required this.state, required this.onIntent, required this.onBack, super.key});

  final WeeklyGoalState state;
  final void Function(WeeklyGoalIntent) onIntent;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(tooltip: l10n.back, icon: const Icon(Icons.arrow_back), onPressed: onBack),
        title: Text(l10n.weeklyGoalTitle),
      ),
      body: PageBody(
        children: [
          if (state.isLoading)
            const LoadingView()
          else ...[
            Text(l10n.weeklyGoalQuestion, style: theme.textTheme.titleMedium, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                IconButton.filledTonal(
                  iconSize: 32,
                  tooltip: l10n.weeklyGoalFewer,
                  onPressed: state.canDecrement ? () => onIntent(const WeeklyGoalStep(-1)) : null,
                  icon: const Icon(Icons.remove),
                ),
                const SizedBox(width: 16),
                Flexible(
                  child: Semantics(
                    liveRegion: true,
                    child: Text(
                      l10n.weeklyGoalWorkoutsPerWeek(state.workouts),
                      style: theme.textTheme.headlineSmall,
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                IconButton.filledTonal(
                  iconSize: 32,
                  tooltip: l10n.weeklyGoalMore,
                  onPressed: state.canIncrement ? () => onIntent(const WeeklyGoalStep(1)) : null,
                  icon: const Icon(Icons.add),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(l10n.weekCountingRule(minCountedDurationSeconds ~/ 60)),
            const SizedBox(height: 8),
            Text(l10n.weeklyGoalAppliesFromThisWeek, style: theme.textTheme.bodySmall),
            const SizedBox(height: 24),
            WideButton(label: l10n.weeklyGoalSave, onPressed: () => onIntent(const WeeklyGoalSave())),
          ],
        ],
      ),
    );
  }
}
