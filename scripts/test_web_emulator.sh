#!/usr/bin/env bash
# The web client end to end against the Firebase emulators (Auth + Firestore, with
# firebase/firestore.rules): web/test/emulator/web_emulator_test.dart runs in Chrome
# (flutter test --platform chrome), seeds data as two email/password test users of the Auth
# emulator through the real rules, and reads it back with the web's repositories and blocs.
# Opt-in (flutter test on the VM skips it). Never touches the real project: the test uses a fake
# API key and stops unless both emulators answer.
# Then web/test/browser/ in Chrome too (no emulator needed): the browser's own photo code — the
# decoder / encoder (EXIF orientation, no metadata, sizes) and IndexedDB.
# Needs Flutter 3.47.7, Google Chrome, Node 22+, Java 21 and firebase-tools.
set -euo pipefail

cd "$(dirname "$0")/.."

(cd web && flutter pub get >/dev/null)

firebase emulators:exec --only firestore,auth --project tiominuter-e43f7 \
  "cd web && flutter test --platform chrome --dart-define=WEB_EMULATOR=true test/emulator/"

(cd web && flutter test --platform chrome test/browser/)
