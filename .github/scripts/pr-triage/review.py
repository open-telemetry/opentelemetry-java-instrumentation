#!/usr/bin/env python3
"""Review a PR and post a pending GitHub review from validated findings."""

from __future__ import annotations

import argparse
import json
import re
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any
from urllib.parse import quote

from common import (
    Summary,
    WorkflowError,
    detect_repo,
    git,
    gh,
    gh_json,
    invoke_copilot,
    make_temp_dir,
    progress,
    run_pr_workflow,
    status_porcelain,
    write_json,
)


HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")
MAX_FILE_CHARS = 80_000
# apply_patch is the write tool; the prompt restricts it to findings.json, and any
# stray edit to the working tree is caught by the post-run status_porcelain() check.
# Tool names differ between models. gpt-5.5 exposes `rg` + `apply_patch`;
# Claude models expose `grep` + `create`/`edit`. Listing an unknown name
# triggers "Unknown tool name in the tool allowlist" and silently drops it,
# so we send the union and let the CLI keep whichever ones the model knows.
REVIEW_COPILOT_TOOLS = ["view", "rg", "grep", "web_fetch", "apply_patch", "create", "edit"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pr", type=int, help="pull request number")
    parser.add_argument("--upstream", default="upstream", help="upstream remote name for trusted base checkout")
    parser.add_argument("--keep-temp", action="store_true", help="reuse and retain the temp bundle directory")
    parser.add_argument("--no-post", action="store_true", help="prepare findings and payload but do not post")
    parser.add_argument(
        "--replace-pending",
        action="store_true",
        help="delete an existing pending review by the current user before posting",
    )
    parser.add_argument("--skip-copilot", action="store_true", help="prepare the review bundle and stop")
    parser.add_argument(
        "--capture-tool-usage",
        action="store_true",
        help="capture Copilot JSONL events and tool usage summary in the review bundle",
    )
    return parser.parse_args()


def pr_metadata(pr: int, summary: Summary) -> dict[str, Any]:
    fields = ",".join(
        [
            "number",
            "title",
            "url",
            "baseRefName",
            "baseRefOid",
            "headRefName",
            "headRefOid",
            "headRepository",
            "headRepositoryOwner",
        ]
    )
    return gh_json(["pr", "view", str(pr), "--json", fields], summary)


def parse_diff(diff: str) -> dict[str, dict[str, Any]]:
    files: dict[str, dict[str, Any]] = {}
    current_file: str | None = None
    right_line = 0
    hunk_start = 0
    hunk_end = 0
    new_file_marker = False
    deleted_file_marker = False

    for line in diff.splitlines():
        if line.startswith("diff --git "):
            new_file_marker = False
            deleted_file_marker = False
            continue
        if line.startswith("new file mode"):
            new_file_marker = True
            continue
        if line.startswith("deleted file mode"):
            deleted_file_marker = True
            continue
        if line.startswith("+++ "):
            path = line[4:]
            if path == "/dev/null":
                current_file = None
                continue
            if path.startswith("b/"):
                path = path[2:]
            current_file = path
            status = "added" if new_file_marker else ("deleted" if deleted_file_marker else "modified")
            files.setdefault(current_file, {"changed_lines": set(), "hunks": [], "status": status})
            continue

        match = HUNK_RE.match(line)
        if match and current_file:
            hunk_start = int(match.group(1))
            line_count = int(match.group(2) or "1")
            hunk_end = hunk_start + line_count - 1 if line_count else hunk_start
            files[current_file]["hunks"].append([hunk_start, hunk_end])
            right_line = hunk_start
            continue

        if not current_file or not files[current_file]["hunks"]:
            continue
        if line.startswith("+") and not line.startswith("+++"):
            files[current_file]["changed_lines"].add(right_line)
            right_line += 1
        elif line.startswith(" "):
            right_line += 1
        elif line.startswith("-"):
            continue

    for info in files.values():
        info["changed_lines"] = sorted(info["changed_lines"])
    return files


def is_reviewable(path: str) -> bool:
    return not path.startswith("licenses/")


def read_file_excerpt(repo: str, ref: str, path: str, summary: Summary | None) -> str:
    endpoint = f"repos/{repo}/contents/{quote(path, safe='/')}?ref={quote(ref, safe='')}"
    text = gh(["api", "-H", "Accept: application/vnd.github.raw", endpoint], summary).stdout
    if len(text) > MAX_FILE_CHARS:
        return text[:MAX_FILE_CHARS] + "\n...[file truncated]...\n"
    return text


def pr_head_repo(metadata: dict[str, Any]) -> str:
    owner = ((metadata.get("headRepositoryOwner") or {}).get("login") or "").strip()
    repo = ((metadata.get("headRepository") or {}).get("name") or "").strip()
    if not owner or not repo:
        raise RuntimeError("PR metadata did not include head repository details")
    return f"{owner}/{repo}"


def list_knowledge_paths(base_repo: str, base_ref: str, summary: Summary) -> list[str]:
    endpoint = f"repos/{base_repo}/contents/.github/agents/knowledge?ref={quote(base_ref, safe='')}"
    entries = gh_json(["api", endpoint], summary)
    if not isinstance(entries, list):
        raise RuntimeError("GitHub contents API did not return the knowledge directory listing")
    paths: list[str] = []
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        path = entry.get("path")
        if entry.get("type") == "file" and isinstance(path, str) and path.endswith(".md"):
            paths.append(path)
    return sorted(paths)


def write_knowledge_bundle(base_repo: str, base_ref: str, directory: Path, summary: Summary) -> list[Path]:
    progress("Loading review knowledge files")
    directory.mkdir(parents=True, exist_ok=True)
    paths = [*list_knowledge_paths(base_repo, base_ref, summary), "docs/contributing/style-guide.md"]
    with ThreadPoolExecutor(max_workers=8) as pool:
        contents_by_path = dict(
            zip(paths, pool.map(lambda p: read_file_excerpt(base_repo, base_ref, p, None), paths))
        )
    bundled_paths: list[Path] = []
    for path in paths:
        destination = directory / Path(path).name
        destination.write_text(contents_by_path[path], encoding="utf-8")
        bundled_paths.append(destination)
    return bundled_paths


def write_review_bundle(
    pr: int,
    metadata: dict[str, Any],
    diff_text: str,
    diff_scope: dict[str, dict[str, Any]],
    directory: Path,
    summary: Summary,
    base_repo: str,
) -> tuple[Path, Path, Path]:
    progress(f"Preparing review bundle in {directory}")
    head_repo = pr_head_repo(metadata)
    head_ref = metadata["headRefOid"]
    base_ref = metadata["baseRefOid"]
    paths_to_fetch = [
        path
        for path, info in diff_scope.items()
        if is_reviewable(path) and info.get("changed_lines")
    ]
    with ThreadPoolExecutor(max_workers=8) as pool:
        file_contents = dict(
            zip(
                paths_to_fetch,
                pool.map(lambda p: read_file_excerpt(head_repo, head_ref, p, None), paths_to_fetch),
            )
        )

    files_dir = directory / "files"
    files_dir.mkdir(parents=True, exist_ok=True)
    knowledge_dir = directory / "knowledge"
    write_knowledge_bundle(base_repo, base_ref, knowledge_dir, summary)

    for path, contents in file_contents.items():
        destination = files_dir / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(contents, encoding="utf-8")

    diff_path = directory / "pr.diff"
    diff_path.write_text(diff_text, encoding="utf-8")
    write_json(directory / "metadata.json", metadata)
    write_json(directory / "diff-scope.json", diff_scope)
    summary.temp_dir = str(directory)
    return diff_path, files_dir, knowledge_dir


def copilot_prompt(
    diff_path: Path,
    files_dir: Path,
    knowledge_dir: Path,
    findings_path: Path,
    base_branch: str,
    diff_scope: dict[str, dict[str, Any]],
) -> str:
    bundled = sorted(p for p, info in diff_scope.items() if is_reviewable(p) and info.get("changed_lines"))
    deleted = sorted(p for p, info in diff_scope.items() if is_reviewable(p) and info.get("status") == "deleted")
    bundled_list = "\n".join(f"- {p}" for p in bundled) or "- (none)"
    deleted_list = "\n".join(f"- {p}" for p in deleted) or "- (none)"
    return f"""You are reviewing a pull request in opentelemetry-java-instrumentation.

The authoritative source for what changed is the unified diff at:

{diff_path}

File resolution rules (read carefully):
- For PR-modified or PR-added files, read the post-change contents from the
  bundle at {files_dir}/<repo-relative-path>. These files do NOT exist in your
  current working tree (which is detached at the PR's base commit on
  {base_branch}).
- For files NOT changed by the PR (e.g. neighbouring metadata.yaml, helper
  classes), read directly from the working tree using their repo-relative path.
  Do NOT prefix these with the bundle path.
- For files deleted by the PR, do not attempt to read them; their contents are
  intentionally absent.

Bundled PR files (post-change contents available under {files_dir}):
{bundled_list}

Deleted by this PR (do not read):
{deleted_list}

Review knowledge articles: {knowledge_dir} (start with README.md to decide
which articles apply). Always apply the general rules, style guide, and
metadata.yaml guidance.

Write your findings as JSON to this exact file path:

{findings_path}

Required JSON shape:
{{
  "body": "brief pending review summary",
  "unavailable_tools": [
    {{
      "tool": "tool name",
      "reason": "why it would have materially improved this review"
    }}
  ],
  "comments": [
    {{
      "path": "repo-relative file path",
      "line": 123,
      "start_line": 120,
      "category": "[Style]",
      "body": "concise review comment",
      "suggestion": "optional exact replacement text"
    }}
  ]
}}

Rules:
- Do not switch branches.
- Do not edit repository files; only write the requested findings JSON file.
- Do not commit.
- Do not push.
- Only flag issues on changed right-side lines in the diff scope.
- Do not flag non-capturing lambdas or method references as unnecessary allocations.
- Use suggestion text only when the replacement is exact and ready for GitHub.
- For deletions, set start_line and line to span the lines to remove and set suggestion to "" (empty string).
- If the available tools prevent a materially better review, report the missing tool in unavailable_tools.
- Return no comments for uncertain or low-confidence observations.
"""


def parse_findings(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise RuntimeError(f"Copilot did not write findings JSON: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def lines_inside_hunk(path: str, start_line: int, line: int, diff_scope: dict[str, dict[str, Any]]) -> bool:
    hunks = diff_scope.get(path, {}).get("hunks") or []
    for current in range(start_line, line + 1):
        if not any(start <= current <= end for start, end in hunks):
            return False
    return True


def validate_comments(findings: dict[str, Any], diff_scope: dict[str, dict[str, Any]], summary: Summary) -> list[dict[str, Any]]:
    comments: list[dict[str, Any]] = []
    for raw in findings.get("comments") or []:
        path = raw.get("path")
        line = raw.get("line")
        body = raw.get("body")
        if not isinstance(path, str) or not isinstance(line, int) or not isinstance(body, str):
            summary.notes.append(f"Skipped malformed comment: {raw}")
            continue
        start_line = raw.get("start_line") or line
        if not isinstance(start_line, int) or start_line > line:
            summary.notes.append(f"Skipped invalid range for {path}:{line}")
            continue
        changed_lines = set(diff_scope.get(path, {}).get("changed_lines") or [])
        if line not in changed_lines:
            summary.notes.append(f"Skipped comment outside changed lines: {path}:{line}")
            continue
        if not lines_inside_hunk(path, start_line, line, diff_scope):
            summary.notes.append(f"Skipped comment outside diff hunk: {path}:{start_line}-{line}")
            continue

        comment = {"path": path, "line": line, "side": "RIGHT", "body": body}
        if start_line != line:
            comment["start_line"] = start_line
            comment["start_side"] = "RIGHT"
        suggestion = raw.get("suggestion")
        if isinstance(suggestion, str):
            comment["body"] = body.rstrip() + "\n\n```suggestion\n" + suggestion.rstrip("\n") + "\n```"
        comments.append(comment)
    return comments


def note_unavailable_tools(findings: dict[str, Any], summary: Summary) -> None:
    unavailable_tools = findings.get("unavailable_tools")
    if not isinstance(unavailable_tools, list):
        return
    if not unavailable_tools:
        summary.notes.append("Copilot wanted unavailable tools: none")
        return
    for raw in unavailable_tools:
        if not isinstance(raw, dict):
            continue
        tool = raw.get("tool")
        reason = raw.get("reason")
        if isinstance(tool, str) and isinstance(reason, str):
            summary.notes.append(f"Copilot wanted unavailable tool {tool}: {reason}")


def post_review(repo: str, pr: int, payload_path: Path, summary: Summary) -> str:
    review = gh_json(
        [
            "api",
            f"repos/{repo}/pulls/{pr}/reviews",
            "--method",
            "POST",
            "--input",
            str(payload_path),
        ],
        summary,
    )
    review_id = str(review.get("id") or "")
    review_url = review.get("html_url")
    if isinstance(review_url, str) and review_url:
        summary.review_url = review_url
    elif review_id and summary.pr_url:
        summary.review_url = f"{summary.pr_url}#pullrequestreview-{review_id}"
    return review_id


def checkout_trusted_base(upstream: str, base_branch: str, base_ref_oid: str, summary: Summary) -> None:
    progress(f"Fetching {upstream}")
    git(["fetch", upstream], summary)
    git(["rev-parse", "--verify", base_ref_oid], summary)
    progress(f"Checking out trusted PR base: {base_branch}@{base_ref_oid[:12]}")
    git(["checkout", "--detach", base_ref_oid], summary)


def check_no_pending_review(repo: str, pr: int, summary: Summary, *, replace: bool = False) -> None:
    """Fail fast if the current user already has a pending review on this PR.

    GitHub rejects a second pending review with HTTP 422, and discovering that
    after a multi-minute Copilot run wastes time and tokens. When ``replace``
    is true, an existing pending review by the current user is deleted instead.
    """
    progress("Checking for an existing pending review")
    viewer = gh(["api", "user", "-q", ".login"], summary).stdout.strip()
    if not viewer:
        raise RuntimeError("could not determine current GitHub user")
    reviews = gh_json(["api", f"repos/{repo}/pulls/{pr}/reviews"], summary)
    if not isinstance(reviews, list):
        return
    for review in reviews:
        if not isinstance(review, dict):
            continue
        state = review.get("state")
        user_login = ((review.get("user") or {}).get("login") or "")
        if state == "PENDING" and user_login == viewer:
            review_id = review.get("id")
            html_url = review.get("html_url") or ""
            if replace and review_id is not None:
                progress(f"Deleting existing pending review {review_id}")
                gh(
                    ["api", "-X", "DELETE", f"repos/{repo}/pulls/{pr}/reviews/{review_id}"],
                    summary,
                )
                continue
            hint = f" ({html_url})" if html_url else ""
            raise WorkflowError(
                f"{viewer} already has a pending review on PR #{pr}; submit it, "
                f"re-run with --replace-pending, or delete it manually{hint}"
            )


def main() -> int:
    args = parse_args()

    def workflow(summary: Summary) -> int:
        metadata = pr_metadata(args.pr, summary)
        summary.pr_url = metadata.get("url")
        base_branch = metadata.get("baseRefName")
        if not isinstance(base_branch, str):
            raise RuntimeError("PR metadata did not include a base branch")
        base_ref_oid = metadata.get("baseRefOid")
        if not isinstance(base_ref_oid, str) or not base_ref_oid:
            raise RuntimeError("PR metadata did not include a base ref OID")
        base_repo = detect_repo(summary)
        if not args.no_post:
            check_no_pending_review(base_repo, args.pr, summary, replace=args.replace_pending)
        checkout_trusted_base(args.upstream, base_branch, base_ref_oid, summary)
        progress("Collecting PR diff")
        diff_names = gh(["pr", "diff", str(args.pr), "--name-only"], summary).stdout.splitlines()
        diff_text = gh(["pr", "diff", str(args.pr), "--color", "never"], summary).stdout
        progress("Parsing PR diff hunks")
        diff_scope = parse_diff(diff_text)
        for path in diff_names:
            diff_scope.setdefault(path, {"changed_lines": [], "hunks": [], "status": "deleted"})

        bundle_dir = make_temp_dir("otel-pr-review", args.pr, args.keep_temp)
        diff_path, files_dir, knowledge_dir = write_review_bundle(
            args.pr, metadata, diff_text, diff_scope, bundle_dir, summary, base_repo
        )
        findings_path = bundle_dir / "findings.json"
        if findings_path.exists():
            findings_path.unlink()
        if args.skip_copilot:
            summary.outcome = "prepared review bundle; skipped Copilot handoff"
            return 0

        event_log_path = bundle_dir / "copilot-events.jsonl" if args.capture_tool_usage else None
        tool_usage_path = bundle_dir / "copilot-tools-used.json" if args.capture_tool_usage else None
        # Scope the model to the tools we want it to use. --allow-all-tools is
        # required to actually grant permission for built-in tools; per-tool
        # --allow-tool=<builtin> flags are silently ignored. The bundle dir
        # lives under cwd (REPO_ROOT) which is in the default path allowlist,
        # so no extra --add-dir or --allow-all-paths is needed.
        copilot_extra_args = ["--available-tools=" + ",".join(REVIEW_COPILOT_TOOLS)]
        response = invoke_copilot(
            copilot_prompt(diff_path, files_dir, knowledge_dir, findings_path, base_branch, diff_scope),
            summary,
            event_log_path=event_log_path,
            tool_usage_path=tool_usage_path,
            allow_all_tools=True,
            extra_args=copilot_extra_args,
        )
        (bundle_dir / "copilot-response.txt").write_text(response + "\n", encoding="utf-8")
        if status_porcelain().strip():
            raise RuntimeError("Copilot changed the working tree during review; refusing to post")

        findings = parse_findings(findings_path)
        note_unavailable_tools(findings, summary)
        progress("Validating Copilot review findings against diff hunks")
        comments = validate_comments(findings, diff_scope, summary)
        body = findings.get("body") if isinstance(findings.get("body"), str) else "Pending automated review."
        payload = {"commit_id": metadata["headRefOid"], "body": body, "comments": comments}
        payload_path = bundle_dir / "review-payload.json"
        write_json(payload_path, payload)

        if args.no_post:
            summary.outcome = f"prepared pending review payload with {len(comments)} comments; not posted (--no-post)"
            return 0
        progress("Posting pending GitHub review")
        review_id = post_review(base_repo, args.pr, payload_path, summary)
        summary.outcome = f"posted pending review {review_id} with {len(comments)} comments"
        summary.notes.append(
            "Submit with: gh api repos/{repo}/pulls/{pr}/reviews/{review}/events --method POST -f event=COMMENT".format(
                repo=base_repo, pr=args.pr, review=review_id
            )
        )
        return 0

    return run_pr_workflow(args.pr, workflow, push_required=False, checkout_required=False)


if __name__ == "__main__":
    sys.exit(main())