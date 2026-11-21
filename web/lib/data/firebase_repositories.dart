/// The Firebase implementations of `repositories.dart`. Every query is one of the reads documented
/// in docs/firestore-model.md "The web client"; nothing else is read or written. Logs carry the
/// operation and the error code only (no names, emails, uids or document contents).
library;

import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';

import '../domain/groups.dart';
import '../domain/training.dart';
import '../domain/weekly_progress.dart';
import '../domain/workout_names.dart';
import '../platform/browser.dart';
import 'firebase_training_repositories.dart' show toFirestore;
import 'firestore_mappers.dart';
import 'repositories.dart';
import 'write_mappers.dart' show Fields;

void _log(String operation, Object error) {
  final code = error is FirebaseException ? error.code : error.runtimeType.toString();
  debugPrint('Firestore: $operation failed ($code)');
}

DataError _errorOf(Object error) {
  if (error is FirebaseException) {
    return switch (error.code) {
      'unavailable' || 'deadline-exceeded' => DataError.network,
      'permission-denied' || 'unauthenticated' => DataError.permissionDenied,
      'not-found' => DataError.notFound,
      _ => DataError.failed,
    };
  }
  return DataError.failed;
}

Future<T> _call<T>(String operation, Future<T> Function() block) async {
  try {
    return await block();
  } on Object catch (e) {
    _log(operation, e);
    throw DataException(_errorOf(e));
  }
}

Stream<T> _stream<T>(String operation, Stream<T> source) => source.handleError((Object e) {
  _log(operation, e);
  throw DataException(_errorOf(e));
});

class FirebaseAuthRepository implements AuthRepository {
  FirebaseAuthRepository(this._auth, {KeyValueStore? tab}) : _tab = tab ?? MemoryKeyValueStore();

  final FirebaseAuth _auth;

  /// This tab's sessionStorage: marks a redirect started here, so its return is recognised.
  final KeyValueStore _tab;
  static const String _redirectKey = 'tm.signInRedirect';

  WebUser? _map(User? user) => user == null
      ? null
      : WebUser(uid: user.uid, displayName: user.displayName, email: user.email, photoUrl: user.photoURL);

  @override
  Stream<WebUser?> get user => _auth.authStateChanges().map(_map);

  @override
  WebUser? get currentUser => _map(_auth.currentUser);

  /// A redirect on phones and tablets (popups are unreliable there), a popup elsewhere; a popup
  /// the browser blocks falls back to the redirect.
  @override
  Future<SignInOutcome> signInWithGoogle() async {
    final provider = GoogleAuthProvider();
    final mobile = defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS;
    try {
      if (mobile) {
        await _redirect(provider);
      } else {
        await _auth.signInWithPopup(provider);
      }
      return SignInOutcome.signedIn;
    } on FirebaseAuthException catch (e) {
      debugPrint('Auth: sign-in failed (${e.code})');
      switch (e.code) {
        case 'popup-closed-by-user' || 'cancelled-popup-request' || 'user-cancelled':
          return SignInOutcome.cancelled;
        case 'popup-blocked' || 'operation-not-supported-in-this-environment':
          try {
            await _redirect(provider);
            return SignInOutcome.signedIn;
          } on FirebaseAuthException catch (redirect) {
            debugPrint('Auth: redirect failed (${redirect.code})');
            return SignInOutcome.popupBlocked;
          }
        case 'network-request-failed':
          return SignInOutcome.noNetwork;
        default:
          return SignInOutcome.failed;
      }
    }
  }

  /// Leaves the page for Google's sign-in; marked first, so the return knows it was expected.
  Future<void> _redirect(GoogleAuthProvider provider) async {
    _tab.write(_redirectKey, '1');
    try {
      await _auth.signInWithRedirect(provider);
    } on Object {
      _tab.remove(_redirectKey);
      rethrow;
    }
  }

  @override
  bool get isReturningFromRedirect => _tab.read(_redirectKey) == '1' || _tab.read(_deletionKey) != null;

  static const String _deletionKey = 'tm.deleteAccountReauth';

  bool get _mobile => defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS;

  @override
  Future<ReauthOutcome> reauthenticate() async {
    final user = _auth.currentUser;
    if (user == null) return ReauthOutcome.failed;
    final provider = GoogleAuthProvider()..setCustomParameters({'login_hint': user.email ?? ''});
    Future<ReauthOutcome> redirect() async {
      // Marked first: the deletion continues when the page comes back, for this account only
      _tab.write(_deletionKey, user.uid);
      try {
        await user.reauthenticateWithRedirect(provider);
        return ReauthOutcome.redirecting;
      } on Object {
        _tab.remove(_deletionKey);
        rethrow;
      }
    }

    try {
      if (_mobile) return await redirect();
      await user.reauthenticateWithPopup(provider);
      return ReauthOutcome.reauthenticated;
    } on FirebaseAuthException catch (e) {
      debugPrint('Auth: re-authentication failed (${e.code})');
      switch (e.code) {
        case 'popup-closed-by-user' || 'cancelled-popup-request' || 'user-cancelled':
          return ReauthOutcome.cancelled;
        case 'popup-blocked' || 'operation-not-supported-in-this-environment':
          try {
            return await redirect();
          } on FirebaseAuthException catch (again) {
            debugPrint('Auth: re-authentication redirect failed (${again.code})');
            return ReauthOutcome.failed;
          }
        case 'user-mismatch':
          return ReauthOutcome.wrongAccount;
        case 'network-request-failed':
          return ReauthOutcome.noNetwork;
        default:
          return ReauthOutcome.failed;
      }
    }
  }

  @override
  bool takeAccountDeletionResume() {
    final uid = _tab.read(_deletionKey);
    _tab.remove(_deletionKey);
    return uid != null && uid == _auth.currentUser?.uid && _reauthenticatedByRedirect;
  }

  /// The redirect that just completed was this account's re-authentication.
  bool _reauthenticatedByRedirect = false;
  ReauthOutcome? _deletionFailure;

  @override
  ReauthOutcome? takeAccountDeletionFailure() {
    final failure = _deletionFailure;
    _deletionFailure = null;
    return failure;
  }

  @override
  Future<void> deleteCurrentUser() async {
    final user = _auth.currentUser;
    if (user == null) throw const DataException(DataError.failed);
    try {
      await user.delete();
    } on FirebaseAuthException catch (e) {
      debugPrint('Auth: account not deleted (${e.code})');
      throw DataException(e.code == 'network-request-failed' ? DataError.network : DataError.failed);
    }
  }

  @override
  Future<SignInOutcome?> completeRedirectSignIn() async {
    final expected = isReturningFromRedirect;
    final deletion = _tab.read(_deletionKey);
    _tab.remove(_redirectKey);
    try {
      final credential = await _auth.getRedirectResult();
      if (credential.user != null) {
        _reauthenticatedByRedirect = deletion != null && credential.user!.uid == deletion;
        if (deletion != null && !_reauthenticatedByRedirect) _tab.remove(_deletionKey);
        return SignInOutcome.signedIn;
      }
      // Backed out of the re-authentication: no deletion, nothing to say
      if (deletion != null) {
        _tab.remove(_deletionKey);
        return null;
      }
      if (!expected) return null;
      // Back from Google with no result: signed in anyway (a restored session), or the browser
      // kept the handler's storage from this site
      final user = await _auth.authStateChanges().first;
      return user != null ? SignInOutcome.signedIn : SignInOutcome.redirectIncomplete;
    } on FirebaseAuthException catch (e) {
      debugPrint('Auth: redirect sign-in failed (${e.code})');
      if (deletion != null) {
        // The re-authentication of a deletion failed: still signed in, nothing deleted
        _tab.remove(_deletionKey);
        _deletionFailure = switch (e.code) {
          'user-cancelled' => ReauthOutcome.cancelled,
          'user-mismatch' => ReauthOutcome.wrongAccount,
          'network-request-failed' => ReauthOutcome.noNetwork,
          _ => ReauthOutcome.failed,
        };
        return null;
      }
      return switch (e.code) {
        'user-cancelled' || 'popup-closed-by-user' => null,
        'network-request-failed' => SignInOutcome.noNetwork,
        'web-storage-unsupported' || 'missing-initial-state' => SignInOutcome.redirectIncomplete,
        _ => SignInOutcome.failed,
      };
    } on Object catch (e) {
      debugPrint('Auth: redirect sign-in failed (${e.runtimeType})');
      return expected ? SignInOutcome.failed : null;
    }
  }

  @override
  Future<void> signOut() => _auth.signOut();
}

class FirestoreUserDataRepository implements UserDataRepository {
  FirestoreUserDataRepository(this._firestore);

  final FirebaseFirestore _firestore;
  final Map<String, Future<bool>> _livePrograms = {};
  final Map<String, Future<ProgramTitleCode?>> _workoutCodes = {};

  CollectionReference<Map<String, dynamic>> _trainingLog(String uid) => _firestore.collection('users/$uid/trainingLog');

  static List<TrainingEntry> _entries(QuerySnapshot<Map<String, dynamic>> snapshot) => [
    for (final doc in snapshot.docs) ?FirestoreMappers.trainingEntry(doc.id, doc.data()),
  ];

  @override
  Stream<List<TrainingEntry>> watchEntriesFrom(String uid, String fromDate) => _stream(
    'watch entries',
    _trainingLog(uid).where('dateLocal', isGreaterThanOrEqualTo: fromDate).snapshots().map(_entries),
  );

  @override
  Future<List<TrainingEntry>> entriesBetween(String uid, String fromDate, String untilDate) => _call(
    'read past entries',
    () async => _entries(
      await _trainingLog(uid)
          .where('dateLocal', isGreaterThanOrEqualTo: fromDate)
          .where('dateLocal', isLessThan: untilDate)
          .get(),
    ),
  );

  @override
  Stream<List<WeeklyGoal>> watchGoals(String uid) => _stream(
    'watch goals',
    _firestore
        .collection('users/$uid/weeklyGoals')
        .snapshots()
        .map((snapshot) => [for (final doc in snapshot.docs) ?FirestoreMappers.weeklyGoal(doc.id, doc.data())]),
  );

  // Ordered by completedAt: a tombstone has no completedAt, so the query never returns one
  @override
  Future<HistoryPage> historyPage(String uid, {Object? after, int limit = 20}) => _call('read history', () async {
    var query = _trainingLog(uid).orderBy('completedAt', descending: true).limit(limit);
    if (after is DocumentSnapshot) query = query.startAfterDocument(after);
    final snapshot = await query.get();
    return HistoryPage(entries: _entries(snapshot), next: snapshot.docs.length == limit ? snapshot.docs.last : null);
  });

  @override
  Future<Map<String, ProgramTitleCode>> programTitleCodes(String uid, Iterable<String> refIds) async {
    final codes = <String, ProgramTitleCode>{};
    for (final refId in refIds.toSet()) {
      final workout = programWorkoutOf(refId);
      if (workout == null) continue;
      final code = await (_workoutCodes[refId] ??= _codeOf(uid, workout.programId, workout.week, workout.dayIndex));
      if (code != null) codes[refId] = code;
    }
    return codes;
  }

  Future<ProgramTitleCode?> _codeOf(String uid, String programId, int week, int dayIndex) async {
    try {
      final live = await (_livePrograms[programId] ??= _firestore
          .doc('users/$uid/programs/$programId')
          .get()
          .then((doc) => FirestoreMappers.isLiveProgram(doc.data())));
      if (!live) return null;
      final doc = await _firestore.doc('users/$uid/programs/$programId/workouts/$week-$dayIndex').get();
      final data = doc.data();
      return data == null ? null : FirestoreMappers.programWorkoutTitleCode(data);
    } on Object catch (e) {
      // Not known: the entry shows its stored title
      _log('read program workout', e);
      _livePrograms.removeWhere((key, _) => key == programId);
      _workoutCodes.removeWhere((key, _) => key == 'program:$programId:$week:$dayIndex');
      return null;
    }
  }
}

class FirestoreGroupsRepository implements GroupsRepository {
  FirestoreGroupsRepository(this._firestore);

  final FirebaseFirestore _firestore;

  @override
  Future<List<Group>> myGroups(String uid) => _call('read my groups', () async {
    final index = await _firestore.collection('users/$uid/groups').get();
    final groups = <Group>[];
    for (final entry in index.docs) {
      final groupId = FirestoreMappers.membershipGroupId(entry.id, entry.data());
      if (groupId == null) continue;
      try {
        final doc = await _firestore.doc('groups/$groupId').get();
        final data = doc.data();
        final group = data == null ? null : FirestoreMappers.group(doc.id, data);
        if (group != null) groups.add(group);
      } on FirebaseException catch (e) {
        // Removed from the group, or the group was deleted: the Android app reconciles the index
        if (e.code != 'permission-denied' && e.code != 'not-found') rethrow;
      }
    }
    groups.sort((a, b) => caseInsensitiveCompare(a.name, b.name));
    return groups;
  });

  @override
  Stream<Group?> watchGroup(String groupId) => _firestore
      .doc('groups/$groupId')
      .snapshots()
      .map((doc) {
        final data = doc.data();
        return data == null ? null : FirestoreMappers.group(doc.id, data);
      })
      .transform(
        StreamTransformer.fromHandlers(
          handleError: (Object error, StackTrace stack, EventSink<Group?> sink) {
            _log('watch group', error);
            // Lost access (left, removed, deleted): the screen says the group is gone
            if (_errorOf(error) == DataError.permissionDenied) {
              sink.add(null);
            } else {
              sink.addError(DataException(_errorOf(error)), stack);
            }
          },
        ),
      );

  @override
  Stream<List<GroupMember>> watchMembers(String groupId) => _stream(
    'watch members',
    _firestore
        .collection('groups/$groupId/members')
        .snapshots()
        .map((snapshot) => [for (final doc in snapshot.docs) ?FirestoreMappers.member(doc.id, doc.data())]),
  );

  // The composite index posts (weekStartLocal ASC, createdAt DESC) of firebase/firestore.indexes.json
  @override
  Stream<List<FeedPost>> watchWeekPosts(String groupId, String weekStart, int limit) => _stream(
    'watch posts',
    _firestore
        .collection('groups/$groupId/posts')
        .where('weekStartLocal', isEqualTo: weekStart)
        .orderBy('createdAt', descending: true)
        .limit(limit)
        .snapshots()
        .map((snapshot) => [for (final doc in snapshot.docs) ?FirestoreMappers.post(doc.id, doc.data())]),
  );

  // As Android: a field-path update of my own key, never read-modify-write of the map
  @override
  Future<void> setReaction(String groupId, String postId, String myUid, Reaction? reaction) => _call(
    'set reaction',
    () => _firestore.doc('groups/$groupId/posts/$postId').update({
      FieldPath(['reactions', myUid]): reaction?.code ?? FieldValue.delete(),
    }),
  );

  // ---------- Managing groups (Android's FirestoreGroupRepository, batch for batch) ----------

  static const GetOptions _server = GetOptions(source: Source.server);

  DocumentReference<Map<String, dynamic>> _groupRef(String groupId) => _firestore.doc('groups/$groupId');
  DocumentReference<Map<String, dynamic>> _memberRef(String groupId, String uid) =>
      _firestore.doc('groups/$groupId/members/$uid');
  DocumentReference<Map<String, dynamic>> _inviteRef(String code) => _firestore.doc('inviteCodes/$code');
  DocumentReference<Map<String, dynamic>> _membershipRef(String uid, String groupId) =>
      _firestore.doc('users/$uid/groups/$groupId');
  DocumentReference<Map<String, dynamic>> _counterRef(String uid) => _firestore.doc('users/$uid/meta/groups');

  Future<void> _commit(String operation, void Function(WriteBatch batch) build) => _call(operation, () {
    final batch = _firestore.batch();
    build(batch);
    return batch.commit();
  });

  /// users/{uid}/meta/groups: groupCount ± 1 naming the group (a merge, so no read is needed).
  void _counterStep(WriteBatch batch, String uid, String groupId, int step) => batch.set(_counterRef(uid), {
    'groupCount': FieldValue.increment(step),
    'lastGroupId': groupId,
    'updatedAt': FieldValue.serverTimestamp(),
    'schemaVersion': 1,
  }, SetOptions(merge: true));

  Map<String, Object?> _newMember(Fields member, String code) => {
    ...toFirestore(member),
    'joinedAt': FieldValue.serverTimestamp(),
    'inviteCode': code,
  };

  Map<String, Object?> _membership(String groupId) => {
    'groupId': groupId,
    'joinedAt': FieldValue.serverTimestamp(),
    'schemaVersion': 1,
  };

  Map<String, Object?> _invite(String groupId, String name) => {
    'groupId': groupId,
    'groupName': name,
    'createdAt': FieldValue.serverTimestamp(),
    'schemaVersion': 1,
  };

  @override
  String newGroupId() => _firestore.collection('groups').doc().id;

  @override
  Future<List<String>> membershipIds(String uid) => _call('read my index', () async {
    final index = await _firestore.collection('users/$uid/groups').get(_server);
    return [for (final doc in index.docs) FirestoreMappers.membershipGroupId(doc.id, doc.data()) ?? doc.id];
  });

  @override
  Future<Group?> group(String groupId) => _call('read group', () async {
    final doc = await _groupRef(groupId).get(_server);
    final data = doc.data();
    return data == null ? null : FirestoreMappers.group(doc.id, data);
  });

  @override
  Future<List<GroupMember>> members(String groupId) => _call('read members', () async {
    final snapshot = await _firestore.collection('groups/$groupId/members').get(_server);
    return [for (final doc in snapshot.docs) ?FirestoreMappers.member(doc.id, doc.data())];
  });

  @override
  Future<bool> ownMemberExists(String groupId, String uid) =>
      _call('read own member', () async => (await _memberRef(groupId, uid).get(_server)).exists);

  @override
  Future<InvitePreview?> findInvite(String code) => _call('read invite', () async {
    final doc = await _inviteRef(code).get(_server);
    final data = doc.data();
    return data == null ? null : FirestoreMappers.invite(code, data);
  });

  @override
  Future<void> createGroup(String uid, String groupId, String name, String code, Fields member) =>
      _commit('create group', (batch) {
        batch
          ..set(_groupRef(groupId), {
            'name': name,
            'ownerUid': uid,
            'inviteCode': code,
            'memberCount': 1,
            'memberChangeUid': uid,
            'createdAt': FieldValue.serverTimestamp(),
            'updatedAt': FieldValue.serverTimestamp(),
            'schemaVersion': 1,
          })
          ..set(_memberRef(groupId, uid), _newMember(member, code))
          ..set(_inviteRef(code), _invite(groupId, name))
          ..set(_membershipRef(uid, groupId), _membership(groupId));
        _counterStep(batch, uid, groupId, 1);
      });

  @override
  Future<void> joinGroup(String uid, InvitePreview invite, Fields member) => _commit('join group', (batch) {
    batch
      ..update(_groupRef(invite.groupId), {
        'memberCount': FieldValue.increment(1),
        'memberChangeUid': uid,
        'updatedAt': FieldValue.serverTimestamp(),
      })
      ..set(_memberRef(invite.groupId, uid), _newMember(member, invite.code))
      ..set(_membershipRef(uid, invite.groupId), _membership(invite.groupId));
    _counterStep(batch, uid, invite.groupId, 1);
  });

  @override
  Future<void> leaveGroup(String uid, String groupId, String? newOwnerUid) => _commit('leave group', (batch) {
    batch
      ..update(_groupRef(groupId), {
        'memberCount': FieldValue.increment(-1),
        'memberChangeUid': uid,
        'updatedAt': FieldValue.serverTimestamp(),
        'ownerUid': ?newOwnerUid,
      })
      ..delete(_memberRef(groupId, uid))
      ..delete(_membershipRef(uid, groupId));
    _counterStep(batch, uid, groupId, -1);
  });

  @override
  Future<void> removeMember(String groupId, String memberUid) => _commit(
    'remove member',
    (batch) => batch
      ..update(_groupRef(groupId), {
        'memberCount': FieldValue.increment(-1),
        'memberChangeUid': memberUid,
        'updatedAt': FieldValue.serverTimestamp(),
      })
      ..delete(_memberRef(groupId, memberUid)),
  );

  @override
  Future<void> transferOwnership(String groupId, String newOwnerUid) => _commit(
    'transfer',
    (batch) => batch.update(_groupRef(groupId), {'ownerUid': newOwnerUid, 'updatedAt': FieldValue.serverTimestamp()}),
  );

  @override
  Future<void> renameGroup(String groupId, String code, String name) => _commit(
    'rename',
    (batch) => batch
      ..update(_groupRef(groupId), {'name': name, 'updatedAt': FieldValue.serverTimestamp()})
      ..update(_inviteRef(code), {'groupName': name}),
  );

  @override
  Future<void> regenerateCode(String groupId, String oldCode, String newCode, String name) => _commit(
    'regenerate code',
    (batch) => batch
      ..update(_groupRef(groupId), {'inviteCode': newCode, 'updatedAt': FieldValue.serverTimestamp()})
      ..delete(_inviteRef(oldCode))
      ..set(_inviteRef(newCode), _invite(groupId, name)),
  );

  @override
  Future<void> deleteGroup(String uid, String groupId, String code) => _commit('delete group', (batch) {
    batch
      ..delete(_memberRef(groupId, uid))
      ..delete(_groupRef(groupId))
      ..delete(_inviteRef(code))
      ..delete(_membershipRef(uid, groupId));
    _counterStep(batch, uid, groupId, -1);
  });

  @override
  Future<void> removeStaleMembership(String uid, String groupId, {required bool deleteOwnMemberDoc}) =>
      _commit('reconcile', (batch) {
        if (deleteOwnMemberDoc) batch.delete(_memberRef(groupId, uid));
        batch.delete(_membershipRef(uid, groupId));
        _counterStep(batch, uid, groupId, -1);
      });

  @override
  Future<void> deleteGroupCounter(String uid) => _call('delete counter', () async {
    final counter = await _counterRef(uid).get(_server);
    // The rules allow it only at 0; a missing counter has nothing to delete
    if (!counter.exists || counter.data()?['groupCount'] != 0) return;
    await _counterRef(uid).delete();
  });

  @override
  Future<List<String>> postIdsBy(String groupId, String authorUid) => _call('read my posts', () async {
    final posts = await _firestore
        .collection('groups/$groupId/posts')
        .where('authorUid', isEqualTo: authorUid)
        .get(_server);
    return [for (final doc in posts.docs) doc.id];
  });

  @override
  Future<List<String>> postIds(String groupId, int limit) => _call('read posts', () async {
    final posts = await _firestore.collection('groups/$groupId/posts').limit(limit).get(_server);
    return [for (final doc in posts.docs) doc.id];
  });

  @override
  Future<void> deletePost(String groupId, String postId) =>
      _call('delete post', () => _firestore.doc('groups/$groupId/posts/$postId').delete());
}

/// `users/{uid}` and its synced subcollections (Android's `FirestoreRemoteUserData` profile and
/// `deleteAll`).
class FirestoreAccountRepository implements AccountRepository {
  FirestoreAccountRepository(this._firestore);

  final FirebaseFirestore _firestore;

  static const int _batchSize = 500;
  static const int _programPage = 100;

  @override
  Future<void> writeProfile(String uid, String? displayName, String? photoUrl) => _call('write profile', () async {
    final ref = _firestore.doc('users/$uid');
    final exists = (await ref.get(const GetOptions(source: Source.server))).exists;
    final fields = <String, Object?>{
      'displayName': displayName,
      'photoUrl': photoUrl,
      'updatedAt': FieldValue.serverTimestamp(),
      'deleted': false,
      'schemaVersion': 1,
    };
    if (exists) {
      await ref.set(fields, SetOptions(merge: true));
    } else {
      await ref.set({...fields, 'createdAt': FieldValue.serverTimestamp()});
    }
  });

  @override
  Future<void> eraseUserData(String uid) => _call('erase user data', () async {
    final programs = _firestore.collection('users/$uid/programs');
    while (true) {
      final page = await programs.limit(_programPage).get(const GetOptions(source: Source.server));
      if (page.docs.isEmpty) break;
      for (final program in page.docs) {
        await _deleteCollection(program.reference.collection('workouts'));
        await _deleteCollection(program.reference.collection('completions'));
      }
      await _deleteDocs(page.docs.map((d) => d.reference).toList());
    }
    await _deleteCollection(_firestore.collection('users/$uid/trainingLog'));
    await _deleteCollection(_firestore.collection('users/$uid/weeklyGoals'));
    await _deleteCollection(_firestore.collection('users/$uid/dailyProgress'));
    await _firestore.doc('users/$uid').delete();
  });

  Future<void> _deleteCollection(CollectionReference<Map<String, dynamic>> collection) async {
    while (true) {
      final page = await collection.limit(_batchSize).get(const GetOptions(source: Source.server));
      if (page.docs.isEmpty) return;
      await _deleteDocs(page.docs.map((d) => d.reference).toList());
    }
  }

  Future<void> _deleteDocs(List<DocumentReference<Map<String, dynamic>>> refs) async {
    for (var i = 0; i < refs.length; i += _batchSize) {
      final batch = _firestore.batch();
      for (final ref in refs.skip(i).take(_batchSize)) {
        batch.delete(ref);
      }
      await batch.commit();
    }
  }
}
