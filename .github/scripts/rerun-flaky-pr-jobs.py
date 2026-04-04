#!/usr/bin/env python3
"""Rerun up to two failed jobs for recent pull request CI runs.

Scans recent failed runs of build-pull-request.yml, ignores the synthetic
required-status-check job, and reruns eligible failed jobs once.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path

LOOKBACK_HOURS = 2
MAX_ELIGIBLE_FAILURES = 2


def main() -> None:
    repository = os.environ.get("GITHUB_REPOSITORY") or get_current_repository()
    owner, repo = repository.split("/", 1)
    lookback_cutoff = datetime.now(timezone.utc).timestamp() - LOOKBACK_HOURS * 60 * 60

    recent_runs = list_recent_pull_request_runs(owner, repo, lookback_cutoff)
    latest_run_by_pull_request = latest_run_per_pull_request(recent_runs)

    processed_runs: list[str] = []
    rerun_jobs: list[str] = []

    for run in latest_run_by_pull_request.values():
        if run["status"] != "completed" or run.get("conclusion") != "failure":
            continue

        if run["run_attempt"] > 1:
            processed_runs.append(f"Skipped {format_run(run)}: already rerun once.")
            continue

        jobs = list_jobs_for_run(owner, repo, run["id"])
        failed_real_jobs = [
            job
            for job in jobs
            if job.get("conclusion") == "failure" and job["name"] != "required-status-check"
        ]

        if not failed_real_jobs:
            processed_runs.append(f"Skipped {format_run(run)}: only synthetic jobs failed.")
            continue

        if len(failed_real_jobs) > MAX_ELIGIBLE_FAILURES:
            processed_runs.append(
                f"Skipped {format_run(run)}: {len(failed_real_jobs)} failed jobs exceeded limit {MAX_ELIGIBLE_FAILURES}."
            )
            continue

        for job in failed_real_jobs:
            try:
                github_request("POST", f"/repos/{owner}/{repo}/actions/jobs/{job['id']}/rerun")
                rerun_jobs.append(f"{format_run(run)}: reran {job['name']} ({job['id']}).")
            except subprocess.CalledProcessError as e:
                message = read_process_error(e)
                processed_runs.append(
                    f"Failed rerun for {format_run(run)} job {job['name']} ({job['id']}): {message}"
                )

    if not processed_runs and not rerun_jobs:
        processed_runs.append("No recent failed PR runs matched the rerun policy.")

    if rerun_jobs:
        for message in rerun_jobs:
            print(f"::notice::{message}")
    else:
        print("::notice::No eligible failed jobs were rerun.")

    for message in processed_runs:
        print(message)

    write_summary(processed_runs, rerun_jobs)


def list_recent_pull_request_runs(owner: str, repo: str, lookback_cutoff: float) -> list[dict]:
    per_page = 100
    runs: list[dict] = []
    page = 1

    while True:
        response = github_request(
            "GET",
            f"/repos/{owner}/{repo}/actions/workflows/build-pull-request.yml/runs",
            {
                "event": "pull_request",
                "per_page": str(per_page),
                "page": str(page),
            },
        )

        page_runs = response["workflow_runs"]
        if not page_runs:
            break

        runs.extend(page_runs)

        oldest_run = page_runs[-1]
        if parse_github_time(oldest_run["created_at"]).timestamp() < lookback_cutoff:
            break

        if len(page_runs) < per_page:
            break

        page += 1

    return [run for run in runs if parse_github_time(run["created_at"]).timestamp() >= lookback_cutoff]


def latest_run_per_pull_request(runs: list[dict]) -> dict[int, dict]:
    latest_by_pr: dict[int, dict] = {}

    for run in runs:
        pr_number = get_pr_number(run)
        if pr_number is None:
            continue

        existing = latest_by_pr.get(pr_number)
        if existing is None or parse_github_time(run["created_at"]) > parse_github_time(existing["created_at"]):
            latest_by_pr[pr_number] = run

    return latest_by_pr


def list_jobs_for_run(owner: str, repo: str, run_id: int) -> list[dict]:
    per_page = 100
    jobs: list[dict] = []
    page = 1

    while True:
        response = github_request(
            "GET",
            f"/repos/{owner}/{repo}/actions/runs/{run_id}/jobs",
            {
                "filter": "latest",
                "per_page": str(per_page),
                "page": str(page),
            },
        )

        page_jobs = response["jobs"]
        jobs.extend(page_jobs)
        if len(page_jobs) < per_page:
            return jobs

        page += 1


def github_request(method: str, path: str, query: dict[str, str] | None = None) -> dict:
    url = path.removeprefix("/")
    if query:
        url += "?" + urllib.parse.urlencode(query)

    result = subprocess.run(
        [
            "gh",
            "api",
            "--method",
            method,
            "-H",
            "Accept: application/vnd.github+json",
            "-H",
            "X-GitHub-Api-Version: 2022-11-28",
            url,
        ],
        capture_output=True,
        text=True,
        check=True,
    )

    if not result.stdout.strip():
        return {}
    return json.loads(result.stdout)


def read_process_error(error: subprocess.CalledProcessError) -> str:
    return error.stderr.strip() or error.stdout.strip() or f"exit code {error.returncode}"


def get_current_repository() -> str:
    result = subprocess.run(
        ["gh", "repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"],
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout.strip()


def get_pr_number(run: dict) -> int | None:
    pull_requests = run.get("pull_requests") or []
    if not pull_requests:
        return None
    return pull_requests[0].get("number")


def format_run(run: dict) -> str:
    pr_number = get_pr_number(run)
    pr_label = f"PR #{pr_number}" if pr_number is not None else "PR unknown"
    return f"{pr_label}, run {run['id']}, attempt {run['run_attempt']}"


def parse_github_time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def write_summary(processed_runs: list[str], rerun_jobs: list[str]) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return

    lines = [
        "# Rerun flaky PR jobs",
        "",
        f"Eligible reruns: {len(rerun_jobs)}",
        "",
    ]
    lines.extend(f"- {message}" for message in processed_runs + rerun_jobs)
    Path(summary_path).write_text("\n".join(lines) + "\n")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # fail the job on unexpected errors
        print(f"::error::{e}", file=sys.stderr)
        raise
