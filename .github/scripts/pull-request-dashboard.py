#!/usr/bin/env python3
"""Generate a PR review dashboard with LLM-suggested next steps.

For each open non-draft PR we:
  1. Deterministically fetch a compact "PR context" via `gh` (PR metadata,
     last comments, last reviews, last commits, checks summary).
  2. Pre-compute signals (last-activity actor, role, age in days, etc.).
  3. Send the assembled context to `copilot -p` (no tool access) and ask
     for a JSON verdict with the next step.

Output: pull-request-dashboard.md (one section per PR, grouped by category).

Usage:
  python .github/scripts/pull-request-dashboard.py [--output FILE]
                                                   [--jobs N]
                                                   [--model NAME]
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

DEFAULT_OUTPUT = "pull-request-dashboard.md"
DEFAULT_JOBS = 4
DEFAULT_MODEL = "gpt-5.4-mini"
PER_PR_TIMEOUT = 180
MAX_COMMENTS = 20
MAX_COMMITS = 5
# Per-commit caps when injecting commits into the timeline.
MAX_COMMIT_MESSAGE_CHARS = 1500
MAX_COMMIT_DIFF_CHARS = 1000
MAX_BODY_CHARS = 1200
MAX_PROMPT_CHARS = 24_000

APPROVER_TEAM_SLUGS = [
    "java-instrumentation-approvers",
]

PROMPT_TEMPLATE = """You are triaging pull request #{number} in {repo} for \
the pull request dashboard.

Decide who needs to act next on this PR using ONLY the context below. \
Merge-conflict and CI-failure status are shown in separate columns of the \
dashboard, so do NOT use those alone to decide; focus on the conversation.

Guidelines:
  - If the latest substantive activity is from the AUTHOR and there is an \
outstanding approver question, an approver should respond next.
  - If an approver's most recent comment asks for a specific change and the \
author has not responded, the author should act next.

Respond with a single JSON object and nothing else (no prose, no fences):
{{"side": "approver" | "author" | "external"}}

Where:
  - "approver" = an approver should act (review, approve, request changes, decide to close)
  - "author" = the PR author should act (respond, rebase, fix CI)
  - "external" = waiting on something outside this repo (upstream PR, etc.)

---BEGIN CONTEXT---
{context}
---END CONTEXT---
"""


# ---------------------------------------------------------------- gh helpers


def gh_api(path: str, paginate: bool = False) -> Any:
    cmd = ["gh", "api", "-H", "Accept: application/vnd.github+json"]
    if paginate:
        cmd += ["--paginate", "--slurp"]
    cmd.append(path)
    proc = subprocess.run(
        cmd, capture_output=True, text=True, check=False,
        encoding="utf-8", errors="replace",
    )
    if proc.returncode != 0:
        raise RuntimeError(f"gh api {path} failed: {proc.stderr.strip()}")
    data = json.loads(proc.stdout or "null")
    if paginate and isinstance(data, list):
        flat: list[Any] = []
        for page in data:
            if isinstance(page, list):
                flat.extend(page)
            else:
                flat.append(page)
        return flat
    return data


def gh_pr_view(repo: str, number: int) -> dict[str, Any]:
    fields = ",".join([
        "number", "title", "url", "author", "state", "isDraft",
        "mergeable", "mergeStateStatus", "createdAt", "updatedAt",
        "labels", "reviewDecision", "reviewRequests", "assignees",
        "additions", "deletions", "changedFiles", "baseRefName",
        "headRefOid", "body",
    ])
    # GitHub computes mergeability lazily: the first request often returns
    # UNKNOWN while it kicks off the computation. Retry a few times with a
    # short sleep until we get a real answer or give up.
    last: dict[str, Any] = {}
    for attempt in range(4):
        proc = subprocess.run(
            ["gh", "pr", "view", str(number), "--repo", repo, "--json", fields],
            capture_output=True, text=True, check=False,
            encoding="utf-8", errors="replace",
        )
        if proc.returncode != 0:
            raise RuntimeError(f"gh pr view {number} failed: {proc.stderr.strip()}")
        last = json.loads(proc.stdout or "{}")
        if last.get("mergeable") not in (None, "", "UNKNOWN"):
            return last
        if attempt < 3:
            time.sleep(1.5)
    return last


def gh_commit_detail(owner: str, repo_name: str, sha: str) -> dict[str, Any]:
    try:
        return gh_api(f"/repos/{owner}/{repo_name}/commits/{sha}")
    except Exception:
        return {}


def format_commit_patch(detail: dict[str, Any], cap: int) -> str:
    """Render a commit's per-file patches, truncated to `cap` total chars."""
    files = detail.get("files") or []
    if not files:
        return ""
    parts: list[str] = []
    used = 0
    for f in files:
        filename = f.get("filename", "")
        status = f.get("status", "")
        adds = f.get("additions", 0)
        dels = f.get("deletions", 0)
        header = f"--- {filename} ({status}, +{adds}/-{dels})"
        patch = f.get("patch") or ""
        if not patch:
            parts.append(header + " [no patch \u2014 binary or too large]")
            continue
        remaining = cap - used
        if remaining <= 0:
            parts.append("\u2026[remaining files truncated]")
            break
        if len(patch) > remaining:
            patch = patch[:remaining] + "\n\u2026[file patch truncated]"
        parts.append(header + "\n" + patch)
        used += len(patch)
    return "\n".join(parts)


def gh_pr_checks(repo: str, number: int) -> list[dict[str, Any]]:
    proc = subprocess.run(
        ["gh", "pr", "checks", str(number), "--repo", repo, "--json",
         "name,state,bucket,workflow,description,link"],
        capture_output=True, text=True, check=False,
        encoding="utf-8", errors="replace",
    )
    if proc.returncode not in (0, 8):  # 8 = checks failing, but still printed
        return []
    try:
        return json.loads(proc.stdout or "[]")
    except json.JSONDecodeError:
        return []


def list_open_prs(repo: str) -> list[dict[str, Any]]:
    proc = subprocess.run(
        ["gh", "pr", "list", "--repo", repo, "--state", "open", "--limit", "500",
         "--json", "number,title,author,isDraft,updatedAt,url"],
        capture_output=True, text=True, check=True,
        encoding="utf-8", errors="replace",
    )
    return json.loads(proc.stdout or "[]")


def detect_repo() -> str:
    proc = subprocess.run(
        ["gh", "repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"],
        capture_output=True, text=True, check=True,
        encoding="utf-8", errors="replace",
    )
    return proc.stdout.strip()


def load_reviewer_set(org: str) -> set[str]:
    reviewers: set[str] = set()
    for slug in APPROVER_TEAM_SLUGS:
        members = gh_api(f"/orgs/{org}/teams/{slug}/members?per_page=100", paginate=True)
        reviewers.update(m["login"] for m in members)
    if not reviewers:
        raise RuntimeError(
            f"no reviewers found in teams {APPROVER_TEAM_SLUGS}; "
            f"the GH_TOKEN must have org:read permission"
        )
    return {r.lower() for r in reviewers}


# ---------------------------------------------------------------- context build


def parse_ts(s: str | None) -> datetime | None:
    if not s:
        return None
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def days_since(ts: datetime | None) -> int | None:
    if ts is None:
        return None
    return max(0, (datetime.now(timezone.utc) - ts).days)


def truncate(s: str, n: int = MAX_BODY_CHARS) -> str:
    s = (s or "").strip()
    if len(s) <= n:
        return s
    return s[:n] + " …[truncated]"


def role_for(login: str, author: str, reviewers: set[str]) -> str:
    if not login:
        return "?"
    low = login.lower()
    if low == author.lower():
        return "author"
    if low in reviewers:
        return "approver"
    if login.startswith("app/") or login.endswith("[bot]"):
        return "bot"
    return "outsider"


# Bot committer logins that should not be treated as the human delegator.
# `Copilot` is the SWE-agent bot itself. Other bot logins (`app/...`,
# `...[bot]`) are caught by the generic patterns in `_is_bot_login`. Real
# human committers (including the delegator and anyone who later pushes a
# fix-up) pass through and are eligible to be picked as the delegator.
_BOT_COMMITTER_LOGINS = {"copilot"}

# PR author logins that delegate work to a human (the Copilot SWE-agent
# opens the PR but a human triggered it). Only for these authors do we look
# up a human delegator from the first commit's committer. For other bots
# (renovate, dependabot, etc.) we keep the bot as the author.
_DELEGATING_BOT_AUTHORS = {"app/copilot-swe-agent", "copilot"}


def _is_bot_login(login: str) -> bool:
    if not login:
        return True
    low = login.lower()
    if low in _BOT_COMMITTER_LOGINS:
        return True
    return low.startswith("app/") or low.endswith("[bot]")


def detect_human_delegator(commits: list[dict[str, Any]]) -> str:
    """For Copilot SWE-agent PRs, return the human who triggered the agent.

    GitHub records the delegating user as the committer of the first commit
    (the agent's initial scaffold). Returns "" if the first commit's
    committer isn't a real user.
    """
    if not commits:
        return ""
    login = ((commits[0].get("committer") or {}).get("login") or "").strip()
    return "" if _is_bot_login(login) else login


def fetch_pr_context(
    repo: str, owner: str, repo_name: str, pr_summary: dict[str, Any],
    reviewers: set[str],
) -> dict[str, Any]:
    number = pr_summary["number"]
    with ThreadPoolExecutor(max_workers=6) as pool:
        f_pr = pool.submit(gh_pr_view, repo, number)
        f_issue = pool.submit(
            gh_api,
            f"/repos/{owner}/{repo_name}/issues/{number}/comments?per_page=100",
            True,
        )
        f_revcom = pool.submit(
            gh_api,
            f"/repos/{owner}/{repo_name}/pulls/{number}/comments?per_page=100",
            True,
        )
        f_reviews = pool.submit(
            gh_api,
            f"/repos/{owner}/{repo_name}/pulls/{number}/reviews?per_page=100",
            True,
        )
        f_commits = pool.submit(
            gh_api,
            f"/repos/{owner}/{repo_name}/pulls/{number}/commits?per_page=100",
            True,
        )
        f_checks = pool.submit(gh_pr_checks, repo, number)
        pr = f_pr.result()
        issue_comments = f_issue.result() or []
        review_comments = f_revcom.result() or []
        reviews = f_reviews.result() or []
        commits = f_commits.result() or []
        checks = f_checks.result() or []

    author = (pr.get("author") or {}).get("login", "") or pr_summary.get("author", {}).get("login", "")

    # For Copilot SWE-agent PRs the API author is the bot; surface the human
    # who delegated the task so reviews/comments by that person are classified
    # as "author" activity instead of "approver". Other bot authors
    # (renovate, dependabot, ...) have no human delegator, so we keep the bot.
    delegator = ""
    if author.lower() in _DELEGATING_BOT_AUTHORS:
        delegator = detect_human_delegator(commits)
        if delegator:
            author = delegator

    # Fetch per-commit diffs for the most recent commits, in parallel.
    recent_commits = commits[-MAX_COMMITS:]
    patches: dict[str, str] = {}
    if recent_commits:
        with ThreadPoolExecutor(max_workers=4) as pool:
            futs = {
                pool.submit(gh_commit_detail, owner, repo_name, c.get("sha") or ""): c.get("sha") or ""
                for c in recent_commits if c.get("sha")
            }
            for fut in as_completed(futs):
                sha = futs[fut]
                try:
                    detail = fut.result()
                except Exception:
                    detail = {}
                patches[sha] = format_commit_patch(detail, MAX_COMMIT_DIFF_CHARS)

    # Build unified activity timeline.
    events: list[dict[str, Any]] = []
    for c in recent_commits:
        sha_full = c.get("sha") or ""
        a = c.get("author") or {}
        commit_obj = c.get("commit") or {}
        commit_author = commit_obj.get("author") or {}
        login = a.get("login") or commit_author.get("name") or ""
        msg = commit_obj.get("message", "")
        date = commit_author.get("date") or ""
        diff = patches.get(sha_full, "")
        body = truncate(msg.rstrip(), MAX_COMMIT_MESSAGE_CHARS)
        if diff:
            body = (body + "\n\n[diff]\n" + diff) if body else ("[diff]\n" + diff)
        events.append({
            "kind": "commit",
            "ts": date,
            "login": login,
            "body": body,
            "sha": sha_full[:7],
        })
    for c in issue_comments:
        events.append({
            "kind": "comment",
            "ts": c.get("created_at"),
            "login": (c.get("user") or {}).get("login", ""),
            "body": c.get("body") or "",
        })
    for c in review_comments:
        events.append({
            "kind": "review-comment",
            "ts": c.get("created_at"),
            "login": (c.get("user") or {}).get("login", ""),
            "body": c.get("body") or "",
            "path": c.get("path"),
        })
    for r in reviews:
        events.append({
            "kind": f"review:{r.get('state')}",
            "ts": r.get("submitted_at"),
            "login": (r.get("user") or {}).get("login", ""),
            "body": r.get("body") or "",
        })
    events = [e for e in events if e["ts"]]
    events.sort(key=lambda e: e["ts"])

    # Last substantive event = last event whose body is non-empty OR whose
    # kind is not "review:COMMENTED" (state changes always count).
    def is_substantive(e: dict[str, Any]) -> bool:
        if e["kind"].startswith("review:") and e["kind"] != "review:COMMENTED":
            return True
        return bool((e.get("body") or "").strip())

    substantive = [e for e in events if is_substantive(e)]
    last_sub = substantive[-1] if substantive else None

    # Commit summaries (subject only) for the brief table in the rendered
    # context. Full commit messages and diffs travel via timeline events.
    commit_rows = []
    for c in recent_commits:
        sha = (c.get("sha") or "")[:7]
        msg = (c.get("commit") or {}).get("message", "").splitlines()[0] if c.get("commit") else ""
        a = c.get("author") or {}
        commit_login = a.get("login") or ((c.get("commit") or {}).get("author") or {}).get("name") or "?"
        commit_date = ((c.get("commit") or {}).get("author") or {}).get("date") or ""
        commit_rows.append({"sha": sha, "msg": msg, "author": commit_login, "date": commit_date})

    last_commit_date = parse_ts(commit_rows[-1]["date"]) if commit_rows else None

    # Checks summary.
    failing = [c for c in checks if (c.get("state") or "").upper() in ("FAILURE", "ERROR")]
    pending = [c for c in checks if (c.get("state") or "").upper() in ("PENDING", "QUEUED", "IN_PROGRESS")]
    successful = [c for c in checks if (c.get("state") or "").upper() == "SUCCESS"]
    skipped = [c for c in checks if (c.get("state") or "").upper() == "SKIPPED"]

    # Last review per user (for approval/changes signal).
    latest_per_user: dict[str, dict[str, Any]] = {}
    for r in reviews:
        state = r.get("state", "")
        if state in ("COMMENTED", "DISMISSED", "PENDING"):
            continue
        u = (r.get("user") or {}).get("login")
        if not u:
            continue
        prev = latest_per_user.get(u)
        if prev is None or (r.get("submitted_at") or "") > (prev.get("submitted_at") or ""):
            latest_per_user[u] = r
    approvers = sorted(u for u, r in latest_per_user.items() if r.get("state") == "APPROVED")
    changes_requested = sorted(u for u, r in latest_per_user.items() if r.get("state") == "CHANGES_REQUESTED")

    return {
        "pr": pr,
        "number": number,
        "title": pr.get("title", "") or pr_summary.get("title", ""),
        "url": pr.get("url", "") or pr_summary.get("url", ""),
        "author": author,
        "delegator": delegator,
        "events": events,
        "substantive": substantive,
        "last_substantive": last_sub,
        "commits": commit_rows,
        "last_commit_date": last_commit_date,
        "checks_failing": failing,
        "checks_pending": pending,
        "checks_successful": successful,
        "checks_skipped": skipped,
        "approvers": approvers,
        "changes_requested": changes_requested,
        "reviewers": reviewers,
    }


def render_context(ctx: dict[str, Any]) -> str:
    pr = ctx["pr"]
    author = ctx["author"]
    reviewers = ctx["reviewers"]

    labels = ", ".join(l.get("name", "") for l in (pr.get("labels") or []))
    review_requests = []
    for rr in pr.get("reviewRequests") or []:
        if isinstance(rr, dict):
            review_requests.append(rr.get("login") or rr.get("name") or "")
        else:
            review_requests.append(str(rr))
    review_requests_s = ", ".join(filter(None, review_requests)) or "(none)"

    last_sub = ctx["last_substantive"]
    last_actor = (last_sub or {}).get("login", "")
    last_role = role_for(last_actor, author, reviewers) if last_actor else "?"
    last_age = days_since(parse_ts((last_sub or {}).get("ts")))
    last_commit_age = days_since(ctx["last_commit_date"])
    pr_age = days_since(parse_ts(pr.get("createdAt")))
    updated_age = days_since(parse_ts(pr.get("updatedAt")))

    lines: list[str] = []
    lines.append(f"PR #{ctx['number']}: {ctx['title']}")
    lines.append(f"URL: {ctx['url']}")
    lines.append(f"Author: @{author}")
    bot_author = ((pr.get("author") or {}).get("login") or "")
    if ctx.get("delegator") and bot_author and bot_author.lower() != author.lower():
        lines.append(
            f"  (PR opened by Copilot SWE-agent @{bot_author} on behalf of @{author}; "
            f"@{author} is treated as the effective author for triage.)"
        )
    lines.append(
        f"State: open | draft={pr.get('isDraft')} | mergeable={pr.get('mergeable')} "
        f"| mergeStateStatus={pr.get('mergeStateStatus')} | reviewDecision={pr.get('reviewDecision')}"
    )
    lines.append(f"Created: {pr.get('createdAt')} ({pr_age}d ago)")
    lines.append(f"Updated: {pr.get('updatedAt')} ({updated_age}d ago)")
    lines.append(f"Size: +{pr.get('additions', 0)}/-{pr.get('deletions', 0)} across {pr.get('changedFiles', 0)} files")
    if labels:
        lines.append(f"Labels: {labels}")
    lines.append(f"Review requests: {review_requests_s}")
    if ctx["approvers"]:
        lines.append(f"Approved by: {', '.join('@' + a for a in ctx['approvers'])}")
    if ctx["changes_requested"]:
        lines.append(f"Changes requested by: {', '.join('@' + a for a in ctx['changes_requested'])}")

    lines.append("")
    lines.append("PR description:")
    lines.append(truncate(pr.get("body") or "", 800))
    lines.append("")

    # Checks
    lines.append("CI checks summary:")
    lines.append(
        f"  successful={len(ctx['checks_successful'])}  failing={len(ctx['checks_failing'])}  "
        f"pending={len(ctx['checks_pending'])}  skipped={len(ctx['checks_skipped'])}"
    )
    for c in ctx["checks_failing"][:10]:
        lines.append(f"  FAIL: {c.get('name')} ({c.get('workflow') or ''})")
    for c in ctx["checks_pending"][:5]:
        lines.append(f"  PENDING: {c.get('name')}")
    lines.append("")

    # Commits
    lines.append(f"Last {len(ctx['commits'])} commits (oldest first):")
    for c in ctx["commits"]:
        lines.append(f"  {c['sha']} {c['date'][:10]} @{c['author']}: {truncate(c['msg'], 120)}")
    lines.append("")

    # Last substantive event highlight
    if last_sub:
        lines.append(
            f"Last substantive activity: {last_sub['kind']} by @{last_actor} "
            f"({last_role}) {last_age}d ago"
        )
        if last_sub.get("kind") == "commit":
            body = last_sub.get("body") or ""
        else:
            body = truncate(last_sub.get("body") or "", 600)
        if body:
            lines.append("  > " + body.replace("\n", "\n  > "))
    else:
        lines.append("Last substantive activity: (none — only metadata events)")
    if last_commit_age is not None:
        lines.append(f"Last commit pushed: {last_commit_age}d ago")
    lines.append("")

    # Recent comments timeline (last N substantive events).
    recent = ctx["substantive"][-MAX_COMMENTS:]
    lines.append(f"Recent substantive events ({len(recent)}, oldest first):")
    for e in recent:
        login = e.get("login", "")
        role = role_for(login, author, reviewers)
        ts = e.get("ts", "")
        kind = e["kind"]
        path = f" on {e.get('path')}" if e.get("path") else ""
        if kind == "commit":
            sha = e.get("sha") or ""
            label = f"commit {sha}" if sha else "commit"
            body = e.get("body") or ""
        else:
            label = kind
            body = truncate(e.get("body") or "", 500)
        lines.append(f"- [{ts}] {label}{path} by @{login} ({role}):")
        if body:
            lines.append("    " + body.replace("\n", "\n    "))
    lines.append("")

    # Pre-computed signals to anchor the model.
    signals: list[str] = []
    if pr.get("isDraft"):
        signals.append("PR is a draft")
    if pr.get("mergeable") == "CONFLICTING" or pr.get("mergeStateStatus") == "DIRTY":
        signals.append("merge conflict with base")
    if ctx["checks_failing"]:
        signals.append(f"{len(ctx['checks_failing'])} CI checks failing")
    if pr.get("reviewDecision") == "APPROVED" and not ctx["checks_failing"] and pr.get("mergeStateStatus") == "CLEAN":
        signals.append("approved + clean + green = mergeable")
    if last_role == "author" and ctx["approvers"]:
        signals.append("latest substantive activity is from author after approvals")
    if last_role == "approver" and last_sub:
        signals.append("latest substantive activity is from an approver")
    lines.append("Pre-computed signals:")
    for s in signals or ["(none)"]:
        lines.append(f"  - {s}")

    return "\n".join(lines)


# ---------------------------------------------------------------- LLM call


def parse_copilot_jsonl(s: str) -> tuple[str, dict[str, Any]]:
    parts: list[str] = []
    usage: dict[str, Any] = {}
    for line in s.splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            evt = json.loads(line)
        except json.JSONDecodeError:
            continue
        if evt.get("type") == "assistant.message":
            content = (evt.get("data") or {}).get("content")
            if isinstance(content, str):
                parts.append(content)
        elif evt.get("type") == "result":
            u = evt.get("usage") or {}
            if isinstance(u.get("premiumRequests"), int):
                usage["premium_requests"] = u["premiumRequests"]
    return "\n".join(parts), usage


def extract_json_object(s: str) -> dict[str, Any] | None:
    s = (s or "").strip()
    s = re.sub(r"^```(?:json)?\s*", "", s, flags=re.I)
    s = re.sub(r"\s*```$", "", s)
    decoder = json.JSONDecoder()
    objects: list[dict[str, Any]] = []
    i = 0
    while i < len(s):
        j = s.find("{", i)
        if j == -1:
            break
        try:
            obj, end = decoder.raw_decode(s, j)
        except json.JSONDecodeError:
            i = j + 1
            continue
        if isinstance(obj, dict):
            objects.append(obj)
        i = end
    for obj in reversed(objects):
        if "next_step" in obj or "side" in obj:
            return obj
    return objects[-1] if objects else None


def run_llm(repo: str, number: int, context_text: str, model: str) -> dict[str, Any]:
    prompt = PROMPT_TEMPLATE.format(repo=repo, number=number, context=context_text)
    if len(prompt) > MAX_PROMPT_CHARS:
        # Trim context (preserve head/tail).
        budget = MAX_PROMPT_CHARS - (len(prompt) - len(context_text)) - 200
        if budget > 0:
            half = budget // 2
            context_text = context_text[:half] + "\n…[context truncated]…\n" + context_text[-half:]
            prompt = PROMPT_TEMPLATE.format(repo=repo, number=number, context=context_text)
    argv = [
        "copilot",
        "-p", prompt,
        "--output-format", "json",
        "--model", model,
    ]
    proc = subprocess.run(
        argv,
        capture_output=True, text=True,
        encoding="utf-8", errors="replace",
        timeout=PER_PR_TIMEOUT,
    )
    response_text, usage = parse_copilot_jsonl(proc.stdout)
    decision = extract_json_object(response_text) if response_text else None
    return {
        "returncode": proc.returncode,
        "decision": decision,
        "usage": usage,
        "raw_stdout": proc.stdout,
        "raw_stderr": proc.stderr[-2000:] if proc.stderr else "",
        "response_text": response_text,
    }


# ---------------------------------------------------------------- rendering


SIDE_LABELS = {
    "maintainer": "Waiting on maintainer (approved)",
    "approver": "Waiting on approvers",
    "author": "Waiting on authors",
    "external": "Waiting on external",
}
SIDE_ORDER = ["maintainer", "approver", "author", "external", "unknown"]


def _md_escape(s: str) -> str:
    return (s or "").replace("|", "\\|").replace("\n", " ").strip()


def _infer_side(decision: dict[str, Any]) -> str:
    side = (decision.get("side") or "").lower().strip()
    if side in ("approver", "author", "external"):
        return side
    return "unknown"


def fetch_workflow_failure_issues(repo: str) -> list[dict[str, Any]]:
    """Fetch open issues created by reusable-workflow-notification.yml.

    Those issues have titles starting with 'Workflow failed: '.
    """
    proc = subprocess.run(
        [
            "gh", "issue", "list", "--repo", repo,
            "--search", "in:title Workflow failed:",
            "--state", "open",
            "--json", "number,title,url,updatedAt,comments,author",
            "--limit", "100",
        ],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if proc.returncode != 0:
        print(f"  warning: failed to fetch workflow failure issues: {proc.stderr}", file=sys.stderr)
        return []
    try:
        issues = json.loads(proc.stdout or "[]")
    except json.JSONDecodeError:
        return []
    # Filter strictly to titles that start with the marker (search matches loosely).
    return [i for i in issues if (i.get("title") or "").startswith("Workflow failed:")]


def render_workflow_failure_section(issues: list[dict[str, Any]]) -> list[str]:
    if not issues:
        return []
    issues = sorted(issues, key=lambda i: i.get("updatedAt") or "", reverse=True)
    lines = ["## Workflow failure tracking issues", ""]
    lines.append("| Issue | Comments | Updated |")
    lines.append("|---|---|---|")
    for i in issues:
        title = _md_escape(i.get("title", ""))
        url = i.get("url", "")
        comments = len(i.get("comments") or [])
        updated = (i.get("updatedAt") or "")[:10]
        lines.append(f"| [{title}]({url}) | {comments} | {updated} |")
    lines.append("")
    return lines


def render_markdown_compact(
    prs: list[dict[str, Any]], results: dict[int, dict[str, Any]],
    workflow_issues: list[dict[str, Any]] | None = None,
) -> str:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    out: list[str] = [
        "> [!NOTE]",
        "> Open PRs are grouped by who is expected to act next. The grouping "
        "is produced by an LLM (GitHub Copilot CLI) from each PR's metadata, "
        "comments, reviews, and CI status, so it can be wrong — treat it as a "
        "triage hint, not ground truth. The CI and Conflicts columns are "
        "computed deterministically from `gh`.",
        "",
    ]

    by_side: dict[str, list[tuple[dict[str, Any], dict[str, Any]]]] = {}
    for pr in prs:
        if pr.get("isDraft"):
            continue
        res = results.get(pr["number"]) or {}
        decision = res.get("decision") or {}
        side = _infer_side(decision) if decision else "unknown"
        # Promote approved PRs that are waiting on approvers into the
        # "maintainer" bucket (deterministic): GitHub already says they have
        # the required approvals, so a maintainer can merge them.
        if side == "approver" and (res.get("facts") or {}).get("approved"):
            side = "maintainer"
        by_side.setdefault(side, []).append((pr, decision))

    for side in SIDE_ORDER:
        rows = by_side.get(side) or []
        if not rows:
            continue
        rows.sort(key=lambda rd: -rd[0]["number"])
        label = SIDE_LABELS.get(side, side)
        out.append(f"## {label}")
        out.append("")
        out.append("| PR | Author | CI | Conflicts |")
        out.append("|---|---|---|---|")
        for pr, decision in rows:
            number = pr["number"]
            title = _md_escape(pr.get("title", ""))
            url = pr.get("url", "")
            res = results.get(number) or {}
            facts = res.get("facts") or {}
            author = facts.get("effective_author") or (pr.get("author") or {}).get("login", "")
            if facts:
                if facts.get("ci_failing", 0) > 0:
                    ci_cell = "❌"
                elif facts.get("ci_pending", 0) > 0:
                    ci_cell = "⏳"
                else:
                    ci_cell = "✅"
                conflicts = facts.get("conflicts")
                if conflicts == "yes":
                    conflicts_cell = "❌"
                elif conflicts == "no":
                    conflicts_cell = "✅"
                else:
                    conflicts_cell = "?"
            else:
                ci_cell = "?"
                conflicts_cell = "?"
            author_cell = author or ""
            out.append(
                f"| [{title}]({url}) | {author_cell} | {ci_cell} | {conflicts_cell} |"
            )
        out.append("")

    out.extend(render_workflow_failure_section(workflow_issues or []))

    out.append(f"_Generated {now}_")
    out.append("")

    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- main


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--output", default=DEFAULT_OUTPUT, help=f"output file (default: {DEFAULT_OUTPUT})")
    ap.add_argument("--jobs", type=int, default=DEFAULT_JOBS, help=f"parallel workers (default: {DEFAULT_JOBS})")
    ap.add_argument("--model", default=DEFAULT_MODEL, help=f"copilot model (default: {DEFAULT_MODEL})")
    args = ap.parse_args()

    repo = detect_repo()
    owner, repo_name = repo.split("/", 1)

    reviewers = load_reviewer_set(owner)
    print(f"reviewer set ({len(reviewers)})", file=sys.stderr)

    prs = list_open_prs(repo)
    drafts = [p for p in prs if p.get("isDraft")]
    non_drafts = [p for p in prs if not p.get("isDraft")]
    if drafts:
        print(f"skipping {len(drafts)} draft PR(s)", file=sys.stderr)

    print(f"processing {len(non_drafts)} PR(s) in {repo} (model={args.model}, jobs={args.jobs})",
          file=sys.stderr)

    def process_one(pr: dict[str, Any]) -> dict[str, Any]:
        number = pr["number"]
        try:
            ctx = fetch_pr_context(repo, owner, repo_name, pr, reviewers)
        except Exception as e:
            return {"pr": number, "returncode": -1, "decision": None, "raw_stderr": f"fetch failed: {e!r}"}
        context_text = render_context(ctx)
        pr_obj = ctx.get("pr") or {}
        merge_state = pr_obj.get("mergeStateStatus")
        mergeable = pr_obj.get("mergeable")
        if mergeable == "CONFLICTING" or merge_state == "DIRTY":
            conflicts = "yes"
        elif mergeable in (None, "", "UNKNOWN"):
            conflicts = "unknown"
        else:
            conflicts = "no"
        facts = {
            "ci_failing": len(ctx["checks_failing"]),
            "ci_pending": len(ctx["checks_pending"]),
            "ci_successful": len(ctx["checks_successful"]),
            "conflicts": conflicts,
            "approved": pr_obj.get("reviewDecision") == "APPROVED",
            "effective_author": ctx.get("author") or "",
        }
        try:
            r = run_llm(repo, number, context_text, args.model)
        except subprocess.TimeoutExpired:
            return {"pr": number, "returncode": -1, "decision": None, "raw_stderr": "timeout", "facts": facts}
        r["pr"] = number
        r["facts"] = facts
        return r

    results: dict[int, dict[str, Any]] = {}
    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        futures = {pool.submit(process_one, p): p for p in non_drafts}
        for i, fut in enumerate(as_completed(futures), 1):
            pr = futures[fut]
            try:
                res = fut.result()
            except Exception as e:
                res = {"pr": pr["number"], "returncode": -1, "decision": None, "raw_stderr": repr(e)}
            results[pr["number"]] = res
            side = (res.get("decision") or {}).get("side", "?")
            print(f"  [{i}/{len(non_drafts)}] #{pr['number']} -> {side}", file=sys.stderr)

    workflow_issues = fetch_workflow_failure_issues(repo)
    md = render_markdown_compact(prs, results, workflow_issues)
    Path(args.output).write_text(md, encoding="utf-8")
    print(f"wrote {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
