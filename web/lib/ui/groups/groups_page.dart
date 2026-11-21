import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../l10n/app_localizations.dart';
import '../../domain/groups.dart';
import '../common/widgets.dart';
import '../train/train_widgets.dart';
import 'group_messages.dart';
import 'groups_bloc.dart';

class GroupsPage extends StatelessWidget {
  const GroupsPage({required this.uid, super.key});

  final String uid;

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final user = services.groupsService == null ? null : context.read<WebUserHolder>().user;
    return BlocProvider(
      create: (_) =>
          GroupsBloc(uid: uid, groups: services.groups, service: services.groupsService, user: user)
            ..add(const GroupsLoad()),
      child: EffectListener<GroupsBloc, GroupsEffect>(
        onEffect: (context, effect) => switch (effect) {
          GroupsOpen(:final next) => context.go(next == GroupsNext.create ? Routes.groupCreate : Routes.groupJoin),
          GroupsMessage(:final error) => showGroupMessage(context, groupErrorText(error, AppLocalizations.of(context))),
        },
        child: BlocConsumer<GroupsBloc, GroupsState>(
          listenWhen: (a, b) => a.goalRequiredFor == null && b.goalRequiredFor != null,
          listener: (context, state) async {
            final l10n = AppLocalizations.of(context);
            final bloc = context.read<GroupsBloc>();
            final next = state.goalRequiredFor!;
            final setGoal = await showDialog<bool>(
              context: context,
              builder: (context) => AlertDialog(
                title: Text(l10n.groupsGoalRequiredTitle),
                content: Text(l10n.groupsGoalRequiredText),
                actions: [
                  TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
                  FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: Text(l10n.groupsGoalRequiredAction),
                  ),
                ],
              ),
            );
            bloc.add(const GroupsGoalDialogDismissed());
            // The goal screen, then on to create / join
            if (setGoal == true && context.mounted) {
              context.go(Routes.weeklyGoalThen(next == GroupsNext.create ? Routes.groupCreate : Routes.groupJoin));
            }
          },
          builder: (context, state) => GroupsContent(
            state: state,
            uid: uid,
            onRetry: () => context.read<GroupsBloc>().add(const GroupsLoad()),
            onOpen: (group) => context.go(Routes.group(group.id)),
            onCreate: () => context.read<GroupsBloc>().add(const GroupsCreateClicked()),
            onJoin: () => context.read<GroupsBloc>().add(const GroupsJoinClicked()),
          ),
        ),
      ),
    );
  }
}

class GroupsContent extends StatelessWidget {
  const GroupsContent({
    required this.state,
    required this.uid,
    required this.onRetry,
    required this.onOpen,
    this.onCreate,
    this.onJoin,
    super.key,
  });

  final GroupsState state;
  final String uid;
  final VoidCallback onRetry;
  final void Function(Group group) onOpen;
  final VoidCallback? onCreate;
  final VoidCallback? onJoin;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final error = state.error;
    final actions = state.canManage && !state.isLoading && error == null;
    return PageBody(
      children: [
        PageTitle(l10n.groupsTitle),
        if (error != null)
          ErrorView(error: error, onRetry: onRetry)
        else if (state.isLoading)
          const LoadingView()
        else if (state.isEmpty)
          EmptyView(title: l10n.groupsEmptyTitle, text: l10n.groupsEmptyText)
        else
          for (final mine in state.groups)
            Card(
              child: ListTile(
                minTileHeight: 56,
                leading: const ExcludeSemantics(child: Icon(Icons.group)),
                title: Text(mine.group.name),
                subtitle: Text(
                  [
                    l10n.groupsMemberCount(mine.group.memberCount),
                    if (mine.myPosition != null) l10n.groupsMyPosition(mine.myPosition!),
                  ].join(' · '),
                ),
                trailing: mine.isOwner
                    ? Tooltip(
                        message: l10n.groupsOwner,
                        child: Icon(Icons.star, semanticLabel: l10n.groupsOwner),
                      )
                    : null,
                onTap: () => onOpen(mine.group),
              ),
            ),
        if (actions) ...[
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              FilledButton.icon(
                onPressed: state.isChecking || state.atLimit ? null : onCreate,
                style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                icon: const Icon(Icons.group_add),
                label: Text(l10n.groupsCreateButton),
              ),
              OutlinedButton.icon(
                onPressed: state.isChecking || state.atLimit ? null : onJoin,
                style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
                icon: const Icon(Icons.vpn_key_outlined),
                label: Text(l10n.groupsJoinButton),
              ),
            ],
          ),
          if (state.atLimit) ...[const SizedBox(height: 8), Text(l10n.groupsMessageGroupLimit)],
        ],
        const SizedBox(height: 16),
        InfoNote(l10n.groupsPrivacyNote),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// Create (Android's GroupCreateScreen)
// ---------------------------------------------------------------------------

class GroupCreatePage extends StatelessWidget {
  const GroupCreatePage({super.key});

  @override
  Widget build(BuildContext context) => BlocProvider(
    create: (_) =>
        GroupCreateBloc(service: context.read<AppServices>().groupsService!, user: context.read<WebUserHolder>().user),
    child: EffectListener<GroupCreateBloc, GroupError>(
      onEffect: (context, error) => showGroupMessage(context, groupErrorText(error, AppLocalizations.of(context))),
      child: BlocBuilder<GroupCreateBloc, GroupCreateState>(
        builder: (context, state) => GroupCreateContent(
          state: state,
          onIntent: context.read<GroupCreateBloc>().add,
          onBack: () => context.go(Routes.groups),
          onOpen: (groupId) => context.go(Routes.group(groupId)),
        ),
      ),
    ),
  );
}

class GroupCreateContent extends StatelessWidget {
  const GroupCreateContent({
    required this.state,
    required this.onIntent,
    required this.onBack,
    required this.onOpen,
    super.key,
  });

  final GroupCreateState state;
  final void Function(GroupCreateIntent) onIntent;
  final VoidCallback onBack;
  final void Function(String groupId) onOpen;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final created = state.created;
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(tooltip: l10n.back, icon: const Icon(Icons.arrow_back), onPressed: onBack),
        title: Text(l10n.groupsCreateTitle),
      ),
      body: PageBody(
        children: created == null
            ? [
                SyncedTextField(
                  value: state.name,
                  maxLength: GroupRules.nameMaxLength,
                  decoration: InputDecoration(
                    labelText: l10n.groupsCreateNameLabel,
                    errorText: state.showNameError ? l10n.groupsCreateNameError : null,
                  ),
                  onChanged: (name) => onIntent(GroupCreateNameChanged(name)),
                ),
                const SizedBox(height: 16),
                WideButton(
                  label: l10n.groupsCreateAction,
                  onPressed: state.isCreating ? null : () => onIntent(const GroupCreateSubmitted()),
                ),
              ]
            : [
                Semantics(
                  header: true,
                  liveRegion: true,
                  child: Text(l10n.groupsCreatedTitle, style: theme.textTheme.headlineSmall),
                ),
                const SizedBox(height: 8),
                Text(l10n.groupsCreatedText(created.name)),
                const SizedBox(height: 16),
                InviteCodeCard(groupName: created.name, code: created.code),
                const SizedBox(height: 16),
                WideButton(label: l10n.groupsOpenGroup, onPressed: () => onOpen(created.id)),
              ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Join (Android's GroupJoinScreen); `/groups/join?code=…` arrives prefilled
// ---------------------------------------------------------------------------

class GroupJoinPage extends StatelessWidget {
  const GroupJoinPage({this.code = '', super.key});

  final String code;

  @override
  Widget build(BuildContext context) => BlocProvider(
    create: (_) => GroupJoinBloc(
      service: context.read<AppServices>().groupsService!,
      user: context.read<WebUserHolder>().user,
      initialCode: code,
    ),
    child: EffectListener<GroupJoinBloc, GroupError>(
      onEffect: (context, error) => showGroupMessage(context, groupErrorText(error, AppLocalizations.of(context))),
      child: BlocConsumer<GroupJoinBloc, GroupJoinState>(
        listenWhen: (a, b) => a.joinedGroupId == null && b.joinedGroupId != null,
        listener: (context, state) => context.go(Routes.group(state.joinedGroupId!)),
        builder: (context, state) => GroupJoinContent(
          state: state,
          onIntent: context.read<GroupJoinBloc>().add,
          onBack: () => context.go(Routes.groups),
        ),
      ),
    ),
  );
}

class GroupJoinContent extends StatelessWidget {
  const GroupJoinContent({required this.state, required this.onIntent, required this.onBack, super.key});

  final GroupJoinState state;
  final void Function(GroupJoinIntent) onIntent;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final preview = state.preview;
    final lookupError = state.lookupError;
    final String? fieldError = state.hasInvalidCharacters
        ? l10n.groupsJoinCodeInvalidChars
        : (lookupError != null ? groupErrorText(lookupError, l10n) : null);
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(tooltip: l10n.back, icon: const Icon(Icons.arrow_back), onPressed: onBack),
        title: Text(l10n.groupsJoinTitle),
      ),
      body: PageBody(
        children: [
          SyncedTextField(
            value: state.code,
            maxLength: InviteCodes.length + 4,
            decoration: InputDecoration(
              labelText: l10n.groupsJoinCodeLabel,
              helperText: l10n.groupsJoinCodeHint,
              errorText: fieldError,
              counterText: '',
            ),
            onChanged: (code) => onIntent(GroupJoinCodeChanged(code)),
          ),
          const SizedBox(height: 16),
          if (state.isLookingUp)
            Semantics(liveRegion: true, child: Text(l10n.groupsJoinLooking))
          else if (preview != null)
            Semantics(
              liveRegion: true,
              child: Text(l10n.groupsJoinPreview(preview.groupName), style: Theme.of(context).textTheme.titleMedium),
            ),
          const SizedBox(height: 16),
          WideButton(
            label: l10n.groupsJoinAction,
            onPressed: state.canJoin ? () => onIntent(const GroupJoinSubmitted()) : null,
          ),
        ],
      ),
    );
  }
}
