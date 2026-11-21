import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../domain/calendar_dates.dart';
import '../../domain/training.dart';
import '../../domain/weekly_progress.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import '../train/photo_widgets.dart';
import '../train/train_widgets.dart';
import '../workout_name_text.dart';
import 'history_bloc.dart';

class HistoryPage extends StatelessWidget {
  const HistoryPage({required this.uid, super.key});

  final String uid;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    return BlocProvider(
      create: (_) => HistoryBloc(
        uid: uid,
        data: services.userData,
        content: services.content,
        deleteEntry: services.trainingDeps == null
            ? null
            : (entry) => services.training.service.deleteEntry(context.read<WebUserHolder>().user, entry),
      )..add(const HistoryLoad()),
      child: Builder(
        builder: (context) => EffectListener<HistoryBloc, HistoryEffect>(
          onEffect: (context, effect) =>
              ScaffoldMessenger.of(context)
                  .showSnackBar(SnackBar(content: Text(AppLocalizations.of(context).loadFailed))),
          child: BlocBuilder<HistoryBloc, HistoryState>(
            builder: (context, state) => HistoryContent(
              state: state,
              today: services.dates.today(),
              onRetry: () => context.read<HistoryBloc>().add(const HistoryLoad()),
              onLoadMore: () => context.read<HistoryBloc>().add(const HistoryLoadMore()),
              onDelete: services.trainingDeps == null
                  ? null
                  : (entry) => context.read<HistoryBloc>().add(HistoryDelete(entry)),
              onEdit: services.trainingDeps == null ? null : (entry) => context.go(Routes.editActivity(entry.id)),
              photoOwner: services.trainingDeps == null ? null : uid,
            ),
          ),
        ),
      ),
    );
  }
}

class HistoryContent extends StatelessWidget {
  const HistoryContent({
    required this.state,
    required this.today,
    required this.onRetry,
    required this.onLoadMore,
    this.onDelete,
    this.onEdit,
    this.photoOwner,
    super.key,
  });

  final HistoryState state;

  /// Whose photos of this browser the rows show (null: none, e.g. without training).
  final String? photoOwner;
  final String today;
  final VoidCallback onRetry;
  final VoidCallback onLoadMore;

  /// Deleting and editing my entries (null: read only).
  final void Function(TrainingEntry entry)? onDelete;
  final void Function(TrainingEntry entry)? onEdit;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final error = state.error;
    final days = <String, List<TrainingEntry>>{};
    for (final entry in state.entries) {
      (days[entry.dateLocal] ??= []).add(entry);
    }
    final dates = days.keys.toList()..sort((a, b) => b.compareTo(a));
    return PageBody(
      children: [
        PageTitle(l10n.historyTitle),
        if (error != null)
          ErrorView(error: error, onRetry: onRetry)
        else if (state.isLoading)
          const LoadingView()
        else if (state.isEmpty)
          EmptyView(text: l10n.historyEmpty)
        else ...[
          for (final date in dates) ...[
            _DayHeader(date: date, today: today),
            for (final entry in days[date]!..sort((a, b) => b.completedAt.compareTo(a.completedAt)))
              _EntryRow(entry: entry, state: state, onDelete: onDelete, onEdit: onEdit, photoOwner: photoOwner),
          ],
          if (state.hasMore)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Center(
                child: state.isLoadingMore
                    ? const CircularProgressIndicator()
                    : OutlinedButton(
                        onPressed: onLoadMore,
                        style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
                        child: Text(l10n.historyLoadMore),
                      ),
              ),
            ),
        ],
      ],
    );
  }
}

/// "Today", "Yesterday" or "Monday, 19 October" (the device's language).
class _DayHeader extends StatelessWidget {
  const _DayHeader({required this.date, required this.today});

  final String date;
  final String today;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final text = date == today
        ? l10n.historyToday
        : date == CalendarDates.plusDays(today, -1)
        ? l10n.historyYesterday
        : DateFormat.MMMMEEEEd(locale).format(CalendarDates.toUtc(date));
    return Padding(
      padding: const EdgeInsets.only(top: 16, bottom: 4),
      child: Semantics(header: true, child: Text(text, style: Theme.of(context).textTheme.titleSmall)),
    );
  }
}

class _EntryRow extends StatelessWidget {
  const _EntryRow({required this.entry, required this.state, this.onDelete, this.onEdit, this.photoOwner});

  final String? photoOwner;
  final TrainingEntry entry;
  final HistoryState state;
  final void Function(TrainingEntry entry)? onDelete;
  final void Function(TrainingEntry entry)? onEdit;

  Future<void> _confirmDelete(BuildContext context, String name) async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.historyDeleteTitle),
        content: Text(l10n.historyDeleteMessage(name)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
          TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.historyDeleteConfirm)),
        ],
      ),
    );
    if (confirmed == true) onDelete?.call(entry);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final duration = entry.durationSeconds;
    final subtitle = [sourceLabel(entry.source, l10n), if (duration != null) durationText(duration, l10n)].join(' · ');
    final time = DateFormat.jm(locale).format(DateTime.fromMillisecondsSinceEpoch(entry.completedAt));
    final name = state.nameOf(entry).text(l10n);
    final canEdit = onEdit != null && entry.isActivity;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: ExcludeSemantics(child: Icon(sourceIcon(entry.source))),
      title: Text(name),
      onTap: canEdit ? () => onEdit!(entry) : null,
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [Text(subtitle), if (!countsTowardGoal(entry)) Text(l10n.historyNotCounted)],
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(time),
          // The photo when this browser has it (a photo added on the phone stays there)
          if (photoOwner != null) HistoryPhotoThumbnail(uid: photoOwner!, entry: entry),
          if (onDelete != null)
            PopupMenuButton<String>(
              tooltip: l10n.historyDeleteAction,
              onSelected: (action) => action == 'edit' ? onEdit!(entry) : _confirmDelete(context, name),
              itemBuilder: (_) => [
                if (canEdit) PopupMenuItem(value: 'edit', child: Text(l10n.historyEditActivity)),
                PopupMenuItem(value: 'delete', child: Text(l10n.historyDeleteAction)),
              ],
            ),
        ],
      ),
    );
  }
}
