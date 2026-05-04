#!/usr/bin/env python3
"""Apply work artifacts produced by the worker jobs and post results.

Runs in the `poster` job, which holds the elevated otelbot token but
runs only trusted code snapshotted from the default branch and uses
git/gh on the worker artifacts only. It must never execute anything
from the PR working tree (no `./gradlew`, no Copilot CLI, no Python
from the PR tree). Doing so would let a malicious PR exfiltrate the
otelbot token.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from common import gh, git, progress
from triage_helpers import (
    comment_on_pr,
    event_repo,
    pr_number,
    read_text,
    truncate_output,
)


WORK_BUNDLE_REF = "refs/pr-triage-applied"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--in-dir",
        required=True,
        type=Path,
        help="directory containing 'gradle/' and/or 'copilot/' subdirectories of worker artifacts",
    )
    return parser.parse_args()


def apply_bundle(bundle_path: Path, head_before: str) -> tuple[bool, str]:
    """gh pr checkout, fast-forward apply the bundle, and push.

    Returns (success, note). On failure, note explains why."""
    pr = pr_number()
    checkout = gh(["pr", "checkout", pr], check=False)
    if checkout.returncode != 0:
        return False, "could not check out PR branch:\n" + (checkout.stderr or checkout.stdout)

    current_head = git(["rev-parse", "HEAD"]).stdout.strip()
    if current_head != head_before:
        return False, (
            f"PR head moved during processing (was {head_before[:7]}, "
            f"now {current_head[:7]}); please retry"
        )

    fetch = git(["fetch", str(bundle_path), f"+HEAD:{WORK_BUNDLE_REF}"], check=False)
    if fetch.returncode != 0:
        return False, "could not fetch work bundle:\n" + (fetch.stderr or fetch.stdout)

    merge = git(["merge", "--ff-only", WORK_BUNDLE_REF], check=False)
    if merge.returncode != 0:
        return False, "could not fast-forward to applied commits:\n" + (merge.stderr or merge.stdout)

    push = git(["push"], check=False)
    if push.returncode != 0:
        return False, "could not push to PR branch:\n" + (push.stderr or push.stdout)

    return True, ""


def post_review(payload_path: Path) -> tuple[bool, str]:
    pr = pr_number()
    result = gh(
        ["api", f"repos/{event_repo()}/pulls/{pr}/reviews",
         "--method", "POST", "--input", str(payload_path), "--jq", ".id"],
        check=False,
    )
    if result.returncode != 0:
        return False, "could not post review:\n" + (result.stderr or result.stdout)
    return True, result.stdout.strip()


def select_work_dir(in_dir: Path) -> Path | None:
    """Pick the artifact whose work is the final user-facing result.

    Copilot ran second (if at all), so it always supersedes gradle when it
    produced a real result. A 'delegate' kind means the gradle phase
    handed off to copilot and produced no postable output of its own.
    """
    for sub in ("copilot", "gradle"):
        sub_dir = in_dir / sub
        if not sub_dir.exists():
            continue
        kind = read_text(sub_dir / "kind.txt").strip()
        if not kind or kind == "delegate":
            continue
        return sub_dir
    return None


def post_from(work_dir: Path) -> int:
    requested = read_text(work_dir / "command.txt").strip()
    if not requested:
        progress("No command recorded in work artifacts; nothing to post")
        return 0

    notes: list[str] = []

    bundle_path = work_dir / "bundle.git"
    if bundle_path.exists():
        head_before = read_text(work_dir / "head-before.txt").strip()
        if not head_before:
            notes.append("missing head-before.txt; cannot apply bundle safely")
        else:
            ok, msg = apply_bundle(bundle_path, head_before)
            if ok:
                notes.append("Pushed worker commits to the PR branch.")
            else:
                notes.append("Could not push worker commits: " + msg)

    review_payload = work_dir / "review-payload.json"
    if review_payload.exists():
        ok, info = post_review(review_payload)
        if ok:
            notes.append(f"Posted pending review {info}.")
        else:
            notes.append(info)

    exit_code_text = read_text(work_dir / "exit-code.txt").strip()
    status = "completed successfully" if exit_code_text == "0" else "failed"
    output = truncate_output(read_text(work_dir / "output.txt") or "No command output was captured.")

    body_parts = [f"`{requested}` {status}."]
    if notes:
        body_parts.extend("- " + n for n in notes)
    body_parts.append("")
    body_parts.append("```text")
    body_parts.append(output)
    body_parts.append("```")
    comment_on_pr("\n".join(body_parts))

    return 0


def main() -> int:
    args = parse_args()
    in_dir: Path = args.in_dir
    if not in_dir.exists():
        progress(f"No work artifacts at {in_dir}; nothing to post")
        return 0

    work_dir = select_work_dir(in_dir)
    if work_dir is None:
        progress(f"No final work artifacts found under {in_dir}; nothing to post")
        return 0

    return post_from(work_dir)


if __name__ == "__main__":
    sys.exit(main())
