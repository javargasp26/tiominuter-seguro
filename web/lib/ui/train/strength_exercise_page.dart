import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../domain/program.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import 'program_workout_page.dart';
import 'strength_exercise_bloc.dart';
import 'train_widgets.dart';

class StrengthExercisePage extends StatelessWidget {
  const StrengthExercisePage({
    required this.programId,
    required this.week,
    required this.dayIndex,
    required this.position,
    super.key,
  });

  final String programId;
  final int week;
  final int dayIndex;
  final int position;

  @override
  Widget build(BuildContext context) {
    final back = Routes.programWorkout(programId, week, dayIndex);
    return ProgramWorkoutLoader(
      programId: programId,
      week: week,
      dayIndex: dayIndex,
      builder: (context, loaded) {
        final workout = loaded.row.workout;
        final slot = workout is StrengthWorkout
            ? workout.slots.where((s) => s.position == position && s.sets > 0).firstOrNull
            : null;
        if (workout is! StrengthWorkout || slot == null) {
          final l10n = AppLocalizations.of(context);
          return Scaffold(
            appBar: AppBar(
              leading: IconButton(
                tooltip: l10n.back,
                icon: const Icon(Icons.arrow_back),
                onPressed: () => context.go(back),
              ),
            ),
            body: PageBody(children: [EmptyView(text: l10n.programWorkoutNotFound)]),
          );
        }
        final services = context.read<AppServices>();
        return BlocProvider(
          create: (_) => StrengthExerciseBloc(
            user: context.read<WebUserHolder>().user,
            programId: programId,
            week: week,
            dayIndex: dayIndex,
            workout: workout,
            slot: slot,
            exercise: loaded.exercises[slot.exerciseId],
            deps: services.training,
          ),
          child: EffectListener<StrengthExerciseBloc, StrengthExerciseEffect>(
            onEffect: (context, effect) => switch (effect) {
              StrengthNavigateBack() => context.go(back),
            },
            child: BlocConsumer<StrengthExerciseBloc, StrengthExerciseState>(
              listenWhen: (a, b) => !a.showExitConfirmation && b.showExitConfirmation,
              listener: (context, state) async {
                final l10n = AppLocalizations.of(context);
                final bloc = context.read<StrengthExerciseBloc>();
                final leave = await showDialog<bool>(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: Text(l10n.programExitExerciseTitle),
                    content: Text(l10n.programExitSetsText(state.completedSets, state.totalSets)),
                    actions: [
                      TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.programStay)),
                      TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.programLeave)),
                    ],
                  ),
                );
                bloc.add(leave == true ? const StrengthConfirmExit() : const StrengthDismissExit());
              },
              builder: (context, state) =>
                  StrengthExerciseContent(state: state, onIntent: context.read<StrengthExerciseBloc>().add),
            ),
          ),
        );
      },
    );
  }
}

class StrengthExerciseContent extends StatelessWidget {
  const StrengthExerciseContent({required this.state, required this.onIntent, super.key});

  final StrengthExerciseState state;
  final void Function(StrengthExerciseIntent) onIntent;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final slot = state.slot;
    final name = state.exercise?.displayName(spanish: languageOf(l10n) == 'es') ?? l10n.unknownExercise;
    final phase = state.phase;
    final setLine = l10n.programSetOf(phase.setIndex + 1, state.totalSets);
    final List<Widget> controls = switch (phase) {
      PerformingReps() => [
        Text(setLine, textAlign: TextAlign.center),
        Semantics(
          liveRegion: true,
          child: Text(
            slot.perSide
                ? l10n.programRepsPerSide(slot.reps[phase.setIndex])
                : l10n.workoutReps(slot.reps[phase.setIndex]),
            style: theme.textTheme.displaySmall,
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: 24),
        WideButton(
          label: l10n.programSetDone,
          onPressed: state.finished ? null : () => onIntent(const StrengthSetDone()),
        ),
      ],
      ReadyToStartTimed() => [
        Text(setLine, textAlign: TextAlign.center),
        TimerText(slot.reps[phase.setIndex]),
        if (slot.perSide) Text(l10n.programPerSideOrder, textAlign: TextAlign.center),
        const SizedBox(height: 24),
        WideButton(label: l10n.programStart, onPressed: () => onIntent(const StrengthStartTimed())),
      ],
      GetReadyPhase(:final remaining) => [
        Text(l10n.programGetReady, style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
        TimerText(remaining),
      ],
      PerformingTimed(:final remaining, :final side) => [
        Text(setLine, textAlign: TextAlign.center),
        Text(
          switch (side) {
            ProgramSide.left => l10n.programLeftSide,
            ProgramSide.right => l10n.programRightSide,
            null => l10n.programHold,
          },
          style: theme.textTheme.titleMedium,
          textAlign: TextAlign.center,
        ),
        TimerText(remaining),
      ],
      SwitchingSides(:final remaining) => [
        Text(l10n.programSwitchSides, style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
        TimerText(remaining),
      ],
      Resting(:final remaining, :final setIndex) => [
        Text(l10n.programRest, style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
        TimerText(remaining),
        Text(l10n.programNextSet(setIndex + 1), textAlign: TextAlign.center),
        const SizedBox(height: 24),
        WideButton(label: l10n.programSkipRest, filled: false, onPressed: () => onIntent(const StrengthSkipRest())),
      ],
    };
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) onIntent(const StrengthBack());
      },
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            tooltip: l10n.back,
            icon: const Icon(Icons.arrow_back),
            onPressed: () => onIntent(const StrengthBack()),
          ),
          title: Text(l10n.programExerciseOf(state.exerciseNumber, state.totalExercises)),
        ),
        body: PageBody(
          children: [
            Semantics(
              header: true,
              child: Text(name, style: theme.textTheme.headlineSmall, textAlign: TextAlign.center),
            ),
            const SizedBox(height: 16),
            Center(
              child: ExerciseVisual(
                kind: phase is Resting
                    ? ExerciseVisualKind.rest
                    : (slot.type == ProgramSlotType.time ? ExerciseVisualKind.time : ExerciseVisualKind.reps),
                animation: state.exercise?.animation,
                name: phase is Resting ? l10n.programRest : name,
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
