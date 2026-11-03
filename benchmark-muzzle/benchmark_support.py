"""Shared workload identity and process helpers for cold-checkout benchmarks."""

import hashlib
import os
from pathlib import Path
import re
import subprocess


CONTROLS = Path(
    "gradle-plugins/src/main/java/io/opentelemetry/javaagent/muzzle/MuzzleBenchmarkSupport.java"
)
RUNTIME_FILES = (
    ".java-version",
    "gradle.properties",
    "gradle/wrapper/gradle-wrapper.properties",
    ".github/config/latest-dep-versions.json",
)


def run(command, *, cwd=None, env=None, log=None):
    kwargs = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
    if log is None:
        return subprocess.run(
            command, cwd=cwd, env=env, check=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs,
        ).stdout
    print(f"Running {log}", flush=True)
    with log.open("wb") as output:
        subprocess.run(
            command, cwd=cwd, env=env, check=True,
            stdout=output, stderr=subprocess.STDOUT, **kwargs,
        )
    return b""


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def inventory(directory):
    return {
        path.relative_to(directory).as_posix(): digest(path)
        for path in sorted(directory.rglob("*")) if path.is_file()
    }


def fixture_identity(tree):
    sources = [
        entry for entry in tree.split(b"\0")
        if entry and b"/src/main" in entry.split(b"\t", 1)[1]
    ]
    if not sources:
        raise ValueError("No instrumentation main sources found")
    return hashlib.sha256(b"\0".join(sorted(sources))).hexdigest()


def manifests(directory):
    result = {}
    for path in sorted(directory.glob("*.txt")):
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines()
        if not lines or ":muzzle-Assert" not in lines[0] or lines[0] in result:
            raise ValueError(f"Invalid or duplicate assertion manifest: {path}")
        result[lines[0]] = text
    if not result:
        raise ValueError(f"No successful Muzzle assertions recorded in {directory}")
    return result


def require_equal(expected, actual, label):
    if expected != actual:
        if isinstance(expected, dict) and isinstance(actual, dict):
            changed = sorted(
                key for key in expected.keys() | actual.keys()
                if expected.get(key) != actual.get(key)
            )
            raise ValueError(f"{label} mismatch: {changed[:10]}")
        raise ValueError(f"{label} mismatch")


def task_outcomes(text):
    tasks = {}
    for task, outcome in re.findall(
        r"^> Task (\S+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?\s*$",
        text, re.MULTILINE,
    ):
        if task in tasks:
            raise ValueError(f"Repeated Gradle task outcome: {task}")
        tasks[task] = outcome or "EXECUTED"
    if not tasks:
        raise ValueError("No Gradle task outcomes in build log")
    return tasks


def order(round_number):
    return (
        ("baseline", "control", "candidate") if round_number % 2 == 0
        else ("candidate", "control", "baseline")
    )
