import 'dart:async';

import 'package:tiominuter_web/data/content_repository.dart';
import 'package:tiominuter_web/data/firestore_mappers.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/data/write_mappers.dart' show Fields;
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/weekly_progress.dart';
import 'package:tiominuter_web/domain/workout_names.dart';

/// In-memory doubles of the repositories, with the rules' semantics where it matters (a reaction
/// changes only my key; a removed member can't read the group).

class FakeAuthRepository implements AuthRepository {
  FakeAuthRepository({this._user});

  WebUser? _user;
  // A test double lives as long as its test: its controllers are never closed
  // ignore: close_sinks
  final StreamController<WebUser?> _changes = StreamController<WebUser?>.broadcast();
  SignInOutcome nextSignIn = SignInOutcome.signedIn;
  WebUser signInAs = const WebUser(uid: 'me', displayName: 'Alex Rivera', email: 'alex@example.test');
  int signIns = 0;

  // The current user first, then every change (a plain controller: cancelling never waits)
  @override
  Stream<WebUser?> get user {
    late final StreamSubscription<WebUser?> changes;
    // ignore: close_sinks (done when its listener cancels)
    late final StreamController<WebUser?> controller;
    controller = StreamController<WebUser?>(
      onListen: () {
        if (!_redirectDone && redirectResult != null) userListensBeforeRedirect++;
        controller.add(_user);
        changes = _changes.stream.listen(controller.add);
      },
      onCancel: () => changes.cancel(),
    );
    return controller.stream;
  }

  @override
  WebUser? get currentUser => _user;

  @override
  Future<SignInOutcome> signInWithGoogle() async {
    signIns++;
    if (nextSignIn == SignInOutcome.signedIn) {
      _user = signInAs;
      _changes.add(_user);
    }
    return nextSignIn;
  }

  @override
  Future<void> signOut() async {
    _user = null;
    _changes.add(null);
  }

  /// The page came back from a sign-in redirect; [redirectResult] completes it ([redirectUser]
  /// signs in when it completes with signedIn). Listening to [user] before it completes is recorded.
  bool returningFromRedirect = false;
  Completer<SignInOutcome?>? redirectResult;
  WebUser? redirectUser;
  int userListensBeforeRedirect = 0;
  bool _redirectDone = false;

  @override
  bool get isReturningFromRedirect => returningFromRedirect;

  /// Delete account: the scripted re-authentication, the resume after a redirect, the user's deletion.
  ReauthOutcome reauthOutcome = ReauthOutcome.reauthenticated;
  bool resumeDeletion = false;
  ReauthOutcome? deletionFailure;
  DataError? deleteUserFails;
  final List<String> authCalls = [];

  @override
  Future<ReauthOutcome> reauthenticate() async {
    authCalls.add('reauthenticate');
    return reauthOutcome;
  }

  @override
  bool takeAccountDeletionResume() {
    final resume = resumeDeletion;
    resumeDeletion = false;
    return resume;
  }

  @override
  ReauthOutcome? takeAccountDeletionFailure() {
    final failure = deletionFailure;
    deletionFailure = null;
    return failure;
  }

  @override
  Future<void> deleteCurrentUser() async {
    final error = deleteUserFails;
    if (error != null) throw DataException(error);
    authCalls.add('deleteUser');
    _user = null;
    _changes.add(null);
  }

  @override
  Future<SignInOutcome?> completeRedirectSignIn() async {
    final outcome = await (redirectResult?.future ?? Future<SignInOutcome?>.value());
    if (outcome == SignInOutcome.signedIn && redirectUser != null) _user = redirectUser;
    _redirectDone = true;
    return outcome;
  }
}

class FakeContentRepository implements ContentRepository {
  FakeContentRepository([this.names = WorkoutNameCatalog.empty, this.trainingContent]);

  WorkoutNameCatalog names;
  TrainingContent? trainingContent;

  @override
  Future<WorkoutNameCatalog> catalog() async => names;

  @override
  Future<TrainingContent?> training() async => trainingContent;
}

TrainingEntry trainingEntry(
  String id, {
  required String date,
  int completedAt = 0,
  TrainingSource source = TrainingSource.gym,
  String? refId = 'gym:64',
  String title = 'Pull Day',
  int? durationSeconds = 1800,
  String? activity,
}) => TrainingEntry(
  id: id,
  dateLocal: date,
  completedAt: completedAt,
  source: source,
  refId: refId,
  title: title,
  durationSeconds: durationSeconds,
  activityTypeCode: activity,
);

class FakeUserDataRepository implements UserDataRepository {
  /// Every live entry of the account (the Firestore collection, tombstones left out).
  List<TrainingEntry> entries = [];
  List<WeeklyGoal> goals = [];
  Map<String, ProgramTitleCode> programCodes = {};
  DataError? failWith;
  final List<String> calls = [];
  final List<({StreamController<Object?> controller, void Function() push})> _listeners = [];
  int get activeListeners => _listeners.where((l) => l.controller.hasListener).length;

  /// Tells the live listeners that the data changed.
  void changed() {
    for (final listener in List.of(_listeners)) {
      if (listener.controller.hasListener) listener.push();
    }
  }

  Stream<T> _live<T>(String name, T Function() read) {
    calls.add(name);
    // ignore: close_sinks (closed when its listener cancels; a test double lives as long as its test)
    late final StreamController<T> controller;
    void push() {
      final error = failWith;
      try {
        if (error != null) throw DataException(error);
        controller.add(read());
      } on DataException catch (e) {
        controller.addError(e);
      }
    }

    controller = StreamController<T>(
      onListen: push,
      onCancel: () => _listeners.removeWhere((listener) => listener.controller == controller),
    );
    _listeners.add((controller: controller, push: push));
    return controller.stream;
  }

  @override
  Stream<List<TrainingEntry>> watchEntriesFrom(String uid, String fromDate) =>
      _live('watchEntriesFrom:$fromDate', () => entries.where((e) => e.dateLocal.compareTo(fromDate) >= 0).toList());

  @override
  Stream<List<WeeklyGoal>> watchGoals(String uid) => _live('watchGoals', () => List.of(goals));

  @override
  Future<List<TrainingEntry>> entriesBetween(String uid, String fromDate, String untilDate) async {
    calls.add('entriesBetween:$fromDate..$untilDate');
    final error = failWith;
    if (error != null) throw DataException(error);
    return entries.where((e) => e.dateLocal.compareTo(fromDate) >= 0 && e.dateLocal.compareTo(untilDate) < 0).toList();
  }

  @override
  Future<HistoryPage> historyPage(String uid, {Object? after, int limit = 20}) async {
    calls.add('historyPage:${after ?? 0}');
    final error = failWith;
    if (error != null) throw DataException(error);
    final sorted = List.of(entries)..sort((a, b) => b.completedAt.compareTo(a.completedAt));
    final start = (after as int?) ?? 0;
    final page = sorted.skip(start).take(limit).toList();
    final next = start + page.length;
    return HistoryPage(entries: page, next: page.length == limit && next < sorted.length ? next : null);
  }

  @override
  Future<Map<String, ProgramTitleCode>> programTitleCodes(String uid, Iterable<String> refIds) async {
    calls.add('programTitleCodes');
    return {
      for (final refId in refIds)
        if (programCodes[refId] != null) refId: programCodes[refId]!,
    };
  }
}

class FakeGroupsRepository implements GroupsRepository {
  final Map<String, Group> groups = {};
  final Map<String, List<GroupMember>> memberDocs = {};
  final Map<String, List<FeedPost>> posts = {};

  /// Groups I can read (my member doc exists).
  final Set<String> readable = {};
  DataError? failWith;
  DataError? failReactionsWith;
  Completer<void>? reactionGate;
  final List<String> calls = [];
  final List<({StreamController<Object?> controller, void Function() push})> _listeners = [];
  int get activeListeners => _listeners.where((l) => l.controller.hasListener).length;

  void addGroup(Group group, List<GroupMember> groupMembers, {bool member = true}) {
    groups[group.id] = group;
    memberDocs[group.id] = groupMembers;
    if (member) readable.add(group.id);
  }

  void changed() {
    for (final listener in List.of(_listeners)) {
      if (listener.controller.hasListener) listener.push();
    }
  }

  Stream<T> _live<T>(String name, T Function() read) {
    calls.add(name);
    // ignore: close_sinks (closed when its listener cancels; a test double lives as long as its test)
    late final StreamController<T> controller;
    void push() {
      final error = failWith;
      try {
        if (error != null) throw DataException(error);
        controller.add(read());
      } on DataException catch (e) {
        controller.addError(e);
      }
    }

    controller = StreamController<T>(
      onListen: push,
      onCancel: () => _listeners.removeWhere((listener) => listener.controller == controller),
    );
    _listeners.add((controller: controller, push: push));
    return controller.stream;
  }

  @override
  Future<List<Group>> myGroups(String uid) async {
    calls.add('myGroups');
    final error = failWith;
    if (error != null) throw DataException(error);
    return [for (final id in readable) ?groups[id]];
  }

  @override
  Stream<Group?> watchGroup(String groupId) =>
      _live('watchGroup:$groupId', () => readable.contains(groupId) ? groups[groupId] : null);

  @override
  Stream<List<GroupMember>> watchMembers(String groupId) => _live('watchMembers:$groupId', () {
    if (!readable.contains(groupId)) throw const DataException(DataError.permissionDenied);
    return List.of(memberDocs[groupId] ?? const []);
  });

  @override
  Stream<List<FeedPost>> watchWeekPosts(String groupId, String weekStart, int limit) =>
      _live('watchWeekPosts:$groupId:$weekStart:$limit', () {
        final week = (posts[groupId] ?? const []).where((p) => p.weekStartLocal == weekStart).toList()
          ..sort((a, b) => (b.createdAtMillis ?? 0).compareTo(a.createdAtMillis ?? 0));
        return week.take(limit).toList();
      });

  @override
  Future<void> setReaction(String groupId, String postId, String myUid, Reaction? reaction) async {
    calls.add('setReaction:$groupId:$postId:${reaction?.code ?? 'none'}');
    await reactionGate?.future;
    final error = failReactionsWith;
    if (error != null) throw DataException(error);
    final list = posts[groupId]!;
    final index = list.indexWhere((p) => p.id == postId);
    list[index] = list[index].withReactions(ReactionRules.withMine(list[index].reactions, myUid, reaction));
    changed();
  }
  // ---------- Managing groups: an in-memory server with the rules' semantics ----------

  /// users/{uid}/groups: the index of each account; users/{uid}/meta/groups: the counter.
  final Map<String, Set<String>> index = {};
  final Map<String, int> counters = {};
  final Map<String, InvitePreview> invites = {};

  /// The writes, in order ("create g1", "remove g1 bob", …), for the order tests.
  final List<String> writes = [];

  /// Fails the next write whose name starts with this ("leave", "deletePost"…).
  String? failNext;
  DataError failNextWith = DataError.failed;
  int _ids = 0;
  int _clock = 0;

  void _write(String name) {
    final fail = failNext;
    if (fail != null && name.startsWith(fail)) {
      failNext = null;
      throw DataException(failNextWith);
    }
    writes.add(name);
  }

  void _counter(String uid, int step) => counters[uid] = (counters[uid] ?? 0) + step;

  GroupMember _memberOf(String uid, Fields fields) {
    final member = FirestoreMappers.member(uid, {...fields, 'schemaVersion': 1})!;
    return GroupMember(
      uid: uid,
      displayName: member.displayName,
      photoUrl: member.photoUrl,
      summary: member.summary,
      joinedAtMillis: ++_clock,
    );
  }

  Group _with(Group g, {String? ownerUid, int? memberCount, String? name, String? inviteCode}) => Group(
    id: g.id,
    name: name ?? g.name,
    ownerUid: ownerUid ?? g.ownerUid,
    memberCount: memberCount ?? g.memberCount,
    inviteCode: inviteCode ?? g.inviteCode,
  );

  @override
  String newGroupId() => 'group-${++_ids}';

  @override
  Future<List<String>> membershipIds(String uid) async {
    calls.add('membershipIds');
    final error = failWith;
    if (error != null) throw DataException(error);
    return [...?index[uid]];
  }

  @override
  Future<Group?> group(String groupId) async => groups[groupId];

  @override
  Future<List<GroupMember>> members(String groupId) async {
    if (!groups.containsKey(groupId)) throw const DataException(DataError.permissionDenied);
    return List.of(memberDocs[groupId] ?? const []);
  }

  @override
  Future<bool> ownMemberExists(String groupId, String uid) async =>
      (memberDocs[groupId] ?? const []).any((m) => m.uid == uid);

  @override
  Future<InvitePreview?> findInvite(String code) async => invites[code];

  @override
  Future<void> createGroup(String uid, String groupId, String name, String code, Fields member) async {
    if (invites.containsKey(code)) throw const DataException(DataError.permissionDenied);
    _write('create $groupId');
    groups[groupId] = Group(id: groupId, name: name, ownerUid: uid, memberCount: 1, inviteCode: code);
    memberDocs[groupId] = [_memberOf(uid, member)];
    invites[code] = InvitePreview(code: code, groupId: groupId, groupName: name);
    (index[uid] ??= {}).add(groupId);
    readable.add(groupId);
    _counter(uid, 1);
    changed();
  }

  @override
  Future<void> joinGroup(String uid, InvitePreview invite, Fields member) async {
    final group = groups[invite.groupId];
    if (group == null ||
        invites[invite.code]?.groupId != invite.groupId ||
        group.memberCount >= GroupRules.maxMembers ||
        (counters[uid] ?? 0) >= GroupRules.maxGroups) {
      throw const DataException(DataError.permissionDenied);
    }
    _write('join ${invite.groupId} $uid');
    groups[invite.groupId] = _with(group, memberCount: group.memberCount + 1);
    memberDocs[invite.groupId] = [...?memberDocs[invite.groupId], _memberOf(uid, member)];
    (index[uid] ??= {}).add(invite.groupId);
    readable.add(invite.groupId);
    _counter(uid, 1);
    changed();
  }

  @override
  Future<void> leaveGroup(String uid, String groupId, String? newOwnerUid) async {
    final group = groups[groupId]!;
    // The rules: an owner leaves only with a transfer in the same batch
    if (group.ownerUid == uid && newOwnerUid == null) throw const DataException(DataError.permissionDenied);
    _write('leave $groupId $uid${newOwnerUid == null ? '' : ' → $newOwnerUid'}');
    groups[groupId] = _with(group, memberCount: group.memberCount - 1, ownerUid: newOwnerUid);
    memberDocs[groupId]!.removeWhere((m) => m.uid == uid);
    index[uid]?.remove(groupId);
    readable.remove(groupId);
    _counter(uid, -1);
    changed();
  }

  @override
  Future<void> removeMember(String groupId, String memberUid) async {
    final group = groups[groupId]!;
    if (!memberDocs[groupId]!.any((m) => m.uid == memberUid)) throw const DataException(DataError.permissionDenied);
    _write('remove $groupId $memberUid');
    groups[groupId] = _with(group, memberCount: group.memberCount - 1);
    memberDocs[groupId]!.removeWhere((m) => m.uid == memberUid);
    changed();
  }

  @override
  Future<void> transferOwnership(String groupId, String newOwnerUid) async {
    _write('transfer $groupId $newOwnerUid');
    groups[groupId] = _with(groups[groupId]!, ownerUid: newOwnerUid);
    changed();
  }

  @override
  Future<void> renameGroup(String groupId, String code, String name) async {
    _write('rename $groupId');
    groups[groupId] = _with(groups[groupId]!, name: name);
    invites[code] = InvitePreview(code: code, groupId: groupId, groupName: name);
    changed();
  }

  @override
  Future<void> regenerateCode(String groupId, String oldCode, String newCode, String name) async {
    if (invites.containsKey(newCode)) throw const DataException(DataError.permissionDenied);
    _write('regenerate $groupId');
    groups[groupId] = _with(groups[groupId]!, inviteCode: newCode);
    invites.remove(oldCode);
    invites[newCode] = InvitePreview(code: newCode, groupId: groupId, groupName: name);
    changed();
  }

  @override
  Future<void> deleteGroup(String uid, String groupId, String code) async {
    // The final batch: only my member doc may be left
    if ((memberDocs[groupId] ?? const []).any((m) => m.uid != uid)) {
      throw const DataException(DataError.permissionDenied);
    }
    _write('deleteGroup $groupId');
    groups.remove(groupId);
    memberDocs.remove(groupId);
    invites.remove(code);
    index[uid]?.remove(groupId);
    readable.remove(groupId);
    _counter(uid, -1);
    changed();
  }

  @override
  Future<void> removeStaleMembership(String uid, String groupId, {required bool deleteOwnMemberDoc}) async {
    _write('stale $groupId $uid');
    if (deleteOwnMemberDoc) memberDocs[groupId]?.removeWhere((m) => m.uid == uid);
    index[uid]?.remove(groupId);
    _counter(uid, -1);
    changed();
  }

  @override
  Future<void> deleteGroupCounter(String uid) async {
    if ((counters[uid] ?? 0) != 0) return;
    _write('deleteCounter $uid');
    counters.remove(uid);
  }

  @override
  Future<List<String>> postIdsBy(String groupId, String authorUid) async => [
    for (final post in posts[groupId] ?? const <FeedPost>[])
      if (post.authorUid == authorUid) post.id,
  ];

  @override
  Future<List<String>> postIds(String groupId, int limit) async => [
    for (final post in (posts[groupId] ?? const <FeedPost>[]).take(limit)) post.id,
  ];

  @override
  Future<void> deletePost(String groupId, String postId) async {
    _write('deletePost $groupId $postId');
    posts[groupId]?.removeWhere((p) => p.id == postId);
    changed();
  }
}

/// `users/{uid}` and its subcollections, in memory.
class FakeAccountRepository implements AccountRepository {
  final Map<String, ({String? displayName, String? photoUrl})> profiles = {};
  final Set<String> erased = {};
  final List<String> writes = [];
  DataError? failEraseWith;

  @override
  Future<void> writeProfile(String uid, String? displayName, String? photoUrl) async {
    writes.add('profile $uid');
    profiles[uid] = (displayName: displayName, photoUrl: photoUrl);
  }

  @override
  Future<void> eraseUserData(String uid) async {
    final error = failEraseWith;
    if (error != null) throw DataException(error);
    writes.add('erase $uid');
    erased.add(uid);
    profiles.remove(uid);
  }
}

GroupMember groupMember(
  String uid,
  String name, {
  int done = 1,
  int goal = 3,
  int streak = 0,
  String week = '2026-10-19',
  Map<int, List<TrainingSource>> days = const {},
}) => GroupMember(
  uid: uid,
  displayName: name,
  photoUrl: null,
  summary: MemberSummary(
    weekStartLocal: week,
    workoutsThisWeek: done,
    weeklyGoal: goal,
    streakWeeks: streak,
    daysThisWeek: days,
    lastTrainedLocal: null,
  ),
);

FeedPost feedPost(
  String id,
  String author, {
  int? createdAt = 1,
  String week = '2026-10-19',
  TrainingSource source = TrainingSource.gym,
  String title = 'Pull Day',
  String? titleKey,
  Map<String, Reaction> reactions = const {},
}) => FeedPost(
  id: id,
  authorUid: author,
  authorName: author[0].toUpperCase() + author.substring(1),
  authorPhotoUrl: null,
  source: source,
  title: title,
  titleKey: titleKey,
  activityType: null,
  durationSeconds: 1800,
  dateLocal: week,
  weekStartLocal: week,
  createdAtMillis: createdAt,
  reactions: reactions,
  thumb: null,
);
