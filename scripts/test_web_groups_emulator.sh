#!/usr/bin/env bash
# Groups across clients against the Firebase emulators (Auth + Firestore, with
# firebase/firestore.rules), in one emulator run:
#   1. the web (web/test/emulator/web_groups_cross_test.dart, CROSS_PHASE=groups-web-creates)
#      creates a group with this run's code;
#   2. an Android user on emulator-5554 (WebGroupsInteropEmulatorTest) joins it with Android's real
#      GroupOperations, sees the web member, and creates a group with the second code;
#   3. the web (CROSS_PHASE=groups-web-joins) sees the Android member and joins the Android group.
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
# Two invite codes of this run, from the code alphabet (no 0 O 1 I L)
codes=$(python3 -I -c '
import random, sys
alphabet = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
r = random.Random(sys.argv[1])
print(" ".join("".join(r.choice(alphabet) for _ in range(8)) for _ in range(2)))
' "$run")
web_code=${codes% *}
android_code=${codes#* }
(cd web && flutter pub get >/dev/null)

web_phase() {
  echo "cd web && flutter test --platform chrome --dart-define=WEB_EMULATOR=true \
    --dart-define=CROSS_RUN=$run --dart-define=CROSS_PHASE=$1 \
    --dart-define=WEB_GROUP_CODE=$web_code --dart-define=ANDROID_GROUP_CODE=$android_code \
    test/emulator/web_groups_cross_test.dart"
}

firebase emulators:exec --only firestore,auth --project tiominuter-e43f7 \
  "set -e; ($(web_phase groups-web-creates)) && \
   ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.javargasp.tiominutermvi.data.groups.WebGroupsInteropEmulatorTest \
     -Pandroid.testInstrumentationRunnerArguments.webGroups=true \
     -Pandroid.testInstrumentationRunnerArguments.crossRun=$run \
     -Pandroid.testInstrumentationRunnerArguments.webGroupCode=$web_code \
     -Pandroid.testInstrumentationRunnerArguments.androidGroupCode=$android_code && \
   ($(web_phase groups-web-joins))"
