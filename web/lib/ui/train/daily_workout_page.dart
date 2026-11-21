import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../domain/daily.dart';
import '../../domain/workout_names.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import '../workout_name_text.dart';
import 'daily_selection_page.dart' show confirmRestartDays;
import 'daily_workout_bloc.dart';
import 'train_widgets.dart';

/// The daily workout, full screen (no navigation bar while training).
class DailyWorkoutPage extends StatelessWidget {
  const DailyWorkoutPage({required this.routineId, super.key});

  final int routineId;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final user = context.read<WebUserHolder>().user;
    return BlocProvider(
      create: (_) =>
          DailyWorkoutBloc(routineId: routineId, user: user, content: services.content, deps: services.training)
            ..add(const DailyWorkoutLoad()),
      child: EffectListener<DailyWorkoutBloc, DailyWorkoutEffect>(
        onEffect: (context, effect) => switch (effect) {
          DailyNavigateBack() => context.go(Routes.daily),
          DailySessionSaved(:final durationSeconds) => showSessionSaved(context, durationSeconds),
        },
        child: Builder(
          // The bloc is read now: onHidden also runs while this element is being removed
          builder: (context) => BlocVisibilityScope<DailyWorkoutBloc>(
            onVisible: (bloc) => bloc.add(const DailyVisibility(true)),
            onHidden: (bloc) => bloc.add(const DailyVisibility(false)),
            child: BlocBuilder<DailyWorkoutBloc, DailyWorkoutState>(
              builder: (context, state) =>
                  DailyWorkoutContent(state: state, onIntent: context.read<DailyWorkoutBloc>().add),
            ),
          ),
        ),
      ),
    );
  }
}

class DailyWorkoutContent extends StatelessWidget {
  const DailyWorkoutContent({required this.state, required this.onIntent, super.key});

  final DailyWorkoutState state;
  final void Function(DailyWorkoutIntent) onIntent;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final routine = state.routine;
    final title = routine == null
        ? l10n.workoutTitle
        : DailyWorkoutName(routine.routineId, title: routine.title, titleEs: routine.titleEs).text(l10n);
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          tooltip: l10n.back,
          icon: const Icon(Icons.arrow_back),
          onPressed: () => onIntent(const DailyBack()),
        ),
        title: Text(title),
      ),
      body: PageBody(children: [_body(context, l10n)]),
    );
  }

  Widget _body(BuildContext context, AppLocalizations l10n) {
    if (state.isLoading) return const LoadingView();
    if (state.notFound) return EmptyView(text: l10n.programWorkoutNotFound);
    final lockedDay = state.lockedDay;
    if (lockedDay != null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 24),
          const ExcludeSemantics(child: Icon(Icons.lock_outline, size: 48)),
          const SizedBox(height: 16),
          Text(l10n.dailyLockedHint(DailyWorkoutName(lockedDay).text(l10n)), textAlign: TextAlign.center),
          const SizedBox(height: 24),
          WideButton(label: l10n.back, onPressed: () => onIntent(const DailyBack())),
        ],
      );
    }
    if (state.isComplete) {
      return CompletionView(
        title: l10n.workoutComplete,
        text: l10n.workoutGreatJob,
        entry: state.recorded,
        onDone: () => onIntent(const DailyBack()),
        extra: state.offersStartAgain
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    l10n.dailyCycleComplete,
                    style: Theme.of(context).textTheme.titleMedium,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 12),
                  WideButton(
                    label: l10n.dailyStartAgain(const DailyWorkoutName(1).text(l10n)),
                    filled: false,
                    onPressed: () async {
                      if (await confirmRestartDays(context, const DailyWorkoutName(1))) {
                        onIntent(const DailyRestartDays());
                      }
                    },
                  ),
                ],
              )
            : null,
      );
    }
    final slot = state.current;
    if (slot == null) return const LoadingView();
    final theme = Theme.of(context);
    final exercise = state.currentExercise;
    final language = l10n.localeName.split('_').first;
    final name = exercise == null ? l10n.unknownExercise : localizedName(exercise.name, exercise.nameEs, language);
    final kind = slot.isRest
        ? ExerciseVisualKind.rest
        : (slot.type == ExerciseType.time ? ExerciseVisualKind.time : ExerciseVisualKind.reps);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.workoutExerciseProgress(state.index + 1, state.total),
          textAlign: TextAlign.center,
          style: theme.textTheme.labelLarge,
        ),
        const SizedBox(height: 8),
        Semantics(
          header: true,
          child: Text(
            slot.isRest ? l10n.workoutRest : name,
            style: theme.textTheme.headlineSmall,
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: 16),
        Center(
          // Only this exercise's GIF; the next one is fetched meanwhile
          child: PreloadExerciseAnimation(
            animation: state.index + 1 < state.total
                ? state.exercises[state.routine!.exercises[state.index + 1].exerciseId]?.animation
                : null,
            child: ExerciseVisual(
              kind: kind,
              animation: exercise?.animation,
              name: slot.isRest ? l10n.workoutRest : name,
            ),
          ),
        ),
        const SizedBox(height: 24),
        ..._controls(context, l10n, slot),
      ],
    );
  }

  List<Widget> _controls(BuildContext context, AppLocalizations l10n, RoutineExercise slot) {
    final theme = Theme.of(context);
    final skip = WideButton(
      label: l10n.workoutSkipExercise,
      filled: false,
      onPressed: () => onIntent(const DailySkipExercise()),
    );
    if (slot.type == ExerciseType.reps) {
      return [
        Semantics(
          liveRegion: true,
          child: Text(l10n.workoutReps(slot.amount), style: theme.textTheme.displaySmall, textAlign: TextAlign.center),
        ),
        const SizedBox(height: 24),
        WideButton(label: l10n.workoutCompleteReps, onPressed: () => onIntent(const DailyCompleteReps())),
        const SizedBox(height: 12),
        skip,
      ];
    }
    if (state.isReadyCountdown) {
      return [
        Text(l10n.workoutGetReadyBanner, style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
        TimerText(state.readyRemaining),
        const SizedBox(height: 24),
        skip,
      ];
    }
    if (state.isTimerRunning) {
      return [
        TimerText(state.timerRemaining),
        Text(l10n.workoutTimerRunning, textAlign: TextAlign.center),
        const SizedBox(height: 24),
        skip,
      ];
    }
    // A TIME exercise waiting for Get Ready (a rest starts on its own)
    return [
      TimerText(state.timerTotal),
      Text(l10n.workoutSeconds, textAlign: TextAlign.center),
      const SizedBox(height: 24),
      if (!slot.isRest) ...[
        WideButton(label: l10n.workoutGetReady, onPressed: () => onIntent(const DailyStartGetReady())),
        const SizedBox(height: 12),
      ],
      skip,
    ];
  }
}
