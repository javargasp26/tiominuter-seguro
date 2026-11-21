#!/usr/bin/env bash
# Groups end to end against the Firebase emulators (Firestore + Auth, with firebase/firestore.rules):
# runs GroupsEmulatorTest on the Android emulator, which reaches the host at 10.0.2.2, with
# email/password test users on the Auth emulator. Opt-in (normal CI and connectedDebugAndroidTest
# skip that test). Never touches the real project.
# connectedDebugAndroidTest uninstalls the app on the target device: only emulator-5554 is allowed,
# and it must be the only device connected.
# Needs Node 22+, Java 21, firebase-tools and a running Android emulator.
set -euo pipefail

cd "$(dirname "$0")/.."

devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [ "$devices" != "emulator-5554" ]; then
  echo "Needs exactly one connected device, emulator-5554 (connected: ${devices:-none})." >&2
  exit 1
fi

firebase emulators:exec --only firestore,auth --project tiominuter-e43f7 \
  "ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.javargasp.tiominutermvi.data.groups.GroupsEmulatorTest \
    -Pandroid.testInstrumentationRunnerArguments.groupsEmulator=true"
