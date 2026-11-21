// Groups and Delete account from the web, end to end against the Firebase EMULATORS (Auth 9099,
// Firestore 8080) with the real firebase/firestore.rules: every batch is the web's real code
// (FirestoreGroupsRepository + GroupsService), so the deployed rules must accept it as Android's.
// Web user A creates a group, B joins with the code, both publish their summaries, B posts, A
// (owner) removes B, B rejoins, A transfers the group, B deletes it; then a web user deletes their
// account and their cloud data is gone. Never the real project (fake API keys, emulators only).
//
// Opt-in, in Chrome: scripts/test_web_emulator.sh (runs every test of test/emulator/).
@TestOn('browser')
library;

import 'dart:convert';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_firestore_web/cloud_firestore_web.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_auth_web/firebase_auth_web.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_core_web/firebase_core_web.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_web_plugins/flutter_web_plugins.dart';
import 'package:http/http.dart' as http;
import 'package:tiominuter_web/app/account_deletion.dart';
import 'package:tiominuter_web/app/groups_service.dart';
import 'package:tiominuter_web/app/training_service.dart';
import 'package:tiominuter_web/data/firebase_repositories.dart';
import 'package:tiominuter_web/data/firebase_training_repositories.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/data/training_repositories.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/firebase_options.dart';
import 'package:tiominuter_web/platform/browser.dart';

import '../support/training_fakes.dart' show EnglishTrainingTexts;

const bool _enabled = bool.fromEnvironment('WEB_EMULATOR') && String.fromEnvironment('CROSS_PHASE') == '';
const String _host = '127.0.0.1';
const String _projectId = 'tiominuter-e43f7';

/// One person in their own browser: a FirebaseApp, the web's training and groups services.
class _Person {
  _Person._(this.app, this.user, this.auth, this.db, this.training, this.groups);

  /// Every person of the current test, closed after it: a browser allows only a few connections
  /// per host, and each Firestore client keeps its channels to the emulator open.
  static final List<_Person> open = [];

  static Future<void> closeAll() async {
    for (final person in open) {
      await person.db.terminate();
      await person.app.delete();
    }
    open.clear();
  }

  final FirebaseApp app;

  final WebUser user;
  final FirebaseAuth auth;
  final FirebaseFirestore db;
  final TrainingService training;
  final GroupsService groups;

  String get uid => user.uid;

  static Future<_Person> create(String name) async {
    final id = '$name${DateTime.now().microsecondsSinceEpoch}';
    final app = await Firebase.initializeApp(
      name: id,
      options: DefaultFirebaseOptions.web.copyWith(apiKey: 'emulator-only-$id', projectId: _projectId),
    );
    final auth = FirebaseAuth.instanceFor(app: app);
    await auth.useAuthEmulator(_host, 9099);
    final db = FirebaseFirestore.instanceFor(app: app)..useFirestoreEmulator(_host, 8080);
    final credential = await auth.createUserWithEmailAndPassword(email: '$id@example.test', password: 'password-123');
    final user = WebUser(uid: credential.user!.uid, displayName: name);
    final pending = PendingWrites();
    final store = MemoryKeyValueStore();
    final dates = LocalDates();
    final training = TrainingService(
      training: FirestoreTrainingRepository(db, pending),
      sharing: FirestoreSharingRepository(db, pending),
      store: store,
      dates: dates,
      texts: EnglishTrainingTexts(),
    );
    final groups = GroupsService(
      repository: FirestoreGroupsRepository(db),
      training: training,
      store: store,
      dates: dates,
      wallClock: () => DateTime.now().millisecondsSinceEpoch,
      isOnline: () => true,
    );
    // A weekly goal: groups need one
    await training.setGoal(user, 3);
    await db.waitForPendingWrites();
    final person = _Person._(app, user, auth, db, training, groups);
    open.add(person);
    return person;
  }

  Future<void> settle() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
    await db.waitForPendingWrites();
  }
}

/// The emulator's admin read (the `owner` token skips the rules): what's left of a path.
Future<int> _adminCount(String path) async {
  final response = await http.get(
    Uri.parse('http://$_host:8080/v1/projects/$_projectId/databases/(default)/documents/$path'),
    headers: {'Authorization': 'Bearer owner'},
  );
  if (response.statusCode == 404) return 0;
  final body = jsonDecode(response.body) as Map<String, Object?>;
  return body.containsKey('documents') ? (body['documents']! as List).length : (body.containsKey('fields') ? 1 : 0);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  FirebaseCoreWeb.registerWith(webPluginRegistrar);
  FirebaseAuthWeb.registerWith(webPluginRegistrar);
  FirebaseFirestoreWeb.registerWith(webPluginRegistrar);

  tearDown(_Person.closeAll);

  setUpAll(() async {
    if (!_enabled) return;
    for (final port in [8080, 9099]) {
      final response = await http.get(Uri.parse('http://$_host:$port/'));
      expect(response.statusCode, lessThan(500), reason: 'emulator on $port');
    }
  });

  test(
    'create, join, summaries, a post, remove, rejoin, transfer, delete: the real rules accept every batch',
    () async {
      final a = await _Person.create('Ana');
      final b = await _Person.create('Bob');

      final created = await a.groups.create(a.user, 'Web crew');
      final invite = await b.groups.findInvite(created.code.toLowerCase());
      expect(invite.groupName, 'Web crew');
      await b.groups.join(b.user, invite);
      await a.settle();
      await b.settle();

      // Both summaries published (the member docs carry this week's numbers)
      final members = await a.groups.repository.members(created.id);
      expect(members.map((m) => m.uid).toSet(), {a.uid, b.uid});
      expect(members.every((m) => m.summary.weekStartLocal == CalendarDates.mondayOf(LocalDates().today())), isTrue);

      // B posts a counted workout of this week
      b.training.setFeedIntroSeen(b.uid);
      final entry = await b.training.recordWorkout(
        user: b.user,
        source: TrainingSource.daily,
        refId: 'daily:3',
        title: 'Day 3',
        durationSeconds: 1200,
      );
      await b.settle();
      expect(await a.groups.repository.postIdsBy(created.id, b.uid), [entry.id]);

      // A removes B: their post goes too; B's index is stale until B's own reconciliation
      var group = (await a.groups.repository.group(created.id))!;
      await a.groups.removeMember(a.user, group, b.uid);
      expect(await a.groups.repository.postIdsBy(created.id, b.uid), isEmpty);
      expect(await b.groups.myGroups(b.user), isEmpty);
      expect(await b.groups.repository.membershipIds(b.uid), isEmpty);

      // B rejoins with the same code; A hands the group to B; B (now owner) renames, regenerates, deletes
      await b.groups.join(b.user, await b.groups.findInvite(created.code));
      group = (await a.groups.repository.group(created.id))!;
      await a.groups.transfer(a.user, group, b.uid);
      group = (await b.groups.repository.group(created.id))!;
      expect(group.ownerUid, b.uid);
      await b.groups.rename(b.user, group, 'Bob crew');
      final newCode = await b.groups.regenerateCode(b.user, (await b.groups.repository.group(created.id))!);
      expect(await b.groups.repository.findInvite(created.code), isNull);
      expect((await a.groups.findInvite(newCode)).groupName, 'Bob crew');
      a.training.setFeedIntroSeen(a.uid);
      await a.training.recordWorkout(
        user: a.user,
        source: TrainingSource.daily,
        refId: 'daily:2',
        title: 'Day 2',
        durationSeconds: 900,
      );
      await a.settle();
      await b.groups.delete(b.user, (await b.groups.repository.group(created.id))!);
      expect(await _adminCount('groups/${created.id}'), 0);
      expect(await _adminCount('groups/${created.id}/members'), 0);
      expect(await _adminCount('groups/${created.id}/posts'), 0);
      expect(await _adminCount('inviteCodes/$newCode'), 0);
      // A's index entry is reconciled away (the group is gone)
      expect(await a.groups.myGroups(a.user), isEmpty);
    },
    skip: _enabled ? false : 'Opt-in: scripts/test_web_emulator.sh',
  );

  // The 20-member cap and the 21st refusal: firebase/rules-tests (groups.test.js) and Android's
  // GroupsEmulatorTest; the web's mapping of that refusal to "group full" is unit-tested (21
  // signed-in apps in one browser tab are too slow for this suite)
  test('an owner leaving with members transfers in the same batch', () async {
    final owner = await _Person.create('Owner');
    final heir = await _Person.create('Heir');
    final created = await owner.groups.create(owner.user, 'Big');
    await heir.groups.join(heir.user, await heir.groups.findInvite(created.code));
    await owner.groups.leave(owner.user, (await owner.groups.repository.group(created.id))!, newOwnerUid: heir.uid);
    expect((await heir.groups.repository.group(created.id))!.ownerUid, heir.uid);
    expect(await owner.groups.repository.membershipIds(owner.uid), isEmpty);
    expect((await heir.groups.repository.members(created.id)).map((m) => m.uid), [heir.uid]);
  }, skip: _enabled ? false : 'Opt-in: scripts/test_web_emulator.sh');

  test(
    'Delete account: posts, groups, users/{uid}, the Auth user — the cloud data is gone',
    () async {
      final friend = await _Person.create('Friend');
      final me = await _Person.create('Leaving');
      final theirs = await friend.groups.create(friend.user, 'Theirs');
      await me.groups.join(me.user, await me.groups.findInvite(theirs.code));
      final mine = await me.groups.create(me.user, 'Mine');
      await friend.groups.join(friend.user, await friend.groups.findInvite(mine.code));
      final alone = await me.groups.create(me.user, 'Alone');
      me.training.setFeedIntroSeen(me.uid);
      await me.training.recordWorkout(
        user: me.user,
        source: TrainingSource.daily,
        refId: 'daily:3',
        title: 'Day 3',
        durationSeconds: 1200,
      );
      await me.settle();
      expect(await _adminCount('users/${me.uid}/trainingLog'), 1);

      // The re-authentication is a Google popup / redirect (not on the emulator): the steps after it
      final deletion = AccountDeletion(
        auth: FirebaseAuthRepository(me.auth),
        groups: me.groups,
        account: FirestoreAccountRepository(me.db),
        training: me.training,
      );
      expect(await deletion.continueAfterReauth(me.user), DeleteAccountResult.deleted);
      expect(me.auth.currentUser, isNull);
      for (final path in [
        'users/${me.uid}',
        'users/${me.uid}/trainingLog',
        'users/${me.uid}/weeklyGoals',
        'users/${me.uid}/groups',
        'users/${me.uid}/meta/groups',
        'groups/${alone.id}',
        'groups/${theirs.id}/members/${me.uid}',
        'groups/${theirs.id}/posts',
        'groups/${mine.id}/members/${me.uid}',
      ]) {
        expect(await _adminCount(path), 0, reason: path);
      }
      // My group with a friend went to them
      expect((await friend.groups.repository.group(mine.id))!.ownerUid, friend.uid);
    },
    skip: _enabled ? false : 'Opt-in: scripts/test_web_emulator.sh',
    timeout: const Timeout(Duration(minutes: 2)),
  );
}
