"""Shared helpers for the pr-review workflow scripts.

Kept intentionally small: the full pr-triage `common` module includes
branch-restoration, Copilot CLI handoff, and other helpers the review
workflow does not need. This module covers only what the three review
scripts in this directory actually use.
"""

from __future__ import annotations

import json
import shlex
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


# Force UTF-8 on parent stdout/stderr so unicode characters in subprocess
# output don't crash the default cp1252 codec on Windows.
for _stream in (sys.stdout, sys.stderr):
    _reconfigure = getattr(_stream, "reconfigure", None)
    if _reconfigure is not None:
        _reconfigure(encoding="utf-8", errors="replace")


@dataclass
class Summary:
    """Lightweight bag of side-effect results, threaded through helpers."""

    pr: int
    pr_url: str | None = None
    review_url: str | None = None
    temp_dir: str | None = None
    notes: list[str] = field(default_factory=list)


def progress(message: str) -> None:
    print(f"[review] {message}", flush=True)


def _run(cmd: list[str], summary: Summary | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    if summary is not None:
        progress("Running: " + " ".join(shlex.quote(part) for part in cmd))
    return subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=check,
    )


def gh(args: list[str], summary: Summary | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    return _run(["gh", *args], summary, check)


def gh_json(args: list[str], summary: Summary | None = None) -> Any:
    result = gh(args, summary)
    return json.loads(result.stdout or "null")


def detect_repo(summary: Summary | None = None) -> str:
    return gh(["repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"], summary).stdout.strip()


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
