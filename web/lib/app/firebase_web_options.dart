/// The web's Firebase options: the generated ones (lib/firebase_options.dart, rewritten by
/// `flutterfire configure`) with **authDomain = the app's own Hosting domain**, set here in code so a
/// regenerated file can't silently bring back `tiominuter-e43f7.firebaseapp.com`.
///
/// Why: the sign-in redirect (phones and tablets) ends on `https://<authDomain>/__/auth/handler`,
/// which stores the result for `getRedirectResult`. With authDomain on firebaseapp.com while the app
/// runs on web.app, that storage is cross-site, and Safari (ITP) and Chrome (storage partitioning)
/// hide it from the app: the page comes back signed out, on the landing page. Served from the same
/// site, the handler's storage is the app's own. Needs, in the Web OAuth client of Google Cloud:
/// the JavaScript origin `https://tiominuter-e43f7.web.app` and the redirect URI
/// `https://tiominuter-e43f7.web.app/__/auth/handler` (done).
library;

import 'package:firebase_core/firebase_core.dart';

import '../firebase_options.dart';

abstract final class WebAuthDomain {
  /// The Hosting site the app is published on.
  static const String hosting = 'tiominuter-e43f7.web.app';

  /// Hosting also serves the app here; signing in on this host uses it (same site again).
  static const String hostingAlias = 'tiominuter-e43f7.firebaseapp.com';

  /// `--dart-define=AUTH_DOMAIN=...` replaces the default for other hosts (a preview channel).
  static const String _override = String.fromEnvironment('AUTH_DOMAIN');

  /// The authDomain for a page served from [pageHost]: that host when it is one of the project's
  /// Hosting hosts, otherwise (localhost, `flutter run`) [hosting] — there the desktop popup works;
  /// a redirect from localhost isn't supported (the handler can't share localhost's storage).
  static String forHost(String pageHost) {
    if (_override.isNotEmpty) return _override;
    return pageHost == hosting || pageHost == hostingAlias ? pageHost : hosting;
  }
}

/// The options to initialize Firebase with in the browser.
FirebaseOptions webFirebaseOptions(String pageHost) =>
    DefaultFirebaseOptions.web.copyWith(authDomain: WebAuthDomain.forHost(pageHost));
