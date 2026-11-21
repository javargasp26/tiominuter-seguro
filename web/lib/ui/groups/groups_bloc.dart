import 'dart:async';

import '../../app/groups_service.dart';
import '../../core/mvi.dart';
import '../../data/repositories.dart';
import '../../domain/calendar_dates.dart';
import '../../domain/groups.dart';

// ---------- The list ----------

sealed class GroupsIntent {
  const GroupsIntent();
}

final class GroupsLoad extends GroupsIntent {
  const GroupsLoad();
}

/// "Create group" / "Join with code": a weekly goal first (Android's goal-required dialog).
final class GroupsCreateClicked extends GroupsIntent {
  const GroupsCreateClicked();
}

final class GroupsJoinClicked extends GroupsIntent {
  const GroupsJoinClicked();
}

final class GroupsGoalDialogDismissed extends GroupsIntent {
  const GroupsGoalDialogDismissed();
}

enum GroupsNext { create, join }

class GroupsState {
  const GroupsState({
    this.isLoading = true,
    this.groups = const [],
    this.error,
    this.canManage = false,
    this.goalRequiredFor,
    this.isChecking = false,
  });

  final bool isLoading;
  final List<MyGroup> groups;
  final DataError? error;

  /// Create and join are offered (not in read-only tests).
  final bool canManage;

  /// The goal-required dialog is open, for what comes after the goal.
  final GroupsNext? goalRequiredFor;

  /// The goal is being checked before creating or joining.
  final bool isChecking;

  bool get isEmpty => !isLoading && error == null && groups.isEmpty;

  /// At most 5 groups: create / join stay available until then (the service checks again).
  bool get atLimit => groups.length >= GroupRules.maxGroups;

  GroupsState copyWith({bool? isChecking, GroupsNext? goalRequiredFor, bool clearGoalRequired = false}) => GroupsState(
    isLoading: isLoading,
    groups: groups,
    error: error,
    canManage: canManage,
    goalRequiredFor: clearGoalRequired ? null : (goalRequiredFor ?? this.goalRequiredFor),
    isChecking: isChecking ?? this.isChecking,
  );
}

sealed class GroupsEffect {
  const GroupsEffect();
}

final class GroupsOpen extends GroupsEffect {
  const GroupsOpen(this.next);

  final GroupsNext next;
}

final class GroupsMessage extends GroupsEffect {
  const GroupsMessage(this.error);

  final GroupError error;
}

/// "Groups" (Android's GroupListViewModel): my groups with my position this week, the index
/// reconciled first (stale entries removed); create and join after the weekly-goal check. Without a
/// [service] (read-only tests) the readable groups of my index only.
class GroupsBloc extends MviBloc<GroupsIntent, GroupsState, GroupsEffect> {
  GroupsBloc({required String uid, required GroupsRepository groups, GroupsService? service, WebUser? user})
    : super(const GroupsState()) {
    on<GroupsLoad>((_, emit) async {
      emit(GroupsState(canManage: service != null && user != null));
      try {
        final mine = service != null && user != null
            ? await service.myGroups(user)
            : [
                for (final group in await groups.myGroups(uid))
                  MyGroup(group: group, myPosition: null, isOwner: group.ownerUid == uid),
              ];
        emit(GroupsState(isLoading: false, groups: mine, canManage: service != null && user != null));
      } on DataException catch (e) {
        emit(GroupsState(isLoading: false, error: e.error, canManage: service != null));
      } on GroupException catch (e) {
        emit(GroupsState(isLoading: false, error: _dataErrorOf(e.error), canManage: service != null));
      }
    });
    Future<void> next(GroupsNext what, Emitter<GroupsState> emit) async {
      if (service == null || user == null || state.isChecking) return;
      if (state.atLimit && what == GroupsNext.create) {
        sendEffect(const GroupsMessage(GroupError.groupLimit));
        return;
      }
      emit(state.copyWith(isChecking: true));
      final hasGoal = await service.hasWeeklyGoal(user).catchError((Object _) => true);
      emit(state.copyWith(isChecking: false));
      if (hasGoal) {
        sendEffect(GroupsOpen(what));
      } else {
        emit(state.copyWith(goalRequiredFor: what));
      }
    }

    on<GroupsCreateClicked>((_, emit) => next(GroupsNext.create, emit));
    on<GroupsJoinClicked>((_, emit) => next(GroupsNext.join, emit));
    on<GroupsGoalDialogDismissed>((_, emit) => emit(state.copyWith(clearGoalRequired: true)));
  }

  static DataError _dataErrorOf(GroupError error) => switch (error) {
    GroupError.network => DataError.network,
    GroupError.permissionDenied => DataError.permissionDenied,
    GroupError.notFound => DataError.notFound,
    _ => DataError.failed,
  };
}

// ---------- Create ----------

sealed class GroupCreateIntent {
  const GroupCreateIntent();
}

final class GroupCreateNameChanged extends GroupCreateIntent {
  const GroupCreateNameChanged(this.name);

  final String name;
}

final class GroupCreateSubmitted extends GroupCreateIntent {
  const GroupCreateSubmitted();
}

class GroupCreateState {
  const GroupCreateState({this.name = '', this.showNameError = false, this.isCreating = false, this.created});

  final String name;
  final bool showNameError;
  final bool isCreating;

  /// Done: the code to share.
  final CreatedGroup? created;

  bool get isNameValid => GroupRules.normalizeName(name) != null;
}

/// Create a group (Android's GroupCreateViewModel): a name of 1–40 characters → the invite code.
class GroupCreateBloc extends MviBloc<GroupCreateIntent, GroupCreateState, GroupError> {
  GroupCreateBloc({required GroupsService service, required WebUser user}) : super(const GroupCreateState()) {
    on<GroupCreateNameChanged>((i, emit) {
      final name = i.name.length > GroupRules.nameMaxLength ? i.name.substring(0, GroupRules.nameMaxLength) : i.name;
      emit(GroupCreateState(name: name, showNameError: state.showNameError && GroupRules.normalizeName(name) == null));
    });
    on<GroupCreateSubmitted>((_, emit) async {
      if (state.isCreating || state.created != null) return;
      if (!state.isNameValid) {
        emit(GroupCreateState(name: state.name, showNameError: true));
        return;
      }
      emit(GroupCreateState(name: state.name, isCreating: true));
      try {
        final created = await service.create(user, state.name);
        emit(GroupCreateState(name: state.name, created: created));
      } on GroupException catch (e) {
        emit(GroupCreateState(name: state.name));
        sendEffect(e.error);
      }
    });
  }
}

// ---------- Join ----------

sealed class GroupJoinIntent {
  const GroupJoinIntent();
}

final class GroupJoinCodeChanged extends GroupJoinIntent {
  const GroupJoinCodeChanged(this.code);

  final String code;
}

final class GroupJoinSubmitted extends GroupJoinIntent {
  const GroupJoinSubmitted();
}

final class _JoinPreviewLoaded extends GroupJoinIntent {
  const _JoinPreviewLoaded(this.code, this.preview, this.error);

  final String code;
  final InvitePreview? preview;
  final GroupError? error;
}

class GroupJoinState {
  const GroupJoinState({
    this.code = '',
    this.isLookingUp = false,
    this.preview,
    this.lookupError,
    this.isJoining = false,
    this.joinedGroupId,
  });

  /// Uppercased, without spaces or dashes.
  final String code;
  final bool isLookingUp;
  final InvitePreview? preview;
  final GroupError? lookupError;
  final bool isJoining;
  final String? joinedGroupId;

  /// 0, O, 1, I or L: never in a code.
  bool get hasInvalidCharacters => InviteCodes.hasInvalidCharacters(code);

  bool get isComplete => InviteCodes.isValid(code);

  bool get canJoin => preview != null && !isJoining && joinedGroupId == null;
}

/// Join with a code (Android's GroupJoinViewModel): normalized as typed, a complete code is looked
/// up in inviteCodes to preview the group's name, then Join; each error has its message.
class GroupJoinBloc extends MviBloc<GroupJoinIntent, GroupJoinState, GroupError> {
  GroupJoinBloc({required GroupsService service, required WebUser user, String initialCode = ''})
    : super(const GroupJoinState()) {
    void changed(String raw, Emitter<GroupJoinState> emit) {
      final code = InviteCodes.normalize(raw);
      final clipped = code.length > InviteCodes.length ? code.substring(0, InviteCodes.length) : code;
      if (clipped == state.code && state.code.isNotEmpty) return;
      final complete = InviteCodes.isValid(clipped);
      emit(GroupJoinState(code: clipped, isLookingUp: complete));
      if (!complete) return;
      service
          .findInvite(clipped)
          .then(
            (preview) => add(_JoinPreviewLoaded(clipped, preview, null)),
            onError: (Object e) =>
                add(_JoinPreviewLoaded(clipped, null, e is GroupException ? e.error : GroupError.failed)),
          );
    }

    on<GroupJoinCodeChanged>((i, emit) => changed(i.code, emit));
    on<_JoinPreviewLoaded>((i, emit) {
      // An older lookup answering after the code changed is ignored
      if (i.code != state.code) return;
      emit(GroupJoinState(code: state.code, preview: i.preview, lookupError: i.error));
    });
    on<GroupJoinSubmitted>((_, emit) async {
      final preview = state.preview;
      if (preview == null || !state.canJoin) return;
      emit(GroupJoinState(code: state.code, preview: preview, isJoining: true));
      try {
        final groupId = await service.join(user, preview);
        emit(GroupJoinState(code: state.code, preview: preview, joinedGroupId: groupId));
      } on GroupException catch (e) {
        emit(GroupJoinState(code: state.code, preview: preview));
        sendEffect(e.error);
      }
    });
    if (initialCode.isNotEmpty) add(GroupJoinCodeChanged(initialCode));
  }
}

// ---------- One group: the ranking ----------

sealed class GroupDetailIntent {
  const GroupDetailIntent();
}

final class GroupVisible extends GroupDetailIntent {
  const GroupVisible();
}

final class GroupHidden extends GroupDetailIntent {
  const GroupHidden();
}

final class _GroupChanged extends GroupDetailIntent {
  const _GroupChanged(this.group);

  final Group? group;
}

final class _MembersChanged extends GroupDetailIntent {
  const _MembersChanged(this.members);

  final List<GroupMember> members;
}

/// Leave (an owner with members names the new owner; an owner alone deletes the group).
final class GroupLeaveRequested extends GroupDetailIntent {
  const GroupLeaveRequested({this.newOwner});

  final GroupMember? newOwner;
}

final class GroupRenameRequested extends GroupDetailIntent {
  const GroupRenameRequested(this.name);

  final String name;
}

final class GroupRegenerateRequested extends GroupDetailIntent {
  const GroupRegenerateRequested();
}

final class GroupRemoveRequested extends GroupDetailIntent {
  const GroupRemoveRequested(this.member);

  final GroupMember member;
}

final class GroupTransferRequested extends GroupDetailIntent {
  const GroupTransferRequested(this.member);

  final GroupMember member;
}

final class GroupDeleteRequested extends GroupDetailIntent {
  const GroupDeleteRequested();
}

final class _GroupFailed extends GroupDetailIntent {
  const _GroupFailed(this.error);

  final DataError error;
}

class GroupDetailState {
  const GroupDetailState({
    this.isLoading = true,
    this.group,
    this.isGone = false,
    this.ranking = const [],
    this.weekStart = '',
    this.error,
    this.canManage = false,
    this.isBusy = false,
  });

  /// Leave and the owner's actions are offered (not in read-only tests).
  final bool canManage;

  /// An action is running: the actions wait.
  final bool isBusy;

  /// The other members, for transfer and remove.
  List<GroupMember> others(String uid) => [
    for (final line in ranking)
      if (line.member.uid != uid) line.member,
  ];

  GroupDetailState withBusy(bool busy) => GroupDetailState(
    isLoading: isLoading,
    group: group,
    isGone: isGone,
    ranking: ranking,
    weekStart: weekStart,
    error: error,
    canManage: canManage,
    isBusy: busy,
  );

  final bool isLoading;
  final Group? group;

  /// I can't read the group any more (left, removed, deleted).
  final bool isGone;
  final List<RankedMember> ranking;

  /// The reader's current Monday: a member summary of another week counts 0.
  final String weekStart;
  final DataError? error;

  bool isOwner(String uid) => group?.ownerUid == uid;
}

sealed class GroupDetailEffect {
  const GroupDetailEffect();
}

enum GroupDone { left, deleted, renamed, codeRegenerated, memberRemoved, ownerChanged }

/// An action worked: what to say ([argument]: a name or the new code); [leaves] goes back to Groups.
final class GroupActionDone extends GroupDetailEffect {
  const GroupActionDone(this.done, {this.argument = '', this.leaves = false});

  final GroupDone done;
  final String argument;
  final bool leaves;
}

final class GroupActionFailed extends GroupDetailEffect {
  const GroupActionFailed(this.error);

  final GroupError error;
}

/// The ranking of one group (the same formula, order and stale-week rule as Android's
/// `GroupRanking`), live only while visible: a listener on the group doc and one on its members
/// (≤ 20 docs, then 1 read per change). With a [service], Leave and the owner's actions (Android's
/// GroupDetailViewModel), each one confirmed by the screen first.
class GroupDetailBloc extends MviBloc<GroupDetailIntent, GroupDetailState, GroupDetailEffect> {
  GroupDetailBloc({
    required this.groupId,
    required this._uid,
    required this._groups,
    required this._dates,
    this._service,
    this._user,
  }) : super(const GroupDetailState()) {
    Future<void> act(
      Emitter<GroupDetailState> emit,
      Future<GroupActionDone> Function(GroupsService, WebUser, Group) run,
    ) async {
      final service = _service;
      final user = _user;
      final group = _group;
      if (service == null || user == null || group == null || state.isBusy) return;
      emit(state.withBusy(true));
      try {
        final done = await run(service, user, group);
        if (done.leaves) _stop();
        sendEffect(done);
      } on GroupException catch (e) {
        sendEffect(GroupActionFailed(e.error));
      } finally {
        if (!isClosed) emit(state.withBusy(false));
      }
    }

    on<GroupLeaveRequested>(
      (i, emit) => act(emit, (service, user, group) async {
        final alone = group.ownerUid == _uid && group.memberCount <= 1;
        await service.leave(user, group, newOwnerUid: i.newOwner?.uid);
        return GroupActionDone(alone ? GroupDone.deleted : GroupDone.left, leaves: true);
      }),
    );
    on<GroupRenameRequested>(
      (i, emit) => act(emit, (service, user, group) async {
        await service.rename(user, group, i.name);
        return const GroupActionDone(GroupDone.renamed);
      }),
    );
    on<GroupRegenerateRequested>(
      (_, emit) => act(emit, (service, user, group) async {
        final code = await service.regenerateCode(user, group);
        return GroupActionDone(GroupDone.codeRegenerated, argument: code);
      }),
    );
    on<GroupRemoveRequested>(
      (i, emit) => act(emit, (service, user, group) async {
        await service.removeMember(user, group, i.member.uid);
        return GroupActionDone(GroupDone.memberRemoved, argument: i.member.displayName);
      }),
    );
    on<GroupTransferRequested>(
      (i, emit) => act(emit, (service, user, group) async {
        await service.transfer(user, group, i.member.uid);
        return GroupActionDone(GroupDone.ownerChanged, argument: i.member.displayName);
      }),
    );
    on<GroupDeleteRequested>(
      (_, emit) => act(emit, (service, user, group) async {
        await service.delete(user, group);
        return const GroupActionDone(GroupDone.deleted, leaves: true);
      }),
    );
    on<GroupVisible>((_, _) => _listen());
    on<GroupHidden>((_, _) => _stop());
    on<_GroupChanged>((intent, emit) {
      if (intent.group == null) {
        emit(const GroupDetailState(isLoading: false, isGone: true));
        _stop();
      } else {
        _group = intent.group;
        emit(_state());
      }
    });
    on<_MembersChanged>((intent, emit) {
      _members = intent.members;
      emit(_state());
    });
    on<_GroupFailed>((intent, emit) {
      // A refused read means I lost access (the members listener may answer before the group's)
      emit(
        intent.error == DataError.permissionDenied
            ? const GroupDetailState(isLoading: false, isGone: true)
            : GroupDetailState(isLoading: false, error: intent.error, group: _group),
      );
      _stop();
    });
  }

  final String groupId;
  final String _uid;
  final GroupsRepository _groups;
  final LocalDates _dates;
  final GroupsService? _service;
  final WebUser? _user;
  final List<StreamSubscription<Object?>> _subscriptions = [];
  Group? _group;
  List<GroupMember>? _members;

  GroupDetailState _state() {
    final group = _group;
    final members = _members;
    final canManage = _service != null && _user != null;
    if (group == null || members == null) return GroupDetailState(group: group, canManage: canManage);
    final weekStart = _dates.currentWeekStart();
    return GroupDetailState(
      isLoading: false,
      group: group,
      ranking: GroupRanking.rank(members, weekStart, _uid),
      weekStart: weekStart,
      canManage: canManage,
      isBusy: state.isBusy,
    );
  }

  void _listen() {
    _stop();
    void failed(Object e) => add(_GroupFailed(e is DataException ? e.error : DataError.failed));
    _subscriptions
      ..add(_groups.watchGroup(groupId).listen((group) => add(_GroupChanged(group)), onError: failed))
      ..add(_groups.watchMembers(groupId).listen((members) => add(_MembersChanged(members)), onError: failed));
  }

  void _stop() {
    for (final subscription in _subscriptions) {
      subscription.cancel();
    }
    _subscriptions.clear();
  }

  @override
  Future<void> close() {
    _stop();
    return super.close();
  }
}
