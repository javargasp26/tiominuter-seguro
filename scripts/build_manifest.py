#!/usr/bin/env python3
"""Writes content/v1/manifest.json: the list of published content files.

    python3 scripts/build_manifest.py [--content-dir content/v1] [--content-version 20261020120000]

The manifest has schemaVersion 1, a contentVersion (UTC timestamp yyyyMMddHHmmss, the time it
was built unless --content-version is given) and, for every file under the content directory,
its relative path ("/" separators), size in bytes and sha256, sorted by path. manifest.json
itself and hidden files (".DS_Store"...) are left out. Only the standard library is used.

The manifest is generated in CI before each deploy and is never committed (.gitignore) nor
bundled in the APK (app/build.gradle.kts ignores it).
"""

import argparse
import hashlib
import json
import os
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 1
MANIFEST_NAME = "manifest.json"
REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CONTENT_DIR = REPO_ROOT / "content" / "v1"


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def content_files(content_dir: Path):
    """Every published file, as (relative posix path, absolute path), sorted by path."""
    found = []
    for root, dirs, files in os.walk(content_dir):
        dirs[:] = [d for d in dirs if not d.startswith(".")]
        for name in files:
            if name.startswith("."):
                continue
            absolute = Path(root) / name
            relative = absolute.relative_to(content_dir).as_posix()
            if relative == MANIFEST_NAME:
                continue
            found.append((relative, absolute))
    return sorted(found)


def build_manifest(content_dir: Path, content_version: str) -> dict:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "contentVersion": content_version,
        "files": [
            {"path": relative, "size": absolute.stat().st_size, "sha256": sha256_of(absolute)}
            for relative, absolute in content_files(content_dir)
        ],
    }


def write_atomically(path: Path, text: str) -> None:
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent, delete=False) as temporary:
        temporary.write(text)
    os.replace(temporary.name, path)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--content-dir", type=Path, default=DEFAULT_CONTENT_DIR)
    parser.add_argument(
        "--content-version",
        help="yyyyMMddHHmmss (UTC); defaults to now",
        default=datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S"),
    )
    args = parser.parse_args()

    content_dir = args.content_dir.resolve()
    if not content_dir.is_dir():
        print(f"Content directory not found: {content_dir}", file=sys.stderr)
        return 1
    version = args.content_version
    if len(version) != 14 or not version.isdigit():
        print(f"contentVersion must be yyyyMMddHHmmss, got '{version}'", file=sys.stderr)
        return 1

    manifest = build_manifest(content_dir, version)
    if not manifest["files"]:
        print(f"No content files in {content_dir}", file=sys.stderr)
        return 1
    write_atomically(content_dir / MANIFEST_NAME, json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    total = sum(entry["size"] for entry in manifest["files"])
    print(f"Wrote {content_dir / MANIFEST_NAME}: {len(manifest['files'])} files, {total} bytes, contentVersion {version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
