#!/usr/bin/env python3
"""Shared helpers for PR maintenance agents.

The scripts in this directory deliberately keep git, GitHub, commit, push, and
branch restoration in deterministic Python code. Copilot CLI is only used for
the parts that need judgment or code editing.
"""

from __future__ import annotations

import json
import os
import re
import shlex
import subprocess
import sys
import tempfile
from collections.abc import Callable
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[3]
COPILOT_MODEL = os.environ.get("PR_AGENT_COPILOT_MODEL", "gpt-5.5")


class WorkflowError(RuntimeError):
    """A user-facing workflow failure."""


@dataclass
class Summary:
    pr: int
    pr_url: str | None = None
    review_url: str | None = None
    original_branch: str = ""
    pr_branch: str = ""
    restored_branch: str | None = None
    restoration_note: str | None = None
    outcome: str = ""
    changed_files: list[str] = field(default_factory=list)
    commits: list[str] = field(default_factory=list)
    push_result: str | None = None
    failures: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)
    temp_dir: str | None = None

    def print_text(self) -> None:
        print(f"PR: #{self.pr}")
        if self.original_branch:
            print(f"Original branch: {self.original_branch}")
        if self.pr_branch:
            print(f"PR branch: {self.pr_branch}")
        if self.restored_branch:
            print(f"Restored branch: {self.restored_branch}")
        if self.restoration_note:
            print(f"Branch restoration: {self.restoration_note}")
        if self.outcome:
            print(f"Outcome: {self.outcome}")
        if self.failures:
            print("Failures found:")
            for failure in self.failures:
                print(f"- {failure}")
        if self.changed_files:
            print("Files changed:")
            for path in self.changed_files:
                print(f"- {path}")
        if self.commits:
            print("Commits created:")
            for sha in self.commits:
                print(f"- {sha}")
        if self.push_result:
            print(f"Push result: {self.push_result}")
        if self.temp_dir and self.outcome == "failed":
            print(f"Work bundle: {display_path(self.temp_dir)}")
        if self.notes:
            print("Notes:")
            for note in self.notes:
                print(f"- {note}")
        if self.pr_url or self.review_url:
            print("Links:")
            if self.pr_url:
                print(f"- PR: {self.pr_url}")
            if self.review_url:
                print(f"- Review: {self.review_url}")


def format_cmd(cmd: list[str]) -> str:
    return " ".join(shlex.quote(part) for part in cmd)


def display_path(path: str) -> str:
    try:
        return str(Path(path).resolve().relative_to(REPO_ROOT))
    except ValueError:
        return path


def progress(message: str) -> None:
    print(f"[pr-triage] {message}", flush=True)


def run(cmd: list[str], summary: Summary | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    if summary is not None:
        progress(f"Running: {format_cmd(cmd)}")
    return subprocess.run(
        cmd,
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=check,
    )


def git(args: list[str], summary: Summary | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run(["git", *args], summary, check)


def gh(args: list[str], summary: Summary | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run(["gh", *args], summary, check)


def gh_json(args: list[str], summary: Summary | None = None) -> Any:
    result = gh(args, summary)
    return json.loads(result.stdout or "null")


def current_branch(summary: Summary | None = None) -> str:
    branch = git(["rev-parse", "--abbrev-ref", "HEAD"], summary).stdout.strip()
    if branch == "HEAD":
        raise WorkflowError("refusing to start from detached HEAD")
    return branch


def status_porcelain(summary: Summary | None = None) -> str:
    return git(["status", "--porcelain"], summary).stdout


def require_clean_worktree(summary: Summary) -> None:
    status = status_porcelain(summary)
    if status.strip():
        raise WorkflowError("working tree is dirty; stash or commit changes before running this script")


def merge_in_progress() -> bool:
    return git(["rev-parse", "-q", "--verify", "MERGE_HEAD"], check=False).returncode == 0


def unmerged_paths(summary: Summary | None = None) -> list[str]:
    output = git(["diff", "--name-only", "--diff-filter=U"], summary).stdout
    return [line for line in output.splitlines() if line]


def changed_files(summary: Summary | None = None) -> list[str]:
    output = git(["diff", "--name-only"], summary).stdout
    return [line for line in output.splitlines() if line]


def staged_or_unstaged_files(summary: Summary | None = None) -> list[str]:
    files: list[str] = []
    for line in status_porcelain(summary).splitlines():
        if not line.startswith("?? "):
            files.append(line[3:])
    return files


def untracked_files(summary: Summary | None = None) -> list[str]:
    return [line[3:] for line in status_porcelain(summary).splitlines() if line.startswith("?? ")]


def restore_original_branch(summary: Summary) -> None:
    if not summary.original_branch:
        return
    try:
        branch = current_branch()
    except WorkflowError as e:
        summary.restoration_note = str(e)
        return
    if branch == summary.original_branch:
        summary.restored_branch = branch
        return
    if merge_in_progress():
        summary.restoration_note = "not restored because a merge is in progress"
        return
    if status_porcelain().strip():
        summary.restoration_note = "not restored because the working tree has uncommitted changes"
        return
    progress(f"Restoring original branch: {summary.original_branch}")
    git(["checkout", summary.original_branch], summary)
    summary.restored_branch = summary.original_branch


def detect_repo(summary: Summary | None = None) -> str:
    return gh(["repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"], summary).stdout.strip()


def pr_view(pr: int, summary: Summary) -> dict[str, Any]:
    fields = "headRepositoryOwner,headRepository,headRefName,isCrossRepository,maintainerCanModify,url"
    return gh_json(["pr", "view", str(pr), "--json", fields], summary)


def remember_pr_url(metadata: dict[str, Any], summary: Summary) -> None:
    url = metadata.get("url")
    if isinstance(url, str) and url:
        summary.pr_url = url


def authed_login(summary: Summary) -> str:
    return gh(["api", "user", "--jq", ".login"], summary).stdout.strip()


def ensure_pr_push_allowed(pr: int, summary: Summary) -> dict[str, Any]:
    metadata = pr_view(pr, summary)
    head_owner = ((metadata.get("headRepositoryOwner") or {}).get("login") or "").strip()
    is_cross_repo = bool(metadata.get("isCrossRepository"))
    maintainer_can_modify = bool(metadata.get("maintainerCanModify"))
    if is_cross_repo and not maintainer_can_modify and head_owner != authed_login(summary):
        raise WorkflowError(
            "PR branch is in a fork that does not allow maintainer edits; cannot push fixes"
        )
    return metadata


def checkout_pr(pr: int, summary: Summary) -> dict[str, Any]:
    progress(f"Checking out PR #{pr}")
    gh(["pr", "checkout", str(pr)], summary)
    summary.pr_branch = current_branch(summary)
    metadata = ensure_pr_push_allowed(pr, summary)
    remember_pr_url(metadata, summary)
    return metadata


def checkout_pr_no_push_check(pr: int, summary: Summary) -> None:
    progress(f"Checking out PR #{pr}")
    gh(["pr", "checkout", str(pr)], summary)
    summary.pr_branch = current_branch(summary)
    remember_pr_url(pr_view(pr, summary), summary)


def run_pr_workflow(pr: int, body: Callable[[Summary], int], *, push_required: bool = True) -> int:
    summary = Summary(pr=pr)
    try:
        require_clean_worktree(summary)
        summary.original_branch = current_branch(summary)
        if push_required:
            checkout_pr(pr, summary)
        else:
            checkout_pr_no_push_check(pr, summary)
        return body(summary)
    except Exception as e:
        summary.outcome = "failed"
        print_failure(e)
        return 1
    finally:
        restore_original_branch(summary)
        summary.print_text()


def gradlew_cmd(task: str) -> list[str]:
    if os.name == "nt":
        return [str(REPO_ROOT / "gradlew.bat"), task]
    return ["./gradlew", task]


def commit_all_tracked(message: str | list[str], summary: Summary) -> str:
    if untracked_files(summary):
        raise WorkflowError("untracked files are present; refusing to commit files not produced by the workflow")
    messages = [message] if isinstance(message, str) else message
    commit_args = ["commit"]
    for paragraph in messages:
        commit_args.extend(["-m", paragraph])
    progress(f"Committing tracked changes: {messages[0]}")
    git(["add", "-u"], summary)
    git(commit_args, summary)
    sha = git(["rev-parse", "--short", "HEAD"], summary).stdout.strip()
    summary.commits.append(sha)
    return sha


def push(summary: Summary) -> None:
    progress("Pushing PR branch")
    git(["push"], summary)
    summary.push_result = "pushed successfully"

def diff_check(summary: Summary) -> None:
    git(["diff", "--check"], summary)


def make_temp_dir(prefix: str, pr: int, keep_temp: bool) -> Path:
    base_dir = REPO_ROOT / "build" / "pr-triage"
    base_dir.mkdir(parents=True, exist_ok=True)
    if keep_temp:
        path = base_dir / f"{prefix}-{pr}"
        path.mkdir(parents=True, exist_ok=True)
        progress(f"Using work bundle directory: {path}")
        return path
    path = Path(tempfile.mkdtemp(prefix=f"{prefix}-{pr}-", dir=base_dir))
    progress(f"Using work bundle directory: {path}")
    return path


def download_actions_job_log(owner: str, repo: str, job_id: int, path: Path, summary: Summary) -> None:
    api_path = f"repos/{owner}/{repo}/actions/jobs/{job_id}/logs"
    cmd = ["gh", "api", "-H", "Accept: application/vnd.github+json", api_path]
    progress(f"Downloading Actions job log {job_id} to {path}")
    with path.open("wb") as output:
        subprocess.run(
            cmd,
            cwd=REPO_ROOT,
            stdout=output,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=True,
        )


def extract_job_id(check: dict[str, Any]) -> int | None:
    details_url = check.get("detailsUrl") or check.get("details_url") or ""
    match = re.search(r"/job/(\d+)", details_url)
    if match:
        return int(match.group(1))
    value = check.get("databaseId") or check.get("database_id")
    return int(value) if isinstance(value, int) else None


def invoke_copilot(prompt: str, summary: Summary) -> str:
    cmd = ["copilot", "-p", prompt, "--allow-all-tools", "--model", COPILOT_MODEL]
    progress(f"Handing off to Copilot CLI using {COPILOT_MODEL}; streaming output below")
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
    output_parts: list[str] = []
    if proc.stdout is not None:
        for line in proc.stdout:
            print(line, end="", flush=True)
            output_parts.append(line)
    returncode = proc.wait()
    output = "".join(output_parts)
    if returncode != 0:
        raise subprocess.CalledProcessError(
            returncode,
            ["copilot", "-p", "<generated prompt>", "--allow-all-tools", "--model", COPILOT_MODEL],
            output,
            "",
        )
    progress("Copilot CLI handoff completed")
    return output.strip()


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def print_failure(error: Exception) -> None:
    print(f"ERROR: {error}", file=sys.stderr)
    if isinstance(error, subprocess.CalledProcessError):
        if error.stdout and error.stdout.strip():
            print("--- stdout ---", file=sys.stderr)
            print(error.stdout, file=sys.stderr)
        if error.stderr and error.stderr.strip():
            print("--- stderr ---", file=sys.stderr)
            print(error.stderr, file=sys.stderr)