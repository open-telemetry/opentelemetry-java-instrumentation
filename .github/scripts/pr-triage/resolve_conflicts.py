#!/usr/bin/env python3
"""Merge upstream/main into a PR branch, using Copilot only for conflicts."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import TypedDict

from common import (
    Summary,
    diff_check,
    git,
    invoke_copilot,
    make_temp_dir,
    progress,
    push,
    run_pr_workflow,
    unmerged_paths,
    write_json,
)


CONFLICT_STAGES = ((1, "base"), (2, "ours-head"), (3, "theirs-merge-head"))


class ConflictEntry(TypedDict):
    path: str
    conflict_diff: str
    stage_snapshots: list[str]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pr", type=int, help="pull request number")
    parser.add_argument("--upstream", default="upstream", help="upstream remote name")
    parser.add_argument("--no-push", action="store_true", help="commit locally but do not push to the PR")
    parser.add_argument("--keep-temp", action="store_true", help="reuse and retain the temp bundle directory")
    return parser.parse_args()


def safe_conflict_name(path: str) -> str:
    return path.replace("/", "__").replace("\\", "__")


def write_stage_snapshot(directory: Path, path: str, summary: Summary) -> list[str]:
    stage_paths: list[str] = []
    for stage, label in CONFLICT_STAGES:
        result = git(["show", f":{stage}:{path}"], summary, check=False)
        if result.returncode != 0:
            continue
        snapshot_path = directory / f"{safe_conflict_name(path)}.{label}"
        snapshot_path.write_text(result.stdout, encoding="utf-8")
        stage_paths.append(str(snapshot_path))
    return stage_paths


def write_conflict_bundle(directory: Path, summary: Summary) -> Path:
    progress(f"Preparing merge conflict bundle in {directory}")
    directory.mkdir(parents=True, exist_ok=True)
    files = unmerged_paths(summary)
    staged_versions_dir = directory / "staged-versions"
    staged_versions_dir.mkdir(parents=True, exist_ok=True)

    conflicts: list[ConflictEntry] = []
    (directory / "status.txt").write_text(git(["status", "--porcelain"], summary).stdout, encoding="utf-8")
    (directory / "head-log.txt").write_text(git(["log", "--oneline", "-n", "20", "HEAD"], summary).stdout, encoding="utf-8")
    (directory / "merge-head-log.txt").write_text(git(["log", "--oneline", "-n", "20", "MERGE_HEAD"], summary).stdout, encoding="utf-8")
    for path in files:
        safe_name = safe_conflict_name(path)
        diff_path = directory / f"{safe_name}.diff"
        diff_path.write_text(git(["diff", "--", path], summary).stdout, encoding="utf-8")
        conflicts.append(
            {
                "path": path,
                "conflict_diff": str(diff_path),
                "stage_snapshots": write_stage_snapshot(staged_versions_dir, path, summary),
            }
        )
    write_json(directory / "conflicts.json", {"files": conflicts})
    plan = directory / "conflict-plan.md"
    lines = ["# Merge Conflict Resolution Plan", "", "## Conflicted Files", ""]
    for conflict in conflicts:
        lines.append(f"- {conflict['path']}")
        lines.append(f"  - Conflict diff: {conflict['conflict_diff']}")
        for stage_snapshot in conflict["stage_snapshots"]:
            lines.append(f"  - Stage snapshot: {stage_snapshot}")
    lines.extend(
        [
            "",
            "## Context",
            "",
            f"- Status: {directory / 'status.txt'}",
            f"- HEAD log: {directory / 'head-log.txt'}",
            f"- MERGE_HEAD log: {directory / 'merge-head-log.txt'}",
            "",
            "## Resolution Checklist",
            "",
            "- Inspect each conflict diff and all available stage snapshots before editing.",
            "- Identify what behavior HEAD changed and what behavior MERGE_HEAD changed.",
            "- Preserve both behaviors when they can coexist.",
            "- If one side is intentionally dropped, record why it is obsolete or incompatible.",
            "- After editing, compare the resolved file against both sides before staging it.",
            "",
        ]
    )
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
- For each conflicted file, inspect the conflict diff plus the available stage snapshots before editing: base, ours-HEAD, and theirs-MERGE_HEAD.
- Preserve the intent of both HEAD and MERGE_HEAD when they can coexist; do not remove helpers, validation, generated bundle content, tests, or workflow steps from either side unless they are clearly obsolete after the merge.
- If you choose one side over the other, explain why the dropped side is incompatible or superseded.
- After editing each file, compare the resolved result against both side snapshots before staging it.
- If the conflict requires product judgment or involves binary/non-text files, stop and explain.
- Stage only files that you resolved.
- When done, print a concise summary of each resolved file.
"""


def main() -> int:
    args = parse_args()

    def workflow(summary: Summary) -> int:
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

    return run_pr_workflow(args.pr, workflow, push_required=not args.no_push)


if __name__ == "__main__":
    sys.exit(main())