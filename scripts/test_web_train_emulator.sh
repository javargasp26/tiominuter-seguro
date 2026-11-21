#!/usr/bin/env bash
# Training across clients against the Firebase emulators (Auth + Firestore, with
# firebase/firestore.rules), in one emulator run:
#   1. the web writes with its real code in Chrome (web/test/emulator/web_train_cross_test.dart,
#      CROSS_PHASE=web-writes): a goal, a daily workout, an activity, a program + a completion, a
#      member summary, feed posts, a deletion;
#   2. Android pulls them into fresh Room databases on emulator-5554 and checks them, then pushes its
#      own changes (WebInteropEmulatorTest, the FirestoreSyncEmulatorTest style);
#   3. the web reads Android's changes back (CROSS_PHASE=android-reads);
#   the daily unlock across the same steps, on an account of its own: Android finishes Day 1 (2.) →
#   the web shows Day 2 unlocked and restarts the days (3.) → 4. Android pulls the new cycle with
#   only Day 1 unlocked (WebInteropEmulatorTest, crossStep=after-web-restart).
# Opt-in. Never touches the real project (fake API keys, the emulators only). connectedDebugAndroidTest
# uninstalls the app on the target device: only emulator-5554 is allowed, and it must be the only
# device connected. Needs Flutter 3.47.7, Google Chrome, Node 22+, Java 21, firebase-tools and a
# running Android emulator.
set -euo pipefail

cd "$(dirname "$0")/.."

devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [ "$devices" != "emulator-5554" ]; then
  echo "Needs exactly one connected device, emulator-5554 (connected: ${devices:-none})." >&2
  exit 1
fi

run="r$(date +%s)"
(cd web && flutter pub get >/dev/null)

# The real content/v1 for the browser test, with CORS, on this machine only
python3 -I - 8099 content/v1 <<'PY' &
import functools, http.server, sys
class Handler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()
    def log_message(self, *args):
        pass
http.server.ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), functools.partial(Handler, directory=sys.argv[2])).serve_forever()
PY
content_server=$!
trap 'kill $content_server 2>/dev/null || true' EXIT

web_phase() {
  echo "cd web && flutter test --platform chrome --dart-define=WEB_EMULATOR=true \
    --dart-define=CROSS_RUN=$run --dart-define=CROSS_PHASE=$1 test/emulator/web_train_cross_test.dart"
}

firebase emulators:exec --only firestore,auth --project tiominuter-e43f7 \
  "set -e; ($(web_phase web-writes)) && \
   ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.javargasp.tiominutermvi.data.sync.WebInteropEmulatorTest \
     -Pandroid.testInstrumentationRunnerArguments.webInterop=true \
     -Pandroid.testInstrumentationRunnerArguments.crossRun=$run && \
   ($(web_phase android-reads)) && \
   ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.javargasp.tiominutermvi.data.sync.WebInteropEmulatorTest#a_restart_on_the_web_locks_the_days_again_on_the_phone \
     -Pandroid.testInstrumentationRunnerArguments.webInterop=true \
     -Pandroid.testInstrumentationRunnerArguments.crossRun=$run \
     -Pandroid.testInstrumentationRunnerArguments.crossStep=after-web-restart"
