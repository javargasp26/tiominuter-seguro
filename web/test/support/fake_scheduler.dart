import 'package:tiominuter_web/domain/sessions.dart';

/// A virtual-time [SessionScheduler], ordered like kotlinx-coroutines' test scheduler: by due
/// time, then by the order things were scheduled. [now] is the virtual time (milliseconds).
class FakeScheduler implements SessionScheduler {
  int now = 0;
  int _sequence = 0;
  final List<_Task> _tasks = [];

  @override
  void post(void Function() callback) => _add(now, callback);

  @override
  Cancellable after(int millis, void Function() callback) => _add(now + millis, callback);

  _Task _add(int due, void Function() callback) {
    final task = _Task(due, _sequence++, callback);
    _tasks.add(task);
    return task;
  }

  /// Runs everything due now (Kotlin's `runCurrent`).
  void runCurrent() => advance(0);

  /// Moves the time by [millis], running every task due up to and including the new time, in order
  /// (Kotlin's `advanceTimeBy` followed by `runCurrent`).
  void advance(int millis) {
    final target = now + millis;
    while (true) {
      _tasks.removeWhere((task) => task.cancelled);
      if (_tasks.isEmpty) break;
      _tasks.sort((a, b) => a.due != b.due ? a.due.compareTo(b.due) : a.sequence.compareTo(b.sequence));
      final next = _tasks.first;
      if (next.due > target) break;
      _tasks.removeAt(0);
      now = next.due;
      next.callback();
    }
    now = target;
  }
}

class _Task implements Cancellable {
  _Task(this.due, this.sequence, this.callback);

  final int due;
  final int sequence;
  final void Function() callback;
  bool cancelled = false;

  @override
  void cancel() => cancelled = true;
}
