import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../domain/activity_entry.dart';
import '../../domain/calendar_dates.dart';
import '../../domain/photos.dart';
import '../../domain/training.dart';
import '../../domain/weekly_progress.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import '../workout_name_text.dart';
import 'log_activity_bloc.dart';
import 'photo_widgets.dart';
import 'train_widgets.dart';

/// Android's `activityTypeIcon`: a decorative emoji per type.
String activityEmoji(ActivityType type) => switch (type) {
  ActivityType.ownGymRoutine => '🏋️',
  ActivityType.running => '🏃',
  ActivityType.walking => '🚶',
  ActivityType.cycling => '🚴',
  ActivityType.swimming => '🏊',
  ActivityType.yoga => '🧘',
  ActivityType.pilates => '🤸',
  ActivityType.dance => '💃',
  ActivityType.teamSport => '🏀',
  ActivityType.racketSport => '🎾',
  ActivityType.martialArts => '🥋',
  ActivityType.hiit => '🔥',
  ActivityType.stretching => '🙆',
  ActivityType.other => '⭐',
};

String intensityLabel(Intensity intensity, AppLocalizations l10n) => switch (intensity) {
  Intensity.easy => l10n.intensityEasy,
  Intensity.moderate => l10n.intensityModerate,
  Intensity.hard => l10n.intensityHard,
};

String intensityDescription(Intensity intensity, AppLocalizations l10n) => switch (intensity) {
  Intensity.easy => l10n.intensityEasyDescription,
  Intensity.moderate => l10n.intensityModerateDescription,
  Intensity.hard => l10n.intensityHardDescription,
};

class LogActivityPage extends StatelessWidget {
  const LogActivityPage({this.entryId, super.key});

  final String? entryId;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final user = context.read<WebUserHolder>().user;
    final back = entryId == null ? Routes.train : Routes.history;
    return BlocProvider(
      create: (_) =>
          LogActivityBloc(user: user, deps: services.training, dates: services.dates, entryId: entryId)
            ..add(const LogActivityLoad()),
      child: EffectListener<LogActivityBloc, LogActivityEffect>(
        onEffect: (context, effect) => switch (effect) {
          LogActivityNavigateBack() => context.go(back),
          LogActivityPhotoFailed(:final error) => showPhotoError(context, error),
        },
        child: _PhotoDrops(
          child: BlocBuilder<LogActivityBloc, LogActivityState>(
            builder: (context, state) => LogActivityContent(
              state: state,
              onIntent: context.read<LogActivityBloc>().add,
              onAddPhoto: () async {
                final bloc = context.read<LogActivityBloc>();
                final picked = await services.training.photoPicker.pick();
                if (picked != null && !bloc.isClosed) bloc.add(LogActivityPhotoPicked(picked));
              },
              showDropHint: showsDropHint(services.training.device),
            ),
          ),
        ),
      ),
    );
  }
}

/// Photos dropped on the page while the form is open (desktop) go to the form.
class _PhotoDrops extends StatefulWidget {
  const _PhotoDrops({required this.child});

  final Widget child;

  @override
  State<_PhotoDrops> createState() => _PhotoDropsState();
}

class _PhotoDropsState extends State<_PhotoDrops> {
  StreamSubscription<PickedPhoto>? _drops;

  @override
  void initState() {
    super.initState();
    final bloc = context.read<LogActivityBloc>();
    _drops = context.read<AppServices>().training.photoPicker.drops.listen((picked) {
      if (!bloc.isClosed) bloc.add(LogActivityPhotoPicked(picked));
    });
  }

  @override
  void dispose() {
    unawaited(_drops?.cancel());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

class LogActivityContent extends StatelessWidget {
  const LogActivityContent({
    required this.state,
    required this.onIntent,
    this.onAddPhoto,
    this.showDropHint = false,
    super.key,
  });

  final LogActivityState state;
  final void Function(LogActivityIntent) onIntent;

  /// Opens the file chooser (from the tap); null where photos can't be added.
  final VoidCallback? onAddPhoto;
  final bool showDropHint;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) onIntent(const LogActivityBack());
      },
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            tooltip: l10n.back,
            icon: const Icon(Icons.arrow_back),
            onPressed: () => onIntent(const LogActivityBack()),
          ),
          title: Text(state.isEditMode ? l10n.logActivityTitleEdit : l10n.logActivityTitleNew),
          actions: [
            if (state.isEditMode)
              IconButton(
                tooltip: l10n.logActivityDelete,
                icon: const Icon(Icons.delete_outline),
                onPressed: () => onIntent(const LogActivityDeleteClicked()),
              ),
          ],
        ),
        body: Stack(
          children: [
            PageBody(children: _body(context, l10n)),
            _Dialogs(state: state, onIntent: onIntent),
          ],
        ),
      ),
    );
  }

  List<Widget> _body(BuildContext context, AppLocalizations l10n) {
    if (state.isLoading) return const [LoadingView()];
    if (state.notFound) return [EmptyView(text: l10n.logActivityNotFound)];
    final form = state.form!;
    final theme = Theme.of(context);
    final errors = state.showErrors ? state.errors : const <ActivityEntryError>{};
    Widget section(String text) => Padding(
      padding: const EdgeInsets.only(top: 20, bottom: 8),
      child: Semantics(header: true, child: Text(text, style: theme.textTheme.titleMedium)),
    );
    Widget error(String text) => Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Text(text, style: TextStyle(color: theme.colorScheme.error)),
    );
    final locale = Localizations.localeOf(context).toLanguageTag();
    final time = MaterialLocalizations.of(context).formatTimeOfDay(
      TimeOfDay(hour: form.hour, minute: form.minute),
      alwaysUse24HourFormat: MediaQuery.alwaysUse24HourFormatOf(context),
    );
    return [
      section(l10n.logActivityType),
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (final type in ActivityType.values)
            FilterChip(
              selected: form.type == type,
              avatar: ExcludeSemantics(child: Text(activityEmoji(type))),
              label: Text(activityLabel(type, l10n)),
              onSelected: (_) => onIntent(LogActivityTypeSelected(type)),
            ),
        ],
      ),
      if (errors.contains(ActivityEntryError.typeMissing)) error(l10n.logActivityErrorType),
      if (form.type == ActivityType.other) ...[
        const SizedBox(height: 12),
        SyncedTextField(
          value: form.customName,
          maxLength: ActivityEntryRules.nameMaxLength,
          decoration: InputDecoration(
            labelText: l10n.logActivityCustomName,
            errorText: errors.contains(ActivityEntryError.nameMissing)
                ? l10n.logActivityErrorName
                : (errors.contains(ActivityEntryError.nameTooLong)
                      ? l10n.logActivityErrorTooLong(ActivityEntryRules.nameMaxLength)
                      : null),
            counterText: l10n.logActivityCounter(form.customName.length, ActivityEntryRules.nameMaxLength),
          ),
          onChanged: (text) => onIntent(LogActivityNameChanged(text)),
        ),
      ],
      section(l10n.logActivityDuration),
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (final minutes in LogActivityForm.quickDurations)
            ChoiceChip(
              selected: form.durationText == '$minutes',
              label: Text(l10n.logActivityMinutesShort(minutes)),
              onSelected: (_) => onIntent(LogActivityQuickDuration(minutes)),
            ),
        ],
      ),
      const SizedBox(height: 12),
      SyncedTextField(
        value: form.durationText,
        keyboardType: TextInputType.number,
        inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(3)],
        decoration: InputDecoration(
          labelText: l10n.logActivityMinutesField,
          errorText: errors.contains(ActivityEntryError.durationOutOfRange)
              ? l10n.logActivityErrorDuration(
                  ActivityEntryRules.minDurationMinutes,
                  ActivityEntryRules.maxDurationMinutes,
                )
              : null,
          helperText: state.showsShortDurationHint
              ? l10n.logActivityShortDurationHint(minCountedDurationSeconds ~/ 60)
              : null,
        ),
        onChanged: (text) => onIntent(LogActivityDurationChanged(text)),
      ),
      section(l10n.logActivityIntensity),
      SegmentedButton<Intensity>(
        emptySelectionAllowed: true,
        segments: [
          for (final intensity in Intensity.values)
            ButtonSegment(
              value: intensity,
              label: Semantics(
                label: l10n.logActivityIntensityOption(
                  intensityLabel(intensity, l10n),
                  intensityDescription(intensity, l10n),
                ),
                excludeSemantics: true,
                child: Text(intensityLabel(intensity, l10n)),
              ),
            ),
        ],
        selected: {?form.intensity},
        onSelectionChanged: (selected) {
          if (selected.isNotEmpty) onIntent(LogActivityIntensitySelected(selected.first));
        },
      ),
      const SizedBox(height: 8),
      for (final intensity in Intensity.values)
        ExcludeSemantics(
          child: Text(
            l10n.logActivityIntensityOption(intensityLabel(intensity, l10n), intensityDescription(intensity, l10n)),
            style: theme.textTheme.bodySmall?.copyWith(
              fontWeight: form.intensity == intensity ? FontWeight.bold : FontWeight.normal,
            ),
          ),
        ),
      if (errors.contains(ActivityEntryError.intensityMissing)) error(l10n.logActivityErrorIntensity),
      section(l10n.logActivityWhen),
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (final date in state.dayOptions)
            ChoiceChip(
              selected: form.date == date,
              label: Text(switch (dayLabelOf(date, state.today)) {
                DayLabel.today => l10n.historyToday,
                DayLabel.yesterday => l10n.historyYesterday,
                DayLabel.date => DateFormat.MMMEd(locale).format(CalendarDates.toUtc(date)),
              }),
              onSelected: (_) => onIntent(LogActivityDateSelected(date)),
            ),
        ],
      ),
      const SizedBox(height: 12),
      Align(
        alignment: Alignment.centerLeft,
        child: Semantics(
          button: true,
          label: l10n.logActivityTimeChange(time),
          excludeSemantics: true,
          child: OutlinedButton.icon(
            style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
            icon: const Icon(Icons.schedule),
            label: Text(l10n.logActivityTime(time)),
            onPressed: () async {
              final picked = await showTimePicker(
                context: context,
                initialTime: TimeOfDay(hour: form.hour, minute: form.minute),
              );
              if (picked != null) onIntent(LogActivityTimeSelected(picked.hour, picked.minute));
            },
          ),
        ),
      ),
      if (errors.contains(ActivityEntryError.dateInFuture)) error(l10n.logActivityErrorFuture),
      if (errors.contains(ActivityEntryError.dateTooEarly))
        error(l10n.logActivityErrorTooEarly(ActivityEntryRules.daysBack + 1)),
      section(l10n.logActivityNote),
      SyncedTextField(
        value: form.note,
        maxLines: 3,
        maxLength: ActivityEntryRules.noteMaxLength,
        decoration: InputDecoration(
          counterText: l10n.logActivityCounter(form.note.length, ActivityEntryRules.noteMaxLength),
        ),
        onChanged: (text) => onIntent(LogActivityNoteChanged(text)),
      ),
      // Android's photo row: optional, kept on Save, discarded with the form
      if (onAddPhoto != null) ...[
        const SizedBox(height: 8),
        Builder(
          builder: (context) {
            final description = photoDescription(
              l10n,
              takenAt: form.toInput().completedAt,
              locale: Localizations.localeOf(context).toLanguageTag(),
            );
            return PhotoPanel(
              thumb: form.photo.thumb,
              isProcessing: state.isProcessingPhoto,
              onAdd: onAddPhoto!,
              onRemove: () async {
                if (await confirmPhotoRemoval(context)) onIntent(const LogActivityPhotoRemoved());
              },
              onOpen: () {
                final full = switch (form.photo) {
                  StoredFormPhoto(:final photo) => photo.full,
                  StagedFormPhoto(:final photo) => photo.full,
                  NoFormPhoto() => null,
                };
                if (full != null) showPhotoViewer(context, full, description);
              },
              description: description,
              showDropHint: showDropHint,
            );
          },
        ),
        const SizedBox(height: 8),
      ],
      if (state.canShare && !state.isEditMode)
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          value: state.share,
          onChanged: (share) => onIntent(LogActivityShareChanged(share)),
          title: Text(l10n.shareInGroups),
          subtitle: Text(state.share ? l10n.shareInGroupsOn : l10n.shareInGroupsOff),
        ),
      if (state.saveFailed) error(l10n.logActivitySaveFailed),
      const SizedBox(height: 16),
      WideButton(
        label: l10n.logActivitySave,
        onPressed: state.isSaving || state.isProcessingPhoto ? null : () => onIntent(const LogActivitySave()),
      ),
      if (state.isEditMode) ...[
        const SizedBox(height: 12),
        WideButton(
          label: l10n.logActivityDelete,
          filled: false,
          onPressed: () => onIntent(const LogActivityDeleteClicked()),
        ),
      ],
    ];
  }
}

/// Android's dialogs: discard changes, delete, and the one-time share explanation.
class _Dialogs extends StatefulWidget {
  const _Dialogs({required this.state, required this.onIntent});

  final LogActivityState state;
  final void Function(LogActivityIntent) onIntent;

  @override
  State<_Dialogs> createState() => _DialogsState();
}

class _DialogsState extends State<_Dialogs> {
  LogActivityDialog _shown = LogActivityDialog.none;

  @override
  void didUpdateWidget(covariant _Dialogs oldWidget) {
    super.didUpdateWidget(oldWidget);
    final dialog = widget.state.dialog;
    if (dialog != _shown && dialog != LogActivityDialog.none) {
      _shown = dialog;
      WidgetsBinding.instance.addPostFrameCallback((_) => _show(dialog));
    } else if (dialog == LogActivityDialog.none) {
      _shown = LogActivityDialog.none;
    }
  }

  Future<void> _show(LogActivityDialog dialog) async {
    final l10n = AppLocalizations.of(context);
    final intent = await showDialog<LogActivityIntent>(
      context: context,
      builder: (context) => switch (dialog) {
        LogActivityDialog.discard => AlertDialog(
          title: Text(l10n.logActivityDiscardTitle),
          content: Text(l10n.logActivityDiscardMessage),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.logActivityKeepEditing)),
            TextButton(
              onPressed: () => Navigator.pop(context, const LogActivityConfirmDiscard()),
              child: Text(l10n.logActivityDiscardConfirm),
            ),
          ],
        ),
        LogActivityDialog.delete => AlertDialog(
          title: Text(l10n.logActivityDeleteTitle),
          content: Text(l10n.logActivityDeleteMessage),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
            TextButton(
              onPressed: () => Navigator.pop(context, const LogActivityConfirmDelete()),
              child: Text(l10n.logActivityDeleteConfirm),
            ),
          ],
        ),
        LogActivityDialog.shareIntro => AlertDialog(
          title: Text(l10n.shareIntroTitle),
          content: Text(l10n.shareIntroText),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, const LogActivityShareIntroAnswered(share: false)),
              child: Text(l10n.shareIntroDontShare),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, const LogActivityShareIntroAnswered(share: true)),
              child: Text(l10n.shareIntroOk),
            ),
          ],
        ),
        LogActivityDialog.none => const SizedBox.shrink(),
      },
    );
    widget.onIntent(intent ?? const LogActivityDismissDialog());
  }

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}
