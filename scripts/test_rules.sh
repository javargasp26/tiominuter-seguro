#!/usr/bin/env bash
# Runs the Firestore security rules tests (firebase/rules-tests) on the Firestore emulator.
# Needs Node 22+, Java 21 and firebase-tools. Installs the test dependencies on first use.
# Usage: scripts/test_rules.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -d firebase/rules-tests/node_modules ]; then
  npm --prefix firebase/rules-tests ci
fi

firebase emulators:exec --only firestore --project tiominuter-e43f7 \
  "npm --prefix firebase/rules-tests test"
