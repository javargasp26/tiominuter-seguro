import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/repositories.dart';
import '../../domain/calendar_dates.dart';
import '../../domain/groups.dart';
import '../../domain/training.dart';
import '../../l10n/app_localizations.dart';
import '../common/widgets.dart';
import '../workout_name_text.dart';
import '../train/train_widgets.dart' show WebUserHolder;
import 'feed_bloc.dart';
import 'group_messages.dart';
import 'groups_bloc.dart';

enum GroupTab { ranking, feed }

/// One group: the Ranking and Feed tabs. The group and members listeners run while the page is
/// visible; the feed's listener only while the Feed tab is selected (only the selected tab is built).
class GroupDetailPage extends StatelessWidget {
  const GroupDetailPage({required this.uid, required this.groupId, required this.tab, super.key});

  final String uid;
  final String groupId;
  final GroupTab tab;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final service = services.groupsService;
    final user = service == null ? null : context.read<WebUserHolder>().user;
    return BlocProvider(
      create: (_) => GroupDetailBloc(
        groupId: groupId,
        uid: uid,
        groups: services.groups,
        dates: services.dates,
        service: service,
        user: user,
      ),
      child: Builder(
        builder: (context) => BlocVisibilityScope<GroupDetailBloc>(
          onVisible: (bloc) => bloc.add(const GroupVisible()),
          onHidden: (bloc) => bloc.add(const GroupHidden()),
          child: EffectListener<GroupDetailBloc, GroupDetailEffect>(
            onEffect: (context, effect) {
              final l10n = AppLocalizations.of(context);
              switch (effect) {
                case GroupActionDone(:final done, :final argument, :final leaves):
                  showGroupMessage(context, switch (done) {
                    GroupDone.left => l10n.groupsMessageLeft,
                    GroupDone.deleted => l10n.groupsMessageDeleted,
                    GroupDone.renamed => l10n.groupsMessageRenamed,
                    GroupDone.codeRegenerated => l10n.groupsMessageCodeRegenerated(argument),
                    GroupDone.memberRemoved => l10n.groupsMessageMemberRemoved(argument),
                    GroupDone.ownerChanged => l10n.groupsMessageOwnerChanged(argument),
                  });
                  if (leaves) context.go(Routes.groups);
                case GroupActionFailed(:final error):
                  showGroupMessage(context, groupErrorText(error, l10n));
              }
            },
            child: BlocBuilder<GroupDetailBloc, GroupDetailState>(
              builder: (context, state) => GroupDetailContent(
                state: state,
                uid: uid,
                tab: tab,
                onTab: (selected) =>
                    context.go(selected == GroupTab.feed ? Routes.groupFeed(groupId) : Routes.group(groupId)),
                onBack: () => context.go(Routes.groups),
                onRetry: () => _add(context, const GroupVisible()),
                actions: state.canManage ? GroupActions(context.read<GroupDetailBloc>(), uid) : null,
                feed: tab == GroupTab.feed ? FeedTab(uid: uid, groupId: groupId, isOwner: state.isOwner(uid)) : null,
              ),
            ),
          ),
        ),
      ),
    );
  }

  static void _add(BuildContext context, GroupDetailIntent intent) {
    final bloc = context.read<GroupDetailBloc>();
    if (!bloc.isClosed) bloc.add(intent);
  }
}

class GroupDetailContent extends StatelessWidget {
  const GroupDetailContent({
    required this.state,
    required this.uid,
    required this.tab,
    required this.onTab,
    required this.onBack,
    required this.onRetry,
    this.feed,
    this.actions,
    super.key,
  });

  /// Leave and the owner's actions (null: read only).
  final GroupActions? actions;

  final GroupDetailState state;
  final String uid;
  final GroupTab tab;
  final void Function(GroupTab tab) onTab;
  final VoidCallback onBack;
  final VoidCallback onRetry;

  /// The Feed tab's content (a slot, so tests render the ranking alone).
  final Widget? feed;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final error = state.error;
    final Widget body;
    if (state.isGone) {
      body = PageBody(children: [EmptyView(text: l10n.groupsGone)]);
    } else if (error != null) {
      body = PageBody(
        children: [ErrorView(error: error, onRetry: onRetry)],
      );
    } else if (tab == GroupTab.feed) {
      body = feed ?? const SizedBox.shrink();
    } else if (state.isLoading) {
      body = const PageBody(children: [LoadingView()]);
    } else {
      final group = state.group;
      final isOwner = state.isOwner(uid);
      final manage = actions;
      body = PageBody(
        children: [
          Text(l10n.groupsThisWeek, style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          for (final line in state.ranking)
            _RankingRow(
              line: line,
              weekStart: state.weekStart,
              onMenu: manage != null && isOwner && !line.isMe && !state.isBusy
                  ? (choice) => manage.onMember(context, state, line.member, choice)
                  : null,
            ),
          if (group != null && group.inviteCode.isNotEmpty) ...[
            const SizedBox(height: 16),
            InviteCodeCard(groupName: group.name, code: group.inviteCode),
          ],
          if (manage != null) ...[
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: state.isBusy ? null : () => manage.leave(context, state),
              style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48)),
              icon: const Icon(Icons.logout),
              label: Text(l10n.groupsLeave),
            ),
            const SizedBox(height: 8),
            Text(l10n.groupsPostsNote, style: Theme.of(context).textTheme.bodySmall),
          ],
        ],
      );
    }
    final ownerMenu = actions != null && state.isOwner(uid) && !state.isGone && state.group != null;
    return Scaffold(
      appBar: AppBar(
        leading: BackButton(onPressed: onBack),
        title: Text(state.group?.name ?? l10n.groupsTitle),
        actions: [
          if (ownerMenu)
            PopupMenuButton<OwnerAction>(
              tooltip: l10n.groupsOwnerMenu,
              enabled: !state.isBusy,
              onSelected: (choice) => actions!.onOwner(context, state, choice),
              itemBuilder: (_) => [
                PopupMenuItem(value: OwnerAction.rename, child: Text(l10n.groupsRename)),
                PopupMenuItem(value: OwnerAction.regenerate, child: Text(l10n.groupsRegenerate)),
                if (state.others(uid).isNotEmpty)
                  PopupMenuItem(value: OwnerAction.transfer, child: Text(l10n.groupsTransfer)),
                PopupMenuItem(value: OwnerAction.delete, child: Text(l10n.groupsDelete)),
              ],
            ),
        ],
        bottom: state.isGone
            ? null
            : PreferredSize(
                preferredSize: const Size.fromHeight(56),
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: SegmentedButton<GroupTab>(
                    segments: [
                      ButtonSegment(value: GroupTab.ranking, label: Text(l10n.groupsTabRanking)),
                      ButtonSegment(value: GroupTab.feed, label: Text(l10n.groupsTabFeed)),
                    ],
                    selected: {tab},
                    showSelectedIcon: false,
                    onSelectionChanged: (selected) => onTab(selected.single),
                  ),
                ),
              ),
      ),
      body: body,
    );
  }
}

class _RankingRow extends StatelessWidget {
  const _RankingRow({required this.line, required this.weekStart, this.onMenu});

  final RankedMember line;
  final String weekStart;

  /// The owner's menu for this member (make owner, remove).
  final void Function(MemberAction choice)? onMenu;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final member = line.member;
    final name = line.isMe ? '${member.displayName} (${l10n.groupsMe})' : member.displayName;
    final goal = member.summary.weeklyGoal;
    final progressText = line.isCurrentWeek ? l10n.groupsProgress(line.workouts, goal) : l10n.groupsNotUpdated;
    return Card(
      color: line.isMe ? theme.colorScheme.secondaryContainer : null,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                SizedBox(
                  width: 40,
                  child: Text(l10n.groupsPosition(line.position), style: theme.textTheme.titleMedium),
                ),
                Avatar(photoUrl: member.photoUrl, name: member.displayName),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(name, style: theme.textTheme.titleSmall, maxLines: 1, overflow: TextOverflow.ellipsis),
                      Text(
                        [
                          progressText,
                          if (member.summary.streakWeeks > 0) l10n.groupsStreak(member.summary.streakWeeks),
                        ].join(' · '),
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
                if (onMenu != null)
                  PopupMenuButton<MemberAction>(
                    tooltip: l10n.groupsMemberMenu(member.displayName),
                    onSelected: onMenu,
                    itemBuilder: (_) => [
                      PopupMenuItem(value: MemberAction.makeOwner, child: Text(l10n.groupsMakeOwner)),
                      PopupMenuItem(value: MemberAction.remove, child: Text(l10n.groupsRemove)),
                    ],
                  ),
              ],
            ),
            const SizedBox(height: 8),
            ExcludeSemantics(
              child: LinearProgressIndicator(value: line.compliance, borderRadius: BorderRadius.circular(4)),
            ),
            if (line.isCurrentWeek) ...[const SizedBox(height: 8), _MemberDays(member: member, weekStart: weekStart)],
          ],
        ),
      ),
    );
  }
}

/// Monday to Sunday with an icon per workout type; one sentence for screen readers.
class _MemberDays extends StatelessWidget {
  const _MemberDays({required this.member, required this.weekStart});

  final GroupMember member;
  final String weekStart;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final scheme = Theme.of(context).colorScheme;
    final dates = CalendarDates.weekDates(weekStart);
    final sentence = [
      for (var day = 1; day <= 7; day++)
        () {
          final name = DateFormat.EEEE(locale).format(CalendarDates.toUtc(dates[day - 1]));
          final sources = member.summary.daysThisWeek[day] ?? const [];
          return sources.isEmpty
              ? l10n.weekDayNone(name)
              : l10n.weekDaySources(name, sources.map((s) => sourceLabel(s, l10n)).join(', '));
        }(),
    ].join('. ');
    return Semantics(
      label: sentence,
      child: ExcludeSemantics(
        child: Row(
          children: [
            for (var day = 1; day <= 7; day++)
              Expanded(
                child: Column(
                  children: [
                    Text(DateFormat.EEEEE(locale).format(CalendarDates.toUtc(dates[day - 1]))),
                    SizedBox(
                      height: 18,
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          for (final source in member.summary.daysThisWeek[day] ?? const <TrainingSource>[])
                            Icon(sourceIcon(source), size: 14, color: scheme.primary),
                        ],
                      ),
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

// ---------- The Feed tab ----------

class FeedTab extends StatelessWidget {
  const FeedTab({required this.uid, required this.groupId, this.isOwner = false, super.key});

  final String uid;
  final String groupId;

  /// The owner may delete any post.
  final bool isOwner;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    return BlocProvider(
      create: (_) => FeedBloc(
        groupId: groupId,
        uid: uid,
        groups: services.groups,
        content: services.content,
        dates: services.dates,
        service: services.groupsService,
        isOwner: isOwner,
      ),
      child: Builder(
        builder: (context) => BlocVisibilityScope<FeedBloc>(
          onVisible: (bloc) => bloc.add(const FeedVisible()),
          onHidden: (bloc) => bloc.add(const FeedHidden()),
          child: EffectListener<FeedBloc, FeedEffect>(
            onEffect: (context, effect) {
              final l10n = AppLocalizations.of(context);
              switch (effect) {
                case FeedReactionFailed(:final error):
                  showGroupMessage(context, error == DataError.network ? l10n.noNetwork : l10n.feedReactionFailed);
                case FeedPostDeleted():
                  showGroupMessage(context, l10n.feedMessageDeleted);
                case FeedPostHidden():
                  showGroupMessage(context, l10n.feedMessageHidden);
                case FeedModerationFailed(:final error):
                  showGroupMessage(context, groupErrorText(error, l10n));
              }
            },
            child: BlocBuilder<FeedBloc, FeedState>(
              builder: (context, state) => FeedContent(
                state: state,
                uid: uid,
                onRetry: () => _add(context, const FeedVisible()),
                onLoadMore: () => _add(context, const FeedLoadMore()),
                onReaction: (post, reaction) => _add(context, FeedReactionTapped(post.id, reaction)),
                onModerate: context.read<FeedBloc>().canModerate
                    ? (post, action) => _moderate(context, post, action)
                    : null,
                isOwner: isOwner,
              ),
            ),
          ),
        ),
      ),
    );
  }

  static void _add(BuildContext context, FeedIntent intent) {
    final bloc = context.read<FeedBloc>();
    if (!bloc.isClosed) bloc.add(intent);
  }

  /// Delete asks first (my post, or the owner deleting another's); "Hide for me" doesn't.
  Future<void> _moderate(BuildContext context, FeedPost post, PostAction action) async {
    final bloc = context.read<FeedBloc>();
    if (action == PostAction.hide) {
      bloc.add(FeedHidePost(post.id));
      return;
    }
    final l10n = AppLocalizations.of(context);
    final mine = post.authorUid == uid;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(mine ? l10n.feedDialogDeleteOwnTitle : l10n.feedDialogDeleteOwnerTitle(post.authorName)),
        content: Text(mine ? l10n.feedDialogDeleteOwnText : l10n.feedDialogDeleteOwnerText),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
          TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.feedDelete)),
        ],
      ),
    );
    if (confirmed == true && !bloc.isClosed) bloc.add(FeedDeletePost(post.id));
  }
}

enum PostAction { delete, hide }

class FeedContent extends StatelessWidget {
  const FeedContent({
    required this.state,
    required this.uid,
    required this.onRetry,
    required this.onLoadMore,
    required this.onReaction,
    this.onModerate,
    this.isOwner = false,
    super.key,
  });

  final FeedState state;
  final String uid;
  final VoidCallback onRetry;
  final VoidCallback onLoadMore;
  final void Function(FeedPost post, Reaction reaction) onReaction;

  /// The post's menu: Delete (mine, or any as the owner) and Hide for me (others'); null: read only.
  final void Function(FeedPost post, PostAction action)? onModerate;
  final bool isOwner;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final error = state.error;
    return PageBody(
      children: [
        if (error != null)
          ErrorView(error: error, onRetry: onRetry)
        else if (state.isLoading)
          const LoadingView()
        else if (state.isEmpty)
          EmptyView(text: state.isFirstDayOfWeek ? l10n.feedEmptyMonday : l10n.feedEmpty)
        else ...[
          for (final post in state.posts)
            _PostCard(
              post: post,
              name: state.nameOf(post).text(l10n),
              uid: uid,
              onReaction: onReaction,
              onModerate: onModerate,
              canDelete: post.authorUid == uid || isOwner,
            ),
          if (state.canLoadMore)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Center(
                child: OutlinedButton(
                  onPressed: onLoadMore,
                  style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
                  child: Text(l10n.feedLoadMore),
                ),
              ),
            ),
        ],
      ],
    );
  }
}

class _PostCard extends StatelessWidget {
  const _PostCard({
    required this.post,
    required this.name,
    required this.uid,
    required this.onReaction,
    this.onModerate,
    this.canDelete = false,
  });

  final FeedPost post;
  final String name;
  final String uid;
  final void Function(FeedPost post, Reaction reaction) onReaction;
  final void Function(FeedPost post, PostAction action)? onModerate;
  final bool canDelete;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final isMine = post.authorUid == uid;
    final author = isMine ? '${post.authorName} (${l10n.groupsMe})' : post.authorName;
    final created = post.createdAtMillis;
    final duration = post.durationSeconds;
    final details = [
      sourceLabel(post.source, l10n),
      if (duration != null) durationText(duration, l10n),
      if (created == null)
        l10n.feedJustNow
      else
        '${DateFormat.MMMEd(locale).format(DateTime.fromMillisecondsSinceEpoch(created))} '
            '${DateFormat.jm(locale).format(DateTime.fromMillisecondsSinceEpoch(created))}',
    ].join(' · ');
    final thumb = post.thumb;
    final counts = ReactionRules.counts(post.reactions);
    final mine = post.reactions[uid];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Avatar(photoUrl: post.authorPhotoUrl, name: post.authorName),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(author, style: theme.textTheme.titleSmall, maxLines: 1, overflow: TextOverflow.ellipsis),
                      Text(details, style: theme.textTheme.bodySmall),
                    ],
                  ),
                ),
                if (onModerate != null)
                  PopupMenuButton<PostAction>(
                    tooltip: l10n.feedPostMenu(post.authorName),
                    onSelected: (action) => onModerate!(post, action),
                    itemBuilder: (_) => [
                      if (canDelete) PopupMenuItem(value: PostAction.delete, child: Text(l10n.feedDelete)),
                      if (!isMine) PopupMenuItem(value: PostAction.hide, child: Text(l10n.feedHide)),
                    ],
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Text(name, style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
            if (thumb != null) ...[
              const SizedBox(height: 8),
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxHeight: 220),
                  child: Image.memory(
                    thumb,
                    width: double.infinity,
                    fit: BoxFit.cover,
                    semanticLabel: l10n.feedPhotoDescription(post.authorName),
                    gaplessPlayback: true,
                    errorBuilder: (_, _, _) => const SizedBox.shrink(),
                  ),
                ),
              ),
            ],
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final reaction in Reaction.values)
                  _ReactionChip(
                    reaction: reaction,
                    count: counts[reaction] ?? 0,
                    selected: mine == reaction,
                    onTap: () => onReaction(post, reaction),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ReactionChip extends StatelessWidget {
  const _ReactionChip({required this.reaction, required this.count, required this.selected, required this.onTap});

  final Reaction reaction;
  final int count;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final label = switch (reaction) {
      Reaction.strong => l10n.feedReactionStrong,
      Reaction.fire => l10n.feedReactionFire,
      Reaction.clap => l10n.feedReactionClap,
      Reaction.raised => l10n.feedReactionRaised,
    };
    return Semantics(
      label: l10n.feedReactionDescription(label, count),
      value: selected ? l10n.feedReactionMine : l10n.feedReactionNotMine,
      selected: selected,
      button: true,
      onTap: onTap,
      excludeSemantics: true,
      child: FilterChip(
        label: Text(count > 0 ? '${reaction.emoji} $count' : reaction.emoji),
        selected: selected,
        showCheckmark: false,
        onSelected: (_) => onTap(),
        materialTapTargetSize: MaterialTapTargetSize.padded,
      ),
    );
  }
}

// ---------- Leave and the owner's actions: the confirmations (Android's dialogs) ----------

enum OwnerAction { rename, regenerate, transfer, delete }

enum MemberAction { makeOwner, remove }

/// The dialogs before each action, then the intent; every destructive one asks first.
class GroupActions {
  const GroupActions(this._bloc, this._uid);

  final GroupDetailBloc _bloc;
  final String _uid;

  Future<bool> _confirm(BuildContext context, String title, String text, String confirm) async {
    final l10n = AppLocalizations.of(context);
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(title),
            content: Text(text),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
              TextButton(onPressed: () => Navigator.pop(context, true), child: Text(confirm)),
            ],
          ),
        ) ==
        true;
  }

  /// Choose the new owner among the other members (sorted as the ranking); null when cancelled.
  Future<GroupMember?> _pickOwner(BuildContext context, GroupDetailState state, {required bool leaving}) {
    final l10n = AppLocalizations.of(context);
    return showDialog<GroupMember>(
      context: context,
      builder: (context) => SimpleDialog(
        title: Text(l10n.groupsDialogTransferTitle),
        children: [
          if (leaving)
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 8),
              child: Text(l10n.groupsDialogTransferBeforeLeaving),
            ),
          for (final member in state.others(_uid))
            SimpleDialogOption(
              onPressed: () => Navigator.pop(context, member),
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Row(
                  children: [
                    Avatar(photoUrl: member.photoUrl, name: member.displayName),
                    const SizedBox(width: 12),
                    Expanded(child: Text(member.displayName)),
                  ],
                ),
              ),
            ),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
          ),
        ],
      ),
    );
  }

  Future<void> leave(BuildContext context, GroupDetailState state) async {
    final l10n = AppLocalizations.of(context);
    final group = state.group;
    if (group == null) return;
    if (group.ownerUid == _uid && group.memberCount > 1) {
      // An owner with members chooses the next owner: transfer and leave in one batch
      final heir = await _pickOwner(context, state, leaving: true);
      if (heir == null || !context.mounted) return;
      if (await _confirm(
        context,
        l10n.groupsDialogTransferConfirmTitle(heir.displayName),
        l10n.groupsDialogTransferAndLeaveText,
        l10n.groupsDialogTransferAndLeaveConfirm,
      )) {
        _bloc.add(GroupLeaveRequested(newOwner: heir));
      }
      return;
    }
    final alone = group.ownerUid == _uid;
    if (await _confirm(
      context,
      l10n.groupsDialogLeaveTitle(group.name),
      alone ? l10n.groupsDialogLeaveAloneText : l10n.groupsDialogLeaveText,
      l10n.groupsDialogLeaveConfirm,
    )) {
      _bloc.add(const GroupLeaveRequested());
    }
  }

  Future<void> onOwner(BuildContext context, GroupDetailState state, OwnerAction choice) async {
    final l10n = AppLocalizations.of(context);
    final group = state.group;
    if (group == null) return;
    switch (choice) {
      case OwnerAction.rename:
        final name = await showDialog<String>(
          context: context,
          builder: (_) => _RenameDialog(name: group.name),
        );
        if (name != null) _bloc.add(GroupRenameRequested(name));
      case OwnerAction.regenerate:
        if (await _confirm(
          context,
          l10n.groupsDialogRegenerateTitle,
          l10n.groupsDialogRegenerateText,
          l10n.groupsDialogRegenerateConfirm,
        )) {
          _bloc.add(const GroupRegenerateRequested());
        }
      case OwnerAction.transfer:
        final member = await _pickOwner(context, state, leaving: false);
        if (member != null && context.mounted) await onMember(context, state, member, MemberAction.makeOwner);
      case OwnerAction.delete:
        // Two steps, as Android
        if (!await _confirm(
              context,
              l10n.groupsDialogDeleteTitle(group.name),
              l10n.groupsDialogDeleteText,
              l10n.groupsDialogDeleteNext,
            ) ||
            !context.mounted) {
          return;
        }
        if (await _confirm(
          context,
          l10n.groupsDialogDeleteConfirmTitle,
          l10n.groupsDialogDeleteConfirmText(group.name),
          l10n.groupsDialogDeleteConfirm,
        )) {
          _bloc.add(const GroupDeleteRequested());
        }
    }
  }

  Future<void> onMember(BuildContext context, GroupDetailState state, GroupMember member, MemberAction choice) async {
    final l10n = AppLocalizations.of(context);
    switch (choice) {
      case MemberAction.makeOwner:
        if (await _confirm(
          context,
          l10n.groupsDialogTransferConfirmTitle(member.displayName),
          l10n.groupsDialogTransferConfirmText,
          l10n.groupsDialogTransferConfirm,
        )) {
          _bloc.add(GroupTransferRequested(member));
        }
      case MemberAction.remove:
        if (await _confirm(
          context,
          l10n.groupsDialogRemoveTitle(member.displayName),
          l10n.groupsDialogRemoveText,
          l10n.groupsDialogRemoveConfirm,
        )) {
          _bloc.add(GroupRemoveRequested(member));
        }
    }
  }
}

class _RenameDialog extends StatefulWidget {
  const _RenameDialog({required this.name});

  final String name;

  @override
  State<_RenameDialog> createState() => _RenameDialogState();
}

class _RenameDialogState extends State<_RenameDialog> {
  late final TextEditingController _controller = TextEditingController(text: widget.name);
  bool _invalid = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _save() {
    final name = GroupRules.normalizeName(_controller.text);
    if (name == null) {
      setState(() => _invalid = true);
    } else {
      Navigator.pop(context, name);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(l10n.groupsDialogRenameTitle),
      content: TextField(
        controller: _controller,
        autofocus: true,
        maxLength: GroupRules.nameMaxLength,
        decoration: InputDecoration(
          labelText: l10n.groupsCreateNameLabel,
          errorText: _invalid ? l10n.groupsCreateNameError : null,
        ),
        onSubmitted: (_) => _save(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        TextButton(onPressed: _save, child: Text(l10n.groupsDialogSave)),
      ],
    );
  }
}
