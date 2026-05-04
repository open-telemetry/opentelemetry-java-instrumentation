#!/usr/bin/env python3
"""Run the gradle phase of a PR triage slash command.

Runs in the `gradle-worker` job, which has Java + Gradle set up and the
PR working tree checked out, but does NOT receive the Copilot token.
This script must never invoke any tool that requires the Copilot token,
because PR-controlled Gradle plugins running in this job could read its
environment.

Handles `/spotless`, `/update-branch`, and the deterministic phase of
`/fix`. For `/fix`, this phase downloads CI logs and tries deterministic
fixes (Spotless, FOSSA). If those resolve the failures it commits and
bundles. Otherwise it writes a CI bundle for handoff to the
`copilot-worker` job and signals `needs-copilot=true`.
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
    return parser.parse_args()


def sub_script_args(command: str, out_dir: Path) -> list[str] | None:
    """Return the sub-script invocation for command, or None if it is not a gradle-phase command."""
    pr = pr_number()
    if command == "spotless":
        return [*python_sub_script("spotless.py"), pr, "--no-push"]
    if command == "update_branch":
        return [*python_sub_script("update_branch.py"), pr, "--no-push"]
    if command == "fix":
        return [
            *python_sub_script("fix.py"), pr,
            "--no-push", "--phase=gradle", "--out-dir", str(out_dir),
        ]
    return None


def main() -> int:
    args = parse_args()
    out_dir: Path = args.out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    requested, command = parsed_command()
    (out_dir / "command.txt").write_text(requested + "\n", encoding="utf-8")
    (out_dir / "role.txt").write_text("gradle\n", encoding="utf-8")

    cmd = sub_script_args(command, out_dir) if command else None
    if cmd is None:
        # Not a gradle-phase command (e.g. /review). Nothing to post from
        # this artifact.
        write_job_output(**{"needs-copilot": "false"})
        return 0

    head_before = git(["rev-parse", "HEAD"]).stdout.strip()
    (out_dir / "head-before.txt").write_text(head_before + "\n", encoding="utf-8")

    try:
        exit_code = run_sub_script(cmd, out_dir)
    except Exception:
        exit_code = 1
        (out_dir / "output.txt").write_text(traceback.format_exc(), encoding="utf-8", errors="replace")

    (out_dir / "exit-code.txt").write_text(f"{exit_code}\n", encoding="utf-8")

    needs_copilot = (
        command == "fix"
        and (out_dir / "needs-copilot.txt").exists()
        and (out_dir / "needs-copilot.txt").read_text(encoding="utf-8").strip() == "true"
    )
    kind = "delegate" if needs_copilot else "commit"
    (out_dir / "kind.txt").write_text(kind + "\n", encoding="utf-8")

    head_after = git(["rev-parse", "HEAD"]).stdout.strip()
    (out_dir / "head-after.txt").write_text(head_after + "\n", encoding="utf-8")
    if head_after != head_before and not needs_copilot:
        bundle_path = out_dir / "bundle.git"
        git(["bundle", "create", str(bundle_path), f"{head_before}..HEAD"])

    write_job_output(**{"needs-copilot": "true" if needs_copilot else "false"})
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
