/// Groups on the web as a full client, ported from Android: `GroupOperations` (the product rules
/// before every write), `GroupReconciler` (stale index entries), `GroupsHome` + `GroupMembersCache`
/// (the "already trained today" banner, one cached read), `FeedModeration` (one delete per post)
/// and `GroupAccountCleaner` (Delete account). Every write is one batch of docs/firestore-model.md
/// "Groups" through [GroupsRepository]; failures are [GroupException]s, logged without personal data.
library;

import 'dart:async';
import 'dart:math';

import 'package:flutter/foundation.dart';

import '../data/repositories.dart';
import '../data/write_mappers.dart';
import '../domain/calendar_dates.dart';
import '../domain/groups.dart';
import '../platform/browser.dart';
import 'training_service.dart';

class GroupsService {
  GroupsService({
    required this.repository,
    required this.training,
    required this.store,
    required this.dates,
    required this.wallClock,
    bool Function()? isOnline,
    String Function()? newCode,
  }) : _isOnline = isOnline ?? Browser.isOnline,
       _newCode = newCode ?? (() => InviteCodes.generate(Random.secure()));

  final GroupsRepository repository;

  /// The summary (and its publisher) and my profile, as for the workouts' posts.
  final TrainingService training;
  final KeyValueStore store;
  final LocalDates dates;
  final int Function() wallClock;
  final bool Function() _isOnline;
  final String Function() _newCode;

  /// Android's throttles: the index reconciled at most every 6 h at start; Home's members kept 10 min.
  static const int reconcileThrottleMillis = 6 * 60 * 60 * 1000;
  static const int homeCacheMillis = 10 * 60 * 1000;
  static const int _codeAttempts = 3;

  /// Android's FeedLimits.DELETE_PAGE: posts deleted page by page.
  static const int deletePage = 50;

  String _key(String uid, String name) => 'tm.$uid.$name';

  ({String uid, int at, List<GroupMembers> groups})? _homeCache;

  void _log(String operation, Object error) {
    final code = error is GroupException
        ? error.error.name
        : (error is DataException ? error.error.name : '${error.runtimeType}');
    debugPrint('Groups: $operation failed ($code)');
  }

  static GroupError _errorOf(DataError error) => switch (error) {
    DataError.network => GroupError.network,
    DataError.permissionDenied => GroupError.permissionDenied,
    DataError.notFound => GroupError.notFound,
    DataError.failed => GroupError.failed,
  };

  /// Runs [block], turning data failures into [GroupException]s and logging the serious ones.
  Future<T> _run<T>(String operation, Future<T> Function() block) async {
    try {
      return await block();
    } on DataException catch (e) {
      final error = GroupException(_errorOf(e.error));
      _log(operation, error);
      throw error;
    } on GroupException catch (e) {
      if (e.error == GroupError.permissionDenied || e.error == GroupError.failed) _log(operation, e);
      rethrow;
    }
  }

  void _requireOnline() {
    if (!_isOnline()) throw const GroupException(GroupError.network);
  }

  // ---------------------------------------------------------------------------
  // Reconciliation (Android's GroupReconciler)
  // ---------------------------------------------------------------------------

  /// The groups I'm really in: for each index entry, my own member doc (always readable by me) and
  /// the group; a stale entry is removed with counter − 1 (one batch each; a failure is retried at
  /// the next reconciliation). Throws when the index can't be read.
  Future<List<Group>> reconcile(String uid) => _run('reconcile', () async {
    final groups = <Group>[];
    for (final groupId in await repository.membershipIds(uid)) {
      if (!await repository.ownMemberExists(groupId, uid)) {
        await _removeStale(uid, groupId, deleteOwnMemberDoc: false);
        continue;
      }
      Group? group;
      try {
        group = await repository.group(groupId);
      } on DataException catch (e) {
        if (e.error != DataError.permissionDenied && e.error != DataError.notFound) rethrow;
      }
      if (group == null) {
        await _removeStale(uid, groupId, deleteOwnMemberDoc: true);
      } else {
        groups.add(group);
      }
    }
    store.write(_key(uid, 'groupsReconciledAt'), '${wallClock()}');
    groups.sort((a, b) => caseInsensitiveCompare(a.name, b.name));
    return groups;
  });

  /// At start and on My week: at most once every 6 hours; failures are only logged.
  Future<void> reconcileIfDue(String uid) async {
    final last = int.tryParse(store.read(_key(uid, 'groupsReconciledAt')) ?? '');
    final now = wallClock();
    if (last != null && now >= last && now - last < reconcileThrottleMillis) return;
    try {
      await reconcile(uid);
    } on GroupException catch (e) {
      _log('scheduled reconcile', e);
    }
  }

  Future<void> _removeStale(String uid, String groupId, {required bool deleteOwnMemberDoc}) async {
    try {
      await repository.removeStaleMembership(uid, groupId, deleteOwnMemberDoc: deleteOwnMemberDoc);
    } on DataException catch (e) {
      _log('remove stale membership', e);
    }
  }

  // ---------------------------------------------------------------------------
  // The list, create, join (Android's GroupOperations)
  // ---------------------------------------------------------------------------

  /// My groups (the index reconciled first) with my position this week.
  Future<List<MyGroup>> myGroups(WebUser user) => _run('list', () async {
    final weekStart = dates.currentWeekStart();
    return [
      for (final group in await reconcile(user.uid))
        MyGroup(
          group: group,
          myPosition: await _myPosition(group.id, user.uid, weekStart),
          isOwner: group.ownerUid == user.uid,
        ),
    ];
  });

  Future<int?> _myPosition(String groupId, String uid, String weekStart) async {
    try {
      return GroupRanking.rank(
        await repository.members(groupId),
        weekStart,
        uid,
      ).where((l) => l.isMe).firstOrNull?.position;
    } on DataException {
      return null;
    }
  }

  /// A weekly goal is required before creating or joining (groups compare against it).
  Future<bool> hasWeeklyGoal(WebUser user) async => await _summary(user) != null;

  Future<MemberSummary?> _summary(WebUser user) => training.currentSummary(user.uid);

  Fields _memberFields(WebUser user, MemberSummary summary) {
    final profile = memberProfileOf(user, training.texts.memberFallbackName);
    return WriteMappers.memberSummary(profile.displayName, profile.photoUrl, summary);
  }

  /// The group a typed code points to; [GroupError.invalidCode] when it's malformed or unused.
  Future<InvitePreview> findInvite(String rawCode) => _run('lookup', () async {
    final code = InviteCodes.normalize(rawCode);
    if (!InviteCodes.isValid(code)) throw const GroupException(GroupError.invalidCode);
    _requireOnline();
    return await repository.findInvite(code) ?? (throw const GroupException(GroupError.invalidCode));
  });

  Future<CreatedGroup> create(WebUser user, String rawName) => _run('create', () async {
    final name = GroupRules.normalizeName(rawName) ?? (throw const GroupException(GroupError.invalidName));
    _requireOnline();
    final summary = await _summary(user) ?? (throw const GroupException(GroupError.goalRequired));
    if ((await reconcile(user.uid)).length >= GroupRules.maxGroups) throw const GroupException(GroupError.groupLimit);
    final groupId = repository.newGroupId();
    final member = _memberFields(user, summary);
    final code = await _withFreshCode((code) => repository.createGroup(user.uid, groupId, name, code, member));
    _afterMembershipChange(user);
    return CreatedGroup(id: groupId, name: name, code: code);
  });

  /// Joins the group of [invite]; returns its id.
  Future<String> join(WebUser user, InvitePreview invite) => _run('join', () async {
    _requireOnline();
    final summary = await _summary(user) ?? (throw const GroupException(GroupError.goalRequired));
    final groups = await reconcile(user.uid);
    if (groups.any((g) => g.id == invite.groupId)) throw const GroupException(GroupError.alreadyMember);
    if (groups.length >= GroupRules.maxGroups) throw const GroupException(GroupError.groupLimit);
    try {
      await repository.joinGroup(user.uid, invite, _memberFields(user, summary));
    } on DataException catch (e) {
      if (e.error != DataError.permissionDenied) rethrow;
      // The code may have been regenerated meanwhile; otherwise the group is full
      final current = await repository.findInvite(invite.code);
      throw GroupException(current?.groupId == invite.groupId ? GroupError.groupFull : GroupError.invalidCode);
    }
    _afterMembershipChange(user);
    return invite.groupId;
  });

  /// A random code; a code already in use is denied by the rules, so up to 3 tries.
  Future<String> _withFreshCode(Future<void> Function(String code) write) async {
    for (var attempt = 1; ; attempt++) {
      final code = _newCode();
      try {
        await write(code);
        return code;
      } on DataException catch (e) {
        if (e.error != DataError.permissionDenied || attempt >= _codeAttempts) rethrow;
      }
    }
  }

  /// After my own membership changed: the banner's cache goes and my summary is published again
  /// (the existing publisher, writing even if it didn't change).
  void _afterMembershipChange(WebUser user) {
    _homeCache = null;
    training.forgetPublishedSummary(user.uid);
    unawaited(training.publishSummary(user));
  }

  // ---------------------------------------------------------------------------
  // Leave and the owner's actions
  // ---------------------------------------------------------------------------

  /// Leave: a member leaves (their posts first); an owner alone deletes the group; an owner with
  /// other members must name [newOwnerUid] (transfer and leave in one batch).
  Future<void> leave(WebUser user, Group group, {String? newOwnerUid}) => _run('leave', () async {
    _requireOnline();
    if (group.ownerUid != user.uid) {
      await deletePostsBy(group.id, user.uid);
      await repository.leaveGroup(user.uid, group.id, null);
    } else if (group.memberCount <= 1) {
      await _deleteChunked(user.uid, group);
    } else if (newOwnerUid == null || newOwnerUid == user.uid) {
      throw const GroupException(GroupError.permissionDenied);
    } else {
      await deletePostsBy(group.id, user.uid);
      await repository.leaveGroup(user.uid, group.id, newOwnerUid);
    }
    _afterMembershipChange(user);
  });

  Future<void> removeMember(WebUser user, Group group, String memberUid) => _run('remove', () async {
    _requireOwner(user, group);
    _requireOnline();
    await repository.removeMember(group.id, memberUid);
    _homeCache = null;
    // The removal is done; posts left behind (a failure here) expire, or the owner deletes them
    try {
      await deletePostsBy(group.id, memberUid);
    } on Object catch (e) {
      _log('delete a removed member\'s posts', e);
    }
  });

  Future<void> transfer(WebUser user, Group group, String newOwnerUid) => _run('transfer', () async {
    _requireOwner(user, group);
    _requireOnline();
    await repository.transferOwnership(group.id, newOwnerUid);
  });

  Future<void> rename(WebUser user, Group group, String rawName) => _run('rename', () async {
    final name = GroupRules.normalizeName(rawName) ?? (throw const GroupException(GroupError.invalidName));
    _requireOwner(user, group);
    _requireOnline();
    await repository.renameGroup(group.id, group.inviteCode, name);
  });

  /// A new invite code (the old one stops working); returns it.
  Future<String> regenerateCode(WebUser user, Group group) => _run('regenerate', () async {
    _requireOwner(user, group);
    _requireOnline();
    return _withFreshCode((code) => repository.regenerateCode(group.id, group.inviteCode, code, group.name));
  });

  /// Delete group (owner): the posts, then every other member one batch at a time, then the final batch.
  Future<void> delete(WebUser user, Group group) => _run('delete', () async {
    _requireOwner(user, group);
    _requireOnline();
    await _deleteChunked(user.uid, group);
    _afterMembershipChange(user);
  });

  /// Production limits the lookups of one batch, so: the posts first, one write each (only while
  /// the group exists can the owner delete them); then the other members, one per batch (the rules
  /// allow one removal per write); the last batch deletes the group, its code and my membership.
  Future<void> _deleteChunked(String uid, Group group) async {
    await deleteAllPosts(group.id);
    for (final member in (await repository.members(group.id)).where((m) => m.uid != uid)) {
      try {
        await repository.removeMember(group.id, member.uid);
      } on DataException catch (e) {
        // Someone who left meanwhile is fine; anyone still there stops the deletion
        if (e.error != DataError.permissionDenied ||
            (await repository.members(group.id)).any((m) => m.uid == member.uid)) {
          rethrow;
        }
      }
    }
    await repository.deleteGroup(uid, group.id, group.inviteCode);
  }

  void _requireOwner(WebUser user, Group group) {
    if (group.ownerUid != user.uid) throw const GroupException(GroupError.permissionDenied);
  }

  // ---------------------------------------------------------------------------
  // The feed (Android's FeedModeration) and "Hide for me"
  // ---------------------------------------------------------------------------

  /// Every post of [authorUid] in the group, one delete each; returns how many.
  Future<int> deletePostsBy(String groupId, String authorUid) async {
    final ids = await repository.postIdsBy(groupId, authorUid);
    for (final id in ids) {
      await repository.deletePost(groupId, id);
    }
    return ids.length;
  }

  /// Every post of the group, page by page (Delete group, first step, while the group exists).
  Future<int> deleteAllPosts(String groupId) async {
    var deleted = 0;
    while (true) {
      final ids = await repository.postIds(groupId, deletePage);
      if (ids.isEmpty) return deleted;
      for (final id in ids) {
        await repository.deletePost(groupId, id);
      }
      deleted += ids.length;
    }
  }

  /// My post, or any post as the group's owner (the screen asked first).
  Future<void> deletePost(String groupId, String postId) => _run('delete post', () async {
    _requireOnline();
    await repository.deletePost(groupId, postId);
  });

  /// "Hide for me": this browser only, per account; forgotten after a post's life (8 days).
  Set<String> hiddenPosts(String uid) => _hidden(uid).keys.toSet();

  void hidePost(String uid, String groupId, String postId) {
    final now = wallClock();
    final hidden = _hidden(uid)
      ..removeWhere((_, at) => now - at > FeedLimits.postLifetimeMillis)
      ..['$groupId/$postId'] = now;
    store.write(
      _key(uid, 'hiddenPosts'),
      [for (final MapEntry(:key, :value) in hidden.entries) '$key=$value'].join(','),
    );
  }

  Map<String, int> _hidden(String uid) {
    final raw = store.read(_key(uid, 'hiddenPosts')) ?? '';
    return {
      for (final item in raw.split(','))
        if (item.contains('='))
          item.substring(0, item.lastIndexOf('=')): int.tryParse(item.substring(item.lastIndexOf('=') + 1)) ?? 0,
    };
  }

  // ---------------------------------------------------------------------------
  // "Already trained today" (Android's GroupsHome + TrainedTodayRule)
  // ---------------------------------------------------------------------------

  /// The banner for My week: my groups' member docs read once (cached 10 minutes, no listeners);
  /// null for nobody, or when they can't be read. A throttled reconciliation runs alongside.
  Future<TrainedTodayBanner?> trainedTodayBanner(WebUser user, {required bool iTrainedToday}) async {
    unawaited(reconcileIfDue(user.uid));
    final today = dates.today();
    final groups = await _homeGroups(user.uid);
    if (groups == null) return null;
    return TrainedTodayRule.banner(
      myUid: user.uid,
      today: today,
      iTrainedToday: iTrainedToday,
      dismissedOn: store.read(_key(user.uid, 'trainedTodayDismissedOn')),
      groups: groups,
    );
  }

  /// X: hidden for the rest of today (a new day shows it again).
  void dismissTrainedToday(String uid) => store.write(_key(uid, 'trainedTodayDismissedOn'), dates.today());

  Future<List<GroupMembers>?> _homeGroups(String uid) async {
    final cached = _homeCache;
    final now = wallClock();
    if (cached != null && cached.uid == uid && now >= cached.at && now - cached.at < homeCacheMillis) {
      return cached.groups;
    }
    try {
      final groups = <GroupMembers>[];
      for (final groupId in await repository.membershipIds(uid)) {
        try {
          groups.add(GroupMembers(groupId: groupId, members: await repository.members(groupId)));
        } on DataException {
          // Removed, or the group is gone: the reconciliation drops it
        }
      }
      _homeCache = (uid: uid, at: now, groups: groups);
      return groups;
    } on DataException catch (e) {
      _log('read the banner\'s members', e);
      return null;
    }
  }

  // ---------------------------------------------------------------------------
  // Delete account (Android's GroupAccountCleaner) and sign-out
  // ---------------------------------------------------------------------------

  /// Before the server data is erased: my posts in every group (all posts in a group I own alone),
  /// then every group left (owned with others → to the oldest other member by joinedAt, transfer and
  /// leave in one batch; alone → deleted), then the counter. Throws on any failure, so the deletion
  /// stops before erasing anything.
  Future<void> leaveAllGroups(String uid) => _run('account cleanup', () async {
    final groups = await reconcile(uid);
    for (final group in groups) {
      if (group.ownerUid == uid && group.memberCount <= 1) {
        await deleteAllPosts(group.id);
      } else {
        await deletePostsBy(group.id, uid);
      }
    }
    for (final group in groups) {
      if (group.ownerUid != uid) {
        await repository.leaveGroup(uid, group.id, null);
      } else {
        final heir = oldestOtherMember(uid, await repository.members(group.id));
        if (heir == null) {
          await repository.deleteGroup(uid, group.id, group.inviteCode);
        } else {
          await repository.leaveGroup(uid, group.id, heir.uid);
        }
      }
    }
    await repository.deleteGroupCounter(uid);
    _homeCache = null;
  });

  /// This browser's group state of [uid]: hidden posts, the banner's dismissal, the throttle.
  void clearLocalData(String uid) {
    for (final name in const ['hiddenPosts', 'trainedTodayDismissedOn', 'groupsReconciledAt']) {
      store.remove(_key(uid, name));
    }
    _homeCache = null;
  }
}
