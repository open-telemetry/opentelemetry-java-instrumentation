#!/usr/bin/env python3
"""Generate a deterministic PR review dashboard with thread-level LLM triage.

The script keeps repository facts deterministic and asks the LLM only one
narrow question per unresolved discussion thread: who has the next action for
that thread?

Output: pull-request-dashboard.md (one section per PR, grouped by category).

Usage:
  python .github/scripts/pull-request-dashboard.py [--output FILE]
                                                   [--jobs N]
                                                   [--model NAME]
"""

from __future__ import annotations

import argparse
import json
import os
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
PER_THREAD_TIMEOUT = 180
PR_COMMENT_WINDOW = 20
MAX_BODY_CHARS = 1200
MAX_PROMPT_CHARS = 18_000

APPROVER_TEAM_SLUGS = [
    "java-instrumentation-approvers",
]

THREAD_PROMPT_TEMPLATE = """You are triaging one discussion thread from pull request #{number} in {repo}.

Classify ONLY this one thread. You are not deciding the final dashboard section.
The final routing is computed later from deterministic facts and all thread
classifications.

Question: who has the next action for this discussion thread?

Use these labels:
  - author: the PR author needs to respond, implement, rebase, or otherwise act
  - reviewer: a reviewer/approver/maintainer needs to review, answer, approve, or merge
  - external: the thread is blocked on something outside this repository
  - none: no follow-up is needed for this thread
  - unclear: the thread does not contain enough information to decide

Guidance:
  - A reviewer saying "this works", "sounds good", or answering the author's
    question may still leave the next implementation step with the author.
  - An author saying "fixed", pushing a commit after feedback, or answering a
    reviewer question usually puts the thread back in the reviewer court.
  - If the author's latest comment asks the reviewer a question or requests
    reviewer input, classify the thread as reviewer.
    - If thread_facts.same_actor_approved_after_thread is true, use that only as
        supporting evidence that an optional suggestion or informational comment is
        non-blocking. Do not classify required follow-up as none just because the
        same actor later approved.
    - A reviewer sharing a reference, example, optional suggestion, or "for ideas"
        link without an explicit requested change is informational; classify it as
        none.
  - If the thread is merely informational and does not require action, classify
    it as none.
  - If the thread is purely social, for example "thanks", "LGTM", or "nice work",
    with no follow-up requested or implied, classify it as none.

Respond with a single JSON object and nothing else:
{{"thread_action": "author" | "reviewer" | "external" | "none" | "unclear", "reason": "short explanation grounded in this thread"}}

---BEGIN PR FACTS---
{facts}
---END PR FACTS---

---BEGIN THREAD---
{thread}
---END THREAD---
"""


# ---------------------------------------------------------------- gh helpers


def run_gh_json(cmd: list[str], token: str | None = None) -> Any:
    env = {**os.environ, "GH_TOKEN": token} if token else None
    proc = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        check=False,
        encoding="utf-8",
        errors="replace",
        env=env,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} failed: {proc.stderr.strip()}")
    return json.loads(proc.stdout or "null")


def gh_api(path: str, paginate: bool = False, token: str | None = None) -> Any:
    cmd = ["gh", "api", "-H", "Accept: application/vnd.github+json"]
    if paginate:
        cmd += ["--paginate", "--slurp"]
    cmd.append(path)
    data = run_gh_json(cmd, token=token)
    if paginate and isinstance(data, list):
        flat: list[Any] = []
        for page in data:
            if isinstance(page, list):
                flat.extend(page)
            else:
                flat.append(page)
        return flat
    return data


def gh_graphql(query: str, fields: dict[str, Any], token: str | None = None) -> dict[str, Any]:
    cmd = ["gh", "api", "graphql", "-f", f"query={query}"]
    for name, value in fields.items():
        if value is None:
            continue
        cmd.extend(["-F", f"{name}={value}"])
    return run_gh_json(cmd, token=token)


def gh_pr_view(repo: str, number: int) -> dict[str, Any]:
    fields = ",".join([
        "number", "title", "url", "author", "state", "isDraft",
        "mergeable", "mergeStateStatus", "createdAt", "updatedAt",
        "labels", "reviewDecision", "reviewRequests", "assignees",
        "additions", "deletions", "changedFiles", "baseRefName",
        "headRefOid", "body",
    ])
    last: dict[str, Any] = {}
    for attempt in range(4):
        proc = subprocess.run(
            ["gh", "pr", "view", str(number), "--repo", repo, "--json", fields],
            capture_output=True,
            text=True,
            check=False,
            encoding="utf-8",
            errors="replace",
        )
        if proc.returncode != 0:
            raise RuntimeError(f"gh pr view {number} failed: {proc.stderr.strip()}")
        last = json.loads(proc.stdout or "{}")
        if last.get("mergeable") not in (None, "", "UNKNOWN"):
            return last
        if attempt < 3:
            time.sleep(1.5)
    return last


def gh_pr_checks(repo: str, number: int) -> list[dict[str, Any]]:
    proc = subprocess.run(
        [
            "gh", "pr", "checks", str(number), "--repo", repo, "--json",
            "name,state,bucket,workflow,description,link",
        ],
        capture_output=True,
        text=True,
        check=False,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode not in (0, 8):
        return []
    try:
        return json.loads(proc.stdout or "[]")
    except json.JSONDecodeError:
        return []


def list_open_prs(repo: str) -> list[dict[str, Any]]:
    return run_gh_json([
        "gh", "pr", "list", "--repo", repo, "--state", "open", "--limit", "500",
        "--json", "number,title,author,isDraft,updatedAt,url",
    ])


def detect_repo() -> str:
    proc = subprocess.run(
        ["gh", "repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"],
        capture_output=True,
        text=True,
        check=True,
        encoding="utf-8",
        errors="replace",
    )
    return proc.stdout.strip()


def load_reviewer_set(org: str) -> set[str]:
    token = os.environ.get("OTELBOT_TOKEN") or None
    reviewers: set[str] = set()
    for slug in APPROVER_TEAM_SLUGS:
        members = gh_api(
            f"/orgs/{org}/teams/{slug}/members?per_page=100",
            paginate=True,
            token=token,
        )
        reviewers.update(m["login"] for m in members)
    if not reviewers:
        raise RuntimeError(
            f"no reviewers found in teams {APPROVER_TEAM_SLUGS}; "
            f"the token must have org:read permission"
        )
    return {r.lower() for r in reviewers}


REVIEW_THREADS_QUERY = """
query($owner: String!, $name: String!, $number: Int!, $after: String) {
  repository(owner: $owner, name: $name) {
    pullRequest(number: $number) {
      reviewThreads(first: 100, after: $after) {
        pageInfo {
          hasNextPage
          endCursor
        }
        nodes {
          id
          isResolved
          isOutdated
          path
          line
          comments(first: 100) {
            nodes {
              id
              body
              createdAt
              author {
                login
              }
            }
          }
        }
      }
    }
  }
}
"""


def fetch_review_threads(owner: str, repo_name: str, number: int) -> list[dict[str, Any]]:
    threads: list[dict[str, Any]] = []
    after: str | None = None
    while True:
        data = gh_graphql(
            REVIEW_THREADS_QUERY,
            {"owner": owner, "name": repo_name, "number": number, "after": after},
        )
        page = (((data.get("data") or {}).get("repository") or {}).get("pullRequest") or {}).get("reviewThreads") or {}
        threads.extend(page.get("nodes") or [])
        page_info = page.get("pageInfo") or {}
        if not page_info.get("hasNextPage"):
            return threads
        after = page_info.get("endCursor") or ""


# ---------------------------------------------------------------- model helpers


def parse_ts(s: str | None) -> datetime | None:
    if not s:
        return None
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def days_since(ts: datetime | None) -> int | None:
    if ts is None:
        return None
    return max(0, (datetime.now(timezone.utc) - ts).days)


def activity_age(ts: datetime | None) -> str:
    if ts is None:
        return "?"
    seconds = max(0, int((datetime.now(timezone.utc) - ts).total_seconds()))
    minutes = seconds // 60
    if minutes < 1:
        return "<1m"
    if minutes < 60:
        return f"{minutes}m"
    hours = minutes // 60
    if hours < 24:
        return f"{hours}h"
    return f"{hours // 24}d"


def truncate(s: str, n: int = MAX_BODY_CHARS) -> str:
    s = (s or "").strip()
    if len(s) <= n:
        return s
    return s[:n] + " ...[truncated]"


def actor_login(obj: dict[str, Any] | None) -> str:
    return ((obj or {}).get("login") or "").strip()


def role_for(login: str, author: str, reviewers: set[str]) -> str:
    if not login:
        return "outsider"
    low = login.lower()
    if low == author.lower():
        return "author"
    if low in reviewers:
        return "approver"
    if low.startswith("app/") or low.endswith("[bot]"):
        return "bot"
    return "outsider"


_BOT_COMMITTER_LOGINS = {"copilot"}
_DELEGATING_BOT_AUTHORS = {"app/copilot-swe-agent", "copilot"}


def is_bot_login(login: str) -> bool:
    if not login:
        return True
    low = login.lower()
    if low in _BOT_COMMITTER_LOGINS:
        return True
    return low.startswith("app/") or low.endswith("[bot]")


def detect_human_delegator(commits: list[dict[str, Any]]) -> str:
    if not commits:
        return ""
    login = actor_login(commits[0].get("committer") or {})
    return "" if is_bot_login(login) else login


def fetch_pr_raw(
    repo: str,
    owner: str,
    repo_name: str,
    pr_summary: dict[str, Any],
) -> dict[str, Any]:
    number = pr_summary["number"]
    with ThreadPoolExecutor(max_workers=7) as pool:
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
        f_threads = pool.submit(fetch_review_threads, owner, repo_name, number)
        return {
            "summary": pr_summary,
            "pr": f_pr.result(),
            "issue_comments": f_issue.result() or [],
            "review_comments": f_revcom.result() or [],
            "reviews": f_reviews.result() or [],
            "commits": f_commits.result() or [],
            "checks": f_checks.result() or [],
            "review_threads": f_threads.result() or [],
        }


def effective_author(raw: dict[str, Any]) -> tuple[str, str]:
    pr = raw["pr"]
    summary = raw["summary"]
    author = actor_login(pr.get("author") or {}) or actor_login(summary.get("author") or {})
    delegator = ""
    if author.lower() in _DELEGATING_BOT_AUTHORS:
        delegator = detect_human_delegator(raw["commits"])
        if delegator:
            author = delegator
    return author, delegator


def is_merge_commit(commit: dict[str, Any]) -> bool:
    return len(commit.get("parents") or []) >= 2


def normalize_events(raw: dict[str, Any], author: str, reviewers: set[str]) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    for c in raw["commits"]:
        commit_obj = c.get("commit") or {}
        commit_author = commit_obj.get("author") or {}
        login = actor_login(c.get("author") or {}) or commit_author.get("name") or ""
        sha = c.get("sha") or ""
        events.append({
            "kind": "commit",
            "timestamp": commit_author.get("date") or "",
            "actor": login,
            "actor_role": role_for(login, author, reviewers),
            "body": commit_obj.get("message") or "",
            "state": None,
            "path": None,
            "sha": sha[:7],
            "is_merge_from_base_by_non_author": is_merge_commit(c) and login.lower() != author.lower(),
        })
    for c in raw["issue_comments"]:
        login = actor_login(c.get("user") or {})
        events.append({
            "kind": "issue-comment",
            "timestamp": c.get("created_at") or "",
            "actor": login,
            "actor_role": role_for(login, author, reviewers),
            "body": c.get("body") or "",
            "state": None,
            "path": None,
            "sha": None,
            "is_merge_from_base_by_non_author": False,
        })
    for c in raw["review_comments"]:
        login = actor_login(c.get("user") or {})
        events.append({
            "kind": "review-comment",
            "timestamp": c.get("created_at") or "",
            "actor": login,
            "actor_role": role_for(login, author, reviewers),
            "body": c.get("body") or "",
            "state": None,
            "path": c.get("path"),
            "sha": None,
            "is_merge_from_base_by_non_author": False,
        })
    for r in raw["reviews"]:
        login = actor_login(r.get("user") or {})
        state = r.get("state") or ""
        events.append({
            "kind": "review-state",
            "timestamp": r.get("submitted_at") or "",
            "actor": login,
            "actor_role": role_for(login, author, reviewers),
            "body": r.get("body") or "",
            "state": state,
            "path": None,
            "sha": None,
            "is_merge_from_base_by_non_author": False,
        })
    events = [e for e in events if e["timestamp"]]
    events.sort(key=lambda e: e["timestamp"])
    return events


def is_substantive_activity(event: dict[str, Any]) -> bool:
    if event.get("is_merge_from_base_by_non_author"):
        return False
    if event.get("actor_role") == "bot":
        return False
    if event["kind"] == "review-state" and event.get("state") != "COMMENTED":
        return True
    return bool((event.get("body") or "").strip())


def compute_conflicts(pr: dict[str, Any]) -> str:
    merge_state = pr.get("mergeStateStatus")
    mergeable = pr.get("mergeable")
    if mergeable == "CONFLICTING" or merge_state == "DIRTY":
        return "yes"
    if mergeable in (None, "", "UNKNOWN"):
        return "unknown"
    return "no"


def compute_facts(raw: dict[str, Any], author: str) -> dict[str, Any]:
    pr = raw["pr"]
    checks = raw["checks"]
    failing = [c for c in checks if (c.get("state") or "").upper() in ("FAILURE", "ERROR")]
    pending = [c for c in checks if (c.get("state") or "").upper() in ("PENDING", "QUEUED", "IN_PROGRESS")]
    last_activity_ts = parse_ts(pr["updatedAt"])
    api_author = actor_login(pr.get("author") or {})
    return {
        "author": author,
        "is_otelbot_author": api_author.lower() == "app/otelbot",
        "is_draft": bool(pr.get("isDraft")),
        "approved": pr.get("reviewDecision") == "APPROVED",
        "ci_failing_count": len(failing),
        "ci_pending_count": len(pending),
        "conflicts": compute_conflicts(pr),
        "days_since_last_activity": days_since(last_activity_ts),
        "last_activity_age": activity_age(last_activity_ts),
    }


def thread_comment(timestamp: str, actor: str, author: str, reviewers: set[str], body: str) -> dict[str, Any]:
    return {
        "timestamp": timestamp,
        "actor": actor,
        "actor_role": role_for(actor, author, reviewers),
        "body": truncate(body),
    }


def approver_approved_after_thread(raw: dict[str, Any], comments: list[dict[str, Any]]) -> bool:
    last_comment_ts = comments[-1]["timestamp"]
    thread_approvers = {
        c["actor"].lower()
        for c in comments
        if c["actor_role"] == "approver" and c.get("actor")
    }
    if not thread_approvers:
        return False
    for review in raw["reviews"]:
        reviewer = actor_login(review.get("user") or {}).lower()
        if reviewer not in thread_approvers:
            continue
        if review.get("state") != "APPROVED":
            continue
        if (review.get("submitted_at") or "") > last_comment_ts:
            return True
    return False


def add_thread_facts(
    raw: dict[str, Any],
    thread: dict[str, Any],
    comments: list[dict[str, Any]],
    facts: dict[str, Any],
) -> dict[str, Any]:
    thread["thread_facts"] = {
        "latest_comment_role": comments[-1].get("actor_role"),
        "same_actor_approved_after_thread": approver_approved_after_thread(raw, comments),
        "current_conflicts": facts.get("conflicts"),
    }
    return thread


def group_review_threads(
    raw: dict[str, Any],
    author: str,
    reviewers: set[str],
    facts: dict[str, Any],
) -> list[dict[str, Any]]:
    threads: list[dict[str, Any]] = []
    for thread in raw["review_threads"]:
        if thread.get("isResolved") or thread.get("isOutdated"):
            continue
        comments = []
        for c in ((thread.get("comments") or {}).get("nodes") or []):
            actor = actor_login(c.get("author") or {})
            comments.append(thread_comment(c.get("createdAt") or "", actor, author, reviewers, c.get("body") or ""))
        comments = [c for c in comments if c["timestamp"]]
        comments.sort(key=lambda c: c["timestamp"])
        if not comments:
            continue
        threads.append(add_thread_facts(raw, {
            "thread_id": thread.get("id") or f"review-thread-{len(threads) + 1}",
            "thread_kind": "review-comment-thread",
            "path": thread.get("path"),
            "line": thread.get("line"),
            "resolved": False,
            "comments": comments,
        }, comments, facts))
    threads.sort(key=lambda t: t["comments"][-1]["timestamp"])
    return threads


def latest_approver_review_event(events: list[dict[str, Any]]) -> str | None:
    timestamps = [
        e["timestamp"]
        for e in events
        if e.get("actor_role") == "approver"
        and e["kind"] in ("review-comment", "review-state")
        and is_substantive_activity(e)
    ]
    return max(timestamps) if timestamps else None


def group_pr_conversation(
    raw: dict[str, Any],
    events: list[dict[str, Any]],
    review_threads: list[dict[str, Any]],
    author: str,
    reviewers: set[str],
    facts: dict[str, Any],
) -> list[dict[str, Any]]:
    comments = []
    for c in raw["issue_comments"]:
        actor = actor_login(c.get("user") or {})
        comment = thread_comment(c.get("created_at") or "", actor, author, reviewers, c.get("body") or "")
        if comment["timestamp"] and comment["actor_role"] != "bot" and comment["body"]:
            comments.append(comment)
    comments.sort(key=lambda c: c["timestamp"])
    if not comments:
        return []

    latest_review_ts = latest_approver_review_event(events)
    if latest_review_ts:
        selected = [c for c in comments if c["timestamp"] > latest_review_ts]
        if not selected and review_threads:
            return []
    elif review_threads:
        selected = []
    else:
        selected = comments

    if facts.get("conflicts") == "no":
        selected = [c for c in selected if not is_conflict_resolution_comment(c.get("body") or "")]
    selected = selected[-PR_COMMENT_WINDOW:]
    if not selected:
        return []
    return [add_thread_facts(raw, {
        "thread_id": "pr-conversation",
        "thread_kind": "pr-conversation",
        "path": None,
        "line": None,
        "resolved": False,
        "comments": selected,
    }, selected, facts)]


def group_discussion_threads(
    raw: dict[str, Any],
    events: list[dict[str, Any]],
    author: str,
    reviewers: set[str],
    facts: dict[str, Any],
) -> list[dict[str, Any]]:
    review_threads = group_review_threads(raw, author, reviewers, facts)
    return review_threads + group_pr_conversation(raw, events, review_threads, author, reviewers, facts)


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
            usage_obj = evt.get("usage") or {}
            if isinstance(usage_obj.get("premiumRequests"), int):
                usage["premium_requests"] = usage_obj["premiumRequests"]
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
    return objects[-1] if objects else None


def valid_thread_action(action: str) -> str:
    action = (action or "").lower().strip()
    if action in ("author", "reviewer", "external", "none", "unclear"):
        return action
    if action == "approver":
        return "reviewer"
    return "unclear"


def parse_thread_decision(response_text: str) -> dict[str, str]:
    obj = extract_json_object(response_text) if response_text else None
    if not obj:
        return {"thread_action": "unclear", "reason": "LLM did not return valid JSON"}
    action = valid_thread_action(str(obj.get("thread_action") or obj.get("side") or ""))
    reason = truncate(str(obj.get("reason") or ""), 300)
    if not reason:
        reason = "No reason provided"
    return {"thread_action": action, "reason": reason}


def is_conflict_resolution_comment(body: str) -> bool:
    text = (body or "").lower()
    return "conflict" in text and any(word in text for word in ("resolve", "resolved", "merge"))


def thread_prompt(repo: str, number: int, pr: dict[str, Any], facts: dict[str, Any], thread: dict[str, Any]) -> str:
    pr_facts = {
        "number": number,
        "title": pr.get("title") or "",
        "description": truncate(pr.get("body") or "", 800),
        **facts,
    }
    facts_text = json.dumps(pr_facts, indent=2, sort_keys=True)
    thread_text = json.dumps(thread, indent=2, sort_keys=True)
    prompt = THREAD_PROMPT_TEMPLATE.format(repo=repo, number=number, facts=facts_text, thread=thread_text)
    if len(prompt) <= MAX_PROMPT_CHARS:
        return prompt
    trimmed = dict(thread)
    comments = [dict(c) for c in thread.get("comments") or []]
    for c in comments:
        c["body"] = truncate(c.get("body") or "", 500)
    trimmed["comments"] = comments[-PR_COMMENT_WINDOW:]
    thread_text = json.dumps(trimmed, indent=2, sort_keys=True)
    return THREAD_PROMPT_TEMPLATE.format(repo=repo, number=number, facts=facts_text, thread=thread_text)


def run_llm_for_thread(
    repo: str,
    number: int,
    pr: dict[str, Any],
    facts: dict[str, Any],
    thread: dict[str, Any],
    model: str,
) -> dict[str, Any]:
    prompt = thread_prompt(repo, number, pr, facts, thread)
    proc = subprocess.run(
        ["copilot", "-p", prompt, "--output-format", "json", "--model", model],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=PER_THREAD_TIMEOUT,
    )
    response_text, usage = parse_copilot_jsonl(proc.stdout)
    decision = parse_thread_decision(response_text)
    return {
        "thread_id": thread["thread_id"],
        "thread_kind": thread["thread_kind"],
        "returncode": proc.returncode,
        "decision": decision,
        "usage": usage,
        "raw_stderr": proc.stderr[-2000:] if proc.stderr else "",
        "response_text": response_text,
    }


def classify_threads(
    repo: str,
    number: int,
    pr: dict[str, Any],
    facts: dict[str, Any],
    threads: list[dict[str, Any]],
    model: str,
) -> list[dict[str, Any]]:
    classifications: list[dict[str, Any]] = []
    for thread in threads:
        try:
            classifications.append(run_llm_for_thread(repo, number, pr, facts, thread, model))
        except subprocess.TimeoutExpired:
            classifications.append({
                "thread_id": thread["thread_id"],
                "thread_kind": thread["thread_kind"],
                "returncode": -1,
                "decision": {"thread_action": "unclear", "reason": "LLM timeout"},
                "raw_stderr": "timeout",
            })
        except Exception as e:
            classifications.append({
                "thread_id": thread["thread_id"],
                "thread_kind": thread["thread_kind"],
                "returncode": -1,
                "decision": {"thread_action": "unclear", "reason": f"LLM failed: {e!r}"},
                "raw_stderr": repr(e),
            })
    return classifications


# ---------------------------------------------------------------- routing and rendering


SIDE_LABELS = {
    "maintainer": "Waiting on maintainer (approved)",
    "approver": "Waiting on approvers",
    "author": "Waiting on authors",
    "external": "Waiting on external",
    "unknown": "Unknown",
}
SIDE_ORDER = ["maintainer", "approver", "author", "external", "unknown"]


def action_counts(classifications: list[dict[str, Any]]) -> dict[str, int]:
    counts = {"author": 0, "reviewer": 0, "external": 0, "none": 0, "unclear": 0}
    for c in classifications:
        action = valid_thread_action((c.get("decision") or {}).get("thread_action") or "")
        counts[action] += 1
    return counts


def route_pr(facts: dict[str, Any], classifications: list[dict[str, Any]]) -> str:
    if facts.get("is_draft"):
        return "draft"
    counts = action_counts(classifications)
    if facts.get("is_otelbot_author"):
        return "external" if counts["external"] else "approver"
    if counts["author"]:
        return "author"
    if counts["external"]:
        return "external"
    if counts["reviewer"]:
        return "maintainer" if facts.get("approved") else "approver"
    if facts.get("approved"):
        return "maintainer"
    return "approver"


def _md_escape(s: str) -> str:
    return (s or "").replace("|", "\\|").replace("\n", " ").strip()


def fetch_workflow_failure_issues(repo: str) -> list[dict[str, Any]]:
    proc = subprocess.run(
        [
            "gh", "issue", "list", "--repo", repo,
            "--search", "in:title Workflow failed:",
            "--state", "open",
            "--json", "number,title,url,updatedAt,comments,author",
            "--limit", "100",
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode != 0:
        print(f"  warning: failed to fetch workflow failure issues: {proc.stderr}", file=sys.stderr)
        return []
    try:
        issues = json.loads(proc.stdout or "[]")
    except json.JSONDecodeError:
        return []
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


def ci_cell(facts: dict[str, Any]) -> str:
    if facts.get("ci_failing_count", 0) > 0:
        return "❌"
    if facts.get("ci_pending_count", 0) > 0:
        return "⏳"
    return "✅"


def conflicts_cell(facts: dict[str, Any]) -> str:
    conflicts = facts.get("conflicts")
    if conflicts == "yes":
        return "❌"
    if conflicts == "no":
        return "✅"
    return "?"


def _html_escape(s: str) -> str:
    return (s or "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def render_diagnostics_section(results: dict[int, dict[str, Any]]) -> list[str]:
    lines = ["<details>", "<summary>Diagnostics</summary>", "", "```text"]
    for number in sorted(results, reverse=True):
        result = results[number]
        facts = result.get("facts") or {}
        counts = action_counts(result.get("classifications") or [])
        lines.append(f"PR #{number}")
        lines.append(
            f"facts: approved={facts.get('approved')} conflicts={facts.get('conflicts')} "
            f"days_since_last_activity={facts.get('days_since_last_activity')}"
        )
        lines.append("threads: " + " ".join(f"{k}={v}" for k, v in counts.items()))
        for c in result.get("classifications") or []:
            decision = c.get("decision") or {}
            reason = (decision.get("reason") or "").replace("\n", " ")
            lines.append(f"llm: {c.get('thread_id')} -> {decision.get('thread_action')} ({reason})")
        if result.get("raw_stderr"):
            lines.append(f"error: {result.get('raw_stderr')}")
        lines.append(f"route: {result.get('side', 'unknown')}")
        lines.append("")
    lines.extend(["```", "", "</details>", ""])
    return [_html_escape(line) if line not in ("<details>", "<summary>Diagnostics</summary>", "</details>") else line for line in lines]


def render_markdown_compact(
    prs: list[dict[str, Any]],
    results: dict[int, dict[str, Any]],
    workflow_issues: list[dict[str, Any]] | None = None,
) -> str:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    out: list[str] = [
        "> [!NOTE]",
        "> Open PRs are grouped by deterministic routing over per-thread LLM classifications. "
        "CI, conflicts, and activity age are computed deterministically and are shown as facts, "
        "not used as standalone routing reasons.",
        "",
    ]

    by_side: dict[str, list[dict[str, Any]]] = {}
    for pr in prs:
        if pr.get("isDraft"):
            continue
        res = results.get(pr["number"]) or {"side": "unknown"}
        by_side.setdefault(res.get("side") or "unknown", []).append(pr)

    for side in SIDE_ORDER:
        rows = by_side.get(side) or []
        if not rows:
            continue
        rows.sort(key=lambda p: -p["number"])
        out.append(f"## {SIDE_LABELS.get(side, side)}")
        out.append("")
        out.append("| PR | Author | CI | Conflicts | Activity |")
        out.append("|---|---|---|---|---|")
        for pr in rows:
            number = pr["number"]
            title = _md_escape(pr.get("title", ""))
            url = pr.get("url", "")
            res = results.get(number) or {}
            facts = res.get("facts") or {}
            author = facts.get("author") or actor_login(pr.get("author") or {})
            activity_cell = facts.get("last_activity_age") or "?"
            out.append(
                f"| [{title}]({url}) | {author} | {ci_cell(facts)} | "
                f"{conflicts_cell(facts)} | {activity_cell} |"
            )
        out.append("")

    out.extend(render_workflow_failure_section(workflow_issues or []))
    out.extend(render_diagnostics_section(results))
    out.append(f"_Generated {now}_")
    out.append("")
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- main


def build_pr_result(
    repo: str,
    owner: str,
    repo_name: str,
    pr_summary: dict[str, Any],
    reviewers: set[str],
    model: str,
) -> dict[str, Any]:
    number = pr_summary["number"]
    try:
        raw = fetch_pr_raw(repo, owner, repo_name, pr_summary)
        author, delegator = effective_author(raw)
        events = normalize_events(raw, author, reviewers)
        facts = compute_facts(raw, author)
        threads = group_discussion_threads(raw, events, author, reviewers, facts)
        classifications = classify_threads(repo, number, raw["pr"], facts, threads, model)
        side = route_pr(facts, classifications)
        return {
            "pr": number,
            "returncode": 0,
            "facts": facts,
            "delegator": delegator,
            "events": events,
            "threads": threads,
            "classifications": classifications,
            "side": side,
        }
    except Exception as e:
        return {
            "pr": number,
            "returncode": -1,
            "facts": {},
            "threads": [],
            "classifications": [],
            "side": "unknown",
            "raw_stderr": repr(e),
        }


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

    print(f"processing {len(non_drafts)} PR(s) in {repo} (model={args.model}, jobs={args.jobs})", file=sys.stderr)

    results: dict[int, dict[str, Any]] = {}
    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        futures = {
            pool.submit(build_pr_result, repo, owner, repo_name, pr, reviewers, args.model): pr
            for pr in non_drafts
        }
        for i, fut in enumerate(as_completed(futures), 1):
            pr = futures[fut]
            try:
                res = fut.result()
            except Exception as e:
                res = {"pr": pr["number"], "returncode": -1, "side": "unknown", "raw_stderr": repr(e)}
            results[pr["number"]] = res
            counts = action_counts(res.get("classifications") or [])
            print(
                f"  [{i}/{len(non_drafts)}] #{pr['number']} -> {res.get('side', 'unknown')} "
                f"({', '.join(f'{k}={v}' for k, v in counts.items())})",
                file=sys.stderr,
            )

    workflow_issues = fetch_workflow_failure_issues(repo)
    md = render_markdown_compact(prs, results, workflow_issues)
    Path(args.output).write_text(md, encoding="utf-8")
    print(f"wrote {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
