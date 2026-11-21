import 'dart:async';

import '../../app/groups_service.dart';
import '../../core/mvi.dart';
import '../../data/content_repository.dart';
import '../../data/repositories.dart';
import '../../domain/calendar_dates.dart';
import '../../domain/groups.dart';
import '../../domain/workout_names.dart';

// ---------- Intents ----------

sealed class FeedIntent {
  const FeedIntent();
}

final class FeedVisible extends FeedIntent {
  const FeedVisible();
}

final class FeedHidden extends FeedIntent {
  const FeedHidden();
}

final class FeedLoadMore extends FeedIntent {
  const FeedLoadMore();
}

/// Tap a reaction: the same again removes it, another changes it ([ReactionRules.toggle]).
final class FeedReactionTapped extends FeedIntent {
  const FeedReactionTapped(this.postId, this.reaction);

  final String postId;
  final Reaction reaction;
}

/// Delete a post: mine, or any as the owner (the screen asked first).
final class FeedDeletePost extends FeedIntent {
  const FeedDeletePost(this.postId);

  final String postId;
}

/// "Hide for me": this browser only.
final class FeedHidePost extends FeedIntent {
  const FeedHidePost(this.postId);

  final String postId;
}

final class _PostsChanged extends FeedIntent {
  const _PostsChanged(this.posts);

  final List<FeedPost> posts;
}

final class _NamesLoaded extends FeedIntent {
  const _NamesLoaded(this.names);

  final WorkoutNameCatalog names;
}

final class _FeedFailed extends FeedIntent {
  const _FeedFailed(this.error);

  final DataError error;
}

final class _ReactionAnswered extends FeedIntent {
  const _ReactionAnswered(this.postId, {required this.failed});

  final String postId;
  final bool failed;
}

// ---------- State ----------

class FeedState {
  const FeedState({
    this.isLoading = true,
    this.posts = const [],
    this.weekStart = '',
    this.isFirstDayOfWeek = false,
    this.canLoadMore = false,
    this.names = WorkoutNameCatalog.empty,
    this.error,
  });

  final bool isLoading;

  /// This week's posts, newest first, with my reactions as I tapped them.
  final List<FeedPost> posts;
  final String weekStart;

  /// Today is Monday: the empty feed says a new week started.
  final bool isFirstDayOfWeek;
  final bool canLoadMore;

  /// The content's gym and daily names, to name posts by their titleKey.
  final WorkoutNameCatalog names;
  final DataError? error;

  bool get isEmpty => !isLoading && error == null && posts.isEmpty;

  /// What [post] is called for this reader: its titleKey when known here, else its title.
  WorkoutName nameOf(FeedPost post) => WorkoutNameResolver.nameOfPost(
    titleKey: post.titleKey,
    source: post.source,
    activityType: post.activityType,
    title: post.title,
    catalog: names,
  );

  FeedState copyWith({
    bool? isLoading,
    List<FeedPost>? posts,
    String? weekStart,
    bool? isFirstDayOfWeek,
    bool? canLoadMore,
    WorkoutNameCatalog? names,
    DataError? error,
    bool clearError = false,
  }) => FeedState(
    isLoading: isLoading ?? this.isLoading,
    posts: posts ?? this.posts,
    weekStart: weekStart ?? this.weekStart,
    isFirstDayOfWeek: isFirstDayOfWeek ?? this.isFirstDayOfWeek,
    canLoadMore: canLoadMore ?? this.canLoadMore,
    names: names ?? this.names,
    error: clearError ? null : (error ?? this.error),
  );
}

// ---------- Effects ----------

sealed class FeedEffect {
  const FeedEffect();
}

final class FeedPostDeleted extends FeedEffect {
  const FeedPostDeleted();
}

final class FeedPostHidden extends FeedEffect {
  const FeedPostHidden();
}

final class FeedModerationFailed extends FeedEffect {
  const FeedModerationFailed(this.error);

  final GroupError error;
}

/// My reaction couldn't be saved (rolled back); [error] says why.
final class FeedReactionFailed extends FeedEffect {
  const FeedReactionFailed(this.error);

  final DataError error;
}

/// The "Feed" tab of a group: this week's posts (the reader's current week) live only while
/// visible, 20 more per "Load more" (Android's GroupFeedViewModel). Reactions are optimistic and
/// rolled back when the write fails; the only write is `reactions.<myUid>`.
class FeedBloc extends MviBloc<FeedIntent, FeedState, FeedEffect> {
  FeedBloc({
    required this.groupId,
    required this._uid,
    required this._groups,
    required this._content,
    required this._dates,
    this._service,
    this.isOwner = false,
  }) : super(const FeedState()) {
    on<FeedDeletePost>((intent, emit) async {
      final service = _service;
      final post = _serverPosts.where((p) => p.id == intent.postId).firstOrNull;
      if (service == null || post == null || (post.authorUid != _uid && !isOwner)) return;
      try {
        await service.deletePost(groupId, post.id);
        sendEffect(const FeedPostDeleted());
      } on GroupException catch (e) {
        sendEffect(FeedModerationFailed(e.error));
      }
    });
    on<FeedHidePost>((intent, emit) {
      final service = _service;
      if (service == null) return;
      service.hidePost(_uid, groupId, intent.postId);
      emit(_shown());
      sendEffect(const FeedPostHidden());
    });
    on<FeedVisible>((_, _) => _listen());
    on<FeedHidden>((_, _) => _stop());
    on<FeedLoadMore>((_, _) {
      if (!state.canLoadMore) return;
      _pages++;
      _listen();
    });
    on<_PostsChanged>((intent, emit) {
      _serverPosts = intent.posts;
      // A pending reaction the posts already show is done
      for (final post in intent.posts) {
        if (_pending.containsKey(post.id) && _answered.contains(post.id) && post.reactions[_uid] == _pending[post.id]) {
          _pending.remove(post.id);
          _answered.remove(post.id);
        }
      }
      emit(_shown(isLoading: false));
    });
    on<_NamesLoaded>((intent, emit) => emit(state.copyWith(names: intent.names)));
    on<_FeedFailed>((intent, emit) {
      emit(state.copyWith(isLoading: false, error: intent.error));
      _stop();
    });
    on<FeedReactionTapped>(_onReaction);
    on<_ReactionAnswered>((intent, emit) {
      if (intent.failed) {
        // Rolled back to what the server has
        _pending.remove(intent.postId);
        _answered.remove(intent.postId);
        emit(_shown());
        return;
      }
      final server = _serverPosts.where((p) => p.id == intent.postId).firstOrNull;
      if (server == null || server.reactions[_uid] == _pending[intent.postId]) {
        _pending.remove(intent.postId);
      } else {
        // Kept until the listener shows the written value
        _answered.add(intent.postId);
      }
    });
  }

  final String groupId;
  final String _uid;
  final GroupsRepository _groups;
  final ContentRepository _content;
  final LocalDates _dates;
  final GroupsService? _service;

  /// The group's owner may delete any post.
  final bool isOwner;

  /// Moderation is offered (not in read-only tests).
  bool get canModerate => _service != null;
  StreamSubscription<List<FeedPost>>? _subscription;
  int _pages = 1;
  List<FeedPost> _serverPosts = const [];
  bool _namesRequested = false;

  /// My reactions tapped but not yet shown by the listener (null: removed).
  final Map<String, Reaction?> _pending = {};

  /// Pending reactions whose write succeeded, waiting for the listener.
  final Set<String> _answered = {};

  void _listen() {
    _stop();
    final today = _dates.today();
    final weekStart = CalendarDates.mondayOf(today);
    // A new week while hidden starts again from the first page
    if (_weekStart.isNotEmpty && _weekStart != weekStart) _pages = 1;
    _weekStart = weekStart;
    _firstDay = today == weekStart;
    if (!_namesRequested) {
      _namesRequested = true;
      _content.catalog().then((names) {
        if (!isClosed) add(_NamesLoaded(names));
      });
    }
    _subscription = _groups
        .watchWeekPosts(groupId, weekStart, FeedLimits.pageSize * _pages)
        .listen(
          (posts) => add(_PostsChanged(posts)),
          onError: (Object e) => add(_FeedFailed(e is DataException ? e.error : DataError.failed)),
        );
  }

  String _weekStart = '';
  bool _firstDay = false;

  void _stop() {
    _subscription?.cancel();
    _subscription = null;
  }

  FeedState _shown({bool? isLoading}) => state.copyWith(
    isLoading: isLoading,
    weekStart: _weekStart,
    isFirstDayOfWeek: _firstDay,
    posts: _visible(currentWeekPosts(_serverPosts, weekStart: _weekStart, myUid: _uid, pendingReactions: _pending)),
    canLoadMore: _serverPosts.length >= FeedLimits.pageSize * _pages,
    clearError: true,
  );

  /// Without the posts hidden for me in this browser.
  List<FeedPost> _visible(List<FeedPost> posts) {
    final hidden = _service?.hiddenPosts(_uid) ?? const <String>{};
    return hidden.isEmpty
        ? posts
        : [
            for (final p in posts)
              if (!hidden.contains('$groupId/${p.id}')) p,
          ];
  }

  Future<void> _onReaction(FeedReactionTapped intent, Emitter<FeedState> emit) async {
    final post = state.posts.where((p) => p.id == intent.postId).firstOrNull;
    if (post == null) return;
    final mine = ReactionRules.toggle(post.reactions[_uid], intent.reaction);
    _pending[post.id] = mine;
    _answered.remove(post.id);
    emit(_shown());
    try {
      await _groups.setReaction(groupId, post.id, _uid, mine);
      add(_ReactionAnswered(post.id, failed: false));
    } on DataException catch (e) {
      add(_ReactionAnswered(post.id, failed: true));
      sendEffect(FeedReactionFailed(e.error));
    }
  }

  @override
  Future<void> close() {
    _stop();
    return super.close();
  }
}
