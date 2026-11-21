#!/usr/bin/env bash
# Regenerates the Kotlin ↔ Dart parity files (shared/golden/*.json) from the real Kotlin code
# (app/src/test/java/com/javargasp/tiominutermvi/parity/). Run it after an intended change of a
# ported rule (program generator, weekly goal / streak / member summary, sessions, workout names,
# the daily unlock),
# then make the web's Dart port match: (cd web && flutter test test/parity/). Normal unit-test runs
# fail when a regeneration would change any byte.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :app:testDebugUnitTest --tests "com.javargasp.tiominutermvi.parity.*" -Pgolden.update=true --rerun
git status --short shared/golden
