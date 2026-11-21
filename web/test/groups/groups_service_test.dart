import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/domain/training.dart';

import '../support/groups_kit.dart';

/// Groups as a full web client (Android's GroupOperations, GroupReconciler, GroupsHome,
/// FeedModeration, GroupAccountCleaner) over an in-memory Firestore with the rules' semantics.
void main() {
  late GroupsKit kit;
  late WebUser ana;
  late WebUser bob;
  late WebUser cleo;

  setUp(() {
    kit = GroupsKit();
    ana = kit.user('ana', 'Ana');
    bob = kit.user('bob', 'Bob');
    cleo = kit.user('cleo', 'Cleo');
  });

  Future<GroupError?> errorOf(Future<Object?> Function() action) async {
    try {
      await action();
      return null;
    } on GroupException catch (e) {
      return e.error;
    }
  }

  group('create', () {
    test('a name of 1–40 → the group, my member doc with my summary, the code, the index, counter + 1', () async {
      kit.codes.add('K7MPX2QA');
      final created = await kit.service.create(ana, '  Morning crew  ');
      expect((created.name, created.code), ('Morning crew', 'K7MPX2QA'));
      final group = kit.groups.groups[created.id]!;
      expect((group.ownerUid, group.memberCount, group.inviteCode), ('ana', 1, 'K7MPX2QA'));
      final me = kit.groups.memberDocs[created.id]!.single;
      expect((me.uid, me.displayName, me.summary.weeklyGoal), ('ana', 'Ana', 3));
      expect(kit.groups.invites['K7MPX2QA']!.groupName, 'Morning crew');
      expect(kit.groups.index['ana'], {created.id});
      expect(kit.groups.counters['ana'], 1);
    });

    test('my summary is published at once (even when unchanged)', () async {
      kit.training.store.write('tm.ana.summaryHash', 'old');
      await kit.service.create(ana, 'Crew');
      expect(kit.training.store.read('tm.ana.summaryHash'), isNot('old'));
    });

    test('refused: a bad name, no weekly goal, 5 groups, offline; a taken code is retried', () async {
      expect(await errorOf(() => kit.service.create(ana, '   ')), GroupError.invalidName);
      expect(await errorOf(() => kit.service.create(ana, 'x' * 41)), GroupError.invalidName);
      expect(await errorOf(() => kit.service.create(ana, 'two\nlines')), GroupError.invalidName);
      kit.online = false;
      expect(await errorOf(() => kit.service.create(ana, 'Crew')), GroupError.network);
      kit.online = true;
      kit.training.training.storedGoals.clear();
      expect(await errorOf(() => kit.service.create(ana, 'Crew')), GroupError.goalRequired);
      kit.training.training.storedGoals[kit.training.monday] = 3;
      for (var i = 0; i < 5; i++) {
        await kit.service.create(ana, 'Crew $i');
      }
      expect(await errorOf(() => kit.service.create(ana, 'Sixth')), GroupError.groupLimit);

      final other = GroupsKit();
      other.groups.invites['TAKEN234'] = const InvitePreview(code: 'TAKEN234', groupId: 'x', groupName: 'X');
      other.codes.addAll(['TAKEN234', 'FRESH234']);
      final created = await other.service.create(other.user('dan'), 'Crew');
      expect(created.code, 'FRESH234');
    });
  });

  group('join', () {
    test('with a code: memberCount + 1, my member doc, index, counter', () async {
      final group = await kit.groupOf(ana, [bob]);
      expect(kit.groups.groups[group.id]!.memberCount, 2);
      expect(kit.groups.memberDocs[group.id]!.map((m) => m.uid), ['ana', 'bob']);
      expect(kit.groups.index['bob'], {group.id});
      expect(kit.groups.counters['bob'], 1);
    });

    test('the code: normalized, malformed or unknown → invalid code', () async {
      kit.codes.add('K7MPX2QA');
      final created = await kit.service.create(ana, 'Crew');
      expect((await kit.service.findInvite(' k7mp-x2qa ')).groupId, created.id);
      expect(await errorOf(() => kit.service.findInvite('K7MPX2Q')), GroupError.invalidCode);
      expect(await errorOf(() => kit.service.findInvite('K7MPX2Q0')), GroupError.invalidCode);
      expect(await errorOf(() => kit.service.findInvite('ZZZZZZZZ')), GroupError.invalidCode);
    });

    test('each error: already a member, full (20), the 5-group limit, offline, no goal, a regenerated code', () async {
      final group = await kit.groupOf(ana, [bob]);
      final invite = await kit.service.findInvite(group.inviteCode);
      expect(await errorOf(() => kit.service.join(bob, invite)), GroupError.alreadyMember);

      kit.groups.groups[group.id] = Group(
        id: group.id,
        name: group.name,
        ownerUid: 'ana',
        memberCount: GroupRules.maxMembers,
        inviteCode: group.inviteCode,
      );
      expect(await errorOf(() => kit.service.join(cleo, invite)), GroupError.groupFull);

      final busy = kit.user('dan');
      for (var i = 0; i < 5; i++) {
        await kit.service.create(busy, 'Crew $i');
      }
      expect(await errorOf(() => kit.service.join(busy, invite)), GroupError.groupLimit);

      kit.online = false;
      expect(await errorOf(() => kit.service.join(cleo, invite)), GroupError.network);
      kit.online = true;

      kit.training.training.storedGoals.clear();
      expect(await errorOf(() => kit.service.join(cleo, invite)), GroupError.goalRequired);
      kit.training.training.storedGoals[kit.training.monday] = 3;

      // The owner made a new code meanwhile: the old one no longer works
      final small = await kit.groupOf(ana, [], name: 'Small');
      final old = await kit.service.findInvite(small.inviteCode);
      await kit.service.regenerateCode(ana, kit.groups.groups[small.id]!);
      expect(await errorOf(() => kit.service.join(cleo, old)), GroupError.invalidCode);
    });
  });

  group('leave', () {
    test('a member: my posts first, then leave (counter − 1)', () async {
      final group = await kit.groupOf(ana, [bob]);
      kit.post(group.id, 'p1', 'bob');
      kit.post(group.id, 'p2', 'ana');
      await kit.service.leave(bob, kit.groups.groups[group.id]!);
      expect(kit.groups.writes.skipWhile((w) => !w.startsWith('deletePost')).take(2), [
        'deletePost ${group.id} p1',
        'leave ${group.id} bob',
      ]);
      expect(kit.groups.posts[group.id]!.map((p) => p.id), ['p2']);
      expect(kit.groups.counters['bob'], 0);
    });

    test('an owner with members must name the new owner (transfer and leave in one batch)', () async {
      final group = await kit.groupOf(ana, [bob, cleo]);
      expect(await errorOf(() => kit.service.leave(ana, kit.groups.groups[group.id]!)), GroupError.permissionDenied);
      await kit.service.leave(ana, kit.groups.groups[group.id]!, newOwnerUid: 'cleo');
      expect(kit.groups.writes.last, 'leave ${group.id} ana → cleo');
      expect(kit.groups.groups[group.id]!.ownerUid, 'cleo');
    });

    test('an owner alone: leaving deletes the group', () async {
      final group = await kit.groupOf(ana, []);
      await kit.service.leave(ana, kit.groups.groups[group.id]!);
      expect(kit.groups.groups.containsKey(group.id), isFalse);
      expect(kit.groups.invites.containsKey(group.inviteCode), isFalse);
      expect(kit.groups.counters['ana'], 0);
    });
  });

  group('the owner', () {
    test('rename (1–40), a new code, transfer; a member can\'t', () async {
      final group = await kit.groupOf(ana, [bob]);
      expect(await errorOf(() => kit.service.rename(ana, group, '')), GroupError.invalidName);
      await kit.service.rename(ana, group, 'Night crew');
      expect(kit.groups.groups[group.id]!.name, 'Night crew');
      expect(kit.groups.invites[group.inviteCode]!.groupName, 'Night crew');
      final code = await kit.service.regenerateCode(ana, kit.groups.groups[group.id]!);
      expect(kit.groups.invites.containsKey(group.inviteCode), isFalse);
      expect(kit.groups.invites[code]!.groupId, group.id);
      expect(await errorOf(() => kit.service.rename(bob, group, 'Mine')), GroupError.permissionDenied);
      await kit.service.transfer(ana, group, 'bob');
      expect(kit.groups.groups[group.id]!.ownerUid, 'bob');
    });

    test('remove a member: the removal, then their posts (a failure there is only logged)', () async {
      final group = await kit.groupOf(ana, [bob, cleo]);
      kit.post(group.id, 'b1', 'bob');
      await kit.service.removeMember(ana, kit.groups.groups[group.id]!, 'bob');
      final writes = kit.groups.writes.skipWhile((w) => !w.startsWith('remove')).toList();
      expect(writes, ['remove ${group.id} bob', 'deletePost ${group.id} b1']);
      expect(kit.groups.groups[group.id]!.memberCount, 2);

      kit.post(group.id, 'c1', 'cleo');
      kit.groups.failNext = 'deletePost';
      await kit.service.removeMember(ana, kit.groups.groups[group.id]!, 'cleo');
      expect(kit.groups.memberDocs[group.id]!.map((m) => m.uid), ['ana']);
    });

    test('delete group in chunks: every post, then each member one batch at a time, then the group', () async {
      final group = await kit.groupOf(ana, [bob, cleo]);
      kit
        ..post(group.id, 'p1', 'bob')
        ..post(group.id, 'p2', 'ana');
      kit.groups.writes.clear();
      await kit.service.delete(ana, kit.groups.groups[group.id]!);
      expect(kit.groups.writes, [
        'deletePost ${group.id} p1',
        'deletePost ${group.id} p2',
        'remove ${group.id} bob',
        'remove ${group.id} cleo',
        'deleteGroup ${group.id}',
      ]);
      expect(kit.groups.groups, isEmpty);
    });

    test('a member leaving during the deletion is fine; anyone still there stops it', () async {
      final group = await kit.groupOf(ana, [bob, cleo]);
      kit.groups.failNext = 'remove ${group.id} bob';
      kit.groups.failNextWith = DataError.permissionDenied;
      expect(await errorOf(() => kit.service.delete(ana, kit.groups.groups[group.id]!)), GroupError.permissionDenied);
      expect(kit.groups.groups.containsKey(group.id), isTrue, reason: 'Bob is still a member: nothing more deleted');
    });
  });

  group('reconciliation', () {
    test('removed from a group, or the group deleted: the stale index entry goes with counter − 1', () async {
      final group = await kit.groupOf(ana, [bob]);
      final gone = await kit.groupOf(cleo, [bob], name: 'Gone');
      // Ana removes Bob; Cleo's group disappears while Bob's member doc stays behind
      await kit.service.removeMember(ana, kit.groups.groups[group.id]!, 'bob');
      kit.groups.groups.remove(gone.id);
      final mine = await kit.service.myGroups(bob);
      expect(mine, isEmpty);
      expect(kit.groups.index['bob'], isEmpty);
      expect(kit.groups.counters['bob'], 0);
      expect(kit.groups.writes.where((w) => w.startsWith('stale')), ['stale ${group.id} bob', 'stale ${gone.id} bob']);
      expect(kit.groups.memberDocs[gone.id]!.any((m) => m.uid == 'bob'), isFalse, reason: 'my doc left behind');
    });

    test('at start and on My week: at most once every 6 hours', () async {
      await kit.groupOf(ana, []);
      await kit.service.reconcileIfDue('ana');
      final reads = kit.groups.calls.where((c) => c == 'membershipIds').length;
      kit.training.wallOffset += const Duration(hours: 5).inMilliseconds;
      await kit.service.reconcileIfDue('ana');
      expect(kit.groups.calls.where((c) => c == 'membershipIds').length, reads);
      kit.training.wallOffset += const Duration(hours: 2).inMilliseconds;
      await kit.service.reconcileIfDue('ana');
      expect(kit.groups.calls.where((c) => c == 'membershipIds').length, reads + 1);
    });

    test('my groups with my position this week', () async {
      final group = await kit.groupOf(ana, [bob]);
      final mine = await kit.service.myGroups(bob);
      expect(mine.single.group.id, group.id);
      expect(mine.single.isOwner, isFalse);
      expect(mine.single.myPosition, isNotNull);
    });
  });

  group('the "already trained today" banner', () {
    GroupMember trained(String uid, String name, {String? on}) {
      final today = kit.training.today;
      return GroupMember(
        uid: uid,
        displayName: name,
        photoUrl: null,
        summary: MemberSummary(
          weekStartLocal: kit.training.monday,
          workoutsThisWeek: 1,
          weeklyGoal: 3,
          streakWeeks: 0,
          daysThisWeek: const {},
          lastTrainedLocal: on ?? today,
        ),
      );
    }

    void seed(List<List<GroupMember>> groups) {
      for (final (i, members) in groups.indexed) {
        kit.groups.groups['g$i'] = Group(id: 'g$i', name: 'G$i', ownerUid: 'x', memberCount: members.length);
        kit.groups.memberDocs['g$i'] = members;
        (kit.groups.index['me'] ??= {}).add('g$i');
      }
    }

    test('1 / 2 / 3+ names, distinct across groups, me excluded, sorted; one group → open it', () async {
      final me = kit.user('me');
      seed([
        [trained('me', 'Me', on: '2026-10-01'), trained('luis', 'Luis')],
        [
          trained('me', 'Me', on: '2026-10-01'),
          trained('ana', 'Ana'),
          trained('luis', 'Luis'),
          trained('zoe', 'Zoe', on: '2026-10-01'),
        ],
      ]);
      final banner = (await kit.service.trainedTodayBanner(me, iTrainedToday: false))!;
      expect(banner.names, ['Ana', 'Luis']);
      expect(banner.groupIds, ['g0', 'g1']);
      final one = TrainedTodayRule.banner(
        myUid: 'me',
        today: kit.training.today,
        iTrainedToday: false,
        dismissedOn: null,
        groups: [
          GroupMembers(groupId: 'g', members: [trained('ana', 'Ana')]),
        ],
      )!;
      expect(one.names, ['Ana']);
      expect(one.groupIds, ['g']);
    });

    test('hidden when I trained, when dismissed today; back on a new day; cached 10 minutes', () async {
      final me = kit.user('me');
      // My own member doc is there too (a real group): the 6-hour reconciliation keeps the group
      seed([
        [trained('me', 'Me', on: '2026-10-01'), trained('ana', 'Ana')],
      ]);
      expect(await kit.service.trainedTodayBanner(me, iTrainedToday: true), isNull);
      expect(await kit.service.trainedTodayBanner(me, iTrainedToday: false), isNotNull);
      final reads = kit.groups.calls.where((c) => c == 'membershipIds').length;
      await kit.service.trainedTodayBanner(me, iTrainedToday: false);
      expect(kit.groups.calls.where((c) => c == 'membershipIds').length, reads, reason: 'cached');
      kit.service.dismissTrainedToday('me');
      expect(await kit.service.trainedTodayBanner(me, iTrainedToday: false), isNull);
      // A new day: the dismissal is yesterday's, and Ana's summary is now yesterday's too
      kit.training.wallOffset += const Duration(days: 1).inMilliseconds;
      kit.groups.memberDocs['g0'] = [
        trained('me', 'Me', on: '2026-10-01'),
        trained('ana', 'Ana', on: kit.training.today),
      ];
      expect(await kit.service.trainedTodayBanner(me, iTrainedToday: false), isNotNull);
    });
  });

  group('the feed', () {
    test('Hide for me: this browser, per account, forgotten after 8 days', () async {
      kit.service.hidePost('me', 'g', 'p1');
      expect(kit.service.hiddenPosts('me'), {'g/p1'});
      expect(kit.service.hiddenPosts('other'), isEmpty);
      kit.training.wallOffset += const Duration(days: 9).inMilliseconds;
      kit.service.hidePost('me', 'g', 'p2');
      expect(kit.service.hiddenPosts('me'), {'g/p2'});
    });

    test('delete a post; offline refused', () async {
      final group = await kit.groupOf(ana, [bob]);
      kit.post(group.id, 'p1', 'bob');
      await kit.service.deletePost(group.id, 'p1');
      expect(kit.groups.posts[group.id], isEmpty);
      kit.online = false;
      expect(await errorOf(() => kit.service.deletePost(group.id, 'p2')), GroupError.network);
    });
  });

  group('Delete account: leave every group', () {
    test('posts first everywhere, then: a member leaves, owned → the oldest other member, alone → deleted', () async {
      final mine = await kit.groupOf(ana, [cleo, bob], name: 'Mine');
      final alone = await kit.groupOf(ana, [], name: 'Alone');
      final theirs = await kit.groupOf(bob, [ana], name: 'Theirs');
      kit
        ..post(mine.id, 'a1', 'ana')
        ..post(mine.id, 'b1', 'bob')
        ..post(alone.id, 'a2', 'ana')
        ..post(theirs.id, 'a3', 'ana');
      kit.groups.writes.clear();
      await kit.service.leaveAllGroups('ana');
      final writes = kit.groups.writes;
      final lastPost = writes.lastIndexWhere((w) => w.startsWith('deletePost'));
      final firstGroup = writes.indexWhere((w) => !w.startsWith('deletePost'));
      expect(lastPost, lessThan(firstGroup), reason: 'every post before any membership change');
      expect(writes.where((w) => w.startsWith('deletePost')).length, 3, reason: 'not Bob\'s post');
      // Cleo joined before Bob: the oldest other member
      expect(writes, contains('leave ${mine.id} ana → cleo'));
      expect(writes, contains('deleteGroup ${alone.id}'));
      expect(writes, contains('leave ${theirs.id} ana'));
      expect(writes.last, 'deleteCounter ana');
    });

    test('a failure stops it (the deletion then erases nothing)', () async {
      final group = await kit.groupOf(bob, [ana]);
      kit.groups.failNext = 'leave';
      expect(await errorOf(() => kit.service.leaveAllGroups('ana')), GroupError.failed);
      expect(kit.groups.memberDocs[group.id]!.any((m) => m.uid == 'ana'), isTrue);
    });

    test('the heir rule: the oldest by joinedAt, a pending time last, then by uid', () {
      GroupMember m(String uid, int? at) => GroupMember(
        uid: uid,
        displayName: uid,
        photoUrl: null,
        summary: const MemberSummary(
          weekStartLocal: '2026-10-19',
          workoutsThisWeek: 0,
          weeklyGoal: 1,
          streakWeeks: 0,
          daysThisWeek: <int, List<TrainingSource>>{},
          lastTrainedLocal: null,
        ),
        joinedAtMillis: at,
      );
      expect(oldestOtherMember('me', [m('me', 1), m('zed', null), m('bob', 5), m('amy', 5)])!.uid, 'amy');
      expect(oldestOtherMember('me', [m('me', 1)]), isNull);
    });
  });

  test('invite codes: 8 characters without 0 O 1 I L, uppercased, spaces and dashes ignored', () {
    expect(InviteCodes.normalize(' k7mp-x2 qa'), 'K7MPX2QA');
    expect(InviteCodes.isValid('K7MPX2QA'), isTrue);
    for (final bad in ['K7MPX2Q0', 'K7MPX2QO', 'K7MPX2Q1', 'K7MPX2QI', 'K7MPX2QL']) {
      expect(InviteCodes.hasInvalidCharacters(bad), isTrue, reason: bad);
    }
    expect(InviteCodes.isValid('K7MPX2Q'), isFalse);
  });
}
