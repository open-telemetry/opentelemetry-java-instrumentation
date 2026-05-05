#!/usr/bin/env python3
"""Review a PR and post a pending GitHub review from validated findings."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

from common import (
    REPO_ROOT,
    Summary,
    detect_repo,
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pr", type=int, help="pull request number")
    parser.add_argument("--keep-temp", action="store_true", help="reuse and retain the temp bundle directory")
    parser.add_argument("--no-post", action="store_true", help="prepare findings and payload but do not post")
    parser.add_argument("--skip-copilot", action="store_true", help="prepare the review bundle and stop")
    return parser.parse_args()


def pr_metadata(pr: int, summary: Summary) -> dict[str, Any]:
    fields = ",".join(["number", "title", "url", "headRefOid", "baseRefName", "headRefName"])
    return gh_json(["pr", "view", str(pr), "--json", fields], summary)


def parse_diff(diff: str) -> dict[str, dict[str, Any]]:
    files: dict[str, dict[str, Any]] = {}
    current_file: str | None = None
    right_line = 0
    hunk_start = 0
    hunk_end = 0

    for line in diff.splitlines():
        if line.startswith("+++ "):
            path = line[4:]
            if path == "/dev/null":
                current_file = None
                continue
            if path.startswith("b/"):
                path = path[2:]
            current_file = path
            files.setdefault(current_file, {"changed_lines": set(), "hunks": []})
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
    if path.startswith("licenses/"):
        return False
    full_path = REPO_ROOT / path
    return full_path.is_file()


def read_file_excerpt(path: str) -> str:
    full_path = REPO_ROOT / path
    try:
        text = full_path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""
    if len(text) > MAX_FILE_CHARS:
        return text[:MAX_FILE_CHARS] + "\n...[file truncated]...\n"
    return text


def load_knowledge(changed_files: list[str]) -> dict[str, str]:
    progress("Loading review knowledge files")
    knowledge = {
        "docs/contributing/style-guide.md": read_file_excerpt("docs/contributing/style-guide.md"),
        ".github/agents/knowledge/general-rules.md": read_file_excerpt(".github/agents/knowledge/general-rules.md"),
    }
    joined_names = "\n".join(changed_files)
    contents = "\n".join(read_file_excerpt(path) for path in changed_files if is_reviewable(path))
    triggers = [
        (".github/agents/knowledge/gradle-conventions.md", lambda: any(p.endswith(("build.gradle.kts", "settings.gradle.kts")) for p in changed_files)),
        (".github/agents/knowledge/javaagent-advice-patterns.md", lambda: "@Advice" in contents),
        (".github/agents/knowledge/javaagent-module-patterns.md", lambda: "InstrumentationModule" in contents or "TypeInstrumentation" in contents),
        (".github/agents/knowledge/javaagent-singletons-patterns.md", lambda: "Singleton" in contents or "Singletons" in contents),
        (".github/agents/knowledge/library-patterns.md", lambda: "/library/" in joined_names),
        (".github/agents/knowledge/module-naming.md", lambda: "settings.gradle.kts" in joined_names),
        (".github/agents/knowledge/testing-general-patterns.md", lambda: any("/test/" in p or p.endswith("Test.java") for p in changed_files)),
        (".github/agents/knowledge/testing-experimental-flags.md", lambda: "testExperimental" in contents),
        (".github/agents/knowledge/testing-semconv-stability.md", lambda: "Semconv" in contents or "semconv" in contents),
        (".github/agents/knowledge/config-property-stability.md", lambda: "otel.instrumentation." in contents),
        (".github/agents/knowledge/api-deprecation-policy.md", lambda: "@Deprecated" in contents),
    ]
    for path, should_load in triggers:
        if should_load():
            knowledge[path] = read_file_excerpt(path)
    return knowledge


def write_review_bundle(pr: int, metadata: dict[str, Any], diff_scope: dict[str, dict[str, Any]], directory: Path, summary: Summary) -> Path:
    progress(f"Preparing review bundle in {directory}")
    reviewable_files = [path for path in diff_scope if is_reviewable(path)]
    files_dir = directory / "files"
    files_dir.mkdir(parents=True, exist_ok=True)
    knowledge = load_knowledge(reviewable_files)

    for path in reviewable_files:
        safe_name = path.replace("/", "__").replace("\\", "__")
        (files_dir / f"{safe_name}.txt").write_text(read_file_excerpt(path), encoding="utf-8")

    write_json(directory / "metadata.json", metadata)
    write_json(directory / "diff-scope.json", diff_scope)
    write_json(directory / "knowledge.json", knowledge)
    plan = directory / "review-plan.md"
    lines = [f"# PR Review Plan for #{pr}", "", f"Title: {metadata.get('title')}", f"URL: {metadata.get('url')}", "", "## Reviewable Files", ""]
    lines.extend(f"- {path}" for path in reviewable_files)
    lines.extend(["", "## Bundle Files", "", f"- Metadata: {directory / 'metadata.json'}", f"- Diff scope: {directory / 'diff-scope.json'}", f"- Knowledge: {directory / 'knowledge.json'}", f"- File contents: {files_dir}", ""])
    plan.write_text("\n".join(lines), encoding="utf-8")
    summary.changed_files = reviewable_files
    summary.temp_dir = str(directory)
    return plan


def copilot_prompt(plan_path: Path, findings_path: Path) -> str:
    return f"""You are reviewing a pull request in opentelemetry-java-instrumentation.

The PR branch is already checked out. Review only the changed PR lines described
in this bundle:

{plan_path}

Write your findings as JSON to this exact file path:

{findings_path}

Required JSON shape:
{{
  "body": "brief pending review summary",
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
- Do not edit files.
- Do not commit.
- Do not push.
- Only flag issues on changed right-side lines in the diff scope.
- Do not flag non-capturing lambdas or method references as unnecessary allocations.
- Use suggestion text only when the replacement is exact and ready for GitHub.
- Return no comments for uncertain or low-confidence observations.
"""


def parse_findings(path: Path, stdout: str) -> dict[str, Any]:
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    text = stdout.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, re.DOTALL)
        if not match:
            raise
        return json.loads(match.group(0))


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
        if isinstance(suggestion, str) and suggestion:
            comment["body"] = body.rstrip() + "\n\n```suggestion\n" + suggestion.rstrip("\n") + "\n```"
        comments.append(comment)
    return comments


def post_review(repo: str, pr: int, payload_path: Path, summary: Summary) -> str:
    result = gh(
        [
            "api",
            f"repos/{repo}/pulls/{pr}/reviews",
            "--method",
            "POST",
            "--input",
            str(payload_path),
            "--jq",
            ".id",
        ],
        summary,
    )
    return result.stdout.strip()


def main() -> int:
    args = parse_args()

    def workflow(summary: Summary) -> int:
        metadata = pr_metadata(args.pr, summary)
        progress("Collecting PR diff")
        diff_names = gh(["pr", "diff", str(args.pr), "--name-only"], summary).stdout.splitlines()
        diff_text = gh(["pr", "diff", str(args.pr), "--color", "never"], summary).stdout
        progress("Parsing PR diff hunks")
        diff_scope = parse_diff(diff_text)
        for path in diff_names:
            diff_scope.setdefault(path, {"changed_lines": [], "hunks": []})

        bundle_dir = make_temp_dir("otel-pr-review", args.pr, args.keep_temp)
        plan_path = write_review_bundle(args.pr, metadata, diff_scope, bundle_dir, summary)
        findings_path = bundle_dir / "findings.json"
        if args.skip_copilot:
            summary.outcome = "prepared review bundle; skipped Copilot handoff"
            return 0

        response = invoke_copilot(copilot_prompt(plan_path, findings_path), summary)
        (bundle_dir / "copilot-response.txt").write_text(response + "\n", encoding="utf-8")
        if status_porcelain().strip():
            raise RuntimeError("Copilot changed the working tree during review; refusing to post")

        findings = parse_findings(findings_path, response)
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
        review_id = post_review(detect_repo(summary), args.pr, payload_path, summary)
        summary.outcome = f"posted pending review {review_id} with {len(comments)} comments"
        summary.notes.append(
            "Submit with: gh api repos/{repo}/pulls/{pr}/reviews/{review}/events --method POST -f event=COMMENT".format(
                repo=detect_repo(), pr=args.pr, review=review_id
            )
        )
        return 0

    return run_pr_workflow(args.pr, workflow, push_required=False)


if __name__ == "__main__":
    sys.exit(main())