import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/repositories.dart';
import '../../data/training_repositories.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import 'program_labels.dart';
import 'train_widgets.dart';

sealed class ProgramDetailIntent {
  const ProgramDetailIntent();
}

final class ProgramDetailLoad extends ProgramDetailIntent {
  const ProgramDetailLoad();
}

class ProgramDetailState {
  const ProgramDetailState({this.isLoading = true, this.detail, this.notFound = false, this.error});

  final bool isLoading;
  final ProgramDetail? detail;
  final bool notFound;
  final DataError? error;
}

/// One program (Android's `ProgramDetailViewModel`): its weeks and workouts with their completion
/// (one read of the program, its workouts and its completions).
class ProgramDetailBloc extends MviBloc<ProgramDetailIntent, ProgramDetailState, Never> {
  ProgramDetailBloc({required this.uid, required this.programId, required this.deps})
    : super(const ProgramDetailState()) {
    on<ProgramDetailLoad>((_, emit) async {
      emit(const ProgramDetailState());
      try {
        final detail = await deps.service.programDetail(uid, programId);
        emit(ProgramDetailState(isLoading: false, detail: detail, notFound: detail == null));
      } on DataException catch (e) {
        emit(ProgramDetailState(isLoading: false, error: e.error));
      }
    });
  }

  final String uid;
  final String programId;
  final TrainingDeps deps;
}

class ProgramDetailPage extends StatelessWidget {
  const ProgramDetailPage({required this.programId, super.key});

  final String programId;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final uid = context.read<WebUserHolder>().user.uid;
    return BlocProvider(
      create: (_) =>
          ProgramDetailBloc(uid: uid, programId: programId, deps: services.training)..add(const ProgramDetailLoad()),
      child: BlocBuilder<ProgramDetailBloc, ProgramDetailState>(
        builder: (context, state) => ProgramDetailContent(
          state: state,
          onRetry: () => context.read<ProgramDetailBloc>().add(const ProgramDetailLoad()),
          onBack: () => context.go(Routes.programs),
          onWorkout: (row) => context.go(Routes.programWorkout(programId, row.week, row.dayIndex)),
        ),
      ),
    );
  }
}

class ProgramDetailContent extends StatelessWidget {
  const ProgramDetailContent({
    required this.state,
    required this.onRetry,
    required this.onBack,
    required this.onWorkout,
    super.key,
  });

  final ProgramDetailState state;
  final VoidCallback onRetry;
  final VoidCallback onBack;
  final void Function(ProgramWorkoutRow row) onWorkout;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final detail = state.detail;
    final weeks = <int, List<ProgramWorkoutRow>>{};
    for (final row in detail?.workouts ?? const <ProgramWorkoutRow>[]) {
      (weeks[row.week] ??= []).add(row);
    }
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(tooltip: l10n.back, icon: const Icon(Icons.arrow_back), onPressed: onBack),
        title: Text(detail == null ? l10n.programsTitle : programName(detail.summary.profile, l10n)),
      ),
      body: PageBody(
        children: [
          if (state.isLoading)
            const LoadingView()
          else if (state.error != null)
            ErrorView(error: state.error!, onRetry: onRetry)
          else if (state.notFound || detail == null)
            EmptyView(text: l10n.programNotFound)
          else ...[
            Text(l10n.programFocusValue(focusLabel(detail.summary.profile.focus, l10n))),
            Text(programMeta(detail.summary.profile, l10n), style: theme.textTheme.bodySmall),
            for (final MapEntry(key: week, value: rows) in weeks.entries) ...[
              const SizedBox(height: 20),
              Semantics(
                header: true,
                child: Text(
                  l10n.programWeekHeader(
                    week,
                    formatPercent(rows.where((r) => r.isCompleted).length, rows.length, languageOf(l10n)),
                  ),
                  style: theme.textTheme.titleMedium,
                ),
              ),
              if (rows.any((r) => r.isDeload)) Text(l10n.programDeload, style: theme.textTheme.labelMedium),
              const SizedBox(height: 8),
              for (final row in rows)
                Card(
                  child: ListTile(
                    minTileHeight: 56,
                    title: Text(row.workout == null ? l10n.programWorkoutNotFound : workoutTitle(row.workout!, l10n)),
                    subtitle: row.workout == null ? null : Text(workoutSummary(row.workout!, l10n)),
                    trailing: Icon(
                      row.isCompleted ? Icons.check_circle : Icons.radio_button_unchecked,
                      semanticLabel: row.isCompleted ? l10n.programCompleted : l10n.programNotCompleted,
                      color: row.isCompleted ? theme.colorScheme.primary : null,
                    ),
                    onTap: row.workout == null ? null : () => onWorkout(row),
                  ),
                ),
            ],
          ],
        ],
      ),
    );
  }
}
