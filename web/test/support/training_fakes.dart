import 'dart:typed_data';
import 'dart:ui';

import 'package:tiominuter_web/app/services.dart';
import 'package:tiominuter_web/app/training_service.dart';
import 'package:tiominuter_web/data/content_repository.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/data/training_repositories.dart';
import 'package:tiominuter_web/data/write_mappers.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/domain/daily.dart';
import 'package:tiominuter_web/domain/daily_progress.dart';
import 'package:tiominuter_web/domain/gym.dart';
import 'package:tiominuter_web/domain/photos.dart';
import 'package:tiominuter_web/domain/program.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/domain/weekly_progress.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/platform/browser.dart';
import 'package:tiominuter_web/platform/photos.dart';
import 'package:tiominuter_web/ui/workout_name_text.dart';

import 'fake_scheduler.dart';
import 'photo_fakes.dart';

/// `users/{uid}` in memory: what the web wrote, with Firestore's semantics where they matter (a
/// tombstone replaces the doc; reads skip tombstones).
class FakeTrainingRepository implements TrainingRepository {
  /// Every entry doc by id; null = a tombstone.
  final Map<String, TrainingEntryDoc?> entries = {};
  final Map<String, int> storedGoals = {};
  final Map<String, ({ProgramProfile profile, GeneratedProgram program, int createdAt})> storedPrograms = {};
  final Map<String, Map<String, int>> completions = {};
  final List<String> writes = [];
  DataError? failWith;

  /// `dailyProgress/state` (null: none yet) and how often the history was read for it.
  DailyProgress? storedDailyProgress;
  int dailyHistoryReads = 0;

  @override
  Future<DailyProgress?> dailyProgress(String uid) async {
    _fail();
    return storedDailyProgress;
  }

  @override
  Future<List<({TrainingSource source, String? refId})>> dailyHistory(String uid) async {
    _fail();
    dailyHistoryReads++;
    return [
      for (final doc in entries.values)
        if (doc != null && doc.source == TrainingSource.daily) (source: doc.source, refId: doc.refId),
    ];
  }

  @override
  Future<DailyProgress> updateDailyProgress(String uid, DailyProgress Function(DailyProgress? current) change) async {
    _fail();
    final next = change(storedDailyProgress);
    if (next != storedDailyProgress) {
      writes.add('dailyProgress ${next.lastCompletedDay}/${next.cycle}');
      storedDailyProgress = next;
    }
    return next;
  }

  void _fail() {
    if (failWith != null) throw DataException(failWith!);
  }

  List<TrainingEntry> get live => [for (final doc in entries.values) ?doc?.toEntry()];

  @override
  Future<void> saveEntry(String uid, TrainingEntryDoc entry) async {
    writes.add('entry ${entry.entryId}');
    entries[entry.entryId] = entry;
  }

  @override
  Future<void> deleteEntry(String uid, String entryId) async {
    writes.add('tombstone $entryId');
    entries[entryId] = null;
  }

  @override
  Future<TrainingEntryDoc?> entry(String uid, String entryId) async {
    _fail();
    return entries[entryId];
  }

  @override
  Future<void> setGoal(String uid, String weekStart, int workoutsPerWeek) async {
    writes.add('goal $weekStart $workoutsPerWeek');
    storedGoals[weekStart] = workoutsPerWeek;
  }

  @override
  Future<List<WeeklyGoal>> goals(String uid) async {
    _fail();
    return [
      for (final MapEntry(:key, :value) in storedGoals.entries)
        WeeklyGoal(workoutsPerWeek: value, effectiveFromWeek: key),
    ];
  }

  @override
  Future<List<TrainingEntry>> entriesFrom(String uid, String fromDate) async {
    _fail();
    return live.where((e) => e.dateLocal.compareTo(fromDate) >= 0).toList();
  }

  @override
  Future<List<TrainingEntry>> entriesBetween(String uid, String fromDate, String untilDate) async {
    _fail();
    return live.where((e) => e.dateLocal.compareTo(fromDate) >= 0 && e.dateLocal.compareTo(untilDate) < 0).toList();
  }

  @override
  Future<String?> lastCountedDateBefore(String uid, String beforeDate, {int maxPages = 4}) async {
    _fail();
    final dates = [
      for (final e in live)
        if (e.dateLocal.compareTo(beforeDate) < 0 && countsTowardGoal(e)) e.dateLocal,
    ]..sort();
    return dates.isEmpty ? null : dates.last;
  }

  @override
  Future<void> createProgram(
    String uid,
    String programId,
    ProgramProfile profile,
    GeneratedProgram program,
    int createdAt,
  ) async {
    writes.add('program $programId');
    storedPrograms[programId] = (profile: profile, program: program, createdAt: createdAt);
  }

  @override
  Future<void> completeProgramWorkout(String uid, String programId, int week, int dayIndex, int completedAt) async {
    writes.add('completion $programId ${workoutDocId(week, dayIndex)}');
    (completions[programId] ??= {})[workoutDocId(week, dayIndex)] = completedAt;
  }

  @override
  Future<List<ProgramSummary>> programs(String uid) async {
    _fail();
    return [
      for (final MapEntry(:key, :value) in storedPrograms.entries)
        ProgramSummary(
          programId: key,
          profile: value.profile,
          createdAt: value.createdAt,
          completedWorkouts: completions[key]?.length ?? 0,
        ),
    ];
  }

  @override
  Future<ProgramDetail?> program(String uid, String programId) async {
    _fail();
    final stored = storedPrograms[programId];
    if (stored == null) return null;
    final done = completions[programId] ?? const {};
    return ProgramDetail(
      summary: ProgramSummary(
        programId: programId,
        profile: stored.profile,
        createdAt: stored.createdAt,
        completedWorkouts: done.length,
      ),
      workouts: [
        for (final week in stored.program.weeks)
          for (var day = 0; day < week.workouts.length; day++)
            ProgramWorkoutRow(
              week: week.weekNumber,
              dayIndex: day,
              isDeload: week.isDeload,
              workout: week.workouts[day],
              completedAt: done[workoutDocId(week.weekNumber, day)],
            ),
      ],
    );
  }
}

/// My membership index, member docs and posts, with the rules' semantics where it matters (a group
/// in [refusing] refuses my writes: I was removed).
class FakeSharingRepository implements SharingRepository {
  List<String> groupIds = [];
  final Set<String> refusing = {};
  final Map<String, Fields> summaries = {};
  final Map<String, Map<String, Fields>> posts = {};
  final List<String> writes = [];

  @override
  Future<List<String>> myGroupIds(String uid) async => groupIds;

  @override
  Future<void> updateMemberSummary(String groupId, String uid, Fields fields) async {
    writes.add('summary $groupId');
    if (refusing.contains(groupId)) throw const DataException(DataError.permissionDenied);
    summaries[groupId] = fields;
  }

  @override
  Future<void> createPost(String groupId, String postId, Fields fields) async {
    writes.add('post $groupId $postId');
    if (refusing.contains(groupId)) throw const DataException(DataError.permissionDenied);
    (posts[groupId] ??= {})[postId] = fields;
  }

  @override
  Future<void> deletePost(String groupId, String postId) async {
    writes.add('delete $groupId $postId');
    posts[groupId]?.remove(postId);
  }

  /// The rules' update of `thumb`: only on an existing post.
  @override
  Future<void> setPostThumb(String groupId, String postId, Uint8List? thumb) async {
    writes.add('thumb $groupId $postId ${thumb == null ? 'removed' : 'set'}');
    final post = posts[groupId]?[postId];
    if (post == null) throw const DataException(DataError.notFound);
    if (thumb == null) {
      post.remove('thumb');
    } else {
      post['thumb'] = thumb;
    }
  }
}

class RecordingFeedback implements WorkoutFeedback {
  int sounds = 0;
  int vibrations = 0;

  @override
  void prepare() {}

  @override
  void playCompletionSound() => sounds++;

  @override
  void vibrate() => vibrations++;
}

/// Whether the screen is kept on.
class RecordingWakeLock implements WakeLockControl {
  bool held = false;

  @override
  Future<void> acquire() async => held = true;

  @override
  Future<void> release() async => held = false;
}

class EnglishTrainingTexts implements TrainingTexts {
  final AppLocalizations _l10n = lookupAppLocalizations(const Locale('en'));

  @override
  String get memberFallbackName => _l10n.memberFallbackName;

  @override
  String programTitle(ProgramWorkout workout) => programTitleOf(workout.titleCode, _l10n);
}

/// A training service over the fakes with one fake clock: the scheduler's virtual time drives the
/// monotonic clock, and the wall clock starts at [start] and moves with it.
class TrainingKit {
  TrainingKit({DateTime? start, this.device = const DeviceInfo(userAgent: 'Mozilla/5.0 (X11; Linux x86_64)')})
    : start = start ?? DateTime(2026, 10, 21, 18) {
    dates = LocalDates(now: () => this.start.add(Duration(milliseconds: scheduler.now + wallOffset)));
    service = TrainingService(
      training: training,
      sharing: sharing,
      store: store,
      dates: dates,
      texts: EnglishTrainingTexts(),
      wallClock: wall,
      uuid: () => 'uuid-${++_ids}',
      photos: photos,
    );
    deps = TrainingDeps(
      service: service,
      pending: pending,
      feedback: feedback,
      wakeLock: wakeLock,
      scheduler: scheduler,
      monotonic: () => scheduler.now,
      wall: wall,
      photoPicker: picker,
      imageCodec: codec,
      device: device,
      requestPersistence: () async => persistenceRequests++,
    );
  }

  /// Photos: this browser's store, the file chooser, a real JPEG codec, the browser's identity.
  final MemoryPhotoStore photos = MemoryPhotoStore();
  final FakePhotoPicker picker = FakePhotoPicker();
  final VmImageCodec codec = VmImageCodec();
  final DeviceInfo device;
  int persistenceRequests = 0;

  final DateTime start;
  final FakeScheduler scheduler = FakeScheduler();
  final FakeTrainingRepository training = FakeTrainingRepository();
  final FakeSharingRepository sharing = FakeSharingRepository();
  final MemoryKeyValueStore store = MemoryKeyValueStore();
  final PendingWrites pending = PendingWrites();
  final RecordingFeedback feedback = RecordingFeedback();
  final RecordingWakeLock wakeLock = RecordingWakeLock();
  late final LocalDates dates;
  late final TrainingService service;
  late final TrainingDeps deps;
  int wallOffset = 0;
  int _ids = 0;

  int wall() => start.millisecondsSinceEpoch + scheduler.now + wallOffset;

  String get today => dates.today();

  String get monday => CalendarDates.mondayOf(today);
}

/// Content with a tiny daily catalog (TIME 30 s, REPS 10, rest 15 s, TIME 20 s), a gym "Pull Day"
/// (64: REPS 10, 8 with 45 s rest; TIME 30 s, 45 s without rest) plus an empty routine (99), and the
/// real program files when [programs] is given.
extension ExtraDailyRoutines on TrainingContent {
  /// The same content with one more daily routine (a copy of the first one) as day [routineId].
  TrainingContent withExtraDailyRoutine(int routineId) => TrainingContent(
    gymRoutines: gymRoutines,
    dailyRoutines: [
      ...dailyRoutines,
      DailyRoutine(routineId: routineId, routineDay: 'Day $routineId', exercises: dailyRoutines.first.exercises),
    ],
    exercises: exercises,
    programCatalog: programCatalog,
    programTemplates: programTemplates,
  );
}

TrainingContent trainingContent({ProgramTemplates? templates, List<ProgramExercise> catalog = const []}) =>
    TrainingContent(
      gymRoutines: const [
        GymRoutine(
          routineId: 64,
          routineName: 'Pull Day',
          routineNameEs: 'Día de tirón',
          exercises: [
            GymSlot(position: 0, exerciseId: 2, type: ExerciseType.reps, reps: [10, 8], restSeconds: 45),
            GymSlot(position: 1, exerciseId: 4, type: ExerciseType.time, reps: [30, 45], restSeconds: 0),
          ],
        ),
        GymRoutine(routineId: 99, routineName: 'Empty', exercises: []),
      ],
      dailyRoutines: const [
        DailyRoutine(
          routineId: 3,
          routineDay: 'Day 3',
          exercises: [
            RoutineExercise(exerciseId: 1, type: ExerciseType.time, amount: 30),
            RoutineExercise(exerciseId: 2, type: ExerciseType.reps, amount: 10),
            RoutineExercise(exerciseId: 13, type: ExerciseType.time, amount: 15, isRest: true),
            RoutineExercise(exerciseId: 4, type: ExerciseType.time, amount: 20),
          ],
        ),
      ],
      exercises: const {
        1: CatalogExercise(
          exerciseId: 1,
          name: 'Jumping Jacks',
          nameEs: 'Saltos de tijera',
          animation: '1_jumping_jack.gif',
        ),
        2: CatalogExercise(exerciseId: 2, name: 'Squats', nameEs: 'Sentadillas', animation: '2_squat.gif'),
        13: CatalogExercise(exerciseId: 13, name: 'Rest', nameEs: 'Descanso', animation: '13_rest.gif'),
        4: CatalogExercise(exerciseId: 4, name: 'Plank', nameEs: 'Plancha', animation: '4_plank.gif'),
      },
      programCatalog: catalog,
      programTemplates:
          templates ??
          const ProgramTemplates(
            splits: [],
            strengthDays: [],
            prescriptions: [],
            progression: ProgressionRules(
              repStepPerWeek: 1,
              maxSets: 5,
              holdStepSeconds: 5,
              deloadEveryWeeks: 4,
              deloadMinProgramWeeks: 6,
              deloadSetsDrop: 1,
              deloadMinSets: 2,
              deloadCardioFactor: 0.7,
            ),
            cardio: CardioTemplates(
              rotation: {},
              outdoorRun: OutdoorRunTemplate(weeklyIncreasePercent: 10, runStartKm: {}, walkStartKm: {}),
              treadmill: {},
              treadmillLowImpact: TreadmillLowImpactTemplate(
                warmUpSeconds: 0,
                coolDownSeconds: 0,
                speedKmh: 5,
                lowIncline: 1,
                highIncline: 8,
                lowSeconds: 60,
                highSeconds: 60,
                baseIntervals: {},
                intervalsPerWeek: 1,
                maxIntervals: 10,
              ),
              homeCircuit: HomeCircuitTemplate(
                levels: {},
                restBetweenRoundsSeconds: 60,
                candidates: [],
                lowImpactCandidates: [],
              ),
            ),
            impact: ImpactRules(
              lowImpactMinBmi: 30,
              lowImpactMinAge: 60,
              seniorMinAge: 60,
              seniorExtraRestSeconds: 15,
              excludedForBeginnerOrLowImpact: {},
            ),
          ),
    );

const WebUser alex = WebUser(
  uid: 'me',
  displayName: '  Alex Rivera  ',
  photoUrl: 'https://lh3.googleusercontent.com/a/photo',
);

/// Lets queued microtasks (fire-and-forget writes) run.
Future<void> settle() => Future<void>.delayed(Duration.zero);
