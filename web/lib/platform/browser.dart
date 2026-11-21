/// What training needs from the browser, behind interfaces so blocs and tests never touch it:
/// [KeyValueStore] (localStorage), [WakeLockControl] (the screen wake lock) and [WorkoutFeedback]
/// (a beep and a vibration). [Browser.create] gives the browser's on the web and in-memory /
/// no-op ones elsewhere (VM tests). Every call tolerates failure: a full or blocked storage, a
/// refused wake lock, audio not allowed yet — training never depends on them.
library;

import 'browser_stub.dart' if (dart.library.js_interop) 'browser_web.dart' as impl;

abstract interface class KeyValueStore {
  /// Null when absent or unreadable.
  String? read(String key);

  /// False when it couldn't be stored.
  bool write(String key, String value);

  void remove(String key);
}

abstract interface class WakeLockControl {
  /// Keeps the screen on while a workout runs; a refusal is ignored.
  Future<void> acquire();

  Future<void> release();
}

/// Sound and vibration at the end of a timed step (Android's `WorkoutFeedback`). Browsers allow
/// audio only after a user gesture: [prepare] is called from one (Start, Get Ready).
abstract interface class WorkoutFeedback {
  void prepare();

  void playCompletionSound();

  void vibrate();
}

enum ShareResult { shared, cancelled, unsupported }

abstract final class Browser {
  static KeyValueStore localStorage() => impl.localStore();

  /// This tab's sessionStorage (it survives the sign-in redirect, not the tab).
  static KeyValueStore sessionStorage() => impl.sessionStore();

  static WakeLockControl wakeLock() => impl.wakeLock();

  static WorkoutFeedback feedback() => impl.feedback();

  /// `navigator.onLine`: false when the browser knows it's offline (true outside a browser).
  static bool isOnline() => impl.isOnline();

  /// The Web Share API (`navigator.share`: the iPhone and Android share sheets); [ShareResult.unsupported]
  /// where the browser has none (most desktops), so the caller copies instead.
  static Future<ShareResult> share({required String text, String? url}) => impl.share(text, url);

  /// `prefers-reduced-motion: reduce` (the OS / browser setting); false outside a browser.
  static bool prefersReducedMotion() => impl.prefersReducedMotion();

  /// Reloads the page (after a sign-out cleared the cache); a no-op outside a browser.
  static void reload() => impl.reload();
}

/// In memory (tests, and the fallback when localStorage is unavailable).
class MemoryKeyValueStore implements KeyValueStore {
  final Map<String, String> values = {};

  @override
  String? read(String key) => values[key];

  @override
  bool write(String key, String value) {
    values[key] = value;
    return true;
  }

  @override
  void remove(String key) => values.remove(key);
}

class NoWakeLock implements WakeLockControl {
  const NoWakeLock();

  @override
  Future<void> acquire() async {}

  @override
  Future<void> release() async {}
}

class NoFeedback implements WorkoutFeedback {
  const NoFeedback();

  @override
  void prepare() {}

  @override
  void playCompletionSound() {}

  @override
  void vibrate() {}
}
