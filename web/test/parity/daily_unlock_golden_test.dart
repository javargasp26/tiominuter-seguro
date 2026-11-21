import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/domain/daily_progress.dart';
import 'package:tiominuter_web/domain/training.dart';

import 'golden.dart';

/// shared/golden/daily_unlock.json: the unlock rules and the sync's merge give Android's results
/// (DailyUnlockGoldenTest.kt).
void main() {
  final golden = Golden.load('daily_unlock');

  DailyProgress? progressOf(Object? json) {
    if (json == null) return null;
    final map = json as Map;
    return DailyProgress(lastCompletedDay: map['lastCompletedDay'] as int, cycle: map['cycle'] as int);
  }

  Object? jsonOf(DailyProgress? progress) =>
      progress == null ? null : {'lastCompletedDay': progress.lastCompletedDay, 'cycle': progress.cycle};

  test('every daily unlock case gives the same result as Android', () {
    final mismatches = Mismatches();
    final cases = Golden.cases(golden);
    expect(cases, hasLength(golden['caseCount']));
    final kinds = <String>{};
    for (final item in cases) {
      final id = item['id']! as String;
      final kind = item['kind']! as String;
      kinds.add(kind);
      final input = item['input']! as Map;
      final output = item['output']! as Map;
      switch (kind) {
        case 'state':
          final progress = progressOf(input['progress'])!;
          final last = input['lastRoutineId'] as int?;
          final days = (input['days']! as List).cast<int>();
          mismatches
            ..check(
              id,
              'nextAvailableDay',
              DailyUnlockRules.nextAvailableDay(progress, last),
              output['nextAvailableDay'],
            )
            ..check(id, 'isCycleComplete', DailyUnlockRules.isCycleComplete(progress, last), output['isCycleComplete'])
            ..check(id, 'unlocked', [
              for (final d in days) DailyUnlockRules.isUnlocked(d, progress),
            ], output['unlocked'])
            ..check(id, 'completed', [
              for (final d in days) DailyUnlockRules.isCompleted(d, progress),
            ], output['completed'])
            ..check(id, 'afterFinishing', [
              for (final d in days) jsonOf(DailyUnlockRules.afterFinishing(progress, d)),
            ], output['afterFinishing'])
            ..check(id, 'restart', jsonOf(DailyUnlockRules.restart(progress)), output['restart']);
        case 'history':
          final entries = [
            for (final entry in (input['entries']! as List).cast<Map<Object?, Object?>>())
              (source: TrainingSource.fromCode(entry['source'])!, refId: entry['refId'] as String?),
          ];
          mismatches.check(id, 'fromHistory', jsonOf(DailyUnlockRules.fromHistory(entries)), output['progress']);
        case 'merge':
          final local = progressOf(input['local'])!;
          final remote = progressOf(input['remote']);
          final history = progressOf(input['history'])!;
          final outcome = DailyProgressSync.reconcile(local, remote, history);
          final reconciled = switch (outcome) {
            ApplyRemoteProgress(:final progress) => {'action': 'applyRemote', 'progress': jsonOf(progress)},
            PushLocalProgress(:final progress) => {'action': 'pushLocal', 'progress': jsonOf(progress)},
            ProgressInSync() => {'action': 'inSync', 'progress': null},
          };
          mismatches
            ..check(id, 'merge', remote == null ? null : jsonOf(DailyUnlockRules.merge(local, remote)), output['merge'])
            ..check(
              id,
              'compare',
              remote == null ? null : DailyUnlockRules.compare(local, remote).clamp(-1, 1),
              output['compare'],
            )
            ..check(id, 'reconcile', reconciled, output['reconcile']);
        default:
          fail('unknown kind $kind in $id');
      }
    }
    expect(kinds, {'state', 'history', 'merge'});
    expect(mismatches.count, 0, reason: mismatches.report());
  });
}
