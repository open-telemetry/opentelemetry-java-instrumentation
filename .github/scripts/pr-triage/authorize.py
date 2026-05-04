#!/usr/bin/env python3
"""Authorize a PR triage slash command from an issue_comment event.

Runs in the `authorize-command` job with only `GITHUB_TOKEN`. Confirms
that both the commenter and the PR author have repository write access,
posts the help reply for `/help` directly, and emits the resolved
command name as a job output so the downstream worker job can gate on it.
"""

# Tokens visible to this script: GITHUB_TOKEN (read + pull-requests:write).
# NOT visible: COPILOT_GITHUB_TOKEN, OTELBOT_*.

from __future__ import annotations

import json
import sys
from urllib.parse import quote

from common import gh, gh_json
from triage_helpers import (
    COMMANDS,
    comment_on_pr,
    event_payload,
    event_repo,
    parsed_command,
    pr_number,
    write_job_output,
)


def gh_json_or_none(args: list[str]) -> object | None:
    result = gh(args, check=False)
    if result.returncode == 0:
        return json.loads(result.stdout or "null")
    if "HTTP 404" in result.stderr or "Not Found" in result.stderr:
        return None
    raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "gh api failed")


def has_repo_write(login: str) -> bool:
    encoded_login = quote(login, safe="")
    data = gh_json_or_none(["api", f"repos/{event_repo()}/collaborators/{encoded_login}/permission"])
    if not isinstance(data, dict):
        return False
    return data.get("permission") in {"admin", "maintain", "write"}


def authorization_reason() -> str | None:
    payload = event_payload()
    comment = payload.get("comment") or {}
    commenter = str((comment.get("user") or {}).get("login") or "")

    if not has_repo_write(commenter):
        return (
            "for security reasons, PR triage commands can only be run by users "
            "who have write access to this repository"
        )

    pr = gh_json(["api", f"repos/{event_repo()}/pulls/{pr_number()}"])
    author = pr["user"]["login"]
    if not has_repo_write(author):
        return (
            "for security reasons, PR triage commands are only supported on PRs "
            "from authors who already have write access to this repository"
        )
    return None


def help_message() -> str:
    supported = ", ".join(f"`{command_name}`" for command_name in COMMANDS)
    return f"Supported PR triage commands: {supported}.\n"


def main() -> int:
    requested, command = parsed_command()
    if requested != "/help" and not command:
        # Not a recognized PR triage command: stay silent and skip.
        write_job_output(allowed="false", command="")
        return 0
    reason = authorization_reason()
    if reason:
        comment_on_pr(f"I did not run `{requested}`: {reason}.")
        write_job_output(allowed="false", command="")
        return 0
    if requested == "/help":
        # /help is small enough to handle here; this avoids spinning up the
        # worker/poster pipeline (and Java/Gradle setup) for a static reply.
        comment_on_pr(help_message())
        write_job_output(allowed="false", command="help")
        return 0
    write_job_output(allowed="true", command=command)
    return 0


if __name__ == "__main__":
    sys.exit(main())
