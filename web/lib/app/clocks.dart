import 'dart:async';

import '../domain/sessions.dart';

/// The real [SessionScheduler]: [post] after the current event, [after] on a timer.
class TimerScheduler implements SessionScheduler {
  const TimerScheduler();

  @override
  void post(void Function() callback) => scheduleMicrotask(callback);

  @override
  Cancellable after(int millis, void Function() callback) =>
      _TimerHandle(Timer(Duration(milliseconds: millis), callback));
}

class _TimerHandle implements Cancellable {
  _TimerHandle(this._timer);

  final Timer _timer;

  @override
  void cancel() => _timer.cancel();
}

/// Monotonic milliseconds since the page loaded (Stopwatch: `performance.now()` in a browser).
final Stopwatch _sinceLoad = Stopwatch()..start();

int monotonicMillis() => _sinceLoad.elapsedMilliseconds;

int wallMillis() => DateTime.now().millisecondsSinceEpoch;
