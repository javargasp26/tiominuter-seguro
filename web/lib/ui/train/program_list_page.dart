import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/content_repository.dart';
import '../../data/repositories.dart';
import '../../data/training_repositories.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import 'program_labels.dart';
import 'train_widgets.dart';

sealed class ProgramListIntent {
  const ProgramListIntent();
}

final class ProgramListLoad extends ProgramListIntent {
  const ProgramListLoad();
}

class ProgramListState {
  const ProgramListState({this.isLoading = true, this.contentMissing = false, this.programs = const [], this.error});

  final bool isLoading;

  /// Creating a program needs the program content.
  final bool contentMissing;
  final List<ProgramSummary> programs;
  final DataError? error;
}

/// My programs (Android's `ProgramListViewModel`): one read per program plus a count of its
/// completions.
class ProgramListBloc extends MviBloc<ProgramListIntent, ProgramListState, Never> {
  ProgramListBloc({required this.user, required this.content, required this.deps}) : super(const ProgramListState()) {
    on<ProgramListLoad>((_, emit) async {
      emit(const ProgramListState());
      final TrainingContent? training = await content.training();
      try {
        emit(
          ProgramListState(
            isLoading: false,
            contentMissing: training == null,
            programs: await deps.service.training.programs(user.uid),
          ),
        );
      } on DataException catch (e) {
        emit(ProgramListState(isLoading: false, contentMissing: training == null, error: e.error));
      }
    });
  }

  final WebUser user;
  final ContentRepository content;
  final TrainingDeps deps;
}

class ProgramListPage extends StatelessWidget {
  const ProgramListPage({super.key});

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final user = context.read<WebUserHolder>().user;
    return BlocProvider(
      create: (_) =>
          ProgramListBloc(user: user, content: services.content, deps: services.training)..add(const ProgramListLoad()),
      child: BlocBuilder<ProgramListBloc, ProgramListState>(
        builder: (context, state) => ProgramListContent(
          state: state,
          onBack: () => backToTrain(context),
          onRetry: () => context.read<ProgramListBloc>().add(const ProgramListLoad()),
          onCreate: () => context.go(Routes.programCreate),
          onProgram: (program) => context.go(Routes.program(program.programId)),
        ),
      ),
    );
  }
}

class ProgramListContent extends StatelessWidget {
  const ProgramListContent({
    required this.state,
    required this.onBack,
    required this.onRetry,
    required this.onCreate,
    required this.onProgram,
    super.key,
  });

  final ProgramListState state;
  final VoidCallback onBack;
  final VoidCallback onRetry;
  final VoidCallback onCreate;
  final void Function(ProgramSummary program) onProgram;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final error = state.error;
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(tooltip: l10n.back, icon: const Icon(Icons.arrow_back), onPressed: onBack),
        title: Text(l10n.programsTitle),
      ),
      body: PageBody(
        children: [
          if (state.isLoading)
            const LoadingView()
          else if (error != null)
            ErrorView(error: error, onRetry: onRetry)
          else if (state.programs.isEmpty)
            EmptyView(title: l10n.programsEmptyTitle, text: l10n.programsEmptyText)
          else
            for (final program in state.programs)
              Card(
                child: ListTile(
                  minTileHeight: 56,
                  leading: const ExcludeSemantics(child: Icon(Icons.event_note_outlined)),
                  title: Text(programName(program.profile, l10n)),
                  subtitle: Text(
                    '${programMeta(program.profile, l10n)}\n'
                    '${l10n.programFocusValue(focusLabel(program.profile.focus, l10n))} · '
                    '${l10n.programProgress(formatPercent(program.completedWorkouts, program.totalWorkouts, languageOf(l10n)))}',
                  ),
                  isThreeLine: true,
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => onProgram(program),
                ),
              ),
          const SizedBox(height: 8),
          WideButton(
            label: l10n.programCreate,
            filled: false,
            onPressed: state.isLoading || state.contentMissing ? null : onCreate,
          ),
        ],
      ),
    );
  }
}
