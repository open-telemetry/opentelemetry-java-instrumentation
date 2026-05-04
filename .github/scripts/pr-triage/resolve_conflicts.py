#!/usr/bin/env python3
"""Merge upstream/main into a PR branch, using Copilot only for conflicts."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from common import (
    Summary,
    checkout_pr,
    current_branch,
    diff_check,
    git,
    invoke_copilot,
    make_temp_dir,
    print_failure,
    progress,
    push,
    require_clean_worktree,
    restore_original_branch,
    unmerged_paths,
    write_json,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pr", type=int, help="pull request number")
    parser.add_argument("--upstream", default="upstream", help="upstream remote name")
    parser.add_argument("--json", action="store_true", help="print JSON summary")
    parser.add_argument("--no-push", action="store_true", help="commit but do not push")
    parser.add_argument("--keep-temp", action="store_true", help="reuse and retain the temp bundle directory")
    parser.add_argument("--skip-copilot", action="store_true", help="stop after collecting conflict context")
    return parser.parse_args()


def write_conflict_bundle(directory: Path, summary: Summary) -> Path:
    progress(f"Preparing merge conflict bundle in {directory}")
    directory.mkdir(parents=True, exist_ok=True)
    files = unmerged_paths(summary)
    write_json(directory / "conflicts.json", {"files": files})
    (directory / "status.txt").write_text(git(["status", "--porcelain"], summary).stdout, encoding="utf-8")
    (directory / "head-log.txt").write_text(git(["log", "--oneline", "-n", "20", "HEAD"], summary).stdout, encoding="utf-8")
    (directory / "merge-head-log.txt").write_text(git(["log", "--oneline", "-n", "20", "MERGE_HEAD"], summary).stdout, encoding="utf-8")
    for path in files:
        safe_name = path.replace("/", "__").replace("\\", "__")
        (directory / f"{safe_name}.diff").write_text(git(["diff", "--", path], summary).stdout, encoding="utf-8")
    plan = directory / "conflict-plan.md"
    lines = ["# Merge Conflict Resolution Plan", "", "## Conflicted Files", ""]
    lines.extend(f"- {path}" for path in files)
    lines.extend(["", "## Context", "", f"- Status: {directory / 'status.txt'}", f"- HEAD log: {directory / 'head-log.txt'}", f"- MERGE_HEAD log: {directory / 'merge-head-log.txt'}", ""])
    plan.write_text("\n".join(lines), encoding="utf-8")
    summary.temp_dir = str(directory)
    return plan


def copilot_prompt(plan_path: Path) -> str:
    return f"""You are resolving merge conflicts in opentelemetry-java-instrumentation.

The PR branch is already checked out and `git merge --no-edit` has already stopped
with conflicts. Read this conflict bundle first:

{plan_path}

Rules:
- Do not switch branches.
- Do not commit.
- Do not push.
- Do not rebase, abort, restart, or use force operations.
- Resolve only the conflicted files listed in the bundle.
- Preserve the intent of both HEAD and MERGE_HEAD when they can coexist.
- If the conflict requires product judgment or involves binary/non-text files, stop and explain.
- Stage only files that you resolved.
- When done, print a concise summary of each resolved file.
"""


def main() -> int:
    args = parse_args()
    summary = Summary(pr=args.pr)
    try:
        require_clean_worktree(summary)
        summary.original_branch = current_branch(summary)
        checkout_pr(args.pr, summary)

        progress(f"Fetching {args.upstream}")
        git(["fetch", args.upstream], summary)
        git(["rev-parse", "--verify", f"{args.upstream}/main"], summary)
        progress(f"Merging {args.upstream}/main")
        merge = git(["merge", "--no-edit", f"{args.upstream}/main"], summary, check=False)
        if merge.returncode == 0:
            if "Already up to date" in merge.stdout:
                summary.outcome = "branch was already up to date"
                return 0
            if args.no_push:
                summary.push_result = "not pushed (--no-push)"
            else:
                push(summary)
            summary.commits.append(git(["rev-parse", "--short", "HEAD"], summary).stdout.strip())
            summary.outcome = "merged upstream/main cleanly"
            return 0

        conflicts = unmerged_paths(summary)
        if not conflicts:
            raise RuntimeError("merge failed but no unmerged paths were detected")
        progress(f"Merge reported {len(conflicts)} conflicted files")
        summary.changed_files = conflicts
        bundle_dir = make_temp_dir("otel-conflicts", args.pr, args.keep_temp)
        plan_path = write_conflict_bundle(bundle_dir, summary)
        if args.skip_copilot:
            summary.outcome = "collected conflict context; skipped Copilot handoff"
            return 1

        response = invoke_copilot(copilot_prompt(plan_path), summary)
        (bundle_dir / "copilot-response.txt").write_text(response + "\n", encoding="utf-8")

        remaining = unmerged_paths(summary)
        if remaining:
            raise RuntimeError("unresolved conflicts remain: " + ", ".join(remaining))
        progress("Validating resolved merge")
        diff_check(summary)
        git(["commit", "--no-edit"], summary)
        summary.commits.append(git(["rev-parse", "--short", "HEAD"], summary).stdout.strip())
        if args.no_push:
            summary.push_result = "not pushed (--no-push)"
        else:
            push(summary)
        summary.outcome = "resolved conflicts and merged upstream/main"
        return 0
    except Exception as e:
        summary.outcome = "failed"
        print_failure(e)
        return 1
    finally:
        restore_original_branch(summary)
        if args.json:
            summary.print_json()
        else:
            summary.print_text()


if __name__ == "__main__":
    sys.exit(main())