import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/app/account_deletion.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/platform/photos.dart';
import 'package:tiominuter_web/ui/account/delete_account_bloc.dart';

import '../support/fakes.dart';
import '../support/groups_kit.dart';

/// Delete account on the web, in Android's order: re-authenticate → my posts → leave every group →
/// erase users/{uid} → the Auth user → this browser's data. Each failure stops the steps after it.
void main() {
  late GroupsKit kit;
  late FakeAuthRepository auth;
  late WebUser ana;

  setUp(() {
    kit = GroupsKit();
    ana = kit.user('ana', 'Ana');
    auth = FakeAuthRepository(user: ana);
  });

  /// Ana owns a group with Bob, and has a photo and keys in this browser.
  Future<String> seed() async {
    final group = await kit.groupOf(ana, [kit.user('bob')]);
    kit.post(group.id, 'p1', 'ana');
    await kit.training.photos.save('ana', 'e1', StoredPhoto(full: Uint8List(4), thumb: null, savedAt: 0));
    kit.training.store.write('tm.ana.progress.daily', '{}');
    kit.service.hidePost('ana', group.id, 'x');
    kit.groups.writes.clear();
    return group.id;
  }

  test('the order: re-authentication, posts, groups, the cloud data, the user, then this browser', () async {
    final groupId = await seed();
    final result = await kit.deletion(auth).start(ana);
    expect(result, DeleteAccountResult.deleted);
    expect(auth.authCalls, ['reauthenticate', 'deleteUser']);
    expect(kit.groups.writes, ['deletePost $groupId p1', 'leave $groupId ana → bob', 'deleteCounter ana']);
    expect(kit.account.erased, {'ana'});
    // This browser keeps nothing of the account
    expect(await kit.training.photos.read('ana', 'e1'), isNull);
    expect(kit.training.store.read('tm.ana.progress.daily'), isNull);
    expect(kit.service.hiddenPosts('ana'), isEmpty);
  });

  test('a cancelled re-authentication: nothing at all, silently', () async {
    await seed();
    auth.reauthOutcome = ReauthOutcome.cancelled;
    expect(await kit.deletion(auth).start(ana), DeleteAccountResult.cancelled);
    expect(kit.groups.writes, isEmpty);
    expect(kit.account.erased, isEmpty);
    expect(await kit.training.photos.read('ana', 'e1'), isNotNull);
  });

  test('a failed re-authentication, another account, offline: nothing deleted', () async {
    await seed();
    for (final (outcome, result) in [
      (ReauthOutcome.failed, DeleteAccountResult.reauthFailed),
      (ReauthOutcome.wrongAccount, DeleteAccountResult.wrongAccount),
      (ReauthOutcome.noNetwork, DeleteAccountResult.noNetwork),
    ]) {
      auth.reauthOutcome = outcome;
      expect(await kit.deletion(auth).start(ana), result);
    }
    kit.online = false;
    expect(await kit.deletion(auth).start(ana), DeleteAccountResult.noNetwork);
    expect(kit.groups.writes, isEmpty);
    expect(kit.account.erased, isEmpty);
  });

  test('a phone: the redirect leaves the page; back, the steps run after it', () async {
    await seed();
    auth.reauthOutcome = ReauthOutcome.redirecting;
    expect(await kit.deletion(auth).start(ana), DeleteAccountResult.redirecting);
    expect(kit.groups.writes, isEmpty);
    expect(await kit.deletion(auth).continueAfterReauth(ana), DeleteAccountResult.deleted);
    expect(kit.account.erased, {'ana'});
  });

  test('the groups fail: nothing erased; the cloud data fails: the user kept; the user fails: reported', () async {
    final groupId = await seed();
    kit.groups.failNext = 'leave';
    expect(await kit.deletion(auth).start(ana), DeleteAccountResult.groupsFailed);
    expect(kit.account.erased, isEmpty);
    expect(auth.authCalls, ['reauthenticate']);

    kit.account.failEraseWith = DataError.network;
    expect(await kit.deletion(auth).start(ana), DeleteAccountResult.serverDataFailed);
    expect(auth.authCalls, isNot(contains('deleteUser')));
    expect(await kit.training.photos.read('ana', 'e1'), isNotNull, reason: 'nothing local before the account goes');

    kit.account.failEraseWith = null;
    auth.deleteUserFails = DataError.failed;
    expect(await kit.deletion(auth).start(ana), DeleteAccountResult.accountNotDeleted);
    expect(kit.account.erased, {'ana'});
    expect(await kit.training.photos.read('ana', 'e1'), isNotNull);
    expect(kit.groups.groups[groupId]!.ownerUid, 'bob');
  });

  group('the two steps (DeleteAccountBloc)', () {
    Future<void> settle() => Future<void>.delayed(const Duration(milliseconds: 5));

    test('explain → confirm → deleting → finished; cancel at any step does nothing', () async {
      await seed();
      final bloc = DeleteAccountBloc(deletion: kit.deletion(auth), user: ana);
      final effects = <DeleteAccountEffect>[];
      final sub = bloc.effects.listen(effects.add);
      bloc.add(const DeleteAccountClicked());
      await settle();
      expect(bloc.state.dialog, DeleteAccountDialog.explain);
      bloc.add(const DeleteAccountDismissed());
      await settle();
      expect(bloc.state.dialog, DeleteAccountDialog.none);
      expect(auth.authCalls, isEmpty);
      bloc
        ..add(const DeleteAccountClicked())
        ..add(const DeleteAccountContinue());
      await settle();
      expect(bloc.state.dialog, DeleteAccountDialog.confirm);
      bloc.add(const DeleteAccountConfirmed());
      await settle();
      await settle();
      expect((effects.single as DeleteAccountFinished).result, DeleteAccountResult.deleted);
      await sub.cancel();
      await bloc.close();
    });

    test('a cancelled re-authentication shows nothing; a resumed one runs the steps; a failed one says so', () async {
      await seed();
      final bloc = DeleteAccountBloc(deletion: kit.deletion(auth), user: ana);
      final effects = <DeleteAccountEffect>[];
      final sub = bloc.effects.listen(effects.add);
      auth.reauthOutcome = ReauthOutcome.cancelled;
      bloc.add(const DeleteAccountConfirmed());
      await settle();
      expect(effects, isEmpty);
      expect(bloc.state.isDeleting, isFalse);
      bloc.add(const DeleteAccountReauthFailed(ReauthOutcome.wrongAccount));
      await settle();
      expect((effects.single as DeleteAccountFinished).result, DeleteAccountResult.wrongAccount);
      bloc.add(const DeleteAccountResumed());
      await settle();
      await settle();
      expect((effects.last as DeleteAccountFinished).result, DeleteAccountResult.deleted);
      expect(auth.authCalls, ['reauthenticate', 'deleteUser'], reason: 'resumed: no second re-authentication');
      await sub.cancel();
      await bloc.close();
    });
  });
}
