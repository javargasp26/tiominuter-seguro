// Training across clients against the Firebase EMULATORS (Auth 9099, Firestore 8080 on this
// machine) with the real firebase/firestore.rules, in three phases run by
// scripts/test_web_train_emulator.sh:
//   1. web-writes (here): an email/password test user writes with the web's REAL code
//      (TrainingService over the Firestore repositories): a goal, a daily workout, a logged
//      activity, a program and a completed strength workout, a deleted entry, my member summary in
//      a group and my feed posts;
//   2. Android (WebInteropEmulatorTest on emulator-5554) pulls them into fresh Room databases,
//      checks them (the program against its own generator, the summary against its calculator),
//      then pushes its own changes;
//   3. android-reads (here): the web reads Android's changes back.
// Never the real project: fake API keys, and nothing runs unless both emulators answer.
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
import 'package:tiominuter_web/app/training_service.dart';
import 'package:tiominuter_web/data/firebase_repositories.dart';
import 'package:tiominuter_web/data/firebase_training_repositories.dart';
import 'package:tiominuter_web/data/jsonc.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/data/training_repositories.dart';
import 'package:tiominuter_web/domain/activity_entry.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/domain/photos.dart';
import 'package:tiominuter_web/domain/gym.dart';
import 'package:tiominuter_web/domain/program.dart';
import 'package:tiominuter_web/domain/program_content.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/daily.dart';
import 'package:tiominuter_web/domain/daily_progress.dart';
import 'package:tiominuter_web/domain/weekly_progress.dart';
import 'package:tiominuter_web/ui/train/daily_selection_page.dart';
import 'package:tiominuter_web/firebase_options.dart';
import 'package:tiominuter_web/platform/browser.dart';
import 'package:tiominuter_web/platform/photos.dart';

import '../support/photo_fakes.dart' show cameraJpeg;
import '../support/training_fakes.dart' show EnglishTrainingTexts;

const String _phase = String.fromEnvironment('CROSS_PHASE');

/// Only scripts/test_web_train_emulator.sh runs it (it names the phase); test_web_emulator.sh skips it.
const bool _enabled = bool.fromEnvironment('WEB_EMULATOR') && _phase != '';
const String _run = String.fromEnvironment('CROSS_RUN');

/// content/v1 served by the script on this machine (a browser test can't read files).
const String _contentUrl = String.fromEnvironment('CONTENT_URL', defaultValue: 'http://127.0.0.1:8099/');
const String _host = '127.0.0.1';
const String _projectId = 'tiominuter-e43f7';
const String _password = 'password-123';

/// A FirebaseApp of its own (Auth + Firestore on the emulators).
Future<({FirebaseAuth auth, FirebaseFirestore db})> _app(String name) async {
  final id = '$name${DateTime.now().microsecondsSinceEpoch}';
  final app = await Firebase.initializeApp(
    name: id,
    options: DefaultFirebaseOptions.web.copyWith(apiKey: 'emulator-only-$id', projectId: _projectId),
  );
  final auth = FirebaseAuth.instanceFor(app: app);
  await auth.useAuthEmulator(_host, 9099);
  final db = FirebaseFirestore.instanceFor(app: app)..useFirestoreEmulator(_host, 8080);
  return (auth: auth, db: db);
}

String _email(String who) => 'cross-$_run-$who@example.test';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  FirebaseCoreWeb.registerWith(webPluginRegistrar);
  FirebaseAuthWeb.registerWith(webPluginRegistrar);
  FirebaseFirestoreWeb.registerWith(webPluginRegistrar);

  final dates = LocalDates();

  setUpAll(() async {
    if (!_enabled) return;
    expect(_run, isNotEmpty, reason: 'CROSS_RUN names the test users');
    for (final port in [8080, 9099]) {
      final response = await http.get(Uri.parse('http://$_host:$port/'));
      expect(response.statusCode, lessThan(500), reason: 'emulator on $port');
    }
  });

  test('phase 1: the web writes training with its real code, through the real rules', () async {
    final me = await _app('me');
    final friend = await _app('friend');
    final uid = (await me.auth.createUserWithEmailAndPassword(email: _email('me'), password: _password)).user!.uid;
    final friendUid = (await friend.auth.createUserWithEmailAndPassword(
      email: _email('friend'),
      password: _password,
    )).user!.uid;
    final monday = dates.currentWeekStart();

    // A group of the friend that I join (the batches of docs/firestore-model.md "Groups")
    final groupId = friend.db.collection('groups').doc().id;
    final code = ('K7MPX2QA'.split('')..shuffle()).join();
    Map<String, Object?> member(String name) => {
      'displayName': name, 'photoUrl': null, 'joinedAt': FieldValue.serverTimestamp(), 'inviteCode': code, //
      'weekStartLocal': monday, 'workoutsThisWeek': 0, 'weeklyGoal': 3, 'streakWeeks': 0, 'daysThisWeek': {},
      'lastTrainedLocal': null, 'updatedAt': FieldValue.serverTimestamp(), 'schemaVersion': 1,
    };
    Map<String, Object?> counter(String group) => {
      'groupCount': FieldValue.increment(1),
      'lastGroupId': group,
      'updatedAt': FieldValue.serverTimestamp(),
      'schemaVersion': 1,
    };
    Map<String, Object?> index(String group) => {
      'groupId': group,
      'joinedAt': FieldValue.serverTimestamp(),
      'schemaVersion': 1,
    };
    await (friend.db.batch()
          ..set(friend.db.doc('groups/$groupId'), {
            'name': 'Cross crew', 'ownerUid': friendUid, 'inviteCode': code, 'memberCount': 1, //
            'memberChangeUid': friendUid, 'createdAt': FieldValue.serverTimestamp(),
            'updatedAt': FieldValue.serverTimestamp(), 'schemaVersion': 1,
          })
          ..set(friend.db.doc('groups/$groupId/members/$friendUid'), member('friend'))
          ..set(friend.db.doc('inviteCodes/$code'), {
            'groupId': groupId,
            'groupName': 'Cross crew',
            'createdAt': FieldValue.serverTimestamp(),
            'schemaVersion': 1,
          })
          ..set(friend.db.doc('users/$friendUid/groups/$groupId'), index(groupId))
          ..set(friend.db.doc('users/$friendUid/meta/groups'), counter(groupId), SetOptions(merge: true)))
        .commit();
    await (me.db.batch()
          ..update(me.db.doc('groups/$groupId'), {
            'memberCount': FieldValue.increment(1),
            'memberChangeUid': uid,
            'updatedAt': FieldValue.serverTimestamp(),
          })
          ..set(me.db.doc('groups/$groupId/members/$uid'), member('me'))
          ..set(me.db.doc('users/$uid/groups/$groupId'), index(groupId))
          ..set(me.db.doc('users/$uid/meta/groups'), counter(groupId), SetOptions(merge: true)))
        .commit();

    // ---------- The web's real training code ----------
    final pending = PendingWrites();
    final service = TrainingService(
      training: FirestoreTrainingRepository(me.db, pending),
      sharing: FirestoreSharingRepository(me.db, pending),
      store: MemoryKeyValueStore(),
      dates: dates,
      texts: EnglishTrainingTexts(),
    );
    final user = WebUser(uid: uid, displayName: 'Cross Me');
    setTrainingUser(user);
    service.setFeedIntroSeen(uid);

    await service.setGoal(user, 4);
    final daily = await service.recordWorkout(
      user: user,
      source: TrainingSource.daily,
      refId: 'daily:3',
      title: 'Day 3',
      durationSeconds: 900,
    );
    // Photos, processed by this browser's real codec: the daily one added after posting (the post's
    // thumb updated), the activity's staged in the form (its post created with the thumb)
    final photos = PhotoProcessor(BrowserPhotos.codec());
    await Future<void>.delayed(const Duration(seconds: 1));
    await service.attachPhoto(user, daily.id, await photos.process(PickedPhoto(bytes: cameraJpeg(orientation: 6))));
    final activityPhoto = await photos.process(PickedPhoto(bytes: cameraJpeg(width: 1600, height: 1200, noise: true)));
    final activity = await service.saveActivity(
      photo: activityPhoto,
      user: user,
      input: ActivityEntryInput(
        type: ActivityType.running,
        customName: '',
        durationMinutes: 30,
        intensity: Intensity.moderate,
        completedAt: DateTime.now().millisecondsSinceEpoch - 60000,
        note: 'Easy run',
      ),
    );
    expect(activity.errors, isEmpty);

    Future<Object?> content(String file) async => jsonDecode((await http.get(Uri.parse('$_contentUrl$file'))).body);
    final catalog = ProgramContent.catalog(await content('program_exercises.json'));
    final templates = ProgramContent.templates(await content('program_templates.json'));
    const profile = ProgramProfile(
      age: 30,
      weightKg: 70.5,
      heightCm: 175,
      level: ProgramLevel.beginner,
      goal: ProgramGoal.gainMuscle,
      location: ProgramLocation.gym,
      weeks: 4,
      daysPerWeek: 3,
    );
    final programId = service.createProgram(uid, profile, catalog, templates);
    final detail = await service.programDetail(uid, programId);
    final strength = detail!.workout(1, 0)!.workout! as StrengthWorkout;
    final sessions = service.strengthSessions(uid);
    for (final slot in strength.slots) {
      sessions.onSetCompleted(programId, 1, 0);
      await service.markSlotCompleted(
        user: user,
        programId: programId,
        week: 1,
        dayIndex: 0,
        workout: strength,
        position: slot.position,
      );
    }
    // A gym routine through the gym screens' calls (progress and session in the browser), on its own
    // clock: the first set 30 minutes ago, the last one now → one GYM entry of exactly 1800 s
    final gymRoutines = GymContent.routines(
      parseJsonc((await http.get(Uri.parse('${_contentUrl}routines_for_gym.jsonc'))).body),
    );
    final pushDay = gymRoutines.singleWhere((r) => r.routineId == 65);
    final firstSetAt = DateTime.now().millisecondsSinceEpoch - 30 * 60000;
    var gymNow = firstSetAt;
    final gymService = TrainingService(
      training: FirestoreTrainingRepository(me.db, pending),
      sharing: FirestoreSharingRepository(me.db, pending),
      store: MemoryKeyValueStore(),
      dates: dates,
      texts: EnglishTrainingTexts(),
      wallClock: () => gymNow,
    );
    gymService
      ..setFeedIntroSeen(uid)
      ..rememberGymRoutine(uid, pushDay);
    final gymSessions = gymService.gymSessions(uid);
    final sets = pushDay.exercises.fold<int>(0, (total, slot) => total + slot.sets);
    var setsDone = 0;
    for (final slot in pushDay.exercises) {
      for (var set = 0; set < slot.sets; set++) {
        // Evenly spread; the last set exactly 30 minutes after the first
        gymNow = firstSetAt + 30 * 60000 * setsDone ~/ (sets - 1);
        setsDone++;
        gymSessions.onSetCompleted(pushDay.routineId);
      }
      gymService.markGymCompleted(uid, pushDay.routineId, slot.position);
    }
    final gym = (await gymService.completeGymRoutine(user, pushDay))!;
    expect(await gymService.completeGymRoutine(user, pushDay), isNull, reason: 'recorded once');
    expect((gym.refId, gym.title, gym.durationSeconds), ('gym:65', 'Push Day', 1800));

    final gone = await service.recordWorkout(
      user: user,
      source: TrainingSource.daily,
      refId: 'daily:1',
      title: 'Day 1',
      durationSeconds: 700,
    );
    await service.deleteEntry(user, gone);
    await Future<void>.delayed(const Duration(seconds: 1));
    await me.db.waitForPendingWrites();

    // What the web sees of its own writes, read from the server
    final entries = await FirestoreTrainingRepository(me.db, PendingWrites()).entriesFrom(uid, monday);
    expect(entries.map((e) => e.id).toSet(), containsAll({daily.id, activity.entry!.id}));
    expect(entries.where((e) => e.id == gone.id), isEmpty);
    final post = await me.db.doc('groups/$groupId/posts/${daily.id}').get(const GetOptions(source: Source.server));
    expect(post.data()?['titleKey'], 'daily/3');
    for (final entryId in [daily.id, activity.entry!.id]) {
      final withThumb = await me.db.doc('groups/$groupId/posts/$entryId').get(const GetOptions(source: Source.server));
      final thumb = (withThumb.data()?['thumb'] as Blob?)?.bytes;
      expect(thumb, isNotNull, reason: 'the thumbnail on the post ($entryId)');
      expect(thumb!.length, lessThanOrEqualTo(PhotoLimits.thumbMaxBytes));
      expect(JpegMetadata.hasMetadata(thumb), isFalse);
    }
    final gymPost = await me.db.doc('groups/$groupId/posts/${gym.id}').get(const GetOptions(source: Source.server));
    expect(gymPost.data()?['titleKey'], 'gym/65');
    expect(gymPost.data()?['title'], 'Push Day');
    final summary = await me.db.doc('groups/$groupId/members/$uid').get(const GetOptions(source: Source.server));
    expect(summary.data()?['weeklyGoal'], 4);
    expect(summary.data()?['displayName'], 'Cross Me');
  }, skip: !_enabled || _phase != 'web-writes' ? 'Opt-in: scripts/test_web_train_emulator.sh' : false);

  test('phase 3: the web reads what Android pushed', () async {
    final me = await _app('me');
    final uid = (await me.auth.signInWithEmailAndPassword(email: _email('me'), password: _password)).user!.uid;
    final training = FirestoreTrainingRepository(me.db, PendingWrites());
    final monday = dates.currentWeekStart();
    final entries = await training.entriesFrom(uid, CalendarDates.plusWeeks(monday, -1));
    // Android's own gym workout (the web's gym:65 came before it)
    final gym = entries.where((e) => e.source == TrainingSource.gym && e.refId == 'gym:64').single;
    expect((gym.refId, gym.title, gym.durationSeconds), ('gym:64', 'Pull Day', 2700));
    expect(entries.where((e) => e.refId == 'gym:65'), hasLength(1));
    expect(entries.where((e) => e.source == TrainingSource.activity), isEmpty, reason: 'deleted on Android');
    expect(entries.where((e) => e.source == TrainingSource.daily && e.refId == 'daily:3'), hasLength(1));
    final goals = await training.goals(uid);
    expect(WeeklyProgressCalculator.goalForWeek(goals, monday), 5);
    // The program entry's name resolves through the synced program (Firestore refId by uuid)
    final history = await FirestoreUserDataRepository(me.db).historyPage(uid, limit: 20);
    final programEntry = history.entries.where((e) => e.source == TrainingSource.program).single;
    final codes = await FirestoreUserDataRepository(me.db).programTitleCodes(uid, [programEntry.refId!]);
    expect(codes, hasLength(1));
  }, skip: !_enabled || _phase != 'android-reads' ? 'Opt-in: scripts/test_web_train_emulator.sh' : false);

  test('phase 3: Day 1 finished on Android unlocks Day 2 here; the web restarts the days', () async {
    final me = await _app('daily');
    // The account Android created in phase 2, its progress pushed by Android's sync
    final uid = (await me.auth.signInWithEmailAndPassword(email: _email('daily'), password: _password)).user!.uid;
    final pending = PendingWrites();
    final service = TrainingService(
      training: FirestoreTrainingRepository(me.db, pending),
      sharing: FirestoreSharingRepository(me.db, pending),
      store: MemoryKeyValueStore(),
      dates: dates,
      texts: EnglishTrainingTexts(),
    );
    final progress = await service.dailyProgress(uid);
    expect(progress, const DailyProgress(lastCompletedDay: 1));
    final selection = DailySelectionState(
      isLoading: false,
      routines: [for (var d = 1; d <= 30; d++) DailyRoutine(routineId: d, routineDay: 'Day $d', exercises: const [])],
      progress: progress,
    );
    expect(selection.statusOf(1), DailyDayStatus.completed);
    expect(selection.statusOf(2), DailyDayStatus.available);
    expect(selection.statusOf(3), DailyDayStatus.locked);
    expect(selection.nextDay, 2, reason: 'the hub says "Day 2 available"');

    // Restart, in a transaction against the server's state, through the real rules
    expect(await service.restartDailyDays(uid), const DailyProgress(cycle: 1));
    final stored = await me.db.doc('users/$uid/dailyProgress/state').get(const GetOptions(source: Source.server));
    expect(stored.data()!.keys.toSet(), {'lastCompletedDay', 'cycle', 'updatedAt', 'schemaVersion'});
    expect((stored.data()!['lastCompletedDay'], stored.data()!['cycle']), (0, 1));
  }, skip: !_enabled || _phase != 'android-reads' ? 'Opt-in: scripts/test_web_train_emulator.sh' : false);
}
