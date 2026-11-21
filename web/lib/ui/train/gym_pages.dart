import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/content_repository.dart';
import '../../data/repositories.dart';
import '../../domain/daily.dart';
import '../../domain/gym.dart';
import '../../domain/workout_names.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import '../workout_name_text.dart';
import 'gym_blocs.dart';
import 'train_widgets.dart';

/// Android's `exerciseSummary` labels (the `gym_summary_*` strings).
ExerciseSummaryLabels gymSummaryLabels(AppLocalizations l10n) => ExerciseSummaryLabels(
  sets: l10n.gymSummarySets,
  reps: l10n.gymSummaryReps,
  time: l10n.gymSummaryTime,
  seconds: l10n.gymSummarySeconds,
  join: l10n.gymSummary,
);

/// The routine's name in the reader's language (through the workout-name resolver's rendering).
String gymRoutineName(GymRoutine routine, AppLocalizations l10n) =>
    GymWorkoutName(routine.routineName, routine.routineNameEs).text(l10n);

String gymExerciseName(CatalogExercise? exercise, AppLocalizations l10n) =>
    exercise == null ? l10n.unknownExercise : localizedName(exercise.name, exercise.nameEs, languageOf(l10n));

class _GymFrame extends StatelessWidget {
  const _GymFrame({required this.title, required this.onBack, required this.children, this.actions = const []});

  final String title;
  final VoidCallback onBack;
  final List<Widget> children;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(tooltip: l10n.back, icon: const Icon(Icons.arrow_back), onPressed: onBack),
        title: Text(title),
        actions: actions,
      ),
      body: PageBody(children: children),
    );
  }
}

// ---------------------------------------------------------------------------
// Selection
// ---------------------------------------------------------------------------

class GymSelectionPage extends StatelessWidget {
  const GymSelectionPage({super.key});

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final user = context.read<WebUserHolder>().user;
    return BlocProvider(
      create: (_) => GymSelectionBloc(user: user, content: services.content, deps: services.training),
      child: EffectListener<GymSelectionBloc, GymSessionSaved>(
        onEffect: (context, effect) => showSessionSaved(context, effect.durationSeconds),
        child: Builder(
          builder: (context) => VisibilityScope(
            onVisible: () => context.read<GymSelectionBloc>().add(const GymSelectionVisible()),
            onHidden: () {},
            child: BlocBuilder<GymSelectionBloc, GymSelectionState>(
              builder: (context, state) => GymSelectionContent(
                state: state,
                onIntent: context.read<GymSelectionBloc>().add,
                onBack: () => backToTrain(context),
                onStart: (routine) => context.go(Routes.gymRoutine(routine.routineId)),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class GymSelectionContent extends StatelessWidget {
  const GymSelectionContent({
    required this.state,
    required this.onIntent,
    required this.onBack,
    required this.onStart,
    super.key,
  });

  final GymSelectionState state;
  final void Function(GymSelectionIntent) onIntent;
  final VoidCallback onBack;
  final void Function(GymRoutine routine) onStart;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return _GymFrame(
      title: l10n.gymTitle,
      onBack: onBack,
      children: [
        InfoNote(l10n.gymBrowserNote),
        const SizedBox(height: 16),
        if (state.isLoading)
          const LoadingView()
        else if (state.contentMissing)
          ErrorView(error: DataError.failed, onRetry: () => onIntent(const GymSelectionRetry()))
        else if (state.routines.isEmpty)
          EmptyView(text: l10n.gymEmptyCatalog)
        else
          for (final routine in state.routines)
            _GymRoutineCard(
              routine: routine,
              exercises: state.exercises,
              expanded: state.expandedRoutineIds.contains(routine.routineId),
              onToggle: () => onIntent(GymToggleExpanded(routine.routineId)),
              onStart: () => onStart(routine),
            ),
      ],
    );
  }
}

class _GymRoutineCard extends StatelessWidget {
  const _GymRoutineCard({
    required this.routine,
    required this.exercises,
    required this.expanded,
    required this.onToggle,
    required this.onStart,
  });

  final GymRoutine routine;
  final Map<int, CatalogExercise> exercises;
  final bool expanded;
  final VoidCallback onToggle;
  final VoidCallback onStart;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final labels = gymSummaryLabels(l10n);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(header: true, child: Text(gymRoutineName(routine, l10n), style: theme.textTheme.titleMedium)),
            Text(l10n.trainExercisesCount(routine.exercises.length)),
            const SizedBox(height: 4),
            // The toggle row: 48 px tall, a button with a show / hide hint and its state
            Semantics(
              container: true,
              button: true,
              onTap: onToggle,
              onTapHint: expanded ? l10n.gymHideExercises : l10n.gymShowExercises,
              value: expanded ? l10n.gymStateExpanded : l10n.gymStateCollapsed,
              excludeSemantics: true,
              label: expanded ? l10n.gymHideExercises : l10n.gymShowExercises,
              child: InkWell(
                onTap: onToggle,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 48),
                  child: Row(
                    children: [
                      Expanded(child: Text(expanded ? l10n.gymHideExercises : l10n.gymShowExercises)),
                      Icon(expanded ? Icons.expand_less : Icons.expand_more),
                    ],
                  ),
                ),
              ),
            ),
            if (expanded) ...[
              if (routine.exercises.isEmpty)
                Text(l10n.gymRoutineEmpty)
              else
                for (final slot in routine.exercises)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(gymExerciseName(exercises[slot.exerciseId], l10n), style: theme.textTheme.bodyLarge),
                        Text(
                          exerciseSummary(slot.type, slot.reps, labels),
                          style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                        ),
                      ],
                    ),
                  ),
              const SizedBox(height: 8),
            ],
            WideButton(label: l10n.gymStartRoutine, onPressed: onStart),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Routine
// ---------------------------------------------------------------------------

class GymRoutinePage extends StatelessWidget {
  const GymRoutinePage({required this.routineId, super.key});

  final int routineId;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final user = context.read<WebUserHolder>().user;
    return BlocProvider(
      create: (_) =>
          GymRoutineBloc(user: user, routineId: routineId, content: services.content, deps: services.training),
      child: EffectListener<GymRoutineBloc, GymSessionSaved>(
        onEffect: (context, effect) => showSessionSaved(context, effect.durationSeconds),
        child: Builder(
          builder: (context) => VisibilityScope(
            onVisible: () => context.read<GymRoutineBloc>().add(const GymRoutineVisible()),
            onHidden: () {},
            child: BlocConsumer<GymRoutineBloc, GymRoutineState>(
              listenWhen: (a, b) => !a.showResetConfirmation && b.showResetConfirmation,
              listener: (context, state) async {
                final l10n = AppLocalizations.of(context);
                final bloc = context.read<GymRoutineBloc>();
                final confirmed = await showDialog<bool>(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: Text(l10n.gymResetProgressTitle),
                    content: Text(l10n.gymResetText),
                    actions: [
                      TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
                      TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.gymReset)),
                    ],
                  ),
                );
                bloc.add(confirmed == true ? const GymResetConfirmed() : const GymResetDismissed());
              },
              builder: (context, state) => GymRoutineContent(
                state: state,
                onIntent: context.read<GymRoutineBloc>().add,
                onBack: () => context.go(Routes.gym),
                onExercise: (slot) => context.go(Routes.gymExercise(routineId, slot.position)),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class GymRoutineContent extends StatelessWidget {
  const GymRoutineContent({
    required this.state,
    required this.onIntent,
    required this.onBack,
    required this.onExercise,
    super.key,
  });

  final GymRoutineState state;
  final void Function(GymRoutineIntent) onIntent;
  final VoidCallback onBack;
  final void Function(GymSlot slot) onExercise;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final routine = state.routine;
    if (state.isLoading) return _GymFrame(title: l10n.gymTitle, onBack: onBack, children: const [LoadingView()]);
    if (state.contentMissing) {
      return _GymFrame(
        title: l10n.gymTitle,
        onBack: onBack,
        children: [ErrorView(error: DataError.failed, onRetry: () => onIntent(const GymRoutineVisible()))],
      );
    }
    if (routine == null) {
      return _GymFrame(
        title: l10n.gymTitle,
        onBack: onBack,
        children: [EmptyView(text: l10n.gymRoutineNotFound)],
      );
    }
    final title = gymRoutineName(routine, l10n);
    if (state.showRoutineCompleted) {
      return _GymFrame(
        title: title,
        onBack: () => onIntent(const GymCompletedDismissed()),
        children: [
          CompletionView(
            title: l10n.gymCompletedTitle,
            text: l10n.gymCompletedText,
            entry: state.completedEntry,
            titleKey: WorkoutTitleKey.valueOrNull(GymTitleKey(routine.routineId)),
            doneLabel: l10n.gymFinish,
            onDone: () => onIntent(const GymCompletedDismissed()),
          ),
        ],
      );
    }
    final labels = gymSummaryLabels(l10n);
    return _GymFrame(
      title: title,
      onBack: onBack,
      actions: [
        IconButton(
          tooltip: l10n.gymResetAction,
          icon: const Icon(Icons.restart_alt),
          onPressed: state.canReset ? () => onIntent(const GymResetClicked()) : null,
        ),
      ],
      children: [
        if (routine.exercises.isEmpty)
          EmptyView(text: l10n.gymRoutineEmpty)
        else ...[
          Semantics(
            liveRegion: true,
            child: Text(
              l10n.gymCompletedCount(state.completedCount, routine.exercises.length),
              style: theme.textTheme.titleMedium,
            ),
          ),
          const SizedBox(height: 8),
          LinearProgressIndicator(value: state.completedCount / routine.exercises.length),
          const SizedBox(height: 16),
          for (final slot in routine.exercises)
            _GymSlotCard(
              name: gymExerciseName(state.exercises[slot.exerciseId], l10n),
              summary: exerciseSummary(slot.type, slot.reps, labels),
              type: slot.type,
              completed: state.completedPositions.contains(slot.position),
              onTap: () => onExercise(slot),
            ),
          const SizedBox(height: 16),
          InfoNote(l10n.gymBrowserNote),
        ],
      ],
    );
  }
}

class _GymSlotCard extends StatelessWidget {
  const _GymSlotCard({
    required this.name,
    required this.summary,
    required this.type,
    required this.completed,
    required this.onTap,
  });

  final String name;
  final String summary;
  final ExerciseType type;
  final bool completed;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final scheme = Theme.of(context).colorScheme;
    return Card(
      child: Semantics(
        value: completed ? l10n.gymStateCompleted : l10n.gymStateNotCompleted,
        child: ListTile(
          minTileHeight: 56,
          leading: ExcludeSemantics(child: Icon(type == ExerciseType.time ? Icons.timer_outlined : Icons.repeat)),
          title: Text(name),
          subtitle: Text(summary),
          // Decorative: the state is in the semantics value
          trailing: ExcludeSemantics(
            child: Icon(
              completed ? Icons.check_circle : Icons.radio_button_unchecked,
              color: completed ? scheme.primary : scheme.outline,
            ),
          ),
          onTap: onTap,
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Exercise
// ---------------------------------------------------------------------------

class GymExercisePage extends StatefulWidget {
  const GymExercisePage({required this.routineId, required this.position, super.key});

  final int routineId;
  final int position;

  @override
  State<GymExercisePage> createState() => _GymExercisePageState();
}

class _GymExercisePageState extends State<GymExercisePage> {
  late final Future<TrainingContent?> _content = context.read<AppServices>().content.training();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final back = Routes.gymRoutine(widget.routineId);
    return FutureBuilder<TrainingContent?>(
      future: _content,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return _GymFrame(title: l10n.gymTitle, onBack: () => context.go(back), children: const [LoadingView()]);
        }
        final routine = snapshot.data?.gymRoutine(widget.routineId);
        final slot = routine?.exercises.where((s) => s.position == widget.position && s.sets > 0).firstOrNull;
        if (routine == null || slot == null) {
          return _GymFrame(
            title: l10n.gymTitle,
            onBack: () => context.go(back),
            children: [EmptyView(text: l10n.gymExerciseNotFound)],
          );
        }
        final services = context.read<AppServices>();
        return BlocProvider(
          create: (_) => GymExerciseBloc(
            user: context.read<WebUserHolder>().user,
            routine: routine,
            slot: slot,
            exercise: snapshot.data?.exercises[slot.exerciseId],
            deps: services.training,
          ),
          child: EffectListener<GymExerciseBloc, GymExerciseEffect>(
            onEffect: (context, effect) => switch (effect) {
              GymNavigateBack() => context.go(back),
            },
            child: BlocConsumer<GymExerciseBloc, GymExerciseState>(
              listenWhen: (a, b) => !a.showExitConfirmation && b.showExitConfirmation,
              listener: (context, state) async {
                final bloc = context.read<GymExerciseBloc>();
                final leave = await showDialog<bool>(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: Text(l10n.gymExitTitle),
                    content: Text(l10n.gymExitText(state.completedSets, state.totalSets)),
                    actions: [
                      TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
                      TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.gymExitConfirm)),
                    ],
                  ),
                );
                bloc.add(leave == true ? const GymConfirmExit() : const GymDismissExit());
              },
              builder: (context, state) =>
                  GymExerciseContent(state: state, onIntent: context.read<GymExerciseBloc>().add),
            ),
          ),
        );
      },
    );
  }
}

class GymExerciseContent extends StatelessWidget {
  const GymExerciseContent({required this.state, required this.onIntent, super.key});

  final GymExerciseState state;
  final void Function(GymExerciseIntent) onIntent;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final slot = state.slot;
    final phase = state.phase;
    final setLine = l10n.gymSetOf(phase.setIndex + 1, state.totalSets);
    final List<Widget> controls = switch (phase) {
      GymPerformingReps() => [
        Text(setLine, textAlign: TextAlign.center),
        Semantics(
          liveRegion: true,
          child: Text(
            l10n.workoutReps(slot.reps[phase.setIndex]),
            style: theme.textTheme.displaySmall,
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: 24),
        WideButton(label: l10n.gymSetDone, onPressed: state.finished ? null : () => onIntent(const GymSetDone())),
      ],
      GymReadyToStartTimed() => [
        Text(setLine, textAlign: TextAlign.center),
        TimerText(slot.reps[phase.setIndex]),
        const SizedBox(height: 24),
        WideButton(label: l10n.gymStart, onPressed: () => onIntent(const GymStartTimed())),
      ],
      GymGetReady(:final remaining) => [
        Text(l10n.gymGetReady, style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
        TimerText(remaining),
      ],
      GymPerformingTimed(:final remaining) => [Text(setLine, textAlign: TextAlign.center), TimerText(remaining)],
      GymResting(:final remaining, :final setIndex) => [
        Text(l10n.gymRest, style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
        TimerText(remaining),
        Text(l10n.gymNextSet(setIndex + 1, state.totalSets), textAlign: TextAlign.center),
        const SizedBox(height: 24),
        WideButton(label: l10n.gymSkipRest, filled: false, onPressed: () => onIntent(const GymSkipRest())),
      ],
    };
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) onIntent(const GymBack());
      },
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            tooltip: l10n.back,
            icon: const Icon(Icons.arrow_back),
            onPressed: () => onIntent(const GymBack()),
          ),
          title: Text(gymRoutineName(state.routine, l10n)),
        ),
        body: PageBody(
          children: [
            Text(l10n.programExerciseOf(state.exerciseNumber, state.totalExercises), textAlign: TextAlign.center),
            Semantics(
              header: true,
              child: Text(
                gymExerciseName(state.exercise, l10n),
                style: theme.textTheme.headlineSmall,
                textAlign: TextAlign.center,
              ),
            ),
            const SizedBox(height: 16),
            Center(
              child: ExerciseVisual(
                kind: phase is GymResting
                    ? ExerciseVisualKind.rest
                    : (slot.type == ExerciseType.time ? ExerciseVisualKind.time : ExerciseVisualKind.reps),
                animation: state.exercise?.animation,
                name: phase is GymResting ? l10n.gymRest : gymExerciseName(state.exercise, l10n),
              ),
            ),
            const SizedBox(height: 24),
            ...controls,
          ],
        ),
      ),
    );
  }
}
