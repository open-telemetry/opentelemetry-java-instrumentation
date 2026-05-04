#!/usr/bin/env python3
"""Run spotless on a PR branch, commit changes, push, and restore the branch."""

from __future__ import annotations

import argparse
import sys

from common import (
    Summary,
    changed_files,
    checkout_pr,
    commit_all_tracked,
    current_branch,
    diff_check,
    gradlew_cmd,
    print_failure,
    progress,
    push,
    require_clean_worktree,
    restore_original_branch,
    run,
    status_porcelain,
    untracked_files,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pr", type=int, help="pull request number")
    parser.add_argument("--json", action="store_true", help="print JSON summary")
    parser.add_argument("--no-push", action="store_true", help="commit but do not push")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    summary = Summary(pr=args.pr)
    try:
        require_clean_worktree(summary)
        summary.original_branch = current_branch(summary)
        checkout_pr(args.pr, summary)

        progress("Running Spotless")
        run(gradlew_cmd("spotless"), summary)
        progress("Checking Spotless changes")
        diff_check(summary)

        if untracked_files(summary):
            raise RuntimeError("spotless produced untracked files; refusing to continue")

        summary.changed_files = changed_files(summary)
        if not status_porcelain(summary).strip():
            progress("No Spotless changes found")
            summary.outcome = "PR was already spotless"
            return 0

        commit_all_tracked("Run spotless", summary)
        if args.no_push:
            summary.push_result = "not pushed (--no-push)"
        else:
            push(summary)
        summary.outcome = "spotless changes committed"
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