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
import 'train_bloc.dart';
import 'train_widgets.dart';

/// The Train tab: Android Home's navigation — the daily routine ("Day X available"), Gym routines
/// and Programs, then Log activity and Weekly goal.
class TrainPage extends StatelessWidget {
  const TrainPage({super.key});

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final user = context.read<WebUserHolder>().user;
    return BlocProvider(
      create: (_) => TrainBloc(user: user, content: services.content, deps: services.training)..add(const TrainLoad()),
      child: EffectListener<TrainBloc, TrainEffect>(
        onEffect: (context, effect) => switch (effect) {
          TrainSessionSaved(:final durationSeconds) => showSessionSaved(context, durationSeconds),
        },
        child: Builder(
          builder: (context) => VisibilityScope(
            onVisible: () => context.read<TrainBloc>().add(const TrainVisible()),
            onHidden: () {},
            child: BlocBuilder<TrainBloc, TrainState>(
              builder: (context, state) => TrainContent(
                state: state,
                onDaily: () => context.go(Routes.daily),
                onContinue: (routine) => context.go(Routes.dailyWorkout(routine.routineId)),
                onGym: () => context.go(Routes.gym),
                onPrograms: () => context.go(Routes.programs),
                onLogActivity: () => context.go(Routes.logActivity),
                onWeeklyGoal: () => context.go(Routes.weeklyGoalFromTrain),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Three entries in a row from this width on; one column below.
const double trainRowMinWidth = 600;

class TrainContent extends StatelessWidget {
  const TrainContent({
    required this.state,
    required this.onDaily,
    required this.onContinue,
    required this.onGym,
    required this.onPrograms,
    required this.onLogActivity,
    required this.onWeeklyGoal,
    super.key,
  });

  final TrainState state;
  final VoidCallback onDaily;
  final void Function(DailyRoutine routine) onContinue;
  final VoidCallback onGym;
  final VoidCallback onPrograms;
  final VoidCallback onLogActivity;
  final VoidCallback onWeeklyGoal;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final inProgress = state.inProgressRoutine;
    final day = state.availableDay;
    final entries = [
      TrainEntryCard(
        icon: Icons.today_outlined,
        title: l10n.trainStartToday,
        subtitle: state.isLoading || day == null ? null : l10n.trainDayAvailable(DailyWorkoutName(day).text(l10n)),
        primary: true,
        onTap: onDaily,
      ),
      TrainEntryCard(icon: Icons.fitness_center, title: l10n.gymTitle, subtitle: l10n.gymBrowserNote, onTap: onGym),
      TrainEntryCard(icon: Icons.event_note_outlined, title: l10n.trainPrograms, onTap: onPrograms),
    ];
    return PageBody(
      children: [
        PageTitle(l10n.trainTitle),
        if (inProgress != null) ...[
          Card(
            color: theme.colorScheme.primaryContainer,
            child: ListTile(
              minTileHeight: 56,
              leading: const ExcludeSemantics(child: Icon(Icons.play_circle_outline)),
              title: Text(
                l10n.trainInProgress(
                  DailyWorkoutName(
                    inProgress.routineId,
                    title: inProgress.title,
                    titleEs: inProgress.titleEs,
                  ).text(l10n),
                ),
              ),
              trailing: Text(l10n.trainContinue),
              onTap: () => onContinue(inProgress),
            ),
          ),
          const SizedBox(height: 16),
        ],
        LayoutBuilder(
          builder: (context, constraints) => constraints.maxWidth >= trainRowMinWidth
              ? IntrinsicHeight(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      for (final (index, entry) in entries.indexed) ...[
                        if (index > 0) const SizedBox(width: 12),
                        Expanded(child: entry),
                      ],
                    ],
                  ),
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    for (final (index, entry) in entries.indexed) ...[if (index > 0) const SizedBox(height: 12), entry],
                  ],
                ),
        ),
        const SizedBox(height: 16),
        // Android Home's secondary actions (in its weekly goal card)
        Wrap(
          alignment: WrapAlignment.center,
          spacing: 8,
          runSpacing: 8,
          children: [
            OutlinedButton.icon(
              onPressed: onLogActivity,
              style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
              icon: const Icon(Icons.add),
              label: Text(l10n.logActivity),
            ),
            OutlinedButton.icon(
              onPressed: onWeeklyGoal,
              style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
              icon: const Icon(Icons.flag_outlined),
              label: Text(l10n.weeklyGoalTitle),
            ),
          ],
        ),
      ],
    );
  }
}

/// One entry of the hub: a card that is one button (keyboard focusable, at least 48 px).
class TrainEntryCard extends StatelessWidget {
  const TrainEntryCard({
    required this.icon,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.primary = false,
    super.key,
  });

  final IconData icon;
  final String title;
  final String? subtitle;

  /// The daily routine: filled, as Android Home's primary button.
  final bool primary;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final foreground = primary ? scheme.onPrimary : scheme.onSurface;
    final text = subtitle;
    return Card(
      margin: EdgeInsets.zero,
      color: primary ? scheme.primary : null,
      clipBehavior: Clip.antiAlias,
      child: Semantics(
        button: true,
        child: InkWell(
          onTap: onTap,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 96),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  ExcludeSemantics(child: Icon(icon, color: foreground)),
                  const SizedBox(height: 8),
                  Text(title, style: theme.textTheme.titleMedium?.copyWith(color: foreground)),
                  if (text != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      text,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: primary ? scheme.onPrimary : scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
