import '../data/content_repository.dart';
import '../data/repositories.dart';
import '../data/training_repositories.dart';
import '../domain/calendar_dates.dart';
import '../domain/photos.dart';
import '../domain/sessions.dart';
import '../platform/browser.dart';
import '../platform/photos.dart';
import 'clocks.dart';
import 'groups_service.dart';
import 'training_service.dart';

/// Build-time settings (`--dart-define`), see web/README.md.
abstract final class AppConfig {
  /// `--dart-define=USE_EMULATORS=true`: Auth (9099) and Firestore (8080) emulators on [emulatorHost].
  static const bool useEmulators = bool.fromEnvironment('USE_EMULATORS');
  static const String emulatorHost = String.fromEnvironment('EMULATOR_HOST', defaultValue: 'localhost');

  /// Where /content/v1/ is served. Default: the same origin (Firebase Hosting). For `flutter run`,
  /// `--dart-define=CONTENT_BASE_URL=https://tiominuter-e43f7.web.app/content/v1/` (CORS allows it).
  static const String contentBaseUrl = String.fromEnvironment('CONTENT_BASE_URL', defaultValue: '/content/v1/');

  /// Exercise animations on the web: **ON by the product owner's decision, for the private test
  /// group only.** Most GIFs in content/v1/animations/ are NOT licensed for distribution yet (one
  /// carries a stock-photo watermark): this MUST be revisited before any public launch — the release
  /// checklist of branch chore/release-readiness (docs/release.md, "the GIFs shown must be
  /// licensed") is the guard. Off, `ExerciseVisual` shows the icon of the exercise type; no screen
  /// changes either way.
  static const bool showExerciseAnimations = true;
}

/// What the training screens need besides the repositories: the service, the browser (feedback,
/// wake lock), time (a scheduler, the monotonic and the wall clock) and the pending writes.
class TrainingDeps {
  TrainingDeps({
    required this.service,
    required this.pending,
    WorkoutFeedback? feedback,
    WakeLockControl? wakeLock,
    SessionScheduler? scheduler,
    MonotonicClock? monotonic,
    WallClock? wall,
    this.clearOfflineCache,
    PhotoPicker? photoPicker,
    ImageCodec? imageCodec,
    DeviceInfo? device,
    Future<void> Function()? requestPersistence,
  }) : photoPicker = photoPicker ?? const NoPhotoPicker(),
       photoProcessor = PhotoProcessor(imageCodec ?? BrowserPhotos.codec()),
       device = device ?? const DeviceInfo(userAgent: ''),
       requestPersistence = requestPersistence ?? _noPersistence,
       feedback = feedback ?? const NoFeedback(),
       wakeLock = wakeLock ?? const NoWakeLock(),
       scheduler = scheduler ?? const TimerScheduler(),
       monotonic = monotonic ?? monotonicMillis,
       wall = wall ?? wallMillis;

  final TrainingService service;
  final PendingWrites pending;
  final WorkoutFeedback feedback;
  final WakeLockControl wakeLock;
  final SessionScheduler scheduler;
  final MonotonicClock monotonic;
  final WallClock wall;

  /// Training photos: the file input (and drops), the processing in the browser, what the iOS hint
  /// needs, and asking the browser to keep the photos' storage.
  final PhotoPicker photoPicker;
  final PhotoProcessor photoProcessor;
  final DeviceInfo device;
  final Future<void> Function() requestPersistence;

  static Future<void> _noPersistence() async {}

  /// After a sign-out: Firestore's offline cache is cleared (a shared computer keeps nothing of the
  /// account) and the page reloads. Null in tests.
  final Future<void> Function()? clearOfflineCache;
}

/// Everything the screens use, created once (tests pass fakes).
class AppServices {
  const AppServices({
    required this.auth,
    required this.userData,
    required this.groups,
    required this.content,
    required this.dates,
    this.trainingDeps,
    this.groupsService,
    this.account,
  });

  final AuthRepository auth;
  final UserDataRepository userData;
  final GroupsRepository groups;
  final ContentRepository content;
  final LocalDates dates;

  /// Training (Phase 5b); null in tests that don't train.
  final TrainingDeps? trainingDeps;

  TrainingDeps get training => trainingDeps!;

  /// Managing groups and Delete account (null in tests that only read).
  final GroupsService? groupsService;
  final AccountRepository? account;
}
