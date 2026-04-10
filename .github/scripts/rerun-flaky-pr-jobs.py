#!/usr/bin/env python3
"""Rerun up to two failed jobs for recent pull request CI runs.

Scans recent failed pull request workflow runs, ignores the synthetic
required-status-check job, and reruns eligible failed jobs up to two times.
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
MAX_FAILED_JOBS_PER_WORKFLOW_RUN = 2
MAX_RERUN_ATTEMPTS = 2


def main() -> None:
    repository = os.environ.get("GITHUB_REPOSITORY") or get_current_repository()
    owner, repo = repository.split("/", 1)
    lookback_cutoff = datetime.now(timezone.utc).timestamp() - LOOKBACK_HOURS * 60 * 60

    recent_runs = list_recent_pull_request_runs(owner, repo, lookback_cutoff)
    latest_run_by_pull_request = latest_run_per_pull_request_workflow(owner, repo, recent_runs)

    processed_runs: list[str] = []
    rerun_jobs: list[str] = []

    for run in latest_run_by_pull_request.values():
        if run["status"] != "completed" or run.get("conclusion") != "failure":
            continue

        rerun_attempts = run["run_attempt"] - 1
        if rerun_attempts > MAX_RERUN_ATTEMPTS:
            processed_runs.append(
                f"Skipped {format_run(run)}: already rerun {rerun_attempts} times."
            )
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

        if len(failed_real_jobs) > MAX_FAILED_JOBS_PER_WORKFLOW_RUN:
            processed_runs.append(
                f"Skipped {format_run(run)}: {len(failed_real_jobs)} failed jobs exceeded limit {MAX_FAILED_JOBS_PER_WORKFLOW_RUN}."
            )
            continue

        try:
            github_request("POST", f"/repos/{owner}/{repo}/actions/runs/{run['id']}/rerun-failed-jobs")
            rerun_jobs.append(f"{format_run(run)}: reran failed jobs {format_jobs(failed_real_jobs)}.")
        except subprocess.CalledProcessError as e:
            message = read_process_error(e)
            processed_runs.append(
                f"Failed rerun for {format_run(run)} jobs {format_jobs(failed_real_jobs)}: {message}"
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
        response = github_request_object(
            "GET",
            f"/repos/{owner}/{repo}/actions/runs",
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


def latest_run_per_pull_request_workflow(owner: str, repo: str, runs: list[dict]) -> dict[tuple[int, int], dict]:
    latest_by_pr_workflow: dict[tuple[int, int], dict] = {}
    branch_cache: dict[tuple[str, str], int | None] = {}

    for run in runs:
        pr_number = resolve_pr_number(owner, repo, run, branch_cache)
        if pr_number is None:
            continue

        key = (pr_number, run["workflow_id"])
        existing = latest_by_pr_workflow.get(key)
        if existing is None or parse_github_time(run["created_at"]) > parse_github_time(existing["created_at"]):
            latest_by_pr_workflow[key] = run

    return latest_by_pr_workflow


def list_jobs_for_run(owner: str, repo: str, run_id: int) -> list[dict]:
    per_page = 100
    jobs: list[dict] = []
    page = 1

    while True:
        response = github_request_object(
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


def github_request(method: str, path: str, query: dict[str, str] | None = None) -> dict | list[dict]:
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


def github_request_object(method: str, path: str, query: dict[str, str] | None = None) -> dict:
    response = github_request(method, path, query)
    if isinstance(response, list):
        raise TypeError(f"Expected object response for {path}")
    return response


def github_request_list(method: str, path: str, query: dict[str, str] | None = None) -> list[dict]:
    response = github_request(method, path, query)
    if not isinstance(response, list):
        raise TypeError(f"Expected list response for {path}")
    return response


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


def resolve_pr_number(
    owner: str, repo: str, run: dict, branch_cache: dict[tuple[str, str], int | None]
) -> int | None:
    cached_pr_number = run.get("resolved_pr_number")
    if isinstance(cached_pr_number, int):
        return cached_pr_number

    pull_requests = run.get("pull_requests") or []
    if pull_requests:
        pr_number = pull_requests[0].get("number")
        run["resolved_pr_number"] = pr_number
        return pr_number

    head_repository = run.get("head_repository") or {}
    head_owner = (head_repository.get("owner") or {}).get("login")
    head_branch = run.get("head_branch")
    if not head_owner or not head_branch:
        run["resolved_pr_number"] = None
        return None

    cache_key = (head_owner, head_branch)
    if cache_key not in branch_cache:
        pull_requests = github_request_list(
            "GET",
            f"/repos/{owner}/{repo}/pulls",
            {
                "state": "open",
                "head": f"{head_owner}:{head_branch}",
                "per_page": "5",
            },
        )
        pr_number = None
        for pull_request in pull_requests:
            if pull_request.get("head", {}).get("sha") == run.get("head_sha"):
                pr_number = pull_request["number"]
                break
        if pr_number is None and pull_requests:
            pr_number = pull_requests[0]["number"]
        branch_cache[cache_key] = pr_number

    run["resolved_pr_number"] = branch_cache[cache_key]
    return branch_cache[cache_key]


def format_run(run: dict) -> str:
    pr_number = run.get("resolved_pr_number")
    pr_label = f"PR #{pr_number}" if pr_number is not None else "PR unknown"
    return f"{pr_label}, run {run['id']}, attempt {run['run_attempt']}"


def format_jobs(jobs: list[dict]) -> str:
    return ", ".join(f"{job['name']} ({job['id']})" for job in jobs)


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
