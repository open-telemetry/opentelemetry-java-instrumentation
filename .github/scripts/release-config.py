#!/usr/bin/env python3

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
VERSION_PATTERN = r"(\d+)\.(\d+)\.(\d+)"


def development_config(ref: str, version: str) -> dict[str, str]:
    maintenance = re.fullmatch(r"maintenance/v(\d+)\.x", ref)
    if ref != "main" and maintenance is None:
        raise ValueError("release preparation must run on main or maintenance/v<major>.x")
    match = re.fullmatch(VERSION_PATTERN + r"-SNAPSHOT", version)
    if match is None:
        raise ValueError(f"expected a minor snapshot version, got {version}")
    major, minor, patch = map(int, match.groups())
    if patch != 0 or (maintenance is not None and ref != f"maintenance/v{major}.x"):
        raise ValueError(f"version {version} does not match development branch {ref}")
    return {
        "VERSION": f"{major}.{minor}.0",
        "DEVELOPMENT_BRANCH": ref,
        "RELEASE_BRANCH_NAME": f"release/v{major}.{minor}.x",
        "NEXT_VERSION": f"{major}.{minor + 1}.0-SNAPSHOT",
    }


def parse_version(version: str) -> tuple[int, int, int]:
    match = re.fullmatch(VERSION_PATTERN, version)
    if match is None:
        raise ValueError(f"expected a release version, got {version}")
    major, minor, patch = map(int, match.groups())
    return major, minor, patch


def prior_release_version(version: str, changelog: str) -> str | None:
    major, minor, patch = parse_version(version)
    if patch > 0:
        return f"{major}.{minor}.{patch - 1}"
    releases = [
        tuple(map(int, match))
        for match in re.findall(rf"^## Version {VERSION_PATTERN}(?: |$)", changelog, re.MULTILINE)
    ]
    if minor > 0:
        prior_versions = [release for release in releases if release[:2] == (major, minor - 1)]
    else:
        prior_versions = [release for release in releases if release[0] == major - 1]
    if not prior_versions:
        return None
    return ".".join(map(str, max(prior_versions)))


def git_output(*args: str) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        check=True,
        creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0,
    ).stdout


def read_stable_version(version_file: str) -> str:
    match = re.search(r'^val stableVersion = "([^"]+)"$', version_file, re.MULTILINE)
    if match is None:
        raise ValueError("could not determine stableVersion")
    return match.group(1)


def resolve_development_branch(version: str) -> str:
    major, _, _ = parse_version(version)
    maintenance = f"maintenance/v{major}.x"
    refs = git_output(
        "for-each-ref",
        "--format=%(refname)",
        f"refs/remotes/origin/{maintenance}",
        "refs/remotes/origin/main",
    ).splitlines()
    branch = maintenance if f"refs/remotes/origin/{maintenance}" in refs else "main"
    ref = f"refs/remotes/origin/{branch}"
    if ref not in refs:
        raise ValueError(f"no development branch found for {version}")
    branch_version = read_stable_version(git_output("show", f"{ref}:version.gradle.kts"))
    development_config(branch, branch_version)
    if parse_version(branch_version.removesuffix("-SNAPSHOT"))[0] != major:
        raise ValueError(f"release {version} does not match {branch} version {branch_version}")
    return branch


def release_config(
    ref: str, version: str, changelog: str, development_branch: str
) -> dict[str, str]:
    major, _, _ = parse_version(version)
    if ref != f"v{version}":
        raise ValueError(f"version {version} does not match release tag {ref}")
    if development_branch not in ("main", f"maintenance/v{major}.x"):
        raise ValueError(f"release {version} does not match development branch {development_branch}")
    prior_version = prior_release_version(version, changelog)
    if prior_version is None:
        raise ValueError(f"no previous release found for {version}")
    return {
        "VERSION": version,
        "DEVELOPMENT_BRANCH": development_branch,
        "PRIOR_VERSION": prior_version,
    }


def post_release_config(version: str, baseline: str) -> dict[str, str]:
    return {
        "UPDATE_API_BASELINE": str(parse_version(version) > parse_version(baseline)).lower(),
    }


def make_latest(version: str, released_tags: list[str]) -> bool:
    candidate = parse_version(version)
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
    post_release = commands.add_parser("post-release")
    post_release.add_argument("--version", required=True)
    args = parser.parse_args()

    try:
        if args.command == "latest":
            config = {
                "MAKE_LATEST": str(
                    make_latest(args.version, sys.stdin.read().splitlines())
                ).lower()
            }
        elif args.command == "post-release":
            version_file = (REPO_ROOT / "version.gradle.kts").read_text(encoding="utf-8")
            baseline_match = re.search(
                r'^val apidiffBaselineVersion = "([^"]+)"$', version_file, re.MULTILINE
            )
            if baseline_match is None:
                raise ValueError("could not determine apidiffBaselineVersion")
            config = post_release_config(args.version, baseline_match.group(1))
        else:
            version_file = (REPO_ROOT / "version.gradle.kts").read_text(encoding="utf-8")
            version = read_stable_version(version_file)
            if args.command == "prepare":
                config = development_config(args.ref, version)
            else:
                config = release_config(
                    args.ref,
                    version,
                    (REPO_ROOT / "CHANGELOG.md").read_text(encoding="utf-8"),
                    resolve_development_branch(version),
                )
    except ValueError as error:
        parser.error(str(error))

    for key, value in config.items():
        print(f"{key}={value}")


if __name__ == "__main__":
    main()
