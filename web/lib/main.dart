import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_web_plugins/url_strategy.dart';

import 'app/app.dart';
import 'app/clocks.dart';
import 'app/firebase_web_options.dart';
import 'app/groups_service.dart';
import 'app/services.dart';
import 'app/training_service.dart';
import 'app/training_texts.dart';
import 'data/content_repository.dart';
import 'data/firebase_repositories.dart';
import 'data/firebase_training_repositories.dart';
import 'data/training_repositories.dart';
import 'domain/calendar_dates.dart';
import 'platform/browser.dart';
import 'platform/photos.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Paths without "#" (Hosting rewrites every path except /content/** to index.html)
  usePathUrlStrategy();
  // authDomain = the page's own Hosting domain (see app/firebase_web_options.dart)
  await Firebase.initializeApp(options: webFirebaseOptions(Uri.base.host));
  final auth = FirebaseAuth.instance;
  final firestore = FirebaseFirestore.instance;
  if (AppConfig.useEmulators) {
    await auth.useAuthEmulator(AppConfig.emulatorHost, 9099);
    firestore.useFirestoreEmulator(AppConfig.emulatorHost, 8080);
  }
  // Training offline: the persistent web cache (IndexedDB, shared by the tabs) keeps writes made
  // offline and sends them later; sign-out clears it (a shared computer keeps nothing)
  firestore.settings = const Settings(
    persistenceEnabled: true,
    webPersistentTabManager: WebPersistentMultipleTabManager(),
  );
  final pending = PendingWrites()..watchQueue(firestore.waitForPendingWrites());
  final dates = LocalDates();
  final training = TrainingDeps(
    service: TrainingService(
      training: FirestoreTrainingRepository(firestore, pending),
      sharing: FirestoreSharingRepository(firestore, pending),
      store: Browser.localStorage(),
      dates: dates,
      texts: LocaleTrainingTexts(),
      photos: BrowserPhotos.store(),
    ),
    photoPicker: BrowserPhotos.picker(),
    imageCodec: BrowserPhotos.codec(),
    device: BrowserPhotos.device(),
    requestPersistence: BrowserPhotos.requestPersistence,
    pending: pending,
    feedback: Browser.feedback(),
    wakeLock: Browser.wakeLock(),
    clearOfflineCache: () async {
      try {
        await firestore.terminate();
        await firestore.clearPersistence();
      } on Object catch (e) {
        debugPrint('Firestore: cache not cleared (${e.runtimeType})');
      }
      Browser.reload();
    },
  );
  final groups = FirestoreGroupsRepository(firestore);
  final groupsService = GroupsService(
    repository: groups,
    training: training.service,
    store: Browser.localStorage(),
    dates: dates,
    wallClock: wallMillis,
  );
  runApp(
    TioMinuterWebApp(
      services: AppServices(
        auth: FirebaseAuthRepository(auth, tab: Browser.sessionStorage()),
        userData: FirestoreUserDataRepository(firestore),
        groups: groups,
        groupsService: groupsService,
        account: FirestoreAccountRepository(firestore),
        content: HttpContentRepository(const ContentLocation(AppConfig.contentBaseUrl)),
        dates: dates,
        trainingDeps: training,
      ),
    ),
  );
}
