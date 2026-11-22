#!/usr/bin/env python3
"""Resolve the review target before starting any model calls."""

import os
from collections.abc import Mapping
from pathlib import Path

from common import gh_json, progress


def review_target(env: Mapping[str, str]) -> int | None:
    if env["GITHUB_EVENT_NAME"] == "issue_comment":
        if env.get("COMMENT_BODY") != "/review" or not env.get("COMMENT_AUTHOR"):
            return None
        repo, author = env["GITHUB_REPOSITORY"], env["COMMENT_AUTHOR"]
        permissions = gh_json(["api", f"repos/{repo}/collaborators/{author}/permission"])
        if permissions["permission"] not in {"write", "admin"}:
            progress("Only users with repository write access can run /review")
            return None
    elif env["GITHUB_EVENT_NAME"] not in {"workflow_dispatch", "push"}:
        raise ValueError(f"Unsupported review event: {env['GITHUB_EVENT_NAME']}")

    pr = int(env["PR_NUMBER"])
    if pr < 1:
        raise ValueError("PR number must be positive")
    return pr


def main() -> None:
    pr = review_target(os.environ)
    if pr is not None:
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
            output.write(f"pr_number={pr}\n")


if __name__ == "__main__":
    main()
