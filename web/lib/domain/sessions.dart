/// Workout sessions, ported from Android's `domain/session/` (`Session.kt`: [SessionRules],
/// the 2-hour timeout; `ActiveSession.kt`: [ActiveSessionTracker], [ActiveTimeMeter];
/// `ActiveWorkoutSession.kt`: [ActiveWorkoutSession]) and
/// `domain/program/repository/ProgramStrengthSessions.kt` ([StrengthSessions]). Checked against
/// shared/golden/sessions.json with a fake scheduler, the same event sequences as Android's
/// virtual-time test.
///
/// Web additions, outside the golden: [ActiveWorkoutSession.resume] and [ActiveTimeMeter.seed] (a
/// workout survives a page reload). Differences that don't change a result: the web is single-threaded, so no lock is needed and
/// the stores are synchronous (the browser's localStorage); recording an abandoned session queues
/// a Firestore write the caller doesn't wait for (the offline cache keeps it).
library;

/// A session closes on its own after this long without activity: 2 hours (milliseconds).
const int sessionInactivityTimeoutMillis = 2 * 60 * 60 * 1000;

/// Wall-clock time in epoch milliseconds (stored timestamps).
typedef WallClock = int Function();

/// Monotonic milliseconds (countdowns and active time); only differences matter.
typedef MonotonicClock = int Function();

abstract final class SessionRules {
  /// More than the timeout since [lastActivityAt]; never when the clock moved back.
  static bool isInactive(int lastActivityAt, int now) =>
      now >= lastActivityAt && now - lastActivityAt > sessionInactivityTimeoutMillis;

  /// [startedAt] → [endedAt] in whole seconds, never negative.
  static int durationSeconds(int startedAt, int endedAt) {
    final millis = endedAt - startedAt;
    return (millis < 0 ? 0 : millis) ~/ 1000;
  }

  /// The latest run of [times] with no gap over the timeout, as (first, last); null without times.
  static (int, int)? latestRun(List<int> times) {
    if (times.isEmpty) return null;
    final sorted = [...times]..sort();
    var start = sorted.last;
    for (var index = sorted.length - 2; index >= 0; index--) {
      if (start - sorted[index] > sessionInactivityTimeoutMillis) break;
      start = sorted[index];
    }
    return (start, sorted.last);
  }
}

/// A session the app closed for inactivity, with the duration it recorded.
class ClosedSession {
  const ClosedSession(this.durationSeconds);

  final int durationSeconds;
}

// ---------------------------------------------------------------------------
// Active time (daily, program cardio)
// ---------------------------------------------------------------------------

/// A workout measured in active time: [key] is its training-log refId ("daily:3",
/// `program:<uuid>:2:1`), [startedAt] and [lastActivityAt] are wall-clock millis.
class ActiveSession {
  const ActiveSession({
    required this.key,
    required this.startedAt,
    required this.activeSeconds,
    required this.lastActivityAt,
  });

  final String key;
  final int startedAt;
  final int activeSeconds;
  final int lastActivityAt;

  ActiveSession copyWith({int? activeSeconds, int? lastActivityAt}) => ActiveSession(
    key: key,
    startedAt: startedAt,
    activeSeconds: activeSeconds ?? this.activeSeconds,
    lastActivityAt: lastActivityAt ?? this.lastActivityAt,
  );

  Map<String, Object?> toJson() => {
    'key': key,
    'startedAt': startedAt,
    'activeSeconds': activeSeconds,
    'lastActivityAt': lastActivityAt,
  };

  /// Null for anything that isn't a stored session (a corrupt or foreign value).
  static ActiveSession? fromJson(Object? json) {
    if (json is! Map) return null;
    final key = json['key'];
    final startedAt = json['startedAt'];
    final activeSeconds = json['activeSeconds'];
    final lastActivityAt = json['lastActivityAt'];
    if (key is! String || startedAt is! int || activeSeconds is! int || lastActivityAt is! int) return null;
    return ActiveSession(key: key, startedAt: startedAt, activeSeconds: activeSeconds, lastActivityAt: lastActivityAt);
  }
}

/// Storage of one kind's active session and how an abandoned one is recorded (one entry with its
/// active time, completed at its last activity, dated on its start).
abstract interface class ActiveSessionStore {
  ActiveSession? get();
  void save(ActiveSession session);
  void delete();
  void recordAbandoned(ActiveSession session);
}

/// The persisted active session of one kind (Android's `ActiveSessionTracker`).
class ActiveSessionTracker {
  ActiveSessionTracker(this._store, this._clock);

  final ActiveSessionStore _store;
  final WallClock _clock;
  final List<ClosedSession> _notices = [];

  /// A workout of this kind starts: an open session (of any workout of this kind) is recorded
  /// first, then a new one starts now with no active time.
  bool start(String key) {
    final current = _store.get();
    if (current != null) _close(current);
    final now = _clock();
    _store.save(ActiveSession(key: key, startedAt: now, activeSeconds: 0, lastActivityAt: now));
    return true;
  }

  /// Saves [activeSeconds] of [key] as of now. False when that session no longer goes on (closed
  /// meanwhile, another workout's, or inactive too long: recorded now); then a new one starts now
  /// with no active time and the caller restarts its count.
  bool update(String key, int activeSeconds) {
    final now = _clock();
    final current = _store.get();
    if (current != null && current.key == key && !SessionRules.isInactive(current.lastActivityAt, now)) {
      _store.save(current.copyWith(activeSeconds: activeSeconds < 0 ? 0 : activeSeconds, lastActivityAt: now));
      return true;
    }
    if (current != null) _close(current);
    _store.save(ActiveSession(key: key, startedAt: now, activeSeconds: 0, lastActivityAt: now));
    return false;
  }

  /// The workout finished normally: its session ends without being recorded here.
  void finish(String key) {
    if (_store.get()?.key == key) _store.delete();
  }

  /// Web only: the stored session (to resume it after a page reload).
  ActiveSession? get storedSession => _store.get();

  /// Records the session if it has been inactive for more than the timeout.
  void closeInactive() {
    final current = _store.get();
    if (current != null && SessionRules.isInactive(current.lastActivityAt, _clock())) _close(current);
  }

  /// Sessions closed (for inactivity or replaced) not announced yet; each one only once.
  List<ClosedSession> takeClosedSessions() {
    final taken = List.of(_notices);
    _notices.clear();
    return taken;
  }

  void _close(ActiveSession session) {
    _store.delete();
    _store.recordAbandoned(session);
    _notices.add(ClosedSession(session.activeSeconds));
  }
}

/// Active time measured with a monotonic clock (Android's `ActiveTimeMeter`): counts while the
/// timer runs (also in the background), or while an untimed step is on screen and visible.
class ActiveTimeMeter {
  ActiveTimeMeter(this._time);

  final MonotonicClock _time;
  int _accumulatedMillis = 0;
  int? _countingSince;
  bool _timerRunning = false;
  bool _activeOnScreen = false;
  bool _visible = true;

  bool get isCounting => _countingSince != null;

  int get seconds {
    final since = _countingSince;
    return (_accumulatedMillis + (since == null ? 0 : _time() - since)) ~/ 1000;
  }

  void setTimerRunning(bool running) {
    _timerRunning = running;
    _refresh();
  }

  void setActiveOnScreen(bool active) {
    _activeOnScreen = active;
    _refresh();
  }

  void setVisible(bool visible) {
    _visible = visible;
    _refresh();
  }

  /// Web only (a page reload): starts from [seconds] already counted by the stored session.
  void seed(int seconds) {
    _accumulatedMillis = seconds * 1000;
    if (_countingSince != null) _countingSince = _time();
  }

  /// Back to 0 (a new session started); keeps counting if it was.
  void reset() {
    _accumulatedMillis = 0;
    if (_countingSince != null) _countingSince = _time();
  }

  void _refresh() {
    final shouldCount = _timerRunning || (_activeOnScreen && _visible);
    final since = _countingSince;
    if (shouldCount && since == null) {
      _countingSince = _time();
    } else if (!shouldCount && since != null) {
      _accumulatedMillis += _time() - since;
      _countingSince = null;
    }
  }
}

/// Something that can be cancelled (a scheduled callback).
abstract interface class Cancellable {
  void cancel();
}

/// Where [ActiveWorkoutSession] runs its deferred work: [post] runs a callback after the current
/// event (Android's `scope.launch`), [after] after a delay (`delay`). In the app: microtasks and
/// timers; in tests: a fake clock.
abstract interface class SessionScheduler {
  void post(void Function() callback);
  Cancellable after(int millis, void Function() callback);
}

/// The active session of the workout a screen shows (Android's `ActiveWorkoutSession`): its meter
/// plus the persisted session; the active time is saved every [checkpointMillis] while counting and
/// on every change. Stored from [start] (the first real action); [finish] ends it once.
class ActiveWorkoutSession {
  ActiveWorkoutSession(this._scheduler, this._tracker, this._meter, this.key);

  /// How often the active time is saved while it counts.
  static const int checkpointMillis = 5000;

  final SessionScheduler _scheduler;
  final ActiveSessionTracker _tracker;
  final ActiveTimeMeter _meter;
  final String key;

  bool _started = false;
  bool _finished = false;
  _Ticker? _ticker;

  bool get isStarted => _started;

  int get activeSeconds => _meter.seconds;

  /// Stores the session (an open one of this kind is recorded first) with the active time so far;
  /// returns the sessions closed meanwhile, to announce. Only the first call does anything.
  List<ClosedSession> start() {
    if (_started || _finished) return const [];
    _started = true;
    _tracker.start(key);
    _checkpoint();
    _refreshTicker();
    return _tracker.takeClosedSessions();
  }

  void setTimerRunning(bool running) {
    _meter.setTimerRunning(running);
    _changed();
  }

  void setActiveOnScreen(bool active) {
    _meter.setActiveOnScreen(active);
    _changed();
  }

  void setVisible(bool visible) {
    _meter.setVisible(visible);
    _changed();
  }

  /// Any action in the workout counts as activity.
  void onUserAction() => _changed();

  /// Web only: the page was reloaded while this workout's session was open. When the stored session
  /// is this workout's and still active (not inactive for 2 hours), it goes on with its saved active
  /// seconds instead of being recorded and restarted (Android, after a process death, records it
  /// when the workout starts again). True when resumed.
  bool resume(ActiveSession? stored, int now) {
    if (_started || _finished || stored == null || stored.key != key) return false;
    if (SessionRules.isInactive(stored.lastActivityAt, now)) return false;
    _meter.seed(stored.activeSeconds);
    _started = true;
    _checkpoint();
    _refreshTicker();
    return true;
  }

  /// The workout finished normally: the session ends; returns the active seconds to record.
  int finish() {
    _meter
      ..setTimerRunning(false)
      ..setActiveOnScreen(false);
    _finished = true;
    _stopTicker();
    if (_started) _tracker.finish(key);
    return _meter.seconds;
  }

  /// The screen goes away: stop saving (the last change already saved the active time).
  void close() => _stopTicker();

  void _changed() {
    _refreshTicker();
    if (_started && !_finished) _scheduler.post(_checkpoint);
  }

  void _checkpoint() {
    if (!_started || _finished) return;
    // The old session was closed (inactive): this one starts from now
    if (!_tracker.update(key, _meter.seconds)) _meter.reset();
  }

  /// Saves every [checkpointMillis] while counting; stops when the active time didn't move since
  /// the last save and restarts on the next change.
  void _refreshTicker() {
    if (_started && !_finished && _meter.isCounting) {
      if (_ticker?.active != true) _ticker = _Ticker(this)..launch();
    } else {
      _stopTicker();
    }
  }

  void _stopTicker() {
    _ticker?.cancel();
    _ticker = null;
  }
}

/// Android's ticker coroutine: `saved = -1; while (seconds != saved) { saved = seconds;
/// delay(5 s); checkpoint() }`.
class _Ticker {
  _Ticker(this._session);

  final ActiveWorkoutSession _session;
  bool active = true;
  int _saved = -1;
  Cancellable? _pending;

  void launch() => _session._scheduler.post(_loop);

  void _loop() {
    if (!active) return;
    final seconds = _session._meter.seconds;
    if (seconds == _saved) {
      active = false;
      return;
    }
    _saved = seconds;
    _pending = _session._scheduler.after(ActiveWorkoutSession.checkpointMillis, () {
      if (!active) return;
      _session._checkpoint();
      _loop();
    });
  }

  void cancel() {
    active = false;
    _pending?.cancel();
  }
}

// ---------------------------------------------------------------------------
// Set to set (program strength)
// ---------------------------------------------------------------------------

/// The open session of a strength workout: [startedAt] is its first completed set and
/// [lastActivityAt] the latest one.
class StrengthSession {
  const StrengthSession({
    required this.programId,
    required this.week,
    required this.dayIndex,
    required this.startedAt,
    required this.lastActivityAt,
  });

  final String programId;
  final int week;
  final int dayIndex;
  final int startedAt;
  final int lastActivityAt;

  /// First set → last set, never negative.
  int get durationSeconds => SessionRules.durationSeconds(startedAt, lastActivityAt);

  Map<String, Object?> toJson() => {
    'programId': programId,
    'week': week,
    'dayIndex': dayIndex,
    'startedAt': startedAt,
    'lastActivityAt': lastActivityAt,
  };

  static StrengthSession? fromJson(Object? json) {
    if (json is! Map) return null;
    final programId = json['programId'];
    final week = json['week'];
    final dayIndex = json['dayIndex'];
    final startedAt = json['startedAt'];
    final last = json['lastActivityAt'];
    if (programId is! String || week is! int || dayIndex is! int || startedAt is! int || last is! int) return null;
    return StrengthSession(
      programId: programId,
      week: week,
      dayIndex: dayIndex,
      startedAt: startedAt,
      lastActivityAt: last,
    );
  }
}

abstract interface class StrengthSessionStore {
  StrengthSession? get(String programId, int week, int dayIndex);
  List<StrengthSession> getAll();
  void save(StrengthSession session);
  void delete(String programId, int week, int dayIndex);

  /// One PROGRAM entry with the real duration, completed at the last set, dated on the start.
  void recordAbandoned(StrengthSession session);
}

/// Strength workout sessions (Android's `ProgramStrengthSessions`): every completed set extends the
/// workout's session; after 2 hours without a set it is closed lazily as one entry. The workout is
/// left resumable.
class StrengthSessions {
  StrengthSessions(this._store, this._clock);

  final StrengthSessionStore _store;
  final WallClock _clock;
  final List<ClosedSession> _notices = [];

  /// A set was completed now: an inactive session is closed first, then this set extends or starts one.
  void onSetCompleted(String programId, int week, int dayIndex) {
    final now = _clock();
    final current = _store.get(programId, week, dayIndex);
    StrengthSession? open = current;
    if (current != null && SessionRules.isInactive(current.lastActivityAt, now)) {
      _close(current);
      open = null;
    }
    final startedAt = open == null ? now : (open.startedAt < now ? open.startedAt : now);
    _store.save(
      StrengthSession(programId: programId, week: week, dayIndex: dayIndex, startedAt: startedAt, lastActivityAt: now),
    );
  }

  /// The session to record when the workout is completed now; removed from the store (Android's
  /// `markSlotCompleted` takes it in its own transaction). Null when there is none.
  StrengthSession? take(String programId, int week, int dayIndex) {
    final current = _store.get(programId, week, dayIndex);
    if (current != null) _store.delete(programId, week, dayIndex);
    return current;
  }

  /// Closes every strength session inactive for more than 2 hours.
  void closeInactive() {
    final now = _clock();
    for (final session in _store.getAll()) {
      if (SessionRules.isInactive(session.lastActivityAt, now)) _close(session);
    }
  }

  List<ClosedSession> takeClosedSessions() {
    final taken = List.of(_notices);
    _notices.clear();
    return taken;
  }

  void _close(StrengthSession session) {
    _store.delete(session.programId, session.week, session.dayIndex);
    _store.recordAbandoned(session);
    _notices.add(ClosedSession(session.durationSeconds));
  }
}
