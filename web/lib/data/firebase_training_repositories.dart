/// The Firebase implementations of `training_repositories.dart`. Writes are the documents of
/// `write_mappers.dart` ([serverTimestamp] → `FieldValue.serverTimestamp()`); reads are the ones
/// documented in docs/firestore-model.md "The web client". With the persistent cache, writes are
/// queued offline and sent later; [PendingWrites] counts them. Logs carry the operation and the
/// error code only.
library;

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/foundation.dart';

import '../domain/daily_progress.dart';
import '../domain/program.dart';
import '../domain/training.dart';
import '../domain/weekly_progress.dart';
import 'firestore_mappers.dart';
import 'repositories.dart';
import 'training_repositories.dart';
import 'write_mappers.dart';

void _log(String operation, Object error) {
  final code = error is FirebaseException ? error.code : error.runtimeType.toString();
  debugPrint('Firestore: $operation failed ($code)');
}

DataError _errorOf(Object error) => switch (error is FirebaseException ? error.code : null) {
  'unavailable' || 'deadline-exceeded' => DataError.network,
  'permission-denied' || 'unauthenticated' => DataError.permissionDenied,
  'not-found' => DataError.notFound,
  _ => DataError.failed,
};

Future<T> _read<T>(String operation, Future<T> Function() block) async {
  try {
    return await block();
  } on Object catch (e) {
    _log(operation, e);
    throw DataException(_errorOf(e));
  }
}

/// [fields] with [serverTimestamp] replaced by the SDK's sentinel, at any depth.
Map<String, Object?> toFirestore(Fields fields) => {
  for (final MapEntry(:key, :value) in fields.entries) key: _value(value),
};

Object? _value(Object? value) => switch (value) {
  ServerTimestamp() => FieldValue.serverTimestamp(),
  // Bytes (a post's thumbnail) are a Firestore Blob, as Android's
  final Uint8List bytes => Blob(bytes),
  final Map<String, Object?> map => toFirestore(map),
  final List<Object?> list => [for (final item in list) _value(item)],
  _ => value,
};

class FirestoreTrainingRepository implements TrainingRepository {
  FirestoreTrainingRepository(this._firestore, this._pending);

  final FirebaseFirestore _firestore;
  final PendingWrites _pending;

  CollectionReference<Map<String, dynamic>> _trainingLog(String uid) => _firestore.collection('users/$uid/trainingLog');

  DocumentReference<Map<String, dynamic>> _program(String uid, String programId) =>
      _firestore.doc('users/$uid/programs/$programId');

  /// A write: counted as pending until acknowledged; a failure is logged and rethrown (the caller
  /// usually doesn't wait for it).
  Future<void> _write(String operation, Future<void> Function() block) =>
      _pending.track(Future(block)).catchError((Object e) {
        _log(operation, e);
        throw DataException(_errorOf(e));
      });

  @override
  Future<void> saveEntry(String uid, TrainingEntryDoc entry) => _write(
    'save entry',
    () => _trainingLog(uid).doc(entry.entryId).set(toFirestore(WriteMappers.trainingEntry(entry))),
  );

  @override
  Future<void> deleteEntry(String uid, String entryId) => _write(
    'delete entry',
    () => _trainingLog(uid).doc(entryId).set(toFirestore(WriteMappers.tombstone('entryId', entryId))),
  );

  @override
  Future<TrainingEntryDoc?> entry(String uid, String entryId) => _read('read entry', () async {
    final doc = await _trainingLog(uid).doc(entryId).get();
    final data = doc.data();
    return data == null ? null : FirestoreMappers.trainingEntryDoc(doc.id, data);
  });

  @override
  Future<void> setGoal(String uid, String weekStart, int workoutsPerWeek) => _write(
    'set goal',
    () => _firestore
        .doc('users/$uid/weeklyGoals/$weekStart')
        .set(toFirestore(WriteMappers.weeklyGoal(weekStart, workoutsPerWeek))),
  );

  @override
  Future<List<WeeklyGoal>> goals(String uid) => _read('read goals', () async {
    final snapshot = await _firestore.collection('users/$uid/weeklyGoals').get();
    return [for (final doc in snapshot.docs) ?FirestoreMappers.weeklyGoal(doc.id, doc.data())];
  });

  List<TrainingEntry> _entries(QuerySnapshot<Map<String, dynamic>> snapshot) => [
    for (final doc in snapshot.docs) ?FirestoreMappers.trainingEntry(doc.id, doc.data()),
  ];

  @override
  Future<List<TrainingEntry>> entriesFrom(String uid, String fromDate) => _read(
    'read entries',
    () async => _entries(await _trainingLog(uid).where('dateLocal', isGreaterThanOrEqualTo: fromDate).get()),
  );

  @override
  Future<List<TrainingEntry>> entriesBetween(String uid, String fromDate, String untilDate) => _read(
    'read past entries',
    () async => _entries(
      await _trainingLog(uid)
          .where('dateLocal', isGreaterThanOrEqualTo: fromDate)
          .where('dateLocal', isLessThan: untilDate)
          .get(),
    ),
  );

  // One field (dateLocal) for the filter and the order: an automatic single-field index
  @override
  Future<String?> lastCountedDateBefore(String uid, String beforeDate, {int maxPages = 4}) =>
      _read('read last trained', () async {
        Query<Map<String, dynamic>> query = _trainingLog(uid)
            .where('dateLocal', isLessThan: beforeDate)
            .orderBy('dateLocal', descending: true)
            .limit(25);
        for (var page = 0; page < maxPages; page++) {
          final snapshot = await query.get();
          for (final entry in _entries(snapshot)) {
            if (countsTowardGoal(entry)) return entry.dateLocal;
          }
          if (snapshot.docs.length < 25) return null;
          query = query.startAfterDocument(snapshot.docs.last);
        }
        return null;
      });

  DocumentReference<Map<String, dynamic>> _dailyProgress(String uid) =>
      _firestore.doc('users/$uid/dailyProgress/state');

  @override
  Future<DailyProgress?> dailyProgress(String uid) => _read('read daily progress', () async {
    final data = (await _dailyProgress(uid).get()).data();
    return data == null ? null : FirestoreMappers.dailyProgress(data);
  });

  // One field (source) for the filter: an automatic single-field index
  @override
  Future<List<({TrainingSource source, String? refId})>> dailyHistory(String uid) => _read(
    'read daily history',
    () async {
      final snapshot = await _trainingLog(uid).where('source', isEqualTo: TrainingSource.daily.code).get();
      return [
        for (final doc in snapshot.docs)
          if (doc.data()['deleted'] != true)
            (source: TrainingSource.daily, refId: doc.data()['refId'] is String ? doc.data()['refId'] as String : null),
      ];
    },
  );

  @override
  Future<DailyProgress> updateDailyProgress(String uid, DailyProgress Function(DailyProgress? current) change) async {
    final ref = _dailyProgress(uid);
    try {
      return await _pending.track(
        _firestore.runTransaction((transaction) async {
          final data = (await transaction.get(ref)).data();
          final current = data == null ? null : FirestoreMappers.dailyProgress(data);
          final next = change(current);
          if (next != current) transaction.set(ref, toFirestore(WriteMappers.dailyProgress(next)));
          return next;
        }),
      );
    } on FirebaseException catch (e) {
      // Transactions need the server: offline, the change applies to the cached state and is queued
      if (e.code != 'unavailable') {
        _log('update daily progress', e);
        throw DataException(_errorOf(e));
      }
      final cached = await _read('read daily progress', () async {
        final data = (await ref.get(const GetOptions(source: Source.cache))).data();
        return data == null ? null : FirestoreMappers.dailyProgress(data);
      }).catchError((Object _) => null);
      final next = change(cached);
      if (next != cached) {
        _write('update daily progress', () => ref.set(toFirestore(WriteMappers.dailyProgress(next)))).ignore();
      }
      return next;
    }
  }

  @override
  Future<void> createProgram(
    String uid,
    String programId,
    ProgramProfile profile,
    GeneratedProgram program,
    int createdAt,
  ) => _write('create program', () {
    final batch = _firestore.batch()
      ..set(_program(uid, programId), toFirestore(WriteMappers.program(programId, profile, createdAt)));
    for (final week in program.weeks) {
      for (var day = 0; day < week.workouts.length; day++) {
        batch.set(
          _program(uid, programId).collection('workouts').doc(workoutDocId(week.weekNumber, day)),
          toFirestore(WriteMappers.programWorkout(week.weekNumber, day, week.workouts[day], isDeload: week.isDeload)),
        );
      }
    }
    return batch.commit();
  });

  @override
  Future<void> completeProgramWorkout(String uid, String programId, int week, int dayIndex, int completedAt) => _write(
    'complete program workout',
    () => _program(uid, programId)
        .collection('completions')
        .doc(workoutDocId(week, dayIndex))
        .set(toFirestore(WriteMappers.programCompletion(week, dayIndex, completedAt))),
  );

  @override
  Future<List<ProgramSummary>> programs(String uid) => _read('read programs', () async {
    final snapshot = await _firestore.collection('users/$uid/programs').get();
    final programs = <ProgramSummary>[];
    for (final doc in snapshot.docs) {
      final program = FirestoreMappers.program(doc.data());
      if (program == null) continue;
      // An aggregation: one read per 1000 completions
      final count = await doc.reference.collection('completions').where('deleted', isEqualTo: false).count().get();
      programs.add(
        ProgramSummary(
          programId: doc.id,
          profile: program.profile,
          createdAt: program.createdAt,
          completedWorkouts: count.count ?? 0,
        ),
      );
    }
    programs.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return programs;
  });

  @override
  Future<ProgramDetail?> program(String uid, String programId) => _read('read program', () async {
    final doc = await _program(uid, programId).get();
    final data = doc.data();
    final program = data == null ? null : FirestoreMappers.program(data);
    if (program == null) return null;
    final workouts = await doc.reference.collection('workouts').get();
    final completions = await doc.reference.collection('completions').get();
    final completedAt = <String, int>{
      for (final c in completions.docs)
        if (FirestoreMappers.programCompletion(c.data()) case final completion?)
          workoutDocId(completion.week, completion.dayIndex): completion.completedAt,
    };
    final completed = completedAt.length;
    final rows = [
      for (final w in workouts.docs)
        if (FirestoreMappers.programWorkout(w.data()) case final row?)
          ProgramWorkoutRow(
            week: row.week,
            dayIndex: row.dayIndex,
            isDeload: row.isDeload,
            workout: row.workout,
            completedAt: completedAt[workoutDocId(row.week, row.dayIndex)],
          ),
    ]..sort((a, b) => a.week != b.week ? a.week.compareTo(b.week) : a.dayIndex.compareTo(b.dayIndex));
    return ProgramDetail(
      summary: ProgramSummary(
        programId: programId,
        profile: program.profile,
        createdAt: program.createdAt,
        completedWorkouts: completed,
      ),
      workouts: rows,
    );
  });
}

class FirestoreSharingRepository implements SharingRepository {
  FirestoreSharingRepository(this._firestore, this._pending);

  final FirebaseFirestore _firestore;
  final PendingWrites _pending;

  Future<void> _write(String operation, Future<void> Function() block) =>
      _pending.track(Future(block)).catchError((Object e) {
        _log(operation, e);
        throw DataException(_errorOf(e));
      });

  @override
  Future<List<String>> myGroupIds(String uid) => _read('read my groups', () async {
    final index = await _firestore.collection('users/$uid/groups').get();
    return [for (final doc in index.docs) ?FirestoreMappers.membershipGroupId(doc.id, doc.data())];
  });

  @override
  Future<void> updateMemberSummary(String groupId, String uid, Fields fields) =>
      _write('update summary', () => _firestore.doc('groups/$groupId/members/$uid').update(toFirestore(fields)));

  @override
  Future<void> createPost(String groupId, String postId, Fields fields) =>
      _write('create post', () => _firestore.doc('groups/$groupId/posts/$postId').set(toFirestore(fields)));

  @override
  Future<void> deletePost(String groupId, String postId) =>
      _write('delete post', () => _firestore.doc('groups/$groupId/posts/$postId').delete());

  @override
  Future<void> setPostThumb(String groupId, String postId, Uint8List? thumb) => _write(
    'set post thumbnail',
    () => _firestore.doc('groups/$groupId/posts/$postId').update({
      'thumb': thumb == null ? FieldValue.delete() : Blob(thumb),
    }),
  );
}
