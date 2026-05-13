#!/usr/bin/env python3
"""Run spotless on a PR branch, commit changes, push, and restore the branch."""

from __future__ import annotations

import argparse
import sys

from common import (
    Summary,
    changed_files,
    commit_all_tracked,
    diff_check,
    gradlew_cmd,
    progress,
    push,
    run,
    run_pr_workflow,
    status_porcelain,
    untracked_files,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pr", type=int, help="pull request number")
    parser.add_argument("--no-push", action="store_true", help="commit but do not push")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    def body(summary: Summary) -> int:
        progress("Running Spotless")
        run(gradlew_cmd("spotlessApply"), summary)
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

    return run_pr_workflow(args.pr, body)


if __name__ == "__main__":
    sys.exit(main())