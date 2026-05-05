#!/usr/bin/env python3
"""Rerun failed jobs for the workflow run that triggered this workflow."""

from __future__ import annotations

import json
import os
import subprocess
import urllib.parse

DEFAULT_IGNORED_JOB_SUFFIXES = ("required-status-check",)
DEFAULT_MAX_FAILED_JOBS_PER_WORKFLOW_RUN = 5
DEFAULT_MAX_RERUN_ATTEMPTS = 2


def main() -> None:
    owner, repo = os.environ["GITHUB_REPOSITORY"].split("/", 1)
    run_id = os.environ["WORKFLOW_RUN_ID"]

    run = gh_get(f"/repos/{owner}/{repo}/actions/runs/{run_id}")
    label = build_label(owner, repo, run)

    if run["status"] != "completed" or run.get("conclusion") != "failure":
        print(f"Skipped {label}: status={run['status']}, conclusion={run.get('conclusion')}.")
        return

    max_rerun_attempts = int(os.getenv("MAX_RERUN_ATTEMPTS", DEFAULT_MAX_RERUN_ATTEMPTS))
    rerun_attempts = run["run_attempt"] - 1
    if rerun_attempts >= max_rerun_attempts:
        print(f"Skipped {label}: already rerun {rerun_attempts} times.")
        return

    ignored_job_suffixes = tuple(
        suffix.strip()
        for suffix in os.getenv("IGNORED_JOB_SUFFIXES", ",".join(DEFAULT_IGNORED_JOB_SUFFIXES)).split(",")
        if suffix.strip()
    )
    failed_real_jobs = [
        job
        for job in list_jobs_for_run(owner, repo, run["id"])
        if job.get("conclusion") == "failure"
        and not any(job["name"].endswith(suffix) for suffix in ignored_job_suffixes)
    ]

    if not failed_real_jobs:
        print(f"Skipped {label}: only ignored jobs failed.")
        return

    max_failed_jobs = int(
        os.getenv("MAX_FAILED_JOBS_PER_WORKFLOW_RUN", DEFAULT_MAX_FAILED_JOBS_PER_WORKFLOW_RUN)
    )
    if len(failed_real_jobs) > max_failed_jobs:
        print(
            f"Skipped {label}: {len(failed_real_jobs)} failed jobs"
            f" exceeded limit {max_failed_jobs}."
        )
        return

    subprocess.run(
        ["gh", "api", "--method", "POST", f"repos/{owner}/{repo}/actions/runs/{run['id']}/rerun-failed-jobs"],
        check=True,
    )
    job_list = ", ".join(f"{j['name']} ({j['id']})" for j in failed_real_jobs)
    print(f"::notice::{label}: reran failed jobs {job_list}.")


def build_label(owner: str, repo: str, run: dict) -> str:
    pr_number = resolve_pr_number(owner, repo, run)
    pr_label = f"PR #{pr_number}, " if pr_number is not None else ""
    return f"{pr_label}{run['name']} #{run['run_number']}, run {run['id']}, attempt {run['run_attempt']}"


def list_jobs_for_run(owner: str, repo: str, run_id: int) -> list[dict]:
    jobs: list[dict] = []
    page = 1
    while True:
        response = gh_get(
            f"/repos/{owner}/{repo}/actions/runs/{run_id}/jobs",
            {"filter": "latest", "per_page": "100", "page": str(page)},
        )
        jobs.extend(response["jobs"])
        if len(response["jobs"]) < 100:
            return jobs
        page += 1


def gh_get(path: str, query: dict[str, str] | None = None):
    url = path.removeprefix("/")
    if query:
        url += "?" + urllib.parse.urlencode(query)
    result = subprocess.run(["gh", "api", url], capture_output=True, text=True, check=True)
    return json.loads(result.stdout) if result.stdout.strip() else {}


def resolve_pr_number(owner: str, repo: str, run: dict) -> int | None:
    pull_requests = run.get("pull_requests") or []
    if pull_requests:
        return pull_requests[0].get("number")

    head_owner = ((run.get("head_repository") or {}).get("owner") or {}).get("login")
    head_branch = run.get("head_branch")
    if not head_owner or not head_branch:
        return None

    matches = gh_get(
        f"/repos/{owner}/{repo}/pulls",
        {"state": "open", "head": f"{head_owner}:{head_branch}", "per_page": "1"},
    )
    return matches[0]["number"] if matches else None


if __name__ == "__main__":
    main()