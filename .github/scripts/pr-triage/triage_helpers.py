"""Shared helpers for the PR triage orchestration scripts.

The orchestration scripts (`authorize.py`, `worker_gradle.py`,
`worker_copilot.py`, `poster.py`) each run in a different workflow job
with a different security posture. This module only holds bits that are
purely about parsing the issue_comment event and producing GitHub API
side effects; it does not reach into the PR working tree or invoke any
PR-controlled tooling.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

from common import REPO_ROOT, gh, progress


COMMANDS = {
    "/spotless": "spotless",
    "/fix": "fix",
    "/update-branch": "update_branch",
    "/review": "review",
}
OUTPUT_LIMIT = 6000

SCRIPT_DIR = Path(__file__).resolve().parent


def event_payload() -> dict[str, Any]:
    path = os.environ.get("GITHUB_EVENT_PATH")
    if not path:
        raise RuntimeError("GITHUB_EVENT_PATH is not set")
    return json.loads(Path(path).read_text(encoding="utf-8"))


def event_repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]


def pr_number() -> str:
    return os.environ["PR_NUMBER"]


def parsed_command() -> tuple[str, str]:
    payload = event_payload()
    comment = payload.get("comment") or {}
    body = str(comment.get("body") or "").strip()
    first_line = body.splitlines()[0].strip() if body else ""
    # Hard cap on length to avoid echoing pathological input back into a
    # PR comment if a later step formats `requested` into Markdown.
    raw = first_line.split(maxsplit=1)[0] if first_line else ""
    if len(raw) > 32 or not raw.startswith("/"):
        return "", ""
    requested = raw.lower()
    if requested != "/help" and requested not in COMMANDS:
        return requested, ""
    command = COMMANDS.get(requested, "")
    return requested, command


def comment_on_pr(body: str) -> None:
    gh(["issue", "comment", pr_number(), "--repo", event_repo(), "--body", body])


def write_job_output(**values: str) -> None:
    output_path = os.environ.get("GITHUB_OUTPUT")
    if not output_path:
        for key, value in values.items():
            print(f"{key}={value}")
        return
    with open(output_path, "a", encoding="utf-8") as output:
        for key, value in values.items():
            output.write(f"{key}={value}\n")


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def truncate_output(output: str) -> str:
    if len(output) <= OUTPUT_LIMIT:
        return output
    return "...[output truncated]...\n" + output[-OUTPUT_LIMIT:]


def run_sub_script(cmd: list[str], out_dir: Path) -> int:
    """Run a sub-script (spotless.py, fix.py, ...) and capture combined stdout/stderr."""
    progress("Running: " + " ".join(cmd))
    output_path = out_dir / "output.txt"
    with output_path.open("w", encoding="utf-8", errors="replace") as output:
        proc = subprocess.Popen(
            cmd,
            cwd=REPO_ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
        if proc.stdout is not None:
            for line in proc.stdout:
                print(line, end="", flush=True)
                output.write(line)
        return proc.wait()


def python_sub_script(name: str) -> list[str]:
    return [sys.executable, str(SCRIPT_DIR / name)]
