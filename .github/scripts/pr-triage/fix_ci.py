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
    checkout_pr,
    commit_all_tracked,
    current_branch,
    detect_repo,
    diff_check,
    download_actions_job_log,
    extract_job_id,
    gh_json,
    gradlew_cmd,
    invoke_copilot,
    make_temp_dir,
    print_failure,
    progress,
    push,
    require_clean_worktree,
    restore_original_branch,
    run,
    status_porcelain,
    untracked_files,
    write_json,
)


ERROR_PATTERN = re.compile(r"error:|Task .*FAILED|FAILURE: Build failed|\[ERROR\]|markdownlint", re.IGNORECASE)
AGGREGATE_CHECK_NAMES = {"required-status-check"}
MAX_LOGS_PER_JOB_FAMILY = 3


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pr", type=int, help="pull request number")
    parser.add_argument("--json", action="store_true", help="print JSON summary")
    parser.add_argument("--no-push", action="store_true", help="commit but do not push")
    parser.add_argument("--keep-temp", action="store_true", help="reuse and retain the temp bundle directory")
    parser.add_argument("--skip-copilot", action="store_true", help="download logs and stop before invoking Copilot")
    return parser.parse_args()


def failed_checks(pr: int, summary: Summary) -> list[dict[str, Any]]:
    data = gh_json(["pr", "view", str(pr), "--json", "statusCheckRollup"], summary)
    checks = data.get("statusCheckRollup") or []
    result = []
    for check in checks:
        name = check.get("name") or "unknown"
        conclusion = (check.get("conclusion") or "").upper()
        if conclusion != "FAILURE":
            continue
        if name in AGGREGATE_CHECK_NAMES or name.endswith("required-status-check"):
            continue
        result.append(check)
    return result


def job_family(name: str) -> str:
    return re.sub(r"\s+\([^)]*\)$", "", name).strip()


def matrix_tokens(name: str) -> set[str]:
    match = re.search(r"\(([^)]*)\)\s*$", name)
    if not match:
        return set()
    return {token.strip().lower() for token in match.group(1).split(",") if token.strip()}


def selected_log_indexes(checks: list[dict[str, Any]], summary: Summary) -> set[int]:
    progress(f"Selecting representative logs for {len(checks)} failing checks")
    families: dict[str, list[int]] = {}
    for index, check in enumerate(checks):
        name = check.get("name") or "unknown"
        families.setdefault(job_family(name), []).append(index)

    selected: set[int] = set()
    for family, indexes in families.items():
        downloadable = [index for index in indexes if extract_job_id(checks[index]) is not None]
        if len(downloadable) <= MAX_LOGS_PER_JOB_FAMILY:
            selected.update(downloadable)
            continue

        picked = [downloadable[0]]
        seen_tokens = set(matrix_tokens(checks[downloadable[0]].get("name") or ""))
        remaining = downloadable[1:]
        while remaining and len(picked) < MAX_LOGS_PER_JOB_FAMILY:
            best_index = max(
                remaining,
                key=lambda index: len(matrix_tokens(checks[index].get("name") or "") - seen_tokens),
            )
            picked.append(best_index)
            seen_tokens.update(matrix_tokens(checks[best_index].get("name") or ""))
            remaining.remove(best_index)

        selected.update(picked)
        summary.notes.append(
            f"Downloaded {len(picked)} representative logs for {family}; "
            f"skipped {len(downloadable) - len(picked)} sibling logs"
        )
    return selected


def extract_snippet(log_text: str, max_lines: int = 160) -> str:
    lines = log_text.splitlines()
    selected: list[str] = []
    for index, line in enumerate(lines):
        if ERROR_PATTERN.search(line):
            start = max(0, index - 2)
            end = min(len(lines), index + 21)
            selected.extend(lines[start:end])
            selected.append("---")
            if len(selected) >= max_lines:
                break
    if selected:
        return "\n".join(selected[:max_lines]) + "\n"
    return "\n".join(lines[-max_lines:]) + "\n"


def write_ci_bundle(pr: int, checks: list[dict[str, Any]], directory: Path, summary: Summary) -> Path:
    progress(f"Preparing CI failure bundle in {directory}")
    repo = detect_repo(summary)
    owner, repo_name = repo.split("/", 1)
    download_indexes = selected_log_indexes(checks, summary)
    logs_dir = directory / "logs"
    snippets_dir = directory / "snippets"
    logs_dir.mkdir(parents=True, exist_ok=True)
    snippets_dir.mkdir(parents=True, exist_ok=True)

    bundle_checks: list[dict[str, Any]] = []
    for index, check in enumerate(checks):
        name = check.get("name") or "unknown"
        job_id = extract_job_id(check)
        summary.failures.append(f"{name} ({job_id or 'no job id'})")
        entry = {
            "name": name,
            "family": job_family(name),
            "job_id": job_id,
            "details_url": check.get("detailsUrl") or check.get("details_url"),
            "database_id": check.get("databaseId") or check.get("database_id"),
            "log_sampled": index in download_indexes,
        }
        if job_id is not None and index in download_indexes:
            progress(f"Sampling log for failed job: {name}")
            log_path = logs_dir / f"{job_id}.log"
            snippet_path = snippets_dir / f"{job_id}-errors.txt"
            download_actions_job_log(owner, repo_name, job_id, log_path, summary)
            snippet_path.write_text(extract_snippet(log_path.read_text(encoding="utf-8", errors="replace")), encoding="utf-8")
            entry["log"] = str(log_path)
            entry["snippet"] = str(snippet_path)
        elif job_id is not None:
            progress(f"Skipping sibling log for failed job: {name}")
            entry["log_note"] = "log download skipped; covered by representative sibling job"
        bundle_checks.append(entry)

    write_json(directory / "summary.json", {"repo": repo, "pr": pr, "failed_checks": bundle_checks})
    plan = directory / "ci-plan.md"
    plan.write_text(render_ci_plan(pr, bundle_checks), encoding="utf-8")
    summary.temp_dir = str(directory)
    return plan


def render_ci_plan(pr: int, checks: list[dict[str, Any]]) -> str:
    lines = [f"# CI Failure Analysis Plan for PR #{pr}", "", "## Failed Jobs", ""]
    for check in checks:
        lines.append(f"- {check['name']} (job ID: {check.get('job_id')})")
        if check.get("snippet"):
            lines.append(f"  - Snippet: {check['snippet']}")
        if check.get("log"):
            lines.append(f"  - Full log: {check['log']}")
        if check.get("log_note"):
            lines.append(f"  - {check['log_note']}")
    lines.extend(["", "## Notes", "", "- Python downloaded logs before Copilot handoff.", ""])
    return "\n".join(lines)


def maybe_apply_deterministic_fix(bundle_dir: Path, plan_path: Path, summary: Summary) -> str | None:
    text = plan_path.read_text(encoding="utf-8")
    for snippet in (bundle_dir / "snippets").glob("*.txt"):
        text += "\n" + snippet.read_text(encoding="utf-8", errors="replace")
    if "spotless" not in text.lower():
        return None
    run(gradlew_cmd("spotless"), summary)
    summary.notes.append("Applied deterministic spotless fix based on CI logs")
    return "spotless"


def ci_fix_commit_message(checks: list[dict[str, Any]], changed_paths: list[str]) -> list[str]:
    families = sorted({job_family(check.get("name") or "unknown") for check in checks})
    if len(families) == 1:
        subject = f"Fix CI failure in {families[0]}"
    else:
        subject = f"Fix CI failures in {len(families)} job families"
    body_lines = ["Failed jobs:"]
    body_lines.extend(f"- {family}" for family in families[:8])
    if len(families) > 8:
        body_lines.append(f"- ... and {len(families) - 8} more")
    body_lines.append("")
    body_lines.append("Changed files:")
    body_lines.extend(f"- {path}" for path in changed_paths[:12])
    if len(changed_paths) > 12:
        body_lines.append(f"- ... and {len(changed_paths) - 12} more")
    return [subject, "\n".join(body_lines)]


def deterministic_ci_fix_commit_message(fix_kind: str, checks: list[dict[str, Any]], changed_paths: list[str]) -> list[str]:
    if fix_kind == "spotless":
        subject = "Apply spotless formatting"
        body_lines = [
            "CI reported Spotless formatting violations.",
            "",
            "Ran:",
            "- ./gradlew spotless",
            "",
            "Failed jobs:",
        ]
        body_lines.extend(f"- {family}" for family in sorted({job_family(check.get("name") or "unknown") for check in checks})[:8])
        body_lines.append("")
        body_lines.append("Formatted files:")
        body_lines.extend(f"- {path}" for path in changed_paths[:12])
        if len(changed_paths) > 12:
            body_lines.append(f"- ... and {len(changed_paths) - 12} more")
        return [subject, "\n".join(body_lines)]
    return ci_fix_commit_message(checks, changed_paths)


def read_copilot_commit_message(path: Path, summary: Summary) -> list[str] | None:
    if not path.exists():
        return None
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return None
    paragraphs = [paragraph.strip() for paragraph in re.split(r"\n\s*\n", text) if paragraph.strip()]
    if not paragraphs:
        return None
    subject = paragraphs[0].splitlines()[0].strip()
    if not subject or len(subject) > 72:
        summary.notes.append("Ignored Copilot commit message because the subject is empty or too long")
        return None
    if any(line.startswith("#") for line in text.splitlines()):
        summary.notes.append("Ignored Copilot commit message because it contained comment lines")
        return None
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


def copilot_prompt(plan_path: Path, commit_message_path: Path, prompt_improvement_path: Path) -> str:
    return f"""You are fixing failing CI in opentelemetry-java-instrumentation.

The PR branch is already checked out. Read this CI bundle first:

{plan_path}

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
- Use the downloaded log snippets and full logs as the source of truth.
- If the failures are flaky or infrastructure-only, do not invent a code fix; leave the tree clean and explain why.
- When done, print a concise summary of the files changed and validation commands run.
"""


def main() -> int:
    args = parse_args()
    summary = Summary(pr=args.pr)
    try:
        require_clean_worktree(summary)
        summary.original_branch = current_branch(summary)
        checkout_pr(args.pr, summary)

        checks = failed_checks(args.pr, summary)
        if not checks:
            summary.outcome = "no failing checks found"
            return 0

        bundle_dir = make_temp_dir("otel-ci-fix", args.pr, args.keep_temp)
        plan_path = write_ci_bundle(args.pr, checks, bundle_dir, summary)

        deterministic_fix = maybe_apply_deterministic_fix(bundle_dir, plan_path, summary)
        if deterministic_fix is None and not args.skip_copilot:
            commit_message_path = bundle_dir / "commit-message.txt"
            prompt_improvement_path = bundle_dir / "prompt-improvement.md"
            response = invoke_copilot(copilot_prompt(plan_path, commit_message_path, prompt_improvement_path), summary)
            (bundle_dir / "copilot-response.txt").write_text(response + "\n", encoding="utf-8")
            read_prompt_improvement(prompt_improvement_path, summary)
        elif args.skip_copilot:
            summary.outcome = "downloaded CI logs; skipped Copilot handoff"
            return 0

        diff_check(summary)
        if untracked_files(summary):
            raise RuntimeError("untracked files are present after CI fix; refusing to commit")
        summary.changed_files = changed_files(summary)
        if not status_porcelain(summary).strip():
            summary.outcome = "no code changes needed"
            return 0

        commit_message = (
            deterministic_ci_fix_commit_message(deterministic_fix, checks, summary.changed_files)
            if deterministic_fix is not None
            else read_copilot_commit_message(bundle_dir / "commit-message.txt", summary)
        )
        if commit_message is None:
            commit_message = ci_fix_commit_message(checks, summary.changed_files)
        commit_all_tracked(commit_message, summary)
        if args.no_push:
            summary.push_result = "not pushed (--no-push)"
        else:
            push(summary)
        summary.outcome = "CI fix committed"
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