// The web client end to end against the Firebase EMULATORS (Auth 9099, Firestore 8080 on this
// machine) with the real firebase/firestore.rules: two email/password test users seed their data
// THROUGH THE RULES (as the Android app writes it: training log, goals, programs, a group, a
// post), then the web's real repositories and blocs read it back. Never the real project: the
// Firebase options carry a fake API key, and the test stops unless both emulators answer.
//
// Opt-in, in Chrome: scripts/test_web_emulator.sh
// (flutter test --platform chrome --dart-define=WEB_EMULATOR=true test/emulator/).
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
import 'package:tiominuter_web/data/firebase_repositories.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/domain/workout_names.dart';
import 'package:tiominuter_web/firebase_options.dart';
import 'package:tiominuter_web/ui/week/week_bloc.dart';

const bool _enabled = bool.fromEnvironment('WEB_EMULATOR');
const String _host = '127.0.0.1';
const String _projectId = 'tiominuter-e43f7';

/// One person in their own browser session: a FirebaseApp with its own Auth and Firestore.
class _User {
  _User._(this.name, this.auth, this.db, this.uid);

  final String name;
  final FirebaseAuth auth;
  final FirebaseFirestore db;
  final String uid;

  static Future<_User> create(String name) async {
    final id = '$name${DateTime.now().microsecondsSinceEpoch}';
    final app = await Firebase.initializeApp(
      name: id,
      // A key the real project would refuse: nothing here can reach production
      options: DefaultFirebaseOptions.web.copyWith(apiKey: 'emulator-only-$id', projectId: _projectId),
    );
    final auth = FirebaseAuth.instanceFor(app: app);
    await auth.useAuthEmulator(_host, 9099);
    final db = FirebaseFirestore.instanceFor(app: app)..useFirestoreEmulator(_host, 8080);
    final credential = await auth.createUserWithEmailAndPassword(email: '$id@example.test', password: 'password-123');
    return _User._(name, auth, db, credential.user!.uid);
  }
}

Map<String, Object?> _sync() => {'updatedAt': FieldValue.serverTimestamp(), 'deleted': false, 'schemaVersion': 1};

String _uuid(int n) => '00000000-0000-4000-8000-${n.toString().padLeft(12, '0')}';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  FirebaseCoreWeb.registerWith(webPluginRegistrar);
  FirebaseAuthWeb.registerWith(webPluginRegistrar);
  FirebaseFirestoreWeb.registerWith(webPluginRegistrar);

  final dates = LocalDates();
  final today = dates.today();
  final monday = dates.currentWeekStart();
  const programId = '9a1b2c3d-4e5f-4a6b-8c7d-112233445566';
  const deletedProgramId = '9a1b2c3d-4e5f-4a6b-8c7d-665544332211';

  late _User me;
  late _User friend;
  late String groupId;
  late String removedGroupId;
  late String postId;

  setUpAll(() async {
    if (!_enabled) return;
    // Both emulators must answer, or nothing runs
    for (final port in [8080, 9099]) {
      final response = await http.get(Uri.parse('http://$_host:$port/'));
      expect(response.statusCode, lessThan(500), reason: 'emulator on $port');
    }
    me = await _User.create('me');
    friend = await _User.create('friend');

    // ---------- My history, goals and programs, written as the Android sync writes them ----------
    final log = me.db.collection('users/${me.uid}/trainingLog');
    Future<void> entry(
      int n,
      String date, {
      String source = 'GYM',
      String? refId = 'gym:64',
      String title = 'Pull Day',
      int? duration = 1800,
    }) => log.doc(_uuid(n)).set({
      'entryId': _uuid(n),
      'dateLocal': date,
      'completedAt': CalendarDates.toUtc(date).millisecondsSinceEpoch + n,
      'source': source,
      'refId': refId,
      'title': title,
      'durationSeconds': duration,
      'createdAt': 1760990400000,
      ..._sync(),
    });
    // This week: a gym routine and a 5-minute daily (doesn't count); last week and the one before: met
    await entry(1, monday);
    await entry(2, today, source: 'DAILY', refId: 'daily:5', title: 'Day 5', duration: 300);
    await entry(3, CalendarDates.plusDays(monday, -5));
    await entry(4, CalendarDates.plusDays(monday, -12));
    // A program workout recorded in Spanish, and one of a program deleted since
    await entry(
      5,
      CalendarDates.plusDays(monday, -6),
      source: 'PROGRAM',
      refId: 'program:$programId:1:0',
      title: 'Tren superior A',
    );
    await entry(
      6,
      CalendarDates.plusDays(monday, -7),
      source: 'PROGRAM',
      refId: 'program:$deletedProgramId:1:0',
      title: 'Tren inferior B',
    );
    // Older entries for the pages, and a tombstone
    for (var n = 10; n < 28; n++) {
      await entry(n, CalendarDates.plusDays(monday, -30 - n));
    }
    await log.doc(_uuid(99)).set({
      'entryId': _uuid(99),
      'deleted': true,
      'updatedAt': FieldValue.serverTimestamp(),
      'schemaVersion': 1,
    });

    final goals = me.db.collection('users/${me.uid}/weeklyGoals');
    final goalWeek = CalendarDates.plusWeeks(monday, -2);
    await goals.doc(goalWeek).set({'effectiveFromWeek': goalWeek, 'workoutsPerWeek': 1, ..._sync()});
    final oldWeek = CalendarDates.plusWeeks(monday, -20);
    await goals.doc(oldWeek).set({
      'effectiveFromWeek': oldWeek,
      'deleted': true,
      'updatedAt': FieldValue.serverTimestamp(),
      'schemaVersion': 1,
    });

    Map<String, Object?> program(String id) => {
      'programId': id, 'goal': 'GAIN_MUSCLE', 'level': 'BEGINNER', 'location': 'HOME', 'focus': 'BALANCED', //
      'weeks': 6, 'daysPerWeek': 3, 'age': 30, 'weightKg': 70.5, 'heightCm': 175, 'createdAt': 1760990400000,
      ..._sync(),
    };
    Map<String, Object?> workout(String title) => {
      'workoutId': '1-0', 'week': 1, 'dayIndex': 0, 'kind': 'STRENGTH', 'isDeload': false, //
      'payload': jsonEncode({'kind': 'STRENGTH', 'title': title, 'slots': <Object>[]}),
      ..._sync(),
    };
    await me.db.doc('users/${me.uid}/programs/$programId').set(program(programId));
    await me.db.doc('users/${me.uid}/programs/$programId/workouts/1-0').set(workout('UPPER_A'));
    await me.db.doc('users/${me.uid}/programs/$deletedProgramId').set(program(deletedProgramId));
    await me.db.doc('users/${me.uid}/programs/$deletedProgramId/workouts/1-0').set(workout('LOWER_B'));
    await me.db.doc('users/${me.uid}/programs/$deletedProgramId').set({
      'programId': deletedProgramId, 'deleted': true, 'updatedAt': FieldValue.serverTimestamp(), 'schemaVersion': 1, //
    });

    // ---------- A group (the batches of docs/firestore-model.md "Groups") ----------
    Map<String, Object?> member(String name, String code, {String week = '', int done = 1}) => {
      'displayName': name, 'photoUrl': null, 'joinedAt': FieldValue.serverTimestamp(), 'inviteCode': code, //
      'weekStartLocal': week.isEmpty ? monday : week, 'workoutsThisWeek': done, 'weeklyGoal': 3, 'streakWeeks': 1,
      'daysThisWeek': {
        '1': ['GYM'],
      },
      'lastTrainedLocal': monday,
      'updatedAt': FieldValue.serverTimestamp(),
      'schemaVersion': 1,
    };
    void counter(WriteBatch batch, _User user, String group, int step) =>
        batch.set(user.db.doc('users/${user.uid}/meta/groups'), {
          'groupCount': FieldValue.increment(step),
          'lastGroupId': group,
          'updatedAt': FieldValue.serverTimestamp(),
          'schemaVersion': 1,
        }, SetOptions(merge: true));
    Future<String> createGroup(_User owner, String name, String code) async {
      final id = owner.db.collection('groups').doc().id;
      final batch = owner.db.batch()
        ..set(owner.db.doc('groups/$id'), {
          'name': name, 'ownerUid': owner.uid, 'inviteCode': code, 'memberCount': 1, 'memberChangeUid': owner.uid, //
          'createdAt': FieldValue.serverTimestamp(), 'updatedAt': FieldValue.serverTimestamp(), 'schemaVersion': 1,
        })
        ..set(owner.db.doc('groups/$id/members/${owner.uid}'), member(owner.name, code, done: 3))
        ..set(owner.db.doc('inviteCodes/$code'), {
          'groupId': id,
          'groupName': name,
          'createdAt': FieldValue.serverTimestamp(),
          'schemaVersion': 1,
        })
        ..set(owner.db.doc('users/${owner.uid}/groups/$id'), {
          'groupId': id,
          'joinedAt': FieldValue.serverTimestamp(),
          'schemaVersion': 1,
        });
      counter(batch, owner, id, 1);
      await batch.commit();
      return id;
    }

    Future<void> join(_User user, String id, String code, {String week = ''}) async {
      final batch = user.db.batch()
        ..update(user.db.doc('groups/$id'), {
          'memberCount': FieldValue.increment(1),
          'memberChangeUid': user.uid,
          'updatedAt': FieldValue.serverTimestamp(), //
        })
        ..set(user.db.doc('groups/$id/members/${user.uid}'), member(user.name, code, week: week))
        ..set(user.db.doc('users/${user.uid}/groups/$id'), {
          'groupId': id,
          'joinedAt': FieldValue.serverTimestamp(),
          'schemaVersion': 1,
        });
      counter(batch, user, id, 1);
      await batch.commit();
    }

    final stamp = DateTime.now().millisecondsSinceEpoch;
    String code(int salt) {
      const alphabet = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
      var rest = stamp + salt;
      return List.generate(8, (_) {
        final char = alphabet[rest % alphabet.length];
        rest ~/= alphabet.length;
        return char;
      }).join();
    }

    final crewCode = code(0);
    groupId = await createGroup(friend, 'Crew', crewCode);
    await join(me, groupId, crewCode, week: CalendarDates.plusWeeks(monday, -1));
    // A group the friend then removes me from: my index entry stays (the Android app reconciles it)
    final oldCode = code(7);
    removedGroupId = await createGroup(friend, 'Old crew', oldCode);
    await join(me, removedGroupId, oldCode);
    await (friend.db.batch()
          ..update(friend.db.doc('groups/$removedGroupId'), {
            'memberCount': FieldValue.increment(-1),
            'memberChangeUid': me.uid,
            'updatedAt': FieldValue.serverTimestamp(), //
          })
          ..delete(friend.db.doc('groups/$removedGroupId/members/${me.uid}')))
        .commit();

    // The friend's post of this week, with its locale-free name
    postId = _uuid(500);
    await friend.db.doc('groups/$groupId/posts/$postId').set({
      'authorUid': friend.uid, 'authorName': 'friend', 'authorPhotoUrl': null, 'source': 'GYM', //
      'title': 'Día de tirón', 'titleKey': 'gym/64', 'durationSeconds': 2700, 'dateLocal': monday,
      'weekStartLocal': monday, 'createdAt': FieldValue.serverTimestamp(), 'reactions': <String, Object?>{},
      'schemaVersion': 1,
    });
  });

  group('my data', () {
    test('the signed-in user', () {
      final user = FirebaseAuthRepository(me.auth).currentUser!;
      expect(user.uid, me.uid);
    }, skip: !_enabled);

    test('the history pages newest first, without tombstones', () async {
      final data = FirestoreUserDataRepository(me.db);
      final first = await data.historyPage(me.uid, limit: 20);
      expect(first.entries, hasLength(20));
      expect(first.entries.first.id, _uuid(2));
      expect(first.next, isNotNull);
      final second = await data.historyPage(me.uid, after: first.next, limit: 20);
      expect(second.entries, hasLength(4));
      expect([...first.entries, ...second.entries].map((e) => e.id), isNot(contains(_uuid(99))));
      expect(second.next, isNull);
    }, skip: !_enabled);

    test('the program workout of a refId, none for a deleted program', () async {
      final codes = await FirestoreUserDataRepository(me.db)
          .programTitleCodes(me.uid, ['program:$programId:1:0', 'program:$deletedProgramId:1:0']);
      expect(codes, {'program:$programId:1:0': ProgramTitleCode.upperA});
    }, skip: !_enabled);

    test('My week: the 10-minute rule, the goal and the streak read back from past weeks', () async {
      final bloc = WeekBloc(uid: me.uid, data: FirestoreUserDataRepository(me.db), dates: dates)
        ..add(const WeekVisible());
      final state = await bloc.stream.firstWhere((s) => s.summary != null && s.streak != null);
      expect(state.summary!.progress.workoutCount, 1, reason: 'the 5-minute daily does not count');
      expect(state.summary!.progress.goal, 1);
      expect(state.streak, 3, reason: 'two past weeks met plus this one');
      await bloc.close();
    }, skip: !_enabled);
  });

  group('groups', () {
    test('my groups: the stale index entry is skipped, nothing written', () async {
      final groups = await FirestoreGroupsRepository(me.db).myGroups(me.uid);
      expect(groups.map((g) => g.id), [groupId]);
      expect((await me.db.doc('users/${me.uid}/groups/$removedGroupId').get()).exists, isTrue);
    }, skip: !_enabled);

    test('the ranking reads live members; a summary of last week counts 0', () async {
      // The first snapshot may come from this user's own cached write; the server's follows
      final members = await FirestoreGroupsRepository(me.db).watchMembers(groupId).firstWhere((m) => m.length == 2);
      final ranking = GroupRanking.rank(members, monday, me.uid);
      expect(ranking.map((r) => r.member.uid), [friend.uid, me.uid]);
      expect(ranking.last.isCurrentWeek, isFalse);
      expect(await FirestoreGroupsRepository(me.db).watchGroup(removedGroupId).first, isNull);
    }, skip: !_enabled);

    test("the feed's post is named by its titleKey; my reaction is set, changed, removed", () async {
      final repository = FirestoreGroupsRepository(me.db);
      final post = (await repository.watchWeekPosts(groupId, monday, 20).first).single;
      final name = WorkoutNameResolver.nameOfPost(
        titleKey: post.titleKey,
        source: post.source,
        activityType: post.activityType,
        title: post.title,
        catalog: const WorkoutNameCatalog(gymRoutines: {64: GymRoutineNames('Pull Day', 'Día de tirón')}),
      );
      expect(name, const GymWorkoutName('Pull Day', 'Día de tirón'));

      await repository.setReaction(groupId, postId, me.uid, Reaction.fire);
      await FirestoreGroupsRepository(friend.db).setReaction(groupId, postId, friend.uid, Reaction.clap);
      await repository.setReaction(groupId, postId, me.uid, Reaction.strong);
      var stored = (await friend.db.doc('groups/$groupId/posts/$postId').get()).data()!;
      expect(stored['reactions'], {me.uid: 'strong', friend.uid: 'clap'});
      await repository.setReaction(groupId, postId, me.uid, null);
      stored = (await friend.db.doc('groups/$groupId/posts/$postId').get()).data()!;
      expect(stored['reactions'], {friend.uid: 'clap'});

      // Someone else's key is refused by the rules
      await expectLater(
        repository.setReaction(groupId, postId, friend.uid, null),
        throwsA(isA<DataException>().having((e) => e.error, 'error', DataError.permissionDenied)),
      );
    }, skip: !_enabled);
  });
}
