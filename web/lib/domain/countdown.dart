/// One countdown at a time (Android's `ui/program/common/ProgramCountdown.kt`): the remaining time
/// comes from an end timestamp of a monotonic clock, so it stays right after time in the background;
/// the next tick is scheduled for when the displayed second changes. Starting a new one cancels the
/// previous one. Used by every timed step of training on the web.
library;

import 'sessions.dart';

class Countdown {
  Countdown(this._scheduler, this._time);

  /// Android's `GET_READY_SECONDS`.
  static const int getReadySeconds = 5;

  final SessionScheduler _scheduler;
  final MonotonicClock _time;
  Cancellable? _pending;
  int _generation = 0;

  bool get isRunning => _pending != null;

  /// Calls [onTick] with the remaining whole seconds (rounded up), starting with [durationSeconds],
  /// then [onFinished] once at zero.
  void start(int durationSeconds, {required void Function(int remaining) onTick, required void Function() onFinished}) {
    cancel();
    final generation = ++_generation;
    onTick(durationSeconds);
    final endAt = _time() + durationSeconds * 1000;
    void step() {
      if (generation != _generation) return;
      final remainingMillis = endAt - _time();
      if (remainingMillis <= 0) {
        // Detach first: onFinished usually starts the next countdown
        _pending = null;
        onFinished();
        return;
      }
      final remainingSeconds = (remainingMillis + 999) ~/ 1000;
      onTick(remainingSeconds);
      _pending = _scheduler.after(remainingMillis - (remainingSeconds - 1) * 1000, step);
    }

    _pending = _scheduler.after(0, step);
  }

  void cancel() {
    _generation++;
    _pending?.cancel();
    _pending = null;
  }
}
