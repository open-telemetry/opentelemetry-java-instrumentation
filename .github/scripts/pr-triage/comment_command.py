#!/usr/bin/env python3
"""Run PR triage slash commands from issue comments."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import traceback
from pathlib import Path
from typing import Any
from urllib.parse import quote

from common import REPO_ROOT, gh, gh_json, progress


COMMANDS = {
    "/spotless": "spotless",
    "/fix": "fix",
    "/update-branch": "update_branch",
    "/review": "review",
}
OUTPUT_LIMIT = 6000


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("authorize", help="authorize the issue commenter and PR author")
    subparsers.add_parser("execute", help="run the selected PR triage command and comment with the result")
    return parser.parse_args()


def event_payload() -> dict[str, Any]:
    path = os.environ.get("GITHUB_EVENT_PATH")
    if not path:
        raise RuntimeError("GITHUB_EVENT_PATH is not set")
    return json.loads(Path(path).read_text(encoding="utf-8"))


def event_repo() -> str:
    return os.environ["GITHUB_REPOSITORY"]


def pr_number() -> str:
    return os.environ["PR_NUMBER"]


def parsed_command() -> tuple[str, str]:
    payload = event_payload()
    comment = payload.get("comment") or {}
    body = str(comment.get("body") or "").strip()
    first_line = body.splitlines()[0].strip() if body else ""
    requested = first_line.split(maxsplit=1)[0].lower() if first_line else ""
    command = COMMANDS.get(requested, "")
    return requested, command


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


def comment_on_pr(body: str) -> None:
    gh(["issue", "comment", pr_number(), "--repo", event_repo(), "--body", body])


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


def authorize_command() -> int:
    requested, command = parsed_command()
    if requested != "/help" and not command:
        # Not a recognized PR triage command: stay silent and skip.
        print("false")
        return 0
    reason = authorization_reason()
    if reason:
        comment_on_pr(f"I did not run `{requested}`: {reason}.")
        print("false")
        return 0
    print("true")
    return 0


def output_file() -> Path:
    return Path(os.environ["RUNNER_TEMP"]) / "pr-triage-output.txt"


def command_args(command: str) -> list[str]:
    if command == "spotless":
        return [sys.executable, ".github/scripts/pr-triage/spotless.py", pr_number()]
    if command == "fix":
        return [sys.executable, ".github/scripts/pr-triage/fix.py", pr_number()]
    if command == "update_branch":
        return [sys.executable, ".github/scripts/pr-triage/update_branch.py", pr_number()]
    if command == "review":
        return [sys.executable, ".github/scripts/pr-triage/review.py", pr_number()]
    raise RuntimeError(f"Unknown command: {command}")


def run_command(command: str) -> int:
    cmd = command_args(command)
    progress("Running: " + " ".join(cmd))
    path = output_file()
    with path.open("w", encoding="utf-8", errors="replace") as output:
        proc = subprocess.Popen(
            cmd,
            cwd=REPO_ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
        if proc.stdout is not None:
            for line in proc.stdout:
                print(line, end="", flush=True)
                output.write(line)
        status = proc.wait()
    return status


def finish_command(requested: str, exit_code: int) -> int:
    if exit_code == 0:
        status = "completed successfully"
    else:
        status = "failed"

    path = output_file()
    if path.exists():
        output = path.read_text(encoding="utf-8", errors="replace")
        if len(output) > OUTPUT_LIMIT:
            output = "...[output truncated]...\n" + output[-OUTPUT_LIMIT:]
    else:
        output = "No command output was captured."

    comment_on_pr(f"`{requested}` {status}.\n\n```text\n{output}\n```")
    return 0


def execute_command() -> int:
    requested, command = parsed_command()
    if requested == "/help":
        supported = ", ".join(f"`{command_name}`" for command_name in COMMANDS)
        comment_on_pr(f"Supported PR triage commands: {supported}.")
        return 0
    if not command:
        # authorize_command() already filtered unsupported/unauthorized cases.
        return 0

    try:
        exit_code = run_command(command)
    except Exception:
        exit_code = 1
        output_file().write_text(traceback.format_exc(), encoding="utf-8", errors="replace")
        finish_command(requested, exit_code)
        raise

    finish_command(requested, exit_code)
    return exit_code


def main() -> int:
    args = parse_args()
    if args.command == "authorize":
        return authorize_command()
    if args.command == "execute":
        return execute_command()
    raise AssertionError(args.command)


if __name__ == "__main__":
    sys.exit(main())
