import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/content_repository.dart';
import '../../domain/program.dart';
import '../../domain/program_form.dart';
import '../../domain/program_generator.dart';

sealed class ProgramCreateIntent {
  const ProgramCreateIntent();
}

final class ProgramCreateLoad extends ProgramCreateIntent {
  const ProgramCreateLoad();
}

final class ProgramFormChanged extends ProgramCreateIntent {
  const ProgramFormChanged(this.change);

  final ProgramForm Function(ProgramForm) change;
}

final class ProgramPreviewClicked extends ProgramCreateIntent {
  const ProgramPreviewClicked();
}

final class ProgramPreviewDismissed extends ProgramCreateIntent {
  const ProgramPreviewDismissed();
}

final class ProgramCreateClicked extends ProgramCreateIntent {
  const ProgramCreateClicked();
}

class ProgramCreateState {
  const ProgramCreateState({
    this.form = const ProgramForm(),
    this.content,
    this.contentMissing = false,
    this.preview,
    this.isWorking = false,
    this.createFailed = false,
  });

  final ProgramForm form;
  final TrainingContent? content;
  final bool contentMissing;

  /// Week 1 of the previewed program; null when the preview is closed.
  final List<ProgramWorkout>? preview;
  final bool isWorking;
  final bool createFailed;

  bool get isLowImpact => form.isLowImpactWith(content?.programTemplates.impact);

  ProgramCreateState copyWith({
    ProgramForm? form,
    TrainingContent? content,
    bool? contentMissing,
    List<ProgramWorkout>? preview,
    bool clearPreview = false,
    bool? isWorking,
    bool? createFailed,
  }) => ProgramCreateState(
    form: form ?? this.form,
    content: content ?? this.content,
    contentMissing: contentMissing ?? this.contentMissing,
    preview: clearPreview ? null : (preview ?? this.preview),
    isWorking: isWorking ?? this.isWorking,
    createFailed: createFailed ?? this.createFailed,
  );
}

sealed class ProgramCreateEffect {
  const ProgramCreateEffect();
}

final class ProgramCreated extends ProgramCreateEffect {
  const ProgramCreated(this.programId);

  final String programId;
}

/// The create form (Android's `ProgramCreateViewModel`): validation, BMI, the week-1 preview made by
/// the Dart generator, and creation (the program and its workouts in one batch).
class ProgramCreateBloc extends MviBloc<ProgramCreateIntent, ProgramCreateState, ProgramCreateEffect> {
  ProgramCreateBloc({required this.uid, required this.content, required this.deps})
    : super(const ProgramCreateState()) {
    on<ProgramCreateLoad>((_, emit) async {
      final training = await content.training();
      emit(training == null ? state.copyWith(contentMissing: true) : state.copyWith(content: training));
    });
    on<ProgramFormChanged>(
      (intent, emit) => emit(state.copyWith(form: intent.change(state.form), createFailed: false)),
    );
    on<ProgramPreviewClicked>((_, emit) {
      final profile = state.form.profile;
      final training = state.content;
      if (profile == null || training == null) {
        emit(state.copyWith(form: state.form.copyWith(showAllErrors: true)));
        return;
      }
      final program = ProgramGenerator.generate(profile, training.programCatalog, training.programTemplates);
      emit(state.copyWith(preview: program.weeks.isEmpty ? const [] : program.weeks.first.workouts));
    });
    on<ProgramPreviewDismissed>((_, emit) => emit(state.copyWith(clearPreview: true)));
    on<ProgramCreateClicked>((_, emit) {
      if (state.isWorking) return;
      final profile = state.form.profile;
      final training = state.content;
      if (profile == null || training == null) {
        emit(state.copyWith(form: state.form.copyWith(showAllErrors: true)));
        return;
      }
      emit(state.copyWith(isWorking: true, createFailed: false, clearPreview: true));
      try {
        final id = deps.service.createProgram(uid, profile, training.programCatalog, training.programTemplates);
        sendEffect(ProgramCreated(id));
      } on ArgumentError {
        // A combination the templates lack (the validation tests keep them complete)
        emit(state.copyWith(isWorking: false, createFailed: true));
      }
    });
  }

  final String uid;
  final ContentRepository content;
  final TrainingDeps deps;
}
