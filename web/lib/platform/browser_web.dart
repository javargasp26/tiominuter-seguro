import 'dart:async';
import 'dart:js_interop';
import 'dart:js_interop_unsafe';

import 'package:web/web.dart' as web;

import 'browser.dart';

KeyValueStore localStore() => _LocalStorage(() => web.window.localStorage);

KeyValueStore sessionStore() => _LocalStorage(() => web.window.sessionStorage);

WakeLockControl wakeLock() => _ScreenWakeLock();

WorkoutFeedback feedback() => _BrowserFeedback();

void reload() => web.window.location.reload();

bool prefersReducedMotion() {
  try {
    return web.window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } on Object {
    return false;
  }
}

/// `window.localStorage`, every access in try/catch (private mode, blocked storage, quota).
/// localStorage or sessionStorage (blocked or full storage falls back to memory for the page).
class _LocalStorage implements KeyValueStore {
  _LocalStorage(this._storage);

  final web.Storage Function() _storage;
  final MemoryKeyValueStore _fallback = MemoryKeyValueStore();

  @override
  String? read(String key) {
    try {
      return _storage().getItem(key);
    } on Object {
      return _fallback.read(key);
    }
  }

  @override
  bool write(String key, String value) {
    try {
      _storage().setItem(key, value);
      return true;
    } on Object {
      // Kept for this page load only
      _fallback.write(key, value);
      return false;
    }
  }

  @override
  void remove(String key) {
    try {
      _storage().removeItem(key);
    } on Object {
      _fallback.remove(key);
    }
  }
}

/// The Screen Wake Lock API; unsupported or refused (a hidden tab, battery saver) is fine. The
/// browser releases it when the tab is hidden, so it is asked again when the workout shows.
class _ScreenWakeLock implements WakeLockControl {
  web.WakeLockSentinel? _sentinel;

  @override
  Future<void> acquire() async {
    if (_sentinel != null) return;
    try {
      _sentinel = await web.window.navigator.wakeLock.request('screen').toDart;
    } on Object {
      _sentinel = null;
    }
  }

  @override
  Future<void> release() async {
    final sentinel = _sentinel;
    _sentinel = null;
    try {
      await sentinel?.release().toDart;
    } on Object {
      // Already released by the browser
    }
  }
}

/// A short beep (Web Audio) and a vibration where the browser allows them.
class _BrowserFeedback implements WorkoutFeedback {
  web.AudioContext? _audio;

  @override
  void prepare() {
    try {
      _audio ??= web.AudioContext();
      unawaited(_audio!.resume().toDart.then((_) {}, onError: (_) {}));
    } on Object {
      _audio = null;
    }
  }

  @override
  void playCompletionSound() {
    final audio = _audio;
    if (audio == null) return;
    try {
      final oscillator = audio.createOscillator();
      final gain = audio.createGain();
      oscillator.type = 'sine';
      oscillator.frequency.value = 880;
      gain.gain.value = 0.2;
      oscillator.connect(gain);
      gain.connect(audio.destination);
      final now = audio.currentTime;
      oscillator.start(now);
      oscillator.stop(now + 0.25);
    } on Object {
      // Audio not allowed: silent
    }
  }

  @override
  void vibrate() {
    try {
      web.window.navigator.vibrate(300.toJS);
    } on Object {
      // No vibration on this device
    }
  }
}

bool isOnline() {
  try {
    return web.window.navigator.onLine;
  } on Object {
    return true;
  }
}

Future<ShareResult> share(String text, String? url) async {
  final navigator = web.window.navigator;
  if (!(navigator as JSObject).has('share')) return ShareResult.unsupported;
  final data = web.ShareData(text: text, url: url ?? '');
  try {
    if ((navigator as JSObject).has('canShare') && !navigator.canShare(data)) return ShareResult.unsupported;
    await navigator.share(data).toDart;
    return ShareResult.shared;
  } on Object catch (e) {
    // AbortError: the person closed the sheet; anything else: copy instead
    // A DOMException from JS ("AbortError: …" when the sheet was closed)
    return '$e'.contains('AbortError') ? ShareResult.cancelled : ShareResult.unsupported;
  }
}
