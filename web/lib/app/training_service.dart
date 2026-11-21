/// Training on the web (Phase 5b): what Android's repositories do when a workout is recorded, an
/// activity is logged, an entry deleted, a goal or a program created — as direct Firestore writes
/// with the shared mappers — plus my member summary (`GroupSummaryPublisher`) and my feed posts
/// (`FeedPublisher`, with the thumbnail of this browser's photo). The in-progress workout sessions
/// live in the browser's localStorage ([KeyValueStore]) with Android's session rules
/// (domain/sessions.dart); photos in IndexedDB ([PhotoStore]).
library;

import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';

import '../data/repositories.dart';
import '../data/training_repositories.dart';
import '../data/write_mappers.dart';
import '../domain/activity_entry.dart';
import '../domain/calendar_dates.dart';
import '../domain/daily_progress.dart';
import '../domain/group_summary.dart';
import '../domain/gym.dart';
import '../domain/groups.dart';
import '../domain/kotlin_text.dart';
import '../domain/photos.dart';
import '../domain/program.dart';
import '../domain/program_generator.dart';
import '../domain/sessions.dart';
import '../domain/training.dart';
import '../domain/weekly_progress.dart';
import '../domain/workout_names.dart';
import '../platform/browser.dart';
import '../platform/photos.dart';

/// A random lowercase UUID v4 (the model's entry and program ids).
String newUuid([Random? random]) {
  final r = random ?? Random.secure();
  final bytes = List<int>.generate(16, (_) => r.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-'
      '${hex.substring(16, 20)}-${hex.substring(20)}';
}

/// The name and photo my member docs and posts carry (Android's `MemberProfiles`): the name trimmed
/// and cut to 100 ([fallbackName] when there is none), only an https photo of ≤ 2048 characters.
({String displayName, String? photoUrl}) memberProfileOf(WebUser user, String fallbackName) {
  final raw = user.displayName;
  String? name;
  if (raw != null) {
    final trimmed = kotlinTrim(raw);
    name = kotlinTrim(
      trimmed.length > GroupLimits.displayNameMaxLength
          ? trimmed.substring(0, GroupLimits.displayNameMaxLength)
          : trimmed,
    );
  }
  final photo = user.photoUrl;
  return (
    displayName: name == null || name.isEmpty ? fallbackName : name,
    photoUrl: photo != null && photo.startsWith('https://') && photo.length <= GroupLimits.photoUrlMaxLength
        ? photo
        : null,
  );
}

/// The texts the service writes into stored titles (the recording device's language, as Android).
abstract interface class TrainingTexts {
  String get memberFallbackName;

  String programTitle(ProgramWorkout workout);
}

class TrainingService {
  TrainingService({
    required this.training,
    required this.sharing,
    required this.store,
    required this.dates,
    required this.texts,
    WallClock? wallClock,
    String Function()? uuid,
    PhotoStore? photos,
  }) : wallClock = wallClock ?? (() => DateTime.now().millisecondsSinceEpoch),
       _uuid = uuid ?? newUuid,
       photos = photos ?? MemoryPhotoStore();

  final TrainingRepository training;
  final SharingRepository sharing;
  final KeyValueStore store;

  /// Training photos of this browser (IndexedDB), per account and entry: never uploaded; only the
  /// thumbnail goes to my posts.
  final PhotoStore photos;
  final LocalDates dates;
  TrainingTexts texts;
  final WallClock wallClock;
  final String Function() _uuid;

  /// Days a share record is kept (Android's `SHARE_RETENTION`: a week of posts, plus margin).
  static const int shareRetentionMillis = 14 * 24 * 60 * 60 * 1000;

  String _key(String uid, String name) => 'tm.$uid.$name';

  // ---------------------------------------------------------------------------
  // Sessions (localStorage)
  // ---------------------------------------------------------------------------

  /// The daily workout's active-time session (one at a time, as Android's DAILY tracker).
  ActiveSessionTracker dailySessions(String uid) =>
      ActiveSessionTracker(_LocalActiveStore(this, uid, 'session.daily', TrainingSource.daily), wallClock);

  /// Program cardio (treadmill, circuit): one session for all of them, as Android's PROGRAM_CARDIO.
  ActiveSessionTracker cardioSessions(String uid) =>
      ActiveSessionTracker(_LocalActiveStore(this, uid, 'session.cardio', TrainingSource.program), wallClock);

  StrengthSessions strengthSessions(String uid) => StrengthSessions(_LocalStrengthStore(this, uid), wallClock);

  /// The title (and post key) an abandoned session of [key] is recorded with, set when its workout
  /// opens: the stored title is in this browser's language, as Android's.
  void rememberSessionTitle(String uid, String key, String title, {String? titleKey}) {
    final titles = _readMap(_key(uid, 'sessionTitles'));
    titles[key] = {'title': title, 'titleKey': titleKey};
    _writeJson(_key(uid, 'sessionTitles'), titles);
  }

  ({String title, String? titleKey}) _sessionTitle(String uid, String key) {
    final value = _readMap(_key(uid, 'sessionTitles'))[key];
    if (value is! Map) return (title: '', titleKey: null);
    final title = value['title'];
    final titleKey = value['titleKey'];
    return (title: title is String ? title : '', titleKey: titleKey is String ? titleKey : null);
  }

  /// Closes every session inactive for more than 2 hours (recording it); the durations to announce.
  List<ClosedSession> closeInactiveSessions(String uid) {
    final daily = dailySessions(uid)..closeInactive();
    final cardio = cardioSessions(uid)..closeInactive();
    final strength = strengthSessions(uid)..closeInactive();
    final gym = gymSessions(uid)..closeInactive();
    return [
      ...daily.takeClosedSessions(),
      ...cardio.takeClosedSessions(),
      ...strength.takeClosedSessions(),
      ...gym.takeClosedSessions(),
    ];
  }

  // ---------------------------------------------------------------------------
  // Gym routines: progress and sessions in this browser (device-local on Android too, not synced);
  // only the finished workout, a GYM entry, is written
  // ---------------------------------------------------------------------------

  /// The gym sessions of [uid] (Android's GymSessionTracker rule).
  GymSessions gymSessions(String uid) => GymSessions(_LocalGymStore(this, uid), wallClock);

  /// The routine's English name is the stored title of its entry (as Android stores it); kept for a
  /// session closed later without the screen.
  void rememberGymRoutine(String uid, GymRoutine routine) => rememberSessionTitle(
    uid,
    gymRefId(routine.routineId),
    routine.routineName,
    titleKey: WorkoutTitleKey.valueOrNull(GymTitleKey(routine.routineId)),
  );

  Set<int> gymCompletedPositions(String uid, int routineId) {
    final value = _readMap(_key(uid, 'progress.gym'))['$routineId'];
    return {
      if (value is List)
        for (final p in value) ?(p is int ? p : null),
    };
  }

  /// The exercise at [position] was done (its last set).
  void markGymCompleted(String uid, int routineId, int position) {
    final all = _readMap(_key(uid, 'progress.gym'))
      ..['$routineId'] = ({...gymCompletedPositions(uid, routineId), position}.toList()..sort());
    _writeJson(_key(uid, 'progress.gym'), all);
  }

  /// Clears the routine's completed exercises (the completion dialog dismissed, or a close).
  void clearGymProgress(String uid, int routineId) {
    final all = _readMap(_key(uid, 'progress.gym'))..remove('$routineId');
    _writeJson(_key(uid, 'progress.gym'), all);
  }

  /// Reset by hand: the progress goes and the session is discarded without being recorded.
  void resetGymRoutine(String uid, int routineId) {
    clearGymProgress(uid, routineId);
    gymSessions(uid).discardSession(routineId);
  }

  /// Every exercise is done: the session's entry (first → last set, dated now), once — null when it
  /// was already recorded (the dialog shown again after a reload).
  Future<TrainingEntry?> completeGymRoutine(WebUser user, GymRoutine routine) async {
    rememberGymRoutine(user.uid, routine);
    final store = _LocalGymStore(this, user.uid, user);
    if (!GymSessions(store, wallClock).completeSession(routine.routineId)) return null;
    return store.recorded;
  }

  /// A workout's position, kept so a reload resumes it ([name] e.g. `progress.daily`).
  Map<String, Object?>? progress(String uid, String name) {
    final value = _readJsonOrNull(_key(uid, name));
    return value is Map<String, Object?> ? value : null;
  }

  void saveProgress(String uid, String name, Map<String, Object?> value) => _writeJson(_key(uid, name), value);

  void clearProgress(String uid, String name) => store.remove(_key(uid, name));

  // ---------------------------------------------------------------------------
  // Recording
  // ---------------------------------------------------------------------------

  /// Records a finished (or abandoned) workout: a new entry, then my summary for my groups and, when
  /// [share] and the explanation was answered, my posts. [completedAt] defaults to now and the date
  /// to [dateOfMillis] (the session's start for an abandoned one) or [completedAt].
  Future<TrainingEntry> recordWorkout({
    required WebUser user,
    required TrainingSource source,
    required String? refId,
    required String title,
    required int? durationSeconds,
    int? completedAt,
    int? dateOfMillis,
    bool share = true,
    String? titleKey,
  }) async {
    final now = wallClock();
    final at = completedAt ?? now;
    final doc = TrainingEntryDoc(
      entryId: _uuid(),
      dateLocal: CalendarDates.dateOf(DateTime.fromMillisecondsSinceEpoch(dateOfMillis ?? at)),
      completedAt: at,
      source: source,
      refId: refId,
      title: title,
      durationSeconds: durationSeconds,
      createdAt: now,
    );
    _fireAndForget(training.saveEntry(user.uid, doc));
    final entry = doc.toEntry();
    unawaited(publishSummary(user, added: entry));
    if (share && isFeedIntroSeen(user.uid)) unawaited(shareEntry(user, entry, titleKey: titleKey));
    return entry;
  }

  /// Saves a logged activity (new when [existing] is null; an edit keeps its id and createdAt).
  /// Returns the validation errors (nothing is written then) and the saved entry.
  /// A new or edited activity; [photo] (a new one) or [removePhoto] (the stored one) apply after the
  /// entry, before it is posted, so a new post carries the thumbnail. A photo that can't be stored
  /// never loses the entry: [photoError] says why.
  Future<({Set<ActivityEntryError> errors, TrainingEntry? entry, PhotoError? photoError})> saveActivity({
    required WebUser user,
    required ActivityEntryInput input,
    TrainingEntryDoc? existing,
    bool share = true,
    ProcessedPhoto? photo,
    bool removePhoto = false,
  }) async {
    final now = wallClock();
    var earliest = ActivityEntryRules.windowStart(dates.today());
    if (existing != null) {
      // The entry's own day stays allowed when editing an older one
      final ownDay = ActivityEntryRules.startOfDay(existing.dateLocal);
      if (ownDay < earliest) earliest = ownDay;
    }
    final errors = ActivityEntryRules.validate(input, now, earliest);
    if (errors.isNotEmpty) return (errors: errors, entry: null, photoError: null);
    final doc = TrainingEntryDoc(
      entryId: existing?.entryId ?? _uuid(),
      dateLocal: CalendarDates.dateOf(DateTime.fromMillisecondsSinceEpoch(input.completedAt)),
      completedAt: input.completedAt,
      source: TrainingSource.activity,
      refId: null,
      title: ActivityEntryRules.titleFor(input),
      durationSeconds: input.durationMinutes! * 60,
      createdAt: existing?.createdAt ?? now,
      activityType: input.type!.code,
      intensity: input.intensity!.code,
      note: ActivityEntryRules.noteFor(input),
    );
    _fireAndForget(training.saveEntry(user.uid, doc));
    final entry = doc.toEntry();
    unawaited(publishSummary(user, added: entry));
    PhotoError? photoError;
    if (photo != null) {
      try {
        await attachPhoto(user, entry.id, photo);
      } on PhotoException catch (e) {
        photoError = e.error;
      }
    } else if (removePhoto) {
      await this.removePhoto(user, entry.id);
    }
    if (existing == null && share && isFeedIntroSeen(user.uid)) unawaited(shareEntry(user, entry));
    return (errors: const <ActivityEntryError>{}, entry: entry, photoError: photoError);
  }

  /// Deletes my entry (its tombstone) and its post in every group of my index, then updates my summary.
  Future<void> deleteEntry(WebUser user, TrainingEntry entry) async {
    _fireAndForget(training.deleteEntry(user.uid, entry.id));
    unawaited(_deletePhoto(user.uid, entry.id));
    final groups = {...await _myGroups(user.uid), ..._postedGroups(user.uid, entry.id)};
    for (final groupId in groups) {
      _fireAndForget(sharing.deletePost(groupId, entry.id));
    }
    _forgetShare(user.uid, entry.id);
    unawaited(publishSummary(user, removedId: entry.id));
  }

  /// This week's goal (`weeklyGoals/{monday}`), then my summary.
  Future<void> setGoal(WebUser user, int workoutsPerWeek) async {
    final week = dates.currentWeekStart();
    _fireAndForget(training.setGoal(user.uid, week, workoutsPerWeek));
    unawaited(
      publishSummary(
        user,
        goal: WeeklyGoal(workoutsPerWeek: workoutsPerWeek, effectiveFromWeek: week),
      ),
    );
  }

  /// Generates and stores a program (a new uuid); returns its id. The detail is cached at once, so
  /// it opens offline too.
  String createProgram(String uid, ProgramProfile profile, List<ProgramExercise> catalog, ProgramTemplates templates) {
    final program = ProgramGenerator.generate(profile, catalog, templates);
    final programId = _uuid();
    final createdAt = wallClock();
    _fireAndForget(training.createProgram(uid, programId, profile, program, createdAt));
    _programDetails[programId] = Future.value(
      ProgramDetail(
        summary: ProgramSummary(programId: programId, profile: profile, createdAt: createdAt, completedWorkouts: 0),
        workouts: [
          for (final week in program.weeks)
            for (var day = 0; day < week.workouts.length; day++)
              ProgramWorkoutRow(
                week: week.weekNumber,
                dayIndex: day,
                isDeload: week.isDeload,
                workout: week.workouts[day],
                completedAt: null,
              ),
        ],
      ),
    );
    return programId;
  }

  /// A program workout is completed: its completion doc, then one PROGRAM entry (Android's
  /// `markSlotCompleted` / `markWorkoutCompleted` + `recordWorkout`).
  Future<TrainingEntry> completeProgramWorkout({
    required WebUser user,
    required String programId,
    required int week,
    required int dayIndex,
    required ProgramWorkout workout,
    required int? durationSeconds,
    bool share = true,
  }) {
    final now = wallClock();
    _fireAndForget(training.completeProgramWorkout(user.uid, programId, week, dayIndex, now));
    _markCompletedInCache(programId, week, dayIndex, now);
    return recordWorkout(
      user: user,
      source: TrainingSource.program,
      refId: programRefId(programId, week, dayIndex),
      title: texts.programTitle(workout),
      durationSeconds: durationSeconds,
      share: share,
      titleKey: 'program/${workout.titleCode.code}',
    );
  }

  // ---------------------------------------------------------------------------
  // Daily unlock (`dailyProgress/state`, Android's DailyProgressRepository)
  // ---------------------------------------------------------------------------

  /// My daily progress. With none yet (the first visit of a version with the unlock), it is created
  /// from my history ([DailyUnlockRules.fromHistory]: the highest DAILY day), once.
  Future<DailyProgress> dailyProgress(String uid) async =>
      await training.dailyProgress(uid) ?? await _changeDailyProgress(uid, (progress) => progress);

  /// The daily workout of [routineId] reached its completion: the next day unlocks when it was the
  /// newest one. A session closed for inactivity never calls this.
  Future<DailyProgress> finishDailyDay(String uid, int routineId) =>
      _changeDailyProgress(uid, (progress) => DailyUnlockRules.afterFinishing(progress, routineId));

  /// "Restart days" / "Start again from Day 1": a new cycle at Day 1 (the history is not touched).
  Future<DailyProgress> restartDailyDays(String uid) => _changeDailyProgress(uid, DailyUnlockRules.restart);

  /// [change] of the server's state, in a transaction (another device's newer state is never
  /// overwritten). The history is read only when there is no state yet.
  Future<DailyProgress> _changeDailyProgress(String uid, DailyProgress Function(DailyProgress) change) async {
    final stored = await training.dailyProgress(uid);
    final history = stored == null
        ? DailyUnlockRules.fromHistory(await training.dailyHistory(uid))
        : const DailyProgress();
    return training.updateDailyProgress(uid, (current) => change(current ?? history));
  }

  // ---------------------------------------------------------------------------
  // Programs: read once per page load (a workout and its exercises share one read)
  // ---------------------------------------------------------------------------

  final Map<String, Future<ProgramDetail?>> _programDetails = {};

  /// The program with its workouts and completions, cached for this page load; a completion made
  /// here updates the cached copy.
  Future<ProgramDetail?> programDetail(String uid, String programId, {bool refresh = false}) {
    if (refresh) _programDetails.remove(programId);
    return _programDetails[programId] ??= training.program(uid, programId).catchError((Object e) {
      _programDetails.remove(programId);
      throw e;
    });
  }

  void _markCompletedInCache(String programId, int week, int dayIndex, int completedAt) {
    final cached = _programDetails[programId];
    if (cached == null) return;
    _programDetails[programId] = cached.then((detail) {
      if (detail == null) return null;
      final rows = [
        for (final row in detail.workouts)
          row.week == week && row.dayIndex == dayIndex
              ? ProgramWorkoutRow(
                  week: row.week,
                  dayIndex: row.dayIndex,
                  isDeload: row.isDeload,
                  workout: row.workout,
                  completedAt: completedAt,
                )
              : row,
      ];
      final completed = rows.where((r) => r.isCompleted).length;
      return ProgramDetail(
        summary: ProgramSummary(
          programId: detail.summary.programId,
          profile: detail.summary.profile,
          createdAt: detail.summary.createdAt,
          completedWorkouts: completed,
        ),
        workouts: rows,
      );
    });
  }

  // ---------------------------------------------------------------------------
  // Program strength workouts: slot progress (local only, as Android's program_slot_progress)
  // ---------------------------------------------------------------------------

  String _slotsKey(String programId, int week, int dayIndex) => '$programId/${workoutDocId(week, dayIndex)}';

  /// The positions done of a strength workout in this browser.
  Set<int> completedSlots(String uid, String programId, int week, int dayIndex) {
    final value = _readMap(_key(uid, 'progress.strength'))[_slotsKey(programId, week, dayIndex)];
    return {
      if (value is List)
        for (final p in value) ?(p is int ? p : null),
    };
  }

  /// Reset by hand: the slots and the session go (the work is not saved).
  void resetStrengthWorkout(String uid, String programId, int week, int dayIndex) {
    final all = _readMap(_key(uid, 'progress.strength'))..remove(_slotsKey(programId, week, dayIndex));
    _writeJson(_key(uid, 'progress.strength'), all);
    strengthSessions(uid).take(programId, week, dayIndex);
  }

  /// Entries of workouts completed in this page load, for their completion view (Android's
  /// `observeCompletionEntryId`), by `programId/week-day`.
  final Map<String, TrainingEntry> justCompleted = {};

  /// Marks [position] done (Android's `markSlotCompleted`). When this completes the workout, its
  /// completion is written; when it wasn't complete before, its session ends here (never recorded
  /// twice) and one PROGRAM entry is recorded with the session's first → last set.
  Future<TrainingEntry?> markSlotCompleted({
    required WebUser user,
    required String programId,
    required int week,
    required int dayIndex,
    required StrengthWorkout workout,
    required int position,
  }) async {
    final doneBefore = completedSlots(user.uid, programId, week, dayIndex);
    final done = {...doneBefore, position};
    final all = _readMap(_key(user.uid, 'progress.strength'))
      ..[_slotsKey(programId, week, dayIndex)] = (done.toList()..sort());
    _writeJson(_key(user.uid, 'progress.strength'), all);
    final positions = workout.slots.map((s) => s.position);
    if (workout.slots.isEmpty || !positions.every(done.contains)) return null;
    if (positions.every(doneBefore.contains)) {
      // Completed again without a reset: the completion is written again, nothing is recorded
      final now = wallClock();
      _fireAndForget(training.completeProgramWorkout(user.uid, programId, week, dayIndex, now));
      _markCompletedInCache(programId, week, dayIndex, now);
      return null;
    }
    final session = strengthSessions(user.uid).take(programId, week, dayIndex);
    final entry = await completeProgramWorkout(
      user: user,
      programId: programId,
      week: week,
      dayIndex: dayIndex,
      workout: workout,
      durationSeconds: session?.durationSeconds,
    );
    justCompleted[_slotsKey(programId, week, dayIndex)] = entry;
    return entry;
  }

  TrainingEntry? takeJustCompleted(String programId, int week, int dayIndex) =>
      justCompleted.remove(_slotsKey(programId, week, dayIndex));

  // ---------------------------------------------------------------------------
  // My summary for my groups (GroupSummaryPublisher)
  // ---------------------------------------------------------------------------

  /// Publishes my member summary to every group of my index when it changed since the last publish
  /// from this browser. [added] / [removedId] / [goal] apply a write the server may not have yet
  /// (the reads come from the local cache, which already has it; this keeps the result exact).
  /// The next [publishSummary] writes even if nothing changed (a group just created or joined).
  void forgetPublishedSummary(String uid) => store.remove(_key(uid, 'summaryHash'));

  Future<void> publishSummary(WebUser user, {TrainingEntry? added, String? removedId, WeeklyGoal? goal}) async {
    try {
      final groups = await _myGroups(user.uid);
      if (groups.isEmpty) return;
      final summary = await currentSummary(user.uid, added: added, removedId: removedId, goal: goal);
      if (summary == null) return;
      final profile = memberProfileOf(user, texts.memberFallbackName);
      final fields = WriteMappers.memberSummary(profile.displayName, profile.photoUrl, summary);
      final hash = sha256.convert(utf8.encode(jsonEncode({...fields, 'updatedAt': null}))).toString();
      if (store.read(_key(user.uid, 'summaryHash')) == hash) return;
      for (final groupId in groups) {
        // A group that refuses (I was removed, it's gone) doesn't block the others
        _fireAndForget(sharing.updateMemberSummary(groupId, user.uid, fields));
      }
      store.write(_key(user.uid, 'summaryHash'), hash);
    } on DataException catch (e) {
      debugPrint('Groups: summary not published (${e.error})');
    }
  }

  /// [GroupSummaryCalculator.summary] over what Firestore has: this week's entries and the goals,
  /// the streak read back week by week ([streakReadingBack]) and the last trained date before this
  /// week when this week has none. Null without a goal this week.
  Future<MemberSummary?> currentSummary(String uid, {TrainingEntry? added, String? removedId, WeeklyGoal? goal}) async {
    final today = dates.today();
    final monday = CalendarDates.mondayOf(today);
    final goals = [...(await training.goals(uid)).where((g) => g.effectiveFromWeek != goal?.effectiveFromWeek), ?goal];
    var thisWeek = (await training.entriesFrom(
      uid,
      monday,
    )).where((e) => e.id != removedId && e.id != added?.id).toList();
    if (added != null && added.dateLocal.compareTo(monday) >= 0) thisWeek = [...thisWeek, added];
    final weekSummary = GroupSummaryCalculator.summary(today, thisWeek, goals);
    if (weekSummary == null) return null;
    final perDay = countedWorkoutsPerDay(thisWeek);
    final streak = await streakReadingBack(
      today: today,
      goals: goals,
      currentWeekPerDay: perDay,
      loadPast: (from, until) async =>
          countedWorkoutsPerDay((await training.entriesBetween(uid, from, until)).where((e) => e.id != removedId)),
    );
    var lastTrained = weekSummary.lastTrainedLocal;
    if (lastTrained == null) {
      final before = await training.lastCountedDateBefore(uid, monday);
      final addedBefore = added != null && countsTowardGoal(added) && added.dateLocal.compareTo(monday) < 0
          ? added.dateLocal
          : null;
      lastTrained = [?before, ?addedBefore].fold<String?>(null, (a, b) => a == null || b.compareTo(a) > 0 ? b : a);
    }
    return MemberSummary(
      weekStartLocal: weekSummary.weekStartLocal,
      workoutsThisWeek: weekSummary.workoutsThisWeek,
      weeklyGoal: weekSummary.weeklyGoal,
      streakWeeks: streak > GroupLimits.maxStreak ? GroupLimits.maxStreak : streak,
      daysThisWeek: weekSummary.daysThisWeek,
      lastTrainedLocal: lastTrained,
    );
  }

  // ---------------------------------------------------------------------------
  // The feed (FeedPublisher; the thumbnail of this browser's photo)
  // ---------------------------------------------------------------------------

  bool isFeedIntroSeen(String uid) => store.read(_key(uid, 'feedIntroSeen')) == '1';

  void setFeedIntroSeen(String uid) => store.write(_key(uid, 'feedIntroSeen'), '1');

  /// Posts [entry] to the groups of my index, one write each (a refusing group doesn't block the
  /// others), when it counts and its week is the current one (the feed shows nothing else). The
  /// post's key: [titleKey] (a program workout's code), else the entry's own (gym, daily).
  Future<void> shareEntry(WebUser user, TrainingEntry entry, {String? titleKey}) async {
    if (!countsTowardGoal(entry)) return;
    if (CalendarDates.mondayOf(entry.dateLocal) != dates.currentWeekStart()) return;
    try {
      final groups = await _myGroups(user.uid);
      if (groups.isEmpty) return;
      final profile = memberProfileOf(user, texts.memberFallbackName);
      final fields = WriteMappers.post(
        authorUid: user.uid,
        authorName: profile.displayName,
        authorPhotoUrl: profile.photoUrl,
        entry: entry,
        titleKey: titleKey ?? WorkoutNameResolver.titleKeyOf(entry, null),
        // The photo's thumbnail when this browser has one (Android: the entry's local thumbnail)
        thumb: (await photoOf(user.uid, entry.id))?.thumb,
      );
      for (final groupId in groups) {
        _fireAndForget(sharing.createPost(groupId, entry.id, fields));
      }
      _rememberShare(user.uid, entry.id, groups);
    } on DataException catch (e) {
      debugPrint('Feed: not posted (${e.error})');
    }
  }

  // ---------------------------------------------------------------------------
  // Photos: the full photo stays in this browser; its thumbnail goes on my posts of the entry
  // ---------------------------------------------------------------------------

  /// This browser's photo of [entryId]; null when there is none (taken on another device, or
  /// evicted by the browser) or it can't be read.
  Future<StoredPhoto?> photoOf(String uid, String entryId) async {
    try {
      return await photos.read(uid, entryId);
    } on Object catch (e) {
      debugPrint('Photos: not read (${e.runtimeType})');
      return null;
    }
  }

  /// Keeps [photo] as the entry's photo (replacing one), then sets its thumbnail on my posts of the
  /// entry: one update per group posted from here (Android's thumbnail update). Throws
  /// [PhotoException] when it can't be stored (nothing changes then).
  Future<void> attachPhoto(WebUser user, String entryId, ProcessedPhoto photo) async {
    await photos.save(user.uid, entryId, StoredPhoto(full: photo.full, thumb: photo.thumb, savedAt: wallClock()));
    _setPostThumbs(user.uid, entryId, photo.thumb);
  }

  /// Deletes the entry's photo here and removes the thumbnail from my posts of it.
  Future<void> removePhoto(WebUser user, String entryId) async {
    await _deletePhoto(user.uid, entryId);
    _setPostThumbs(user.uid, entryId, null);
  }

  Future<void> _deletePhoto(String uid, String entryId) async {
    try {
      await photos.delete(uid, entryId);
    } on Object catch (e) {
      debugPrint('Photos: not deleted (${e.runtimeType})');
    }
  }

  void _setPostThumbs(String uid, String entryId, Uint8List? thumb) {
    for (final groupId in _postedGroups(uid, entryId)) {
      _fireAndForget(sharing.setPostThumb(groupId, entryId, thumb));
    }
  }

  /// The switch was turned off: the posts already made are deleted.
  void unshareEntry(String uid, String entryId) {
    for (final groupId in _postedGroups(uid, entryId)) {
      _fireAndForget(sharing.deletePost(groupId, entryId));
    }
    _forgetShare(uid, entryId);
  }

  bool isShared(String uid, String entryId) => _postedGroups(uid, entryId).isNotEmpty;

  Future<List<String>> _myGroups(String uid) async {
    try {
      return await sharing.myGroupIds(uid);
    } on DataException {
      return const [];
    }
  }

  List<String> _postedGroups(String uid, String entryId) {
    final record = _readMap(_key(uid, 'shares'))[entryId];
    if (record is! Map) return const [];
    return [for (final id in record['groups'] as List? ?? const []) ?(id is String ? id : null)];
  }

  void _rememberShare(String uid, String entryId, List<String> groups) {
    final now = wallClock();
    final shares = _readMap(_key(uid, 'shares'))
      ..removeWhere((_, value) => value is! Map || (value['at'] as int? ?? 0) < now - shareRetentionMillis);
    shares[entryId] = {'groups': groups, 'at': now};
    _writeJson(_key(uid, 'shares'), shares);
  }

  void _forgetShare(String uid, String entryId) {
    final shares = _readMap(_key(uid, 'shares'))..remove(entryId);
    _writeJson(_key(uid, 'shares'), shares);
  }

  // ---------------------------------------------------------------------------

  /// Every training key of [uid] (sign-out on a shared computer: nothing of the account stays).
  Future<void> clearLocalData(String uid) async {
    for (final name in const [
      'session.daily',
      'session.cardio',
      'session.strength',
      'sessionTitles',
      'progress.daily',
      'progress.cardio',
      'progress.strength',
      'session.gym',
      'progress.gym',
      'feedIntroSeen',
      'summaryHash',
      'shares',
    ]) {
      store.remove(_key(uid, name));
    }
    try {
      await photos.clear(uid);
    } on Object catch (e) {
      debugPrint('Photos: not cleared (${e.runtimeType})');
    }
  }

  void _fireAndForget(Future<void> write) {
    // The failure is logged by the repository; nothing waits for the server
    write.catchError((Object _) {}).ignore();
  }

  Object? _readJsonOrNull(String key) {
    final text = store.read(key);
    if (text == null) return null;
    try {
      return jsonDecode(text);
    } on FormatException {
      return null;
    }
  }

  Map<String, Object?> _readMap(String key) {
    final value = _readJsonOrNull(key);
    return value is Map<String, Object?> ? Map.of(value) : <String, Object?>{};
  }

  void _writeJson(String key, Object? value) => store.write(key, jsonEncode(value));
}

/// An active-time session kind in localStorage; an abandoned one is recorded as one entry with its
/// active time, completed at its last activity, dated on its start (Android's stores).
class _LocalActiveStore implements ActiveSessionStore {
  _LocalActiveStore(this._service, this._uid, this._name, this._source);

  final TrainingService _service;
  final String _uid;
  final String _name;
  final TrainingSource _source;

  /// Set by the screen that owns the session; recording needs the signed-in user.
  static WebUser? user;

  String get _storeKey => _service._key(_uid, _name);

  @override
  ActiveSession? get() => ActiveSession.fromJson(_service._readJsonOrNull(_storeKey));

  @override
  void save(ActiveSession session) => _service._writeJson(_storeKey, session.toJson());

  @override
  void delete() => _service.store.remove(_storeKey);

  @override
  void recordAbandoned(ActiveSession session) {
    final user = _LocalActiveStore.user;
    if (user == null || user.uid != _uid) return;
    final remembered = _service._sessionTitle(_uid, session.key);
    unawaited(
      _service.recordWorkout(
        user: user,
        source: _source,
        refId: session.key,
        title: remembered.title,
        durationSeconds: session.activeSeconds,
        completedAt: session.lastActivityAt,
        dateOfMillis: session.startedAt,
        titleKey: remembered.titleKey,
      ),
    );
  }
}

/// The strength sessions in localStorage (a list, one per workout in progress).
class _LocalStrengthStore implements StrengthSessionStore {
  _LocalStrengthStore(this._service, this._uid);

  final TrainingService _service;
  final String _uid;

  String get _storeKey => _service._key(_uid, 'session.strength');

  List<StrengthSession> _all() {
    final value = _service._readJsonOrNull(_storeKey);
    return [
      if (value is List)
        for (final item in value) ?StrengthSession.fromJson(item),
    ];
  }

  void _saveAll(List<StrengthSession> sessions) =>
      _service._writeJson(_storeKey, [for (final s in sessions) s.toJson()]);

  bool _same(StrengthSession s, String programId, int week, int dayIndex) =>
      s.programId == programId && s.week == week && s.dayIndex == dayIndex;

  @override
  StrengthSession? get(String programId, int week, int dayIndex) {
    for (final session in _all()) {
      if (_same(session, programId, week, dayIndex)) return session;
    }
    return null;
  }

  @override
  List<StrengthSession> getAll() => _all();

  @override
  void save(StrengthSession session) =>
      _saveAll([..._all().where((s) => !_same(s, session.programId, session.week, session.dayIndex)), session]);

  @override
  void delete(String programId, int week, int dayIndex) =>
      _saveAll(_all().where((s) => !_same(s, programId, week, dayIndex)).toList());

  @override
  void recordAbandoned(StrengthSession session) {
    final user = _LocalActiveStore.user;
    if (user == null || user.uid != _uid) return;
    final refId = programRefId(session.programId, session.week, session.dayIndex);
    final remembered = _service._sessionTitle(_uid, refId);
    unawaited(
      _service.recordWorkout(
        user: user,
        source: TrainingSource.program,
        refId: refId,
        title: remembered.title,
        durationSeconds: session.durationSeconds,
        completedAt: session.lastActivityAt,
        dateOfMillis: session.startedAt,
        titleKey: remembered.titleKey,
      ),
    );
  }
}

/// Who is signed in, for recording abandoned sessions (set by the app when the user changes).
void setTrainingUser(WebUser? user) => _LocalActiveStore.user = user;

/// The gym sessions in localStorage (a list) and what closing one touches: the GYM entry (the
/// routine's English name, `gym:<id>`, its key `gym/<id>` for the feed) and the routine's progress.
class _LocalGymStore implements GymSessionStore {
  _LocalGymStore(this._service, this._uid, [this._user]);

  final TrainingService _service;
  final String _uid;
  final WebUser? _user;

  /// The entry recorded last through this store (a normal completion's).
  Future<TrainingEntry>? recorded;

  String get _storeKey => _service._key(_uid, 'session.gym');

  List<GymSession> _all() {
    final value = _service._readJsonOrNull(_storeKey);
    return [
      if (value is List)
        for (final item in value)
          if (item is Map && item['routineId'] is int && item['startedAt'] is int && item['lastSetAt'] is int)
            GymSession(
              routineId: item['routineId'] as int,
              startedAt: item['startedAt'] as int,
              lastSetAt: item['lastSetAt'] as int,
            ),
    ];
  }

  void _saveAll(List<GymSession> sessions) => _service._writeJson(_storeKey, [
    for (final s in sessions) {'routineId': s.routineId, 'startedAt': s.startedAt, 'lastSetAt': s.lastSetAt},
  ]);

  @override
  GymSession? get(int routineId) => _all().where((s) => s.routineId == routineId).firstOrNull;

  @override
  List<GymSession> getAll() => _all();

  @override
  void save(GymSession session) => _saveAll([..._all().where((s) => s.routineId != session.routineId), session]);

  @override
  void delete(int routineId) => _saveAll(_all().where((s) => s.routineId != routineId).toList());

  @override
  void clearProgress(int routineId) => _service.clearGymProgress(_uid, routineId);

  @override
  void recordSession(int routineId, int durationSeconds, {required int completedAt, required int dateOfMillis}) {
    final user = _user ?? _LocalActiveStore.user;
    if (user == null || user.uid != _uid) return;
    final remembered = _service._sessionTitle(_uid, gymRefId(routineId));
    final entry = _service.recordWorkout(
      user: user,
      source: TrainingSource.gym,
      refId: gymRefId(routineId),
      title: remembered.title,
      durationSeconds: durationSeconds,
      completedAt: completedAt,
      dateOfMillis: dateOfMillis,
      titleKey: remembered.titleKey,
    );
    recorded = entry;
    unawaited(entry);
  }
}
