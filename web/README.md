# Tio Minuter web

The Flutter web client: a signed-in person sees their week, their training history and their groups
(ranking and feed), sets their own reaction on posts (Phase 5a), and trains (Phase 5b): the daily
routines, logged activities, programs (strength, treadmill, circuit), the weekly goal and deleting
entries, with their member summary and feed posts. Gym routines, the outdoor run, photos, group
management and account deletion stay in the Android app. Exercise animations are off for now
(`AppConfig.showExerciseAnimations`). The rules of this folder are in the repository's
CLAUDE.md ("Web"); the architecture is in `.coda/docs/TECHNICAL_SUMMARY.md` §25.

- Flutter **3.47.7** stable / Dart **3.13.5** (pinned in `pubspec.yaml` and CI). Dependencies are
  exact versions; `pubspec.lock` is committed (`flutter pub get --enforce-lockfile` in CI).
- Firebase project `tiominuter-e43f7`, web app "Tio Minuter Web"
  (`1:897898729805:web:4194859198281db6162704`); `lib/firebase_options.dart` was generated with
  `flutterfire configure --project=tiominuter-e43f7 --platforms=web --out=lib/firebase_options.dart
  --web-app-id=1:897898729805:web:4194859198281db6162704` (run inside `web/`; `firebase.json` here
  is flutterfire's record of that). The web config is not a secret.

## Run it

```bash
cd web
flutter pub get

# Against the local emulators (nothing real is touched). In another terminal, from the repo root:
#   firebase emulators:start --only auth,firestore --project tiominuter-e43f7
# The Auth emulator's Google sign-in shows a page where you type any test account.
flutter run -d chrome \
  --dart-define=USE_EMULATORS=true \
  --dart-define=CONTENT_BASE_URL=https://tiominuter-e43f7.web.app/content/v1/

# Against the real project (your own Google account; localhost is an authorized domain)
flutter run -d chrome --dart-define=CONTENT_BASE_URL=https://tiominuter-e43f7.web.app/content/v1/
```

`CONTENT_BASE_URL` is only needed when the app isn't served by Hosting (the published content allows
any origin); deployed, the app reads `/content/v1/` from its own origin. `EMULATOR_HOST` (default
`localhost`) changes the emulators' host.

## Check it

```bash
cd web
flutter analyze --fatal-infos --fatal-warnings   # no issues
dart format --line-length 120 --output=none --set-exit-if-changed lib test
flutter test                                     # unit, bloc and widget tests (VM), incl. the parity goldens (test/parity/)
flutter build web --release
../scripts/test_web_emulator.sh                  # opt-in: Chrome + the Auth and Firestore emulators
../scripts/test_web_train_emulator.sh            # opt-in: web writes → Android (emulator-5554) → web reads
```

## Structure

```
lib/
  main.dart                 Firebase, the emulators (USE_EMULATORS), path URLs, the services
  app/                      the app, routes (go_router), theme, AppServices / AppConfig
  core/mvi.dart             MviBloc (intents → state + one-shot effects), EffectListener, VisibilityScope
  domain/                   pure rules ported from Android (dates, weekly goal, member summary, names,
                            ranking, feed, the program generator, sessions, daily routines, activities)
  data/                     Firestore mappers, repositories (interfaces + Firebase), content (manifest)
  platform/                 the browser for training: localStorage, the wake lock, a beep + vibration
  ui/<screen>/              one bloc + one page per screen (landing, week, train, history, groups, account, shell)
  l10n/                     app_en.arb, app_es.arb (gen-l10n; the generated Dart is committed)
test/                       domain, data and bloc tests, widget tests per screen; parity/ (the Kotlin goldens);
                            train/ (training); emulator/ (browser only)
assets/mascot.png           the Android app's mascot (landing page)
```
