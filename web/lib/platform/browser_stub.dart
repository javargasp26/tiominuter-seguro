import 'browser.dart';

KeyValueStore localStore() => MemoryKeyValueStore();

KeyValueStore sessionStore() => MemoryKeyValueStore();

WakeLockControl wakeLock() => const NoWakeLock();

WorkoutFeedback feedback() => const NoFeedback();

void reload() {}

bool prefersReducedMotion() => false;

bool isOnline() => true;

Future<ShareResult> share(String text, String? url) async => ShareResult.unsupported;
