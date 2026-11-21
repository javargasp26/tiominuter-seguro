import '../../core/mvi.dart';
import '../../data/content_repository.dart';
import '../../data/repositories.dart';
import '../../domain/training.dart';
import '../../domain/workout_names.dart';

// ---------- Intents ----------

sealed class HistoryIntent {
  const HistoryIntent();
}

final class HistoryLoad extends HistoryIntent {
  const HistoryLoad();
}

final class HistoryLoadMore extends HistoryIntent {
  const HistoryLoadMore();
}

/// Deletes my entry (its tombstone and its posts), confirmed by the person.
final class HistoryDelete extends HistoryIntent {
  const HistoryDelete(this.entry);

  final TrainingEntry entry;
}

// ---------- State ----------

class HistoryState {
  const HistoryState({
    this.isLoading = true,
    this.isLoadingMore = false,
    this.entries = const [],
    this.hasMore = false,
    this.names = WorkoutNameCatalog.empty,
    this.error,
  });

  final bool isLoading;
  final bool isLoadingMore;

  /// Newest first (by completedAt), tombstones never included.
  final List<TrainingEntry> entries;
  final bool hasMore;

  /// The content's gym and daily names plus the program workouts of the loaded entries.
  final WorkoutNameCatalog names;
  final DataError? error;

  bool get isEmpty => !isLoading && error == null && entries.isEmpty;

  WorkoutName nameOf(TrainingEntry entry) => WorkoutNameResolver.nameOf(entry, names);

  HistoryState copyWith({
    bool? isLoading,
    bool? isLoadingMore,
    List<TrainingEntry>? entries,
    bool? hasMore,
    WorkoutNameCatalog? names,
    DataError? error,
    bool clearError = false,
  }) => HistoryState(
    isLoading: isLoading ?? this.isLoading,
    isLoadingMore: isLoadingMore ?? this.isLoadingMore,
    entries: entries ?? this.entries,
    hasMore: hasMore ?? this.hasMore,
    names: names ?? this.names,
    error: clearError ? null : (error ?? this.error),
  );
}

// ---------- Effects ----------

sealed class HistoryEffect {
  const HistoryEffect();
}

/// Loading more failed while entries are on screen (they stay).
final class HistoryLoadMoreFailed extends HistoryEffect {
  const HistoryLoadMoreFailed(this.error);

  final DataError error;
}

/// "My history": `users/{uid}/trainingLog` newest first, [pageSize] at a time (one-shot reads, no
/// listener). Names follow WorkoutNameResolver: the content catalogs (read once) and, for
/// PROGRAM entries, the synced program workout their refId points to (2 reads per program
/// workout, cached).
class HistoryBloc extends MviBloc<HistoryIntent, HistoryState, HistoryEffect> {
  HistoryBloc({required this._uid, required this._data, required this._content, this.pageSize = 20, this.deleteEntry})
    : super(const HistoryState()) {
    on<HistoryDelete>((intent, emit) async {
      final delete = deleteEntry;
      if (delete == null) return;
      // At once (the write is queued offline too)
      emit(state.copyWith(entries: state.entries.where((e) => e.id != intent.entry.id).toList()));
      await delete(intent.entry);
    });
    on<HistoryLoad>((_, emit) async {
      emit(const HistoryState());
      _next = null;
      try {
        final catalog = await _content.catalog();
        final page = await _data.historyPage(_uid, limit: pageSize);
        _next = page.next;
        final names = await _withPrograms(catalog, page.entries);
        emit(HistoryState(isLoading: false, entries: page.entries, hasMore: page.next != null, names: names));
      } on DataException catch (e) {
        emit(HistoryState(isLoading: false, error: e.error));
      }
    });
    on<HistoryLoadMore>((_, emit) async {
      if (state.isLoading || state.isLoadingMore || _next == null) return;
      emit(state.copyWith(isLoadingMore: true));
      try {
        final page = await _data.historyPage(_uid, after: _next, limit: pageSize);
        _next = page.next;
        final entries = [...state.entries, ...page.entries];
        final names = await _withPrograms(state.names, entries);
        emit(state.copyWith(isLoadingMore: false, entries: entries, hasMore: page.next != null, names: names));
      } on DataException catch (e) {
        emit(state.copyWith(isLoadingMore: false));
        sendEffect(HistoryLoadMoreFailed(e.error));
      }
    });
  }

  final String _uid;
  final UserDataRepository _data;
  final ContentRepository _content;
  final int pageSize;

  /// Deleting an entry (the training service); null where the web can't delete (tests of reading).
  final Future<void> Function(TrainingEntry entry)? deleteEntry;
  Object? _next;

  Future<WorkoutNameCatalog> _withPrograms(WorkoutNameCatalog catalog, List<TrainingEntry> entries) async {
    final refIds = [
      for (final entry in entries)
        if (entry.source == TrainingSource.program && entry.refId != null) entry.refId!,
    ];
    if (refIds.isEmpty) return catalog;
    return catalog.withProgramWorkouts(await _data.programTitleCodes(_uid, refIds));
  }
}
