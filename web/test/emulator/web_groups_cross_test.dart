// Groups across clients against the Firebase EMULATORS with the real rules, phases 1 and 3 of
// scripts/test_web_groups_emulator.sh: the web (its real GroupsService) creates a group with the
// run's code; an Android user on emulator-5554 joins it and creates one of its own
// (WebGroupsInteropEmulatorTest, phase 2); then the web joins the Android group and both see each
// other. Never the real project (fake API keys, emulators only).
@TestOn('browser')
library;

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_firestore_web/cloud_firestore_web.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_auth_web/firebase_auth_web.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_core_web/firebase_core_web.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_web_plugins/flutter_web_plugins.dart';
import 'package:tiominuter_web/app/groups_service.dart';
import 'package:tiominuter_web/app/training_service.dart';
import 'package:tiominuter_web/data/firebase_repositories.dart';
import 'package:tiominuter_web/data/firebase_training_repositories.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/data/training_repositories.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/firebase_options.dart';
import 'package:tiominuter_web/platform/browser.dart';

import '../support/training_fakes.dart' show EnglishTrainingTexts;

const String _phase = String.fromEnvironment('CROSS_PHASE');
const bool _webEmulator = bool.fromEnvironment('WEB_EMULATOR');
final bool _enabled = _webEmulator && _phase.startsWith('groups-');
const String _run = String.fromEnvironment('CROSS_RUN');

/// The two invite codes of this run (the script derives them from the run id).
const String _webCode = String.fromEnvironment('WEB_GROUP_CODE');
const String _androidCode = String.fromEnvironment('ANDROID_GROUP_CODE');
const String _host = '127.0.0.1';
const String _projectId = 'tiominuter-e43f7';
const String _password = 'password-123';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  FirebaseCoreWeb.registerWith(webPluginRegistrar);
  FirebaseAuthWeb.registerWith(webPluginRegistrar);
  FirebaseFirestoreWeb.registerWith(webPluginRegistrar);

  Future<({FirebaseFirestore db, WebUser user, GroupsService groups})> signIn({required bool create}) async {
    final app = await Firebase.initializeApp(
      name: 'cross-$_phase-${DateTime.now().microsecondsSinceEpoch}',
      options: DefaultFirebaseOptions.web.copyWith(apiKey: 'emulator-only-$_run-$_phase', projectId: _projectId),
    );
    final auth = FirebaseAuth.instanceFor(app: app);
    await auth.useAuthEmulator(_host, 9099);
    final db = FirebaseFirestore.instanceFor(app: app)..useFirestoreEmulator(_host, 8080);
    final email = 'cross-$_run-web@example.test';
    final credential = create
        ? await auth.createUserWithEmailAndPassword(email: email, password: _password)
        : await auth.signInWithEmailAndPassword(email: email, password: _password);
    final user = WebUser(uid: credential.user!.uid, displayName: 'Web Ana');
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
    if (create) {
      await training.setGoal(user, 3);
      await db.waitForPendingWrites();
    }
    final groups = GroupsService(
      repository: FirestoreGroupsRepository(db),
      training: training,
      store: store,
      dates: dates,
      wallClock: () => DateTime.now().millisecondsSinceEpoch,
      isOnline: () => true,
      newCode: () => _webCode,
    );
    return (db: db, user: user, groups: groups);
  }

  test('phase 1: the web creates a group with the run\'s code', () async {
    final web = await signIn(create: true);
    final created = await web.groups.create(web.user, 'Web crew $_run');
    expect(created.code, _webCode);
    await web.db.waitForPendingWrites();
  }, skip: _enabled && _phase == 'groups-web-creates' ? false : 'Opt-in: scripts/test_web_groups_emulator.sh');

  test('phase 3: the Android user is in the web group; the web joins the Android group', () async {
    final web = await signIn(create: false);
    final mine = await web.groups.myGroups(web.user);
    final webGroup = mine.singleWhere((g) => g.group.inviteCode == _webCode).group;
    expect(webGroup.memberCount, 2);
    final members = await web.groups.repository.members(webGroup.id);
    final droid = members.singleWhere((m) => m.uid != web.user.uid);
    expect(droid.displayName, 'Droid');
    expect(droid.summary.weekStartLocal, CalendarDates.mondayOf(LocalDates().today()));
    // The ranking every reader computes (the same as Android's)
    expect(GroupRanking.rank(members, LocalDates().currentWeekStart(), web.user.uid), hasLength(2));

    final invite = await web.groups.findInvite(_androidCode);
    expect(invite.groupName, 'Android crew $_run');
    final groupId = await web.groups.join(web.user, invite);
    final androidGroup = (await web.groups.repository.group(groupId))!;
    expect(androidGroup.ownerUid, droid.uid);
    expect((await web.groups.repository.members(groupId)).map((m) => m.uid).toSet(), {droid.uid, web.user.uid});
  }, skip: _enabled && _phase == 'groups-web-joins' ? false : 'Opt-in: scripts/test_web_groups_emulator.sh');
}
