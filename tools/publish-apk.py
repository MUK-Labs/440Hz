#!/usr/bin/env python3
"""Publish immutable test APKs from a successful, trusted main-branch build.

Bundled versions are already downloadable from Pages, and are never republished.
Only new increasing X.Y.Z versions are published. Pages chooses its default by
version number, not GitHub's mutable Latest flag. --dry-run performs reads only.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


VERSION = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z")


def version_tuple(value: str) -> tuple[int, int, int]:
    if not VERSION.fullmatch(value):
        raise ValueError("Versions must use the numeric X.Y.Z format")
    return tuple(map(int, value.split(".")))


def source_version(source: str) -> tuple[str, int]:
    names = re.findall(r'^\s*versionName\s*=\s*"([^"\n]+)"\s*$', source, re.M)
    codes = re.findall(r"^\s*versionCode\s*=\s*([0-9]+)\s*$", source, re.M)
    if len(names) != 1 or len(codes) != 1 or int(codes[0]) < 1:
        raise ValueError("Expected one literal versionName and positive versionCode")
    version_tuple(names[0])
    return names[0], int(codes[0])


def skip_reason(version: str, code: int, bundled: list[dict], releases: list[dict],
                tag_exists: bool = False) -> str | None:
    candidate = version_tuple(version)
    if any(item["version"] == version for item in bundled):
        return "version already bundled on the download page"
    if any(release["tag_name"] == "v" + version for release in releases):
        return "release already exists (including drafts); never overwrite it"
    if tag_exists:
        return "tag already exists; never overwrite or reuse an unrelated tag"
    known = [version_tuple(item["version"]) for item in bundled]
    known_codes = [int(item["versionCode"]) for item in bundled]
    for release in releases:
        tag = release["tag_name"]
        if not release.get("draft") and not release.get("prerelease") and tag.startswith("v"):
            if VERSION.fullmatch(tag[1:]):
                known.append(version_tuple(tag[1:]))
                # Script-generated public releases retain the Android version code.
                match = re.search(r"\(version code ([1-9][0-9]*)\)", release.get("body") or "")
                if match:
                    known_codes.append(int(match.group(1)))
    if known and candidate <= max(known):
        return "newer or equal version already available; skip a stale build"
    if known_codes and code <= max(known_codes):
        raise ValueError("Increase versionCode as well as versionName before publishing")
    return None


def gh(*args: str) -> str:
    result = subprocess.run(["gh", *args], capture_output=True, text=True)
    if result.returncode:
        # gh's stderr may contain transport details; never dump auth environment.
        raise RuntimeError(f"GitHub operation failed: {args[0]}")
    return result.stdout


def read_tag(repo: str, tag: str) -> bool:
    result = subprocess.run(["gh", "api", f"repos/{repo}/git/ref/tags/{tag}"],
                            capture_output=True, text=True)
    if result.returncode == 0:
        return True
    if "HTTP 404" in result.stderr:
        return False
    raise RuntimeError("Could not check existing tag; refusing to publish")


def all_releases(repo: str) -> list[dict]:
    releases: list[dict] = []
    page = 1
    while True:
        batch = json.loads(gh("api", f"repos/{repo}/releases?per_page=100&page={page}"))
        if not isinstance(batch, list):
            raise ValueError("Unexpected releases API response")
        releases.extend(batch)
        if len(batch) < 100:
            return releases
        page += 1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    if (repo != "MUK-Labs/440Hz" or os.environ.get("GITHUB_REF") != "refs/heads/main"
            or os.environ.get("GITHUB_EVENT_NAME") not in {"push", "workflow_dispatch"}):
        print("Publication skipped: only trusted main push/manual builds may publish.")
        return
    sha = os.environ.get("GITHUB_SHA", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Expected an exact commit SHA")
    root = Path(__file__).resolve().parent.parent
    version, code = source_version((root / "app/build.gradle.kts").read_text())
    bundled = json.loads((root / "site/versions.json").read_text())["versions"]
    reason = skip_reason(version, code, bundled, [])
    if reason:
        print(f"Publication skipped for v{version}: {reason}.")
        return
    releases = all_releases(repo)
    reason = skip_reason(version, code, bundled, releases, read_tag(repo, "v" + version))
    if reason:
        print(f"Publication skipped for v{version}: {reason}.")
        return
    if not args.apk.is_file() or args.apk.stat().st_size == 0:
        raise ValueError("Successful build APK is missing or empty")
    if args.dry_run:
        print(f"Would publish v{version}, versionCode {code}, from {sha}; no changes made.")
        return
    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    with tempfile.TemporaryDirectory(prefix="440-publish-") as directory:
        stage = Path(directory)
        asset = stage / f"440-v{version}-test.apk"
        shutil.copyfile(args.apk, asset)
        notes = stage / "notes.md"
        notes.write_text(
            f"Android test build **{version}** (version code {code}).\n\n"
            "Built after scheduling, gesture, response-window, compilation and lint checks passed.\n\n"
            "Download the APK directly below. Android 8.0 or newer is required.\n\n"
            "This APK is debug-signed. Fresh CI runners may use a different signing key, so an "
            "update may require reinstalling, which erases reminder settings. It is not a Play Store release.\n\n"
            f"Source commit: `{sha}`\n\nSHA-256: `{digest}`\n"
        )
        tag = "v" + version
        # Stage as a draft; make public only once the APK upload has completed.
        # No --clobber: a version's bytes and signature are never silently changed.
        gh("release", "create", tag, str(asset), "--repo", repo, "--target", sha,
           "--title", f"440 {version} — Android test build", "--notes-file", str(notes),
           "--draft", "--latest=false")
        gh("release", "edit", tag, "--repo", repo, "--draft=false", "--latest=false")
    print(f"Published v{version}: https://github.com/{repo}/releases/tag/v{version}")


if __name__ == "__main__":
    main()
