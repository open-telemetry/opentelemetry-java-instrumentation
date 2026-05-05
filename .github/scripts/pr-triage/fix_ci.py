#!/usr/bin/env python3
"""Prepare and fix failing CI on a PR branch with a narrow Copilot handoff."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any

from common import (
    Summary,
    changed_files,
    commit_all_tracked,
    detect_repo,
    diff_check,
    download_actions_job_log,
    extract_job_id,
    gh_json,
    invoke_copilot,
    make_temp_dir,
    progress,
    push,
    run_pr_workflow,
    status_porcelain,
    untracked_files,
    write_json,
)


AGGREGATE_CHECK_SUFFIX = "required-status-check"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pr", type=int, help="pull request number")
    parser.add_argument("--no-push", action="store_true", help="commit but do not push")
    parser.add_argument("--keep-temp", action="store_true", help="reuse and retain the temp bundle directory")
    parser.add_argument("--skip-copilot", action="store_true", help="download logs and stop before invoking Copilot")
    return parser.parse_args()


def failed_checks(pr: int, summary: Summary) -> list[dict[str, Any]]:
    data = gh_json(["pr", "view", str(pr), "--json", "statusCheckRollup"], summary)
    return [
        check
        for check in (data.get("statusCheckRollup") or [])
        if (check.get("conclusion") or "").upper() == "FAILURE"
        and not (check.get("name") or "").endswith(AGGREGATE_CHECK_SUFFIX)
    ]


def job_family(name: str) -> str:
    return re.sub(r"\s+\([^)]*\)$", "", name).strip()


def write_ci_bundle(pr: int, checks: list[dict[str, Any]], directory: Path, summary: Summary) -> list[dict[str, Any]]:
    progress(f"Preparing CI failure bundle in {directory}")
    repo = detect_repo(summary)
    owner, repo_name = repo.split("/", 1)
    logs_dir = directory / "logs"
    logs_dir.mkdir(parents=True, exist_ok=True)

    bundle_checks: list[dict[str, Any]] = []
    seen_families: set[str] = set()
    for check in checks:
        name = check.get("name") or "unknown"
        job_id = extract_job_id(check)
        summary.failures.append(f"{name} ({job_id or 'no job id'})")
        entry: dict[str, Any] = {"name": name, "job_id": job_id}
        family = job_family(name)
        if job_id is None:
            pass
        elif family in seen_families:
            progress(f"Skipping sibling log for failed job: {name}")
            entry["log_note"] = "log download skipped; covered by representative sibling job"
        else:
            seen_families.add(family)
            progress(f"Downloading log for failed job: {name}")
            log_path = logs_dir / f"{job_id}.log"
            download_actions_job_log(owner, repo_name, job_id, log_path, summary)
            entry["log"] = str(log_path)
        bundle_checks.append(entry)

    write_json(directory / "summary.json", {"repo": repo, "pr": pr, "failed_checks": bundle_checks})
    summary.temp_dir = str(directory)
    return bundle_checks


def render_failed_jobs(checks: list[dict[str, Any]]) -> str:
    lines: list[str] = []
    for check in checks:
        lines.append(f"- {check['name']} (job ID: {check.get('job_id')})")
        if check.get("log"):
            lines.append(f"  - Log: {check['log']}")
        if check.get("log_note"):
            lines.append(f"  - {check['log_note']}")
    return "\n".join(lines)


def read_commit_message(path: Path) -> list[str]:
    if not path.exists():
        raise RuntimeError(f"Copilot did not write a commit message to {path}")
    text = path.read_text(encoding="utf-8").strip()
    paragraphs = [paragraph.strip() for paragraph in re.split(r"\n\s*\n", text) if paragraph.strip()]
    if not paragraphs:
        raise RuntimeError(f"Copilot commit message at {path} is empty")
    subject = paragraphs[0].splitlines()[0].strip()
    if not subject or len(subject) > 72:
        raise RuntimeError(f"Copilot commit message subject is empty or longer than 72 characters: {subject!r}")
    if any(line.startswith("#") for line in text.splitlines()):
        raise RuntimeError(f"Copilot commit message at {path} contains comment lines")
    return [subject, *paragraphs[1:]]


def read_prompt_improvement(path: Path, summary: Summary) -> None:
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8").strip()
    if not text or text.lower() in {"none", "no suggestions", "no changes"}:
        return
    summary.notes.append("Copilot suggested a reusable prompt improvement:")
    for line in text.splitlines()[:12]:
        if line.strip():
            summary.notes.append(line)


def copilot_prompt(pr: int, checks: list[dict[str, Any]], commit_message_path: Path, prompt_improvement_path: Path) -> str:
    return f"""You are fixing failing CI in opentelemetry-java-instrumentation.

The PR branch (#{pr}) is already checked out. The following CI jobs are failing.
Use the referenced log files as the source of truth:

{render_failed_jobs(checks)}

After fixing the issue, write a commit message to this exact file path:

{commit_message_path}

Before finishing, consider whether the CI-fix prompt or bundle should be improved
for future runs based on anything that slowed you down, was missing, ambiguous,
or required avoidable exploration. If there is a generally reusable improvement,
write a short note to this exact file path:

{prompt_improvement_path}

If no reusable prompt improvement is needed, do not create that file.

Commit message rules:
- First line: concise subject, 72 characters or fewer.
- Then a blank line.
- Then a short body explaining the root cause and the fix.
- Mention the validation command you ran, if any.
- Do not include markdown fences or comment lines.

Rules:
- Do not switch branches.
- Do not commit.
- Do not push.
- Do not rebase, merge, amend, or use force operations.
- Edit only files needed to fix the failures listed in the CI bundle.
- Use the downloaded log files as the source of truth.
- For deterministic formatting or generated-file failures (for example Spotless
  or FOSSA), run the corresponding Gradle task (for example `./gradlew spotlessApply`
  or `./gradlew generateFossaConfiguration`) instead of editing files by hand.
- If the failures are flaky or infrastructure-only, do not invent a code fix; leave the tree clean and explain why.
- When done, print a concise summary of the files changed and validation commands run.
"""


def main() -> int:
    args = parse_args()

    def body(summary: Summary) -> int:
        checks = failed_checks(args.pr, summary)
        if not checks:
            summary.outcome = "no failing checks found"
            return 0

        bundle_dir = make_temp_dir("otel-ci-fix", args.pr, args.keep_temp)
        bundle_checks = write_ci_bundle(args.pr, checks, bundle_dir, summary)

        if args.skip_copilot:
            summary.outcome = "downloaded CI logs; skipped Copilot handoff"
            return 0

        commit_message_path = bundle_dir / "commit-message.txt"
        prompt_improvement_path = bundle_dir / "prompt-improvement.md"
        response = invoke_copilot(copilot_prompt(args.pr, bundle_checks, commit_message_path, prompt_improvement_path), summary)
        (bundle_dir / "copilot-response.txt").write_text(response + "\n", encoding="utf-8")
        read_prompt_improvement(prompt_improvement_path, summary)

        diff_check(summary)
        if untracked_files(summary):
            raise RuntimeError("untracked files are present after CI fix; refusing to commit")
        summary.changed_files = changed_files(summary)
        if not status_porcelain(summary).strip():
            summary.outcome = "no code changes needed"
            return 0

        commit_message = read_commit_message(commit_message_path)
        commit_all_tracked(commit_message, summary)
        if args.no_push:
            summary.push_result = "not pushed (--no-push)"
        else:
            push(summary)
        summary.outcome = "CI fix committed"
        return 0

    return run_pr_workflow(args.pr, body)


if __name__ == "__main__":
    sys.exit(main())