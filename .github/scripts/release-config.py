#!/usr/bin/env python3

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
VERSION_PATTERN = r"(\d+)\.(\d+)\.(\d+)"


def development_config(ref: str, version: str) -> dict[str, str]:
    if ref not in ("main", "2.x"):
        raise ValueError("release preparation must run on main or 2.x")
    match = re.fullmatch(VERSION_PATTERN + r"-SNAPSHOT", version)
    if match is None:
        raise ValueError(f"expected a minor snapshot version, got {version}")
    major, minor, patch = map(int, match.groups())
    if patch != 0 or (ref == "2.x" and major != 2):
        raise ValueError(f"version {version} does not match development branch {ref}")
    return {
        "VERSION": f"{major}.{minor}.0",
        "DEVELOPMENT_BRANCH": ref,
        "RELEASE_BRANCH_NAME": f"release/v{major}.{minor}.x",
        "NEXT_VERSION": f"{major}.{minor + 1}.0-SNAPSHOT",
    }


def release_config(ref: str, version: str, changelog: str) -> dict[str, str]:
    match = re.fullmatch(VERSION_PATTERN, version)
    if match is None:
        raise ValueError(f"expected a release version, got {version}")
    major, minor, patch = map(int, match.groups())
    if ref != f"release/v{major}.{minor}.x":
        raise ValueError(f"version {version} does not match release branch {ref}")
    if patch > 0:
        prior_version = f"{major}.{minor}.{patch - 1}"
    elif minor > 0:
        prior_version = f"{major}.{minor - 1}.0"
    else:
        prior_minors = re.findall(
            rf"^## Version {major - 1}\.(\d+)\.\d+ ", changelog, re.MULTILINE
        )
        if not prior_minors:
            raise ValueError(f"no previous major release found for {version}")
        prior_version = f"{major - 1}.{max(map(int, prior_minors))}.0"
    return {
        "VERSION": version,
        "DEVELOPMENT_BRANCH": "2.x" if major == 2 else "main",
        "PRIOR_VERSION": prior_version,
    }


def make_latest(version: str, released_tags: list[str]) -> bool:
    match = re.fullmatch(VERSION_PATTERN, version)
    if match is None:
        raise ValueError(f"expected a release version, got {version}")
    candidate = tuple(map(int, match.groups()))
    for tag in released_tags:
        released = re.fullmatch("v" + VERSION_PATTERN, tag)
        if released is not None and tuple(map(int, released.groups())) > candidate:
            return False
    return True


def main() -> None:
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ("prepare", "release"):
        subparser = commands.add_parser(command)
        subparser.add_argument("--ref", required=True)
    latest = commands.add_parser("latest")
    latest.add_argument("--version", required=True)
    args = parser.parse_args()

    try:
        if args.command == "latest":
            config = {
                "MAKE_LATEST": str(
                    make_latest(args.version, sys.stdin.read().splitlines())
                ).lower()
            }
        else:
            version_file = (REPO_ROOT / "version.gradle.kts").read_text(encoding="utf-8")
            version_match = re.search(
                r'^val stableVersion = "([^"]+)"$', version_file, re.MULTILINE
            )
            if version_match is None:
                raise ValueError("could not determine stableVersion")
            version = version_match.group(1)
            if args.command == "prepare":
                config = development_config(args.ref, version)
            else:
                config = release_config(
                    args.ref,
                    version,
                    (REPO_ROOT / "CHANGELOG.md").read_text(encoding="utf-8"),
                )
    except ValueError as error:
        parser.error(str(error))

    for key, value in config.items():
        print(f"{key}={value}")


if __name__ == "__main__":
    main()
