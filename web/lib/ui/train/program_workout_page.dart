import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/content_repository.dart';
import '../../data/repositories.dart';
import '../../data/training_repositories.dart';
import '../../domain/program.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import 'cardio_blocs.dart';
import 'program_labels.dart';
import 'train_widgets.dart';

/// The program workout and what it needs from the content (exercise names), loaded once.
class LoadedWorkout {
  const LoadedWorkout({required this.row, required this.exercises});

  final ProgramWorkoutRow row;
  final Map<int, ProgramExercise> exercises;
}

/// Loads [programId]'s workout (week, day) through the service's cache, then builds [builder].
class ProgramWorkoutLoader extends StatefulWidget {
  const ProgramWorkoutLoader({
    required this.programId,
    required this.week,
    required this.dayIndex,
    required this.builder,
    super.key,
  });

  final String programId;
  final int week;
  final int dayIndex;
  final Widget Function(BuildContext context, LoadedWorkout workout) builder;

  @override
  State<ProgramWorkoutLoader> createState() => _ProgramWorkoutLoaderState();
}

class _ProgramWorkoutLoaderState extends State<ProgramWorkoutLoader> {
  late Future<LoadedWorkout?> _loaded = _load();

  Future<LoadedWorkout?> _load() async {
    final services = context.read<AppServices>();
    final uid = context.read<WebUserHolder>().user.uid;
    final detail = await services.training.service.programDetail(uid, widget.programId);
    final row = detail?.workout(widget.week, widget.dayIndex);
    if (row == null || row.workout == null) return null;
    final TrainingContent? content = await services.content.training();
    return LoadedWorkout(row: row, exercises: content?.programExercises ?? const {});
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return FutureBuilder<LoadedWorkout?>(
      future: _loaded,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return _Frame(
            title: l10n.programsTitle,
            onBack: () => context.go(Routes.program(widget.programId)),
            child: const LoadingView(),
          );
        }
        if (snapshot.hasError) {
          final error = snapshot.error;
          return _Frame(
            title: l10n.programsTitle,
            onBack: () => context.go(Routes.program(widget.programId)),
            child: ErrorView(
              error: error is DataException ? error.error : DataError.failed,
              onRetry: () => setState(() => _loaded = _load()),
            ),
          );
        }
        final loaded = snapshot.data;
        if (loaded == null) {
          return _Frame(
            title: l10n.programsTitle,
            onBack: () => context.go(Routes.program(widget.programId)),
            child: EmptyView(text: l10n.programWorkoutNotFound),
          );
        }
        return widget.builder(context, loaded);
      },
    );
  }
}

class _Frame extends StatelessWidget {
  const _Frame({required this.title, required this.onBack, required this.child, this.actions = const []});

  final String title;
  final VoidCallback onBack;
  final Widget child;
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
      body: PageBody(children: [child]),
    );
  }
}

class ProgramWorkoutPage extends StatelessWidget {
  const ProgramWorkoutPage({required this.programId, required this.week, required this.dayIndex, super.key});

  final String programId;
  final int week;
  final int dayIndex;

  @override
  Widget build(BuildContext context) => ProgramWorkoutLoader(
    programId: programId,
    week: week,
    dayIndex: dayIndex,
    builder: (context, loaded) {
      final workout = loaded.row.workout!;
      final l10n = AppLocalizations.of(context);
      void back() => context.go(Routes.program(programId));
      return switch (workout) {
        StrengthWorkout() => StrengthWorkoutView(
          programId: programId,
          week: week,
          dayIndex: dayIndex,
          workout: workout,
          exercises: loaded.exercises,
          onBack: back,
        ),
        TreadmillWorkout() => TreadmillView(
          programId: programId,
          week: week,
          dayIndex: dayIndex,
          treadmill: workout,
          onBack: back,
        ),
        HomeCircuitWorkout() => CircuitView(
          programId: programId,
          week: week,
          dayIndex: dayIndex,
          circuit: workout,
          exercises: loaded.exercises,
          onBack: back,
        ),
        OutdoorRunWorkout() => _Frame(
          title: workoutTitle(workout, l10n),
          onBack: back,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(workoutSummary(workout, l10n), style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 16),
              InfoNote(l10n.programOutdoorOnAndroid),
            ],
          ),
        ),
      };
    },
  );
}

// ---------------------------------------------------------------------------
// Strength: the list of exercises (Android's ProgramStrengthScreen)
// ---------------------------------------------------------------------------

class StrengthWorkoutView extends StatefulWidget {
  const StrengthWorkoutView({
    required this.programId,
    required this.week,
    required this.dayIndex,
    required this.workout,
    required this.exercises,
    required this.onBack,
    super.key,
  });

  final String programId;
  final int week;
  final int dayIndex;
  final StrengthWorkout workout;
  final Map<int, ProgramExercise> exercises;
  final VoidCallback onBack;

  @override
  State<StrengthWorkoutView> createState() => _StrengthWorkoutViewState();
}

class _StrengthWorkoutViewState extends State<StrengthWorkoutView> {
  late final _service = context.read<AppServices>().training.service;
  late final _user = context.read<WebUserHolder>().user;
  late final completed = _service.takeJustCompleted(widget.programId, widget.week, widget.dayIndex);
  bool _showCompletion = false;

  @override
  void initState() {
    super.initState();
    _showCompletion = completed != null;
    _service.rememberSessionTitle(
      _user.uid,
      'program:${widget.programId}:${widget.week}:${widget.dayIndex}',
      _service.texts.programTitle(widget.workout),
      titleKey: 'program/${widget.workout.titleCode.code}',
    );
    for (final closed in _service.closeInactiveSessions(_user.uid)) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) showSessionSaved(context, closed.durationSeconds);
      });
    }
  }

  Future<void> _reset() async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.programResetTitle),
        content: Text(l10n.programResetText),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
          TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.programReset)),
        ],
      ),
    );
    if (confirmed == true) {
      setState(() => _service.resetStrengthWorkout(_user.uid, widget.programId, widget.week, widget.dayIndex));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final done = _service.completedSlots(_user.uid, widget.programId, widget.week, widget.dayIndex);
    final slots = widget.workout.slots;
    final language = languageOf(l10n);
    if (_showCompletion) {
      return _Frame(
        title: workoutTitle(widget.workout, l10n),
        onBack: widget.onBack,
        child: CompletionView(
          title: l10n.programWorkoutCompletedTitle,
          text: l10n.programWorkoutCompletedText,
          entry: completed,
          titleKey: 'program/${widget.workout.titleCode.code}',
          onDone: () => setState(() => _showCompletion = false),
        ),
      );
    }
    return _Frame(
      title: workoutTitle(widget.workout, l10n),
      onBack: widget.onBack,
      actions: [if (done.isNotEmpty) TextButton(onPressed: _reset, child: Text(l10n.programReset))],
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Semantics(
            liveRegion: true,
            child: Text(
              l10n.programExercisesCompleted(slots.where((s) => done.contains(s.position)).length, slots.length),
              style: theme.textTheme.titleMedium,
            ),
          ),
          const SizedBox(height: 12),
          for (final slot in slots)
            Card(
              child: ListTile(
                minTileHeight: 56,
                leading: ExerciseVisual(
                  kind: slot.type == ProgramSlotType.time ? ExerciseVisualKind.time : ExerciseVisualKind.reps,
                  animation: widget.exercises[slot.exerciseId]?.animation,
                  size: 40,
                  // A list: the icons only (one GIF per screen, on the exercise itself)
                  animate: false,
                ),
                title: Text(
                  widget.exercises[slot.exerciseId]?.displayName(spanish: language == 'es') ?? l10n.unknownExercise,
                ),
                subtitle: Text(
                  programExerciseSummary(
                    l10n,
                    isTime: slot.type == ProgramSlotType.time,
                    values: slot.reps,
                    perSide: slot.perSide,
                  ),
                ),
                trailing: Icon(
                  done.contains(slot.position) ? Icons.check_circle : Icons.radio_button_unchecked,
                  semanticLabel: done.contains(slot.position) ? l10n.programCompleted : l10n.programNotCompleted,
                  color: done.contains(slot.position) ? theme.colorScheme.primary : null,
                ),
                onTap: () =>
                    context.go(Routes.programExercise(widget.programId, widget.week, widget.dayIndex, slot.position)),
              ),
            ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Treadmill and circuit
// ---------------------------------------------------------------------------

Future<void> _confirmLeave(BuildContext context, void Function(CardioIntent) onIntent) async {
  final l10n = AppLocalizations.of(context);
  final leave = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(l10n.programLeaveWorkoutTitle),
      content: Text(l10n.programLeaveWorkoutText),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.programStay)),
        TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.programLeave)),
      ],
    ),
  );
  onIntent(leave == true ? const CardioConfirmExit() : const CardioDismissExit());
}

void _onCardioEffect(BuildContext context, CardioEffect effect, VoidCallback onBack) => switch (effect) {
  CardioNavigateBack() => onBack(),
  CardioSessionSaved(:final durationSeconds) => showSessionSaved(context, durationSeconds),
};

class TreadmillView extends StatelessWidget {
  const TreadmillView({
    required this.programId,
    required this.week,
    required this.dayIndex,
    required this.treadmill,
    required this.onBack,
    super.key,
  });

  final String programId;
  final int week;
  final int dayIndex;
  final TreadmillWorkout treadmill;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    return BlocProvider(
      create: (_) => TreadmillBloc(
        deps: services.training,
        user: context.read<WebUserHolder>().user,
        programId: programId,
        week: week,
        dayIndex: dayIndex,
        treadmill: treadmill,
      ),
      child: EffectListener<TreadmillBloc, CardioEffect>(
        onEffect: (context, effect) => _onCardioEffect(context, effect, onBack),
        child: Builder(
          // The bloc is read now: onHidden also runs while this element is being removed
          builder: (context) => BlocVisibilityScope<TreadmillBloc>(
            onVisible: (bloc) => bloc.add(const CardioVisibility(true)),
            onHidden: (bloc) => bloc.add(const CardioVisibility(false)),
            child: BlocConsumer<TreadmillBloc, TreadmillState>(
              listenWhen: (a, b) => !a.showExitConfirmation && b.showExitConfirmation,
              listener: (context, _) => _confirmLeave(context, context.read<TreadmillBloc>().add),
              builder: (context, state) => TreadmillContent(
                workout: treadmill,
                state: state,
                onIntent: context.read<TreadmillBloc>().add,
                onDone: onBack,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class TreadmillContent extends StatelessWidget {
  const TreadmillContent({
    required this.workout,
    required this.state,
    required this.onIntent,
    required this.onDone,
    super.key,
  });

  final TreadmillWorkout workout;
  final TreadmillState state;
  final void Function(CardioIntent) onIntent;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final language = languageOf(l10n);
    final segment = state.currentSegment;
    final next = state.nextSegment;
    return _Frame(
      title: workoutTitle(workout, l10n),
      onBack: () => onIntent(const CardioBack()),
      child: state.isFinished
          ? CompletionView(
              title: l10n.programWorkoutCompletedTitle,
              text: l10n.programWorkoutCompletedText,
              entry: state.recorded,
              titleKey: 'program/${workout.titleCode.code}',
              onDone: onDone,
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(l10n.programSegmentOf(state.currentIndex + 1, state.segments.length), textAlign: TextAlign.center),
                if (segment != null) ...[
                  const SizedBox(height: 8),
                  Semantics(
                    header: true,
                    child: Text(
                      segmentLabel(segment.label, l10n),
                      style: theme.textTheme.headlineSmall,
                      textAlign: TextAlign.center,
                    ),
                  ),
                  Text(
                    '${l10n.programSpeed(formatOneDecimal(segment.speedKmh, language))} · '
                    '${l10n.programIncline(formatOneDecimal(segment.inclinePercent, language))}',
                    textAlign: TextAlign.center,
                  ),
                ],
                const SizedBox(height: 16),
                TimerText(state.remainingSeconds),
                Text(
                  l10n.programTotalRemaining(formatSeconds(state.totalRemainingSeconds)),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(
                  next == null ? l10n.programLastSegment : l10n.programNext(segmentLabel(next.label, l10n)),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                if (!state.isStarted)
                  WideButton(label: l10n.programStart, onPressed: () => onIntent(const CardioStart()))
                else if (state.isRunning)
                  WideButton(label: l10n.programPause, onPressed: () => onIntent(const CardioPause()))
                else
                  WideButton(label: l10n.programResume, onPressed: () => onIntent(const CardioResume())),
                const SizedBox(height: 12),
                WideButton(
                  label: l10n.programSkipSegment,
                  filled: false,
                  onPressed: state.isStarted ? () => onIntent(const CardioSkip()) : null,
                ),
              ],
            ),
    );
  }
}

class CircuitView extends StatelessWidget {
  const CircuitView({
    required this.programId,
    required this.week,
    required this.dayIndex,
    required this.circuit,
    required this.exercises,
    required this.onBack,
    super.key,
  });

  final String programId;
  final int week;
  final int dayIndex;
  final HomeCircuitWorkout circuit;
  final Map<int, ProgramExercise> exercises;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    return BlocProvider(
      create: (_) => CircuitBloc(
        deps: services.training,
        user: context.read<WebUserHolder>().user,
        programId: programId,
        week: week,
        dayIndex: dayIndex,
        circuit: circuit,
      ),
      child: EffectListener<CircuitBloc, CardioEffect>(
        onEffect: (context, effect) => _onCardioEffect(context, effect, onBack),
        child: Builder(
          // The bloc is read now: onHidden also runs while this element is being removed
          builder: (context) => BlocVisibilityScope<CircuitBloc>(
            onVisible: (bloc) => bloc.add(const CardioVisibility(true)),
            onHidden: (bloc) => bloc.add(const CardioVisibility(false)),
            child: BlocConsumer<CircuitBloc, CircuitState>(
              listenWhen: (a, b) => !a.showExitConfirmation && b.showExitConfirmation,
              listener: (context, _) => _confirmLeave(context, context.read<CircuitBloc>().add),
              builder: (context, state) => CircuitContent(
                state: state,
                exercises: exercises,
                onIntent: context.read<CircuitBloc>().add,
                onDone: onBack,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class CircuitContent extends StatelessWidget {
  const CircuitContent({
    required this.state,
    required this.exercises,
    required this.onIntent,
    required this.onDone,
    super.key,
  });

  final CircuitState state;
  final Map<int, ProgramExercise> exercises;
  final void Function(CardioIntent) onIntent;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final workout = state.workout;
    final shown = state.shownExerciseId;
    final exercise = shown == null ? null : exercises[shown];
    final name = exercise?.displayName(spanish: languageOf(l10n) == 'es') ?? l10n.unknownExercise;
    final phase = state.phase;
    final (label, remaining) = switch (phase) {
      CircuitWork(:final remaining) => (l10n.programWork, remaining),
      CircuitRest(:final remaining) => (l10n.programRest, remaining),
      CircuitRoundRest(:final remaining) => (l10n.programRoundRest, remaining),
      null => (l10n.programWorkRestSeconds(workout.workSeconds, workout.restSeconds), workout.workSeconds),
    };
    return _Frame(
      title: workoutTitle(workout, l10n),
      onBack: () => onIntent(const CardioBack()),
      child: state.isFinished
          ? CompletionView(
              title: l10n.programWorkoutCompletedTitle,
              text: l10n.programWorkoutCompletedText,
              entry: state.recorded,
              titleKey: 'program/${workout.titleCode.code}',
              onDone: onDone,
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(l10n.programRoundOf(state.currentRound, workout.rounds), textAlign: TextAlign.center),
                const SizedBox(height: 8),
                Text(label, style: theme.textTheme.titleMedium, textAlign: TextAlign.center),
                Semantics(
                  header: true,
                  child: Text(
                    phase is CircuitWork ? name : l10n.programNext(name),
                    style: theme.textTheme.headlineSmall,
                    textAlign: TextAlign.center,
                  ),
                ),
                const SizedBox(height: 16),
                Center(
                  // While resting, the rest GIF plays and the next exercise's is fetched
                  child: PreloadExerciseAnimation(
                    animation: exercise?.animation,
                    child: ExerciseVisual(
                      kind: phase is CircuitWork || phase == null ? ExerciseVisualKind.time : ExerciseVisualKind.rest,
                      animation: exercise?.animation,
                      name: phase is CircuitWork || phase == null ? name : l10n.programRest,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                TimerText(remaining),
                const SizedBox(height: 24),
                if (phase == null)
                  WideButton(label: l10n.programStart, onPressed: () => onIntent(const CardioStart()))
                else
                  WideButton(label: l10n.programSkip, filled: false, onPressed: () => onIntent(const CardioSkip())),
              ],
            ),
    );
  }
}
