#!/usr/bin/env python3
"""Run the copilot phase of a PR triage slash command.

Runs in the `copilot-worker` job, which holds the Copilot CLI token and
has the PR working tree checked out for Copilot to edit, but does NOT
have Java or Gradle set up. This script must never invoke `./gradlew`
or any other PR-controlled build tooling: doing so would let a
malicious PR exfiltrate the Copilot token via a crafted Gradle plugin
or build script.

Handles `/review` and the Copilot phase of `/fix`. For `/fix`, this
phase consumes the CI bundle prepared by the `gradle-worker` job and
runs Copilot only.
"""

from __future__ import annotations

import argparse
import sys
import traceback
from pathlib import Path

from common import git
from triage_helpers import (
    parsed_command,
    pr_number,
    python_sub_script,
    run_sub_script,
    write_job_output,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out-dir", required=True, type=Path, help="directory to write work artifacts")
    parser.add_argument(
        "--in-dir",
        type=Path,
        help="directory holding gradle-phase handoff artifacts (required for /fix)",
    )
    return parser.parse_args()


def sub_script_args(command: str, out_dir: Path, in_dir: Path | None) -> list[str] | None:
    """Return the sub-script invocation for command, or None if it is not a copilot-phase command."""
    pr = pr_number()
    if command == "review":
        return [*python_sub_script("review.py"), pr, "--no-post", "--out-dir", str(out_dir)]
    if command == "fix":
        if in_dir is None:
            raise RuntimeError("--in-dir is required for /fix in copilot phase")
        ci_bundle = in_dir / "ci-bundle"
        return [
            *python_sub_script("fix.py"), pr,
            "--no-push", "--phase=copilot", "--in-dir", str(ci_bundle),
        ]
    return None


def main() -> int:
    args = parse_args()
    out_dir: Path = args.out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    requested, command = parsed_command()
    (out_dir / "command.txt").write_text(requested + "\n", encoding="utf-8")
    (out_dir / "role.txt").write_text("copilot\n", encoding="utf-8")

    cmd = sub_script_args(command, out_dir, args.in_dir) if command else None
    if cmd is None:
        # Not a copilot-phase command (e.g. /spotless). Nothing to post from
        # this artifact.
        return 0

    head_before = git(["rev-parse", "HEAD"]).stdout.strip()
    (out_dir / "head-before.txt").write_text(head_before + "\n", encoding="utf-8")

    try:
        exit_code = run_sub_script(cmd, out_dir)
    except Exception:
        exit_code = 1
        (out_dir / "output.txt").write_text(traceback.format_exc(), encoding="utf-8", errors="replace")

    (out_dir / "exit-code.txt").write_text(f"{exit_code}\n", encoding="utf-8")
    (out_dir / "kind.txt").write_text(("review" if command == "review" else "commit") + "\n", encoding="utf-8")

    head_after = git(["rev-parse", "HEAD"]).stdout.strip()
    (out_dir / "head-after.txt").write_text(head_after + "\n", encoding="utf-8")
    if head_after != head_before:
        bundle_path = out_dir / "bundle.git"
        git(["bundle", "create", str(bundle_path), f"{head_before}..HEAD"])

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
