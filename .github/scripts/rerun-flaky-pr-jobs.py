#!/usr/bin/env python3
"""Rerun up to two failed jobs for the pull request CI run that triggered this workflow.

Reads the triggering workflow run id from the WORKFLOW_RUN_ID environment variable,
ignores the synthetic required-status-check job, and reruns eligible failed jobs
up to two times.
"""

from __future__ import annotations

import json
import os
import subprocess
import urllib.parse

MAX_FAILED_JOBS_PER_WORKFLOW_RUN = 5
MAX_RERUN_ATTEMPTS = 2


def main() -> None:
    owner, repo = os.environ["GITHUB_REPOSITORY"].split("/", 1)
    run_id = os.environ["WORKFLOW_RUN_ID"]

    run = gh_get(f"/repos/{owner}/{repo}/actions/runs/{run_id}")
    pr_number = resolve_pr_number(owner, repo, run)
    pr_label = f"PR #{pr_number}" if pr_number is not None else "PR unknown"
    label = f"{pr_label}, run {run['id']}, attempt {run['run_attempt']}"

    if run["status"] != "completed" or run.get("conclusion") != "failure":
        print(f"Skipped {label}: status={run['status']}, conclusion={run.get('conclusion')}.")
        return

    rerun_attempts = run["run_attempt"] - 1
    if rerun_attempts >= MAX_RERUN_ATTEMPTS:
        print(f"Skipped {label}: already rerun {rerun_attempts} times.")
        return

    failed_real_jobs = [
        job
        for job in list_jobs_for_run(owner, repo, run["id"])
        if job.get("conclusion") == "failure"
        and not job["name"].endswith("required-status-check")
    ]

    if not failed_real_jobs:
        print(f"Skipped {label}: only synthetic jobs failed.")
        return

    if len(failed_real_jobs) > MAX_FAILED_JOBS_PER_WORKFLOW_RUN:
        print(
            f"Skipped {label}: {len(failed_real_jobs)} failed jobs"
            f" exceeded limit {MAX_FAILED_JOBS_PER_WORKFLOW_RUN}."
        )
        return

    subprocess.run(
        ["gh", "api", "--method", "POST", f"repos/{owner}/{repo}/actions/runs/{run['id']}/rerun-failed-jobs"],
        check=True,
    )
    job_list = ", ".join(f"{j['name']} ({j['id']})" for j in failed_real_jobs)
    print(f"::notice::{label}: reran failed jobs {job_list}.")


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
