/// Delete account on the web, in Android's order (CLAUDE.md "Accounts"), each step only if the one
/// before worked:
///   1. a fresh Google re-authentication (a popup on desktop; on phones a redirect, the steps below
///      run when the page comes back — [continueAfterReauth]); cancelled → nothing, silently;
///   2. my posts in every group, then every group left (owned with others → the oldest other member
///      by joinedAt; alone → deleted), then the group counter (`GroupsService.leaveAllGroups`);
///   3. everything under `users/{uid}` (programs with their workouts and completions, the log, the
///      goals, the profile doc), in batches;
///   4. the Firebase Auth user;
///   5. this browser's data of the account (photos, progress, sessions, hidden posts, caches).
/// A failure stops everything after it (nothing more is deleted) and says which step failed.
library;

import '../data/repositories.dart';
import 'groups_service.dart';
import 'training_service.dart';

enum DeleteAccountResult {
  deleted,

  /// The re-authentication was closed: nothing happened.
  cancelled,

  /// The page is leaving for Google (a phone); the deletion continues when it comes back.
  redirecting,
  noNetwork,

  /// The re-authentication failed (or another Google account was chosen): nothing deleted.
  reauthFailed,
  wrongAccount,

  /// Step 2 failed: nothing was erased.
  groupsFailed,

  /// Step 3 failed: the account is kept.
  serverDataFailed,

  /// Step 4 failed after the data was erased.
  accountNotDeleted,
}

class AccountDeletion {
  AccountDeletion({
    required this.auth,
    required this.groups,
    required this.account,
    required this.training,
    this.isOnline,
  });

  final AuthRepository auth;
  final GroupsService groups;
  final AccountRepository account;
  final TrainingService training;
  final bool Function()? isOnline;

  /// From the confirmation: re-authenticate first, then the rest (or [DeleteAccountResult.redirecting]).
  Future<DeleteAccountResult> start(WebUser user) async {
    if (!(isOnline?.call() ?? true)) return DeleteAccountResult.noNetwork;
    final outcome = await auth.reauthenticate();
    return switch (outcome) {
      ReauthOutcome.reauthenticated => continueAfterReauth(user),
      ReauthOutcome.redirecting => DeleteAccountResult.redirecting,
      ReauthOutcome.cancelled => DeleteAccountResult.cancelled,
      ReauthOutcome.noNetwork => DeleteAccountResult.noNetwork,
      ReauthOutcome.wrongAccount => DeleteAccountResult.wrongAccount,
      ReauthOutcome.failed => DeleteAccountResult.reauthFailed,
    };
  }

  /// Steps 2–5, once re-authenticated (here or after the redirect came back).
  Future<DeleteAccountResult> continueAfterReauth(WebUser user) async {
    final uid = user.uid;
    try {
      await groups.leaveAllGroups(uid);
    } on Object {
      return DeleteAccountResult.groupsFailed;
    }
    try {
      await account.eraseUserData(uid);
    } on Object {
      return DeleteAccountResult.serverDataFailed;
    }
    try {
      await auth.deleteCurrentUser();
    } on Object {
      return DeleteAccountResult.accountNotDeleted;
    }
    await clearLocalData(uid);
    return DeleteAccountResult.deleted;
  }

  /// This browser's data of [uid]: training keys and photos, the groups' local state.
  Future<void> clearLocalData(String uid) async {
    await training.clearLocalData(uid);
    groups.clearLocalData(uid);
  }
}
