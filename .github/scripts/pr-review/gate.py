#!/usr/bin/env python3
"""Gate the pr-review workflow: resolve trigger, validate, emit outputs.

Reads trigger context from environment variables, decides whether the agent
should run, and writes outputs to $GITHUB_OUTPUT:

  should_run     - "true" if the agent should run, else "false"
  pr_number      - PR number to review
  model          - resolved Copilot model (default if override invalid)
  model_warning  - human-readable warning if the requested model was rejected
  triggered_by   - short string for the review-body footer
Required env: GH_TOKEN, EVENT_NAME, DEFAULT_MODEL, ALLOWED_MODELS, plus the
trigger-specific variables documented inline.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

from common import gh_json, progress


REVIEW_RE = re.compile(r"^/review(?:\s+(\S+))?\s*$")


def emit(outputs: dict[str, str]) -> None:
    path = os.environ.get("GITHUB_OUTPUT")
    if not path:
        for key, value in outputs.items():
            print(f"{key}={value}")
        return
    with Path(path).open("a", encoding="utf-8") as f:
        for key, value in outputs.items():
            if "\n" in value:
                f.write(f"{key}<<__GATE_EOF__\n{value}\n__GATE_EOF__\n")
            else:
                f.write(f"{key}={value}\n")


def skip(reason: str) -> int:
    progress(f"Gate: {reason} - skipping run.")
    emit(
        {
            "should_run": "false",
            "pr_number": "",
            "model": "",
            "model_warning": "",
            "triggered_by": "",
        }
    )
    return 0


def resolve_model(requested: str, default_model: str, allowed_models: str) -> tuple[str, str]:
    if not requested:
        return default_model, ""
    allowed = {m.strip() for m in allowed_models.split(",") if m.strip()}
    if requested in allowed:
        return requested, ""
    return (
        default_model,
        f"requested model `{requested}` is not in the allowlist; using default `{default_model}`.",
    )


def commenter_has_write_access(repo: str, login: str) -> bool:
    # gh returns non-zero (404) for users without an explicit collaborator
    # entry, which we treat the same as "no write access". This also denies
    # on transient gh/API failures, which is the safer default for a gate
    # that controls whether the reviewer agent runs.
    try:
        result = gh_json(
            ["api", f"repos/{repo}/collaborators/{login}/permission", "-q", ".permission"],
        )
    except Exception:
        return False
    # gh_json returns parsed JSON; with -q the output is a bare string.
    return result in {"admin", "write"}


class SkipRun(Exception):
    """Raised to abort the gate cleanly with a skip outcome."""


def resolve_trigger(env: dict[str, str]) -> tuple[str, str, str, str]:
    """Return (pr, model, warning, triggered_by). Raises SkipRun to skip."""
    event = env.get("EVENT_NAME", "")
    default_model = env.get("DEFAULT_MODEL", "")
    allowed_models = env.get("ALLOWED_MODELS", "")
    repo = env.get("GITHUB_REPOSITORY", "")

    if event == "issue_comment":
        pr = env.get("PR_FROM_COMMENT", "")
        if not pr:
            raise SkipRun("no PR number on issue_comment event")
        body = (env.get("COMMENT_BODY", "") or "").strip()
        match = REVIEW_RE.match(body)
        if not match:
            raise SkipRun("comment body does not match /review[ <model>]")
        author = env.get("COMMENT_AUTHOR", "")
        if not author or not commenter_has_write_access(repo, author):
            raise SkipRun(f"commenter @{author} lacks write permission")
        requested_model = match.group(1) or ""
        model, warning = resolve_model(requested_model, default_model, allowed_models)
        return pr, model, warning, f"`/review` by @{author}"

    raise SkipRun(f"unsupported event: {event}")


def pr_state(repo: str, pr: str) -> dict | None:
    try:
        return gh_json(
            ["pr", "view", pr, "--repo", repo, "--json", "state,number"],
        )
    except Exception:
        return None


def main() -> int:
    env = os.environ
    repo = env.get("GITHUB_REPOSITORY", "")

    try:
        pr, model, warning, triggered_by = resolve_trigger(env)
        info = pr_state(repo, pr)
        if not info:
            raise SkipRun(f"PR #{pr} not found")
        if info.get("state") != "OPEN":
            raise SkipRun(f"PR #{pr} is not open (state={info.get('state')})")
    except SkipRun as e:
        return skip(str(e))

    progress(f"Gate accepted: pr={pr} trigger={triggered_by} model={model}")
    emit(
        {
            "should_run": "true",
            "pr_number": str(pr),
            "model": model,
            "model_warning": warning,
            "triggered_by": triggered_by,
        }
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
