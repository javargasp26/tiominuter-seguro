import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../app/app.dart';
import '../../app/groups_service.dart';
import '../../app/services.dart';
import '../../app/theme.dart';
import '../../core/mvi.dart';
import '../../data/repositories.dart' show WebUser;
import '../../domain/calendar_dates.dart';
import '../../domain/groups.dart';
import '../../domain/training.dart';
import '../../domain/weekly_progress.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import '../train/train_widgets.dart' show WebUserHolder;
import '../workout_name_text.dart';
import 'week_bloc.dart';

class WeekPage extends StatelessWidget {
  const WeekPage({required this.uid, super.key});

  final String uid;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    return BlocProvider(
      create: (_) => WeekBloc(uid: uid, data: services.userData, dates: services.dates),
      child: Builder(
        builder: (context) => BlocVisibilityScope<WeekBloc>(
          onVisible: (bloc) => bloc.add(const WeekVisible()),
          onHidden: (bloc) => bloc.add(const WeekHidden()),
          child: BlocBuilder<WeekBloc, WeekState>(
            builder: (context, state) {
              final summary = state.summary;
              final service = services.groupsService;
              return WeekContent(
                state: state,
                onRetry: () => _add(context, const WeekRetry()),
                onEditGoal: services.trainingDeps == null ? null : () => context.go(Routes.weeklyGoal),
                // Android Home's "already trained today" banner, once the week is known
                banner: service != null && summary != null
                    ? TrainedTodayCard(
                        service: service,
                        user: context.read<WebUserHolder>().user,
                        iTrainedToday: summary.progress.workoutsOn(summary.today) > 0,
                      )
                    : null,
              );
            },
          ),
        ),
      ),
    );
  }

  static void _add(BuildContext context, WeekIntent intent) {
    final bloc = context.read<WeekBloc>();
    if (!bloc.isClosed) bloc.add(intent);
  }
}

/// Stateless: the widget tests render it from plain states.
class WeekContent extends StatelessWidget {
  const WeekContent({required this.state, required this.onRetry, this.onEditGoal, this.banner, super.key});

  final WeekState state;
  final VoidCallback onRetry;

  /// The "already trained today" banner (shown above the week when there is one).
  final Widget? banner;

  /// Opens the weekly goal (null: the goal is set in the Android app).
  final VoidCallback? onEditGoal;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final summary = state.summary;
    final error = state.error;
    return PageBody(
      children: [
        PageTitle(l10n.navWeek),
        if (error != null)
          ErrorView(error: error, onRetry: onRetry)
        else if (state.isLoading || summary == null)
          const LoadingView()
        else ...[
          ?banner,
          _GoalCard(summary: summary, streak: state.streak, onEditGoal: onEditGoal),
          const SizedBox(height: 16),
          _DayStrip(dates: state.weekDates, today: summary.today, summary: summary, sources: state.sourcesPerDay),
          const SizedBox(height: 16),
          Text(l10n.weekCountingRule(minCountedDurationSeconds ~/ 60), style: Theme.of(context).textTheme.bodySmall),
        ],
      ],
    );
  }
}

class _GoalCard extends StatelessWidget {
  const _GoalCard({required this.summary, required this.streak, this.onEditGoal});

  final CurrentWeekSummary summary;
  final int? streak;
  final VoidCallback? onEditGoal;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final goal = summary.progress.goal;
    final done = summary.progress.workoutCount;
    if (goal == null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(l10n.weekNoGoal, style: theme.textTheme.titleMedium),
              const SizedBox(height: 8),
              if (onEditGoal == null)
                InfoNote(l10n.weekNoGoalHint)
              else
                FilledButton(
                  onPressed: onEditGoal,
                  style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                  child: Text(l10n.weeklyGoalSet),
                ),
            ],
          ),
        ),
      );
    }
    final met = summary.progress.isGoalMet;
    final message = met ? l10n.weekGoalMet : l10n.weekWorkoutsLeft(summary.workoutsNeeded);
    final streakText = streak == null || streak! < 1 ? null : l10n.weekStreak(streak!);
    final success = SuccessColors.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(l10n.weeklyGoalTitle, style: theme.textTheme.titleMedium)),
                Text(l10n.weekProgress(done, goal), style: theme.textTheme.titleMedium),
              ],
            ),
            const SizedBox(height: 12),
            // The bar is capped at 100 % (5 of 4 still reads "5 of 4 workouts")
            ExcludeSemantics(
              child: LinearProgressIndicator(
                value: (done / goal).clamp(0, 1).toDouble(),
                minHeight: 8,
                borderRadius: BorderRadius.circular(4),
                color: met ? success.success : null,
              ),
            ),
            const SizedBox(height: 12),
            Semantics(
              liveRegion: true,
              child: Text(
                streakText == null ? message : '$message · $streakText',
                style: theme.textTheme.bodyLarge?.copyWith(color: met ? success.success : null),
              ),
            ),
            if (onEditGoal != null)
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: onEditGoal,
                  style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                  child: Text(l10n.weeklyGoalChange),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Monday to Sunday: a circle per day, filled with the workout types of the day (the number when
/// more than one workout), today outlined; one sentence for screen readers.
class _DayStrip extends StatelessWidget {
  const _DayStrip({required this.dates, required this.today, required this.summary, required this.sources});

  final List<String> dates;
  final String today;
  final CurrentWeekSummary summary;
  final Map<String, List<TrainingSource>> sources;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final scheme = Theme.of(context).colorScheme;
    String dayName(String date) => DateFormat.EEEE(locale).format(CalendarDates.toUtc(date));
    final sentence = [
      for (final date in dates)
        () {
          final name = date == today ? '${dayName(date)} (${l10n.weekToday})' : dayName(date);
          final daySources = sources[date] ?? const [];
          return daySources.isEmpty
              ? l10n.weekDayNone(name)
              : l10n.weekDaySources(name, daySources.map((s) => sourceLabel(s, l10n)).join(', '));
        }(),
    ].join('. ');
    return Semantics(
      label: sentence,
      child: ExcludeSemantics(
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            for (final date in dates)
              Expanded(
                child: Column(
                  children: [
                    Text(DateFormat.EEEEE(locale).format(CalendarDates.toUtc(date))),
                    const SizedBox(height: 4),
                    _DayCircle(
                      workouts: summary.progress.workoutsOn(date),
                      sources: sources[date] ?? const [],
                      isToday: date == today,
                      scheme: scheme,
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _DayCircle extends StatelessWidget {
  const _DayCircle({required this.workouts, required this.sources, required this.isToday, required this.scheme});

  final int workouts;
  final List<TrainingSource> sources;
  final bool isToday;
  final ColorScheme scheme;

  @override
  Widget build(BuildContext context) {
    final trained = workouts > 0;
    return Column(
      children: [
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: trained ? scheme.primary : scheme.surfaceContainerHighest,
            border: isToday ? Border.all(color: scheme.primary, width: 2) : null,
          ),
          alignment: Alignment.center,
          child: trained
              ? (workouts > 1
                    ? Text(
                        '$workouts',
                        style: TextStyle(color: scheme.onPrimary, fontWeight: FontWeight.bold),
                      )
                    : Icon(Icons.check, color: scheme.onPrimary, size: 20))
              : null,
        ),
        const SizedBox(height: 4),
        SizedBox(
          height: 16,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [for (final source in sources) Icon(sourceIcon(source), size: 14, color: scheme.primary)],
          ),
        ),
      ],
    );
  }
}

/// Android Home's `TrainedTodayBannerCard`: "Ana already trained today. Your turn 💪" when a group
/// mate trained today and I haven't (one cached read of my groups' members, no listener). Tap →
/// the group (Groups when several have trainers); X → hidden for the rest of today.
class TrainedTodayCard extends StatefulWidget {
  const TrainedTodayCard({required this.service, required this.user, required this.iTrainedToday, super.key});

  final GroupsService service;
  final WebUser user;
  final bool iTrainedToday;

  @override
  State<TrainedTodayCard> createState() => _TrainedTodayCardState();
}

class _TrainedTodayCardState extends State<TrainedTodayCard> {
  TrainedTodayBanner? _banner;
  bool _hidden = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(TrainedTodayCard old) {
    super.didUpdateWidget(old);
    // My own workout today hides it at once
    if (old.iTrainedToday != widget.iTrainedToday) _load();
  }

  void _load() {
    widget.service.trainedTodayBanner(widget.user, iTrainedToday: widget.iTrainedToday).then((banner) {
      if (mounted) setState(() => _banner = banner);
    });
  }

  @override
  Widget build(BuildContext context) {
    final banner = _banner;
    if (banner == null || _hidden) return const SizedBox.shrink();
    final l10n = AppLocalizations.of(context);
    final scheme = Theme.of(context).colorScheme;
    final names = banner.names;
    final text = switch (names.length) {
      1 => l10n.trainedTodayOne(names[0]),
      2 => l10n.trainedTodayTwo(names[0], names[1]),
      _ => l10n.trainedTodayMore(names[0], names[1], names.length - 2),
    };
    void open() => context.go(banner.groupIds.length == 1 ? Routes.group(banner.groupIds.single) : Routes.groups);
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Card(
        color: scheme.tertiaryContainer,
        child: Row(
          children: [
            Expanded(
              child: Semantics(
                button: true,
                onTapHint: l10n.trainedTodayOpen,
                child: InkWell(
                  onTap: open,
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 56),
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Text(text, style: TextStyle(color: scheme.onTertiaryContainer)),
                    ),
                  ),
                ),
              ),
            ),
            IconButton(
              tooltip: l10n.trainedTodayDismiss,
              style: IconButton.styleFrom(minimumSize: const Size(48, 48)),
              icon: const Icon(Icons.close),
              onPressed: () {
                widget.service.dismissTrainedToday(widget.user.uid);
                setState(() => _hidden = true);
              },
            ),
          ],
        ),
      ),
    );
  }
}
