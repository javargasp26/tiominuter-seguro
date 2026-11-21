#!/usr/bin/env python3
"""Builds the Firebase Hosting public folder (hosting/public/): the Flutter web app at the root and
the content under /content/<version>/.

    (cd web && flutter build web --release)
    python3 scripts/build_manifest.py
    python3 scripts/stage_hosting.py

Runs as the hosting "predeploy" step of firebase.json (and explicitly in CI, after the web build
and the manifest). hosting/public/ is generated (gitignored) and replaced as a whole:

- web/build/web/ is copied to the root (index.html, flutter_bootstrap.js, assets/, canvaskit/...).
  Flutter doesn't hash its file names, so main.dart.js is renamed to main.dart.<sha256 prefix>.js
  and flutter_bootstrap.js is pointed at it: that one file can be cached for a year (firebase.json),
  everything else is short-cached or not cached. A web build without index.html stops the deploy.
- Every content version folder (content/v1, later v2...) is copied to content/<version>/, so it is
  served at /content/<version>/<file>. Hidden files and the editing guide (README.md) are left out.
  A version without manifest.json stops the deploy: run scripts/build_manifest.py first.

The web build must not contain a top-level content/ folder (it would mix with the published content).
"""

import hashlib
import shutil
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CONTENT_ROOT = REPO_ROOT / "content"
WEB_BUILD = REPO_ROOT / "web" / "build" / "web"
PUBLIC = REPO_ROOT / "hosting" / "public"
PUBLIC_CONTENT = PUBLIC / "content"

MAIN_JS = "main.dart.js"
BOOTSTRAP = "flutter_bootstrap.js"
# Build files that are never published
WEB_EXCLUDED = {".last_build_id"}


def is_published(path: Path) -> bool:
    return not path.name.startswith(".") and path.name != "README.md"


def stage_web() -> int:
    if not (WEB_BUILD / "index.html").is_file():
        print(f"No web build in {WEB_BUILD}: run (cd web && flutter build web --release) first", file=sys.stderr)
        return -1
    if (WEB_BUILD / "content").exists():
        print(f"{WEB_BUILD}/content would mix with the published content: remove it", file=sys.stderr)
        return -1
    copied = 0
    for source in sorted(WEB_BUILD.rglob("*")):
        relative = source.relative_to(WEB_BUILD)
        if source.is_dir() or relative.name in WEB_EXCLUDED:
            continue
        target = PUBLIC / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        copied += 1

    # The one long-cached file gets a content hash in its name
    main_js = PUBLIC / MAIN_JS
    bootstrap = PUBLIC / BOOTSTRAP
    if not main_js.is_file() or not bootstrap.is_file():
        print(f"The web build has no {MAIN_JS} or {BOOTSTRAP}", file=sys.stderr)
        return -1
    digest = hashlib.sha256(main_js.read_bytes()).hexdigest()[:16]
    hashed = f"main.dart.{digest}.js"
    text = bootstrap.read_text(encoding="utf-8")
    reference = f'"mainJsPath":"{MAIN_JS}"'
    if text.count(reference) != 1:
        print(f"{BOOTSTRAP} doesn't name {MAIN_JS} as expected: check the Flutter version", file=sys.stderr)
        return -1
    bootstrap.write_text(text.replace(reference, f'"mainJsPath":"{hashed}"'), encoding="utf-8")
    main_js.rename(PUBLIC / hashed)
    print(f"Staged the web app ({copied} files, {MAIN_JS} as {hashed})")
    return copied


def stage_content() -> int:
    versions = sorted(p for p in CONTENT_ROOT.iterdir() if p.is_dir() and p.name.startswith("v"))
    if not versions:
        print(f"No content version folder in {CONTENT_ROOT}", file=sys.stderr)
        return -1
    missing = [v.name for v in versions if not (v / "manifest.json").is_file()]
    if missing:
        print(
            f"manifest.json missing in {', '.join(missing)}: run python3 scripts/build_manifest.py first",
            file=sys.stderr,
        )
        return -1
    copied = 0
    for version in versions:
        for source in sorted(version.rglob("*")):
            relative = source.relative_to(CONTENT_ROOT)
            if source.is_dir() or not all(is_published(Path(part)) for part in relative.parts):
                continue
            target = PUBLIC_CONTENT / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            copied += 1
    print(f"Staged {copied} content files from {', '.join(v.name for v in versions)} into {PUBLIC_CONTENT}")
    return copied


def main() -> int:
    if PUBLIC.exists():
        shutil.rmtree(PUBLIC)
    PUBLIC.mkdir(parents=True)
    if stage_web() < 0 or stage_content() < 0:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
