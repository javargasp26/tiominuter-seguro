/// What the screens need from Firebase, as interfaces (the screens and their tests never see
/// Firebase types). Implementations: `firebase_repositories.dart`; test fakes: test/fakes.dart.
library;

import '../domain/groups.dart';
import '../domain/training.dart';
import '../domain/weekly_progress.dart';
import '../domain/workout_names.dart';
import 'write_mappers.dart' show Fields;

/// The signed-in person (never logged: no names, emails or uids in logs).
class WebUser {
  const WebUser({required this.uid, this.displayName, this.email, this.photoUrl});

  final String uid;
  final String? displayName;
  final String? email;
  final String? photoUrl;
}

/// How the re-authentication before Delete account ended.
enum ReauthOutcome {
  reauthenticated,

  /// The page is leaving for Google's sign-in (a redirect): the deletion resumes when it returns.
  redirecting,

  /// Closed or backed out: nothing happens and nothing is shown (as Android).
  cancelled,
  noNetwork,

  /// Another Google account than the signed-in one was chosen.
  wrongAccount,
  failed,
}

enum SignInOutcome {
  signedIn,
  cancelled,
  popupBlocked,
  noNetwork,

  /// A sign-in redirect came back without a session: the browser kept the sign-in page's storage
  /// from this site (blocked cookies or storage, a private mode).
  redirectIncomplete,
  failed,
}

abstract interface class AuthRepository {
  /// The current user; null when signed out. Emits the current value first.
  Stream<WebUser?> get user;

  WebUser? get currentUser;

  /// Google sign-in: a popup on desktop browsers, a redirect on mobile ones (or when the popup
  /// can't open). A redirect leaves the page; the result arrives with [user] on return.
  Future<SignInOutcome> signInWithGoogle();

  /// A sign-in redirect was started from this tab and the page is coming back from it.
  bool get isReturningFromRedirect;

  /// Once at startup, before the app decides where to go: the result of a sign-in redirect.
  /// [SignInOutcome.signedIn] when it signed in, null when there was none (or it was cancelled), the
  /// failure otherwise.
  Future<SignInOutcome?> completeRedirectSignIn();

  /// Delete account, step 1: a fresh Google sign-in of the same account — a popup on desktop; on
  /// phones a redirect (the page leaves; [takeAccountDeletionResume] tells the next start).
  Future<ReauthOutcome> reauthenticate();

  /// True once when the page comes back from the re-authentication redirect of a deletion.
  bool takeAccountDeletionResume();

  /// Once: how that re-authentication redirect failed (null when it didn't).
  ReauthOutcome? takeAccountDeletionFailure();

  /// Deletes the signed-in Firebase user (the last cloud step of Delete account).
  Future<void> deleteCurrentUser();

  Future<void> signOut();
}

/// Why a read or write failed (the screens show a message; logs keep only this and the class).
enum DataError { network, permissionDenied, notFound, failed }

class DataException implements Exception {
  const DataException(this.error);

  final DataError error;

  @override
  String toString() => 'DataException($error)';
}

/// One page of the history, newest first, and where the next one starts.
class HistoryPage {
  const HistoryPage({required this.entries, required this.next});

  final List<TrainingEntry> entries;

  /// Opaque cursor of the next page; null when this was the last one.
  final Object? next;
}

/// My own data under `users/{uid}` (read only).
abstract interface class UserDataRepository {
  /// Entries with `dateLocal >= fromDate`, live (a listener: cancel the subscription when hidden).
  Stream<List<TrainingEntry>> watchEntriesFrom(String uid, String fromDate);

  /// Entries with `fromDate <= dateLocal < untilDate` (one read; for past weeks of the streak).
  Future<List<TrainingEntry>> entriesBetween(String uid, String fromDate, String untilDate);

  /// Every live weekly goal, live (a listener).
  Stream<List<WeeklyGoal>> watchGoals(String uid);

  /// One page of the history, newest first (`completedAt` desc), tombstones excluded.
  Future<HistoryPage> historyPage(String uid, {Object? after, int limit});

  /// The program workout each PROGRAM refId of [refIds] points to; a deleted program (or workout)
  /// is left out. Cached for the page load.
  Future<Map<String, ProgramTitleCode>> programTitleCodes(String uid, Iterable<String> refIds);
}

/// My groups and their feed (read only, plus my own reaction).
abstract interface class GroupsRepository {
  /// The groups of my index I can still read (a stale entry — removed, or the group deleted — is
  /// skipped; the Android app reconciles it).
  Future<List<Group>> myGroups(String uid);

  /// The group, live; null when it can't be read any more.
  Stream<Group?> watchGroup(String groupId);

  /// The member docs, live.
  Stream<List<GroupMember>> watchMembers(String groupId);

  /// This week's posts, newest first, up to [limit] (a listener).
  Stream<List<FeedPost>> watchWeekPosts(String groupId, String weekStart, int limit);

  /// Sets ([reaction]) or removes (null) my reaction: an update of `reactions.<myUid>` only.
  Future<void> setReaction(String groupId, String postId, String myUid, Reaction? reaction);

  // ---------- Managing groups: the batches of docs/firestore-model.md "Groups" (Android's
  // FirestoreGroupRepository; firebase/rules-tests/test/groupBatches.js is the reference). Reads
  // that decide something come from the server. Failures throw [DataException].

  /// A new random group id (a Firestore auto id).
  String newGroupId();

  /// The group ids of my membership index (`users/{uid}/groups`).
  Future<List<String>> membershipIds(String uid);

  /// The group, or null when it no longer exists.
  Future<Group?> group(String groupId);

  /// Every member doc of a group I'm in.
  Future<List<GroupMember>> members(String groupId);

  /// Whether my own member doc exists (always readable by me).
  Future<bool> ownMemberExists(String groupId, String uid);

  /// `inviteCodes/{code}`, or null when no group uses that code.
  Future<InvitePreview?> findInvite(String code);

  /// Create: the group, my member doc ([member] + joinedAt and the code), the invite code, my index
  /// entry, counter + 1.
  Future<void> createGroup(String uid, String groupId, String name, String code, Fields member);

  /// Join: memberCount + 1 (an increment: a joiner can't read the group), my member doc, my index
  /// entry, counter + 1.
  Future<void> joinGroup(String uid, InvitePreview invite, Fields member);

  /// Leave: memberCount − 1, my member doc and index entry, counter − 1; an owner hands the group
  /// to [newOwnerUid] in the same batch.
  Future<void> leaveGroup(String uid, String groupId, String? newOwnerUid);

  /// Remove member (owner): memberCount − 1 naming them, their member doc. One per batch.
  Future<void> removeMember(String groupId, String memberUid);

  Future<void> transferOwnership(String groupId, String newOwnerUid);

  /// Rename (owner): the group and its invite code doc.
  Future<void> renameGroup(String groupId, String code, String name);

  /// New code (owner): the old invite doc deleted, the new one created, group.inviteCode.
  Future<void> regenerateCode(String groupId, String oldCode, String newCode, String name);

  /// Delete group, the final batch (every other member already removed): my member doc, the group,
  /// its invite code, my index entry, counter − 1.
  Future<void> deleteGroup(String uid, String groupId, String code);

  /// A stale index entry: the entry and counter − 1, plus my own member doc when [deleteOwnMemberDoc].
  Future<void> removeStaleMembership(String uid, String groupId, {required bool deleteOwnMemberDoc});

  /// Delete account, the last group step: the counter, when it exists and is at 0.
  Future<void> deleteGroupCounter(String uid);

  /// The ids of [authorUid]'s posts in the group.
  Future<List<String>> postIdsBy(String groupId, String authorUid);

  /// Up to [limit] post ids of the group (Delete group deletes them all, page by page).
  Future<List<String>> postIds(String groupId, int limit);

  /// One post (mine, or any as the owner); a missing post is a no-op.
  Future<void> deletePost(String groupId, String postId);
}

/// My account's documents outside the groups (Android's `FirestoreRemoteUserData`).
abstract interface class AccountRepository {
  /// `users/{uid}`: created with `createdAt` = server time, or merged keeping it (the rules check it).
  Future<void> writeProfile(String uid, String? displayName, String? photoUrl);

  /// Delete account: every program with its workouts and completions, the training log, the
  /// weekly goals, then `users/{uid}` (batches of ≤ 500).
  Future<void> eraseUserData(String uid);
}
