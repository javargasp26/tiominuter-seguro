import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/repositories.dart';
import '../../domain/program.dart';
import '../../domain/program_form.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import 'program_create_bloc.dart';
import 'program_labels.dart';
import 'train_widgets.dart';

class ProgramCreatePage extends StatelessWidget {
  const ProgramCreatePage({super.key});

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final uid = context.read<WebUserHolder>().user.uid;
    return BlocProvider(
      create: (_) =>
          ProgramCreateBloc(uid: uid, content: services.content, deps: services.training)
            ..add(const ProgramCreateLoad()),
      child: EffectListener<ProgramCreateBloc, ProgramCreateEffect>(
        onEffect: (context, effect) => switch (effect) {
          ProgramCreated(:final programId) => context.go(Routes.program(programId)),
        },
        child: BlocBuilder<ProgramCreateBloc, ProgramCreateState>(
          builder: (context, state) => ProgramCreateContent(
            state: state,
            onIntent: context.read<ProgramCreateBloc>().add,
            onBack: () => context.go(Routes.programs),
          ),
        ),
      ),
    );
  }
}

class ProgramCreateContent extends StatelessWidget {
  const ProgramCreateContent({required this.state, required this.onIntent, required this.onBack, super.key});

  final ProgramCreateState state;
  final void Function(ProgramCreateIntent) onIntent;
  final VoidCallback onBack;

  void _change(ProgramForm Function(ProgramForm) change) => onIntent(ProgramFormChanged(change));

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final form = state.form;
    String? fieldError(ProgramFieldError? error, int min, int max) => switch (error) {
      ProgramFieldError.required => l10n.programErrorRequired,
      ProgramFieldError.outOfRange => l10n.programErrorRange(min, max),
      null => null,
    };
    Widget section(String text) => Padding(
      padding: const EdgeInsets.only(top: 20, bottom: 8),
      child: Semantics(header: true, child: Text(text, style: theme.textTheme.titleMedium)),
    );
    Widget choices<T>(List<T> values, T selected, String Function(T) label, void Function(T) onSelect) => Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final value in values)
          ChoiceChip(selected: value == selected, label: Text(label(value)), onSelected: (_) => onSelect(value)),
      ],
    );
    Widget stepper(String label, int value, String text, void Function(int) onChanged) => Row(
      children: [
        Expanded(child: Text(label)),
        IconButton(
          tooltip: l10n.programDecrease(label),
          icon: const Icon(Icons.remove),
          onPressed: () => onChanged(value - 1),
        ),
        Semantics(liveRegion: true, child: Text(text)),
        IconButton(
          tooltip: l10n.programIncrease(label),
          icon: const Icon(Icons.add),
          onPressed: () => onChanged(value + 1),
        ),
      ],
    );
    final bmi = form.bmi;
    final preview = state.preview;
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(tooltip: l10n.back, icon: const Icon(Icons.arrow_back), onPressed: onBack),
        title: Text(l10n.programCreateTitle),
      ),
      body: PageBody(
        children: [
          if (state.contentMissing) ...[
            ErrorView(error: DataError.failed, onRetry: () => onIntent(const ProgramCreateLoad())),
          ] else ...[
            SyncedTextField(
              value: form.ageText,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(
                labelText: l10n.programAgeLabel,
                helperText: l10n.programHintRange(ProgramProfile.ageRange.first, ProgramProfile.ageRange.last),
                errorText: fieldError(form.ageError, ProgramProfile.ageRange.first, ProgramProfile.ageRange.last),
              ),
              onChanged: (text) => _change((f) => f.copyWith(ageText: ProgramForm.digits(text, 2))),
            ),
            const SizedBox(height: 12),
            SyncedTextField(
              value: form.weightText,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: l10n.programWeightLabel,
                helperText: l10n.programHintRange(
                  ProgramProfile.minWeightKg.toInt(),
                  ProgramProfile.maxWeightKg.toInt(),
                ),
                errorText: fieldError(
                  form.weightError,
                  ProgramProfile.minWeightKg.toInt(),
                  ProgramProfile.maxWeightKg.toInt(),
                ),
              ),
              onChanged: (text) => _change((f) => f.copyWith(weightText: ProgramForm.decimal(text))),
            ),
            const SizedBox(height: 12),
            SyncedTextField(
              value: form.heightText,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(
                labelText: l10n.programHeightLabel,
                helperText: l10n.programHintRange(
                  ProgramProfile.heightRangeCm.first,
                  ProgramProfile.heightRangeCm.last,
                ),
                errorText: fieldError(
                  form.heightError,
                  ProgramProfile.heightRangeCm.first,
                  ProgramProfile.heightRangeCm.last,
                ),
              ),
              onChanged: (text) => _change((f) => f.copyWith(heightText: ProgramForm.digits(text, 3))),
            ),
            if (bmi != null) ...[
              const SizedBox(height: 8),
              Text(l10n.programBmi(formatOneDecimal(bmi, languageOf(l10n)))),
            ],
            if (state.isLowImpact) InfoNote(l10n.programLowImpactNote),
            section(l10n.programLevelLabel),
            choices(
              ProgramLevel.values,
              form.level,
              (v) => levelLabel(v, l10n),
              (v) => _change((f) => f.copyWith(level: v)),
            ),
            section(l10n.programGoalLabel),
            choices(
              ProgramGoal.values,
              form.goal,
              (v) => goalLabel(v, l10n),
              (v) => _change((f) => f.copyWith(goal: v)),
            ),
            if (form.showUnderweightWarning) InfoNote(l10n.programUnderweightWarning),
            section(l10n.programFocusLabel),
            RadioGroup<ProgramFocus>(
              groupValue: form.focus,
              onChanged: (focus) {
                if (focus != null) _change((f) => f.copyWith(focus: focus));
              },
              child: Column(
                children: [
                  for (final focus in ProgramFocus.values)
                    RadioListTile<ProgramFocus>(
                      value: focus,
                      title: Text(focusLabel(focus, l10n)),
                      subtitle: Text(focusDescription(focus, l10n)),
                    ),
                ],
              ),
            ),
            section(l10n.programLocationLabel),
            choices(
              ProgramLocation.values,
              form.location,
              (v) => locationLabel(v, l10n),
              (v) => _change((f) => f.copyWith(location: v)),
            ),
            const SizedBox(height: 16),
            stepper(
              l10n.programWeeksLabel,
              form.weeks,
              l10n.programWeeks(form.weeks),
              (v) => _change((f) => f.copyWith(weeks: v)),
            ),
            stepper(
              l10n.programDaysLabel,
              form.daysPerWeek,
              l10n.programDaysPerWeek(form.daysPerWeek),
              (v) => _change((f) => f.copyWith(daysPerWeek: v)),
            ),
            const SizedBox(height: 16),
            InfoNote(l10n.programDisclaimer),
            if (state.createFailed) Text(l10n.programCreateFailed, style: TextStyle(color: theme.colorScheme.error)),
            if (preview != null) ...[
              section(l10n.programWeekTitle(1)),
              for (final workout in preview)
                Card(
                  child: ListTile(
                    title: Text(workoutTitle(workout, l10n)),
                    subtitle: Text(workoutSummary(workout, l10n)),
                  ),
                ),
            ],
            const SizedBox(height: 16),
            WideButton(
              label: l10n.programPreview,
              filled: false,
              onPressed: () => onIntent(const ProgramPreviewClicked()),
            ),
            const SizedBox(height: 12),
            WideButton(
              label: l10n.programCreateButton,
              onPressed: state.isWorking || state.content == null ? null : () => onIntent(const ProgramCreateClicked()),
            ),
          ],
        ],
      ),
    );
  }
}
