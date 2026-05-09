#!/usr/bin/env python3
"""Validate Copilot review findings and post the GitHub review.

CI entry point for the pr-review agentic workflow finalize job. Reads the
findings JSON the agent produced, validates each comment against the diff
hunks recorded in the bundle, and posts a single GitHub review.

Default submit event is COMMENT (immediately published, non-blocking,
non-approving).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from common import Summary, detect_repo, gh_json, progress, write_json


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pr", type=int, help="pull request number")
    parser.add_argument("--bundle-dir", type=Path, required=True, help="bundle directory written by prepare_review_bundle.py")
    parser.add_argument("--findings", type=Path, required=True, help="path to findings.json the agent produced")
    parser.add_argument(
        "--event",
        choices=["COMMENT", "PENDING"],
        default="COMMENT",
        help="review submit event (default: COMMENT)",
    )
    parser.add_argument("--triggered-by", default="", help="footer: trigger source")
    parser.add_argument("--model", default="", help="footer: resolved model name")
    parser.add_argument("--model-warning", default="", help="footer: optional model-fallback warning")
    parser.add_argument("--no-post", action="store_true", help="prepare payload but do not post")
    return parser.parse_args()


def compose_body_suffix(triggered_by: str, model: str, model_warning: str) -> str:
    if not triggered_by and not model:
        return ""
    lines = ["---", f"_Triggered by: {triggered_by}. Model: `{model}`._"]
    if model_warning:
        lines += ["", f"_Note: {model_warning}_"]
    return "\n".join(lines) + "\n"


def lines_inside_hunk(path: str, start_line: int, line: int, diff_scope: dict[str, dict[str, Any]]) -> bool:
    hunks = diff_scope.get(path, {}).get("hunks") or []
    for current in range(start_line, line + 1):
        if not any(start <= current <= end for start, end in hunks):
            return False
    return True


def validate_comments(
    findings: dict[str, Any],
    diff_scope: dict[str, dict[str, Any]],
    summary: Summary,
) -> list[dict[str, Any]]:
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


def load_json(path: Path, label: str) -> dict[str, Any]:
    if not path.exists():
        raise RuntimeError(f"missing {label}: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    args = parse_args()
    summary = Summary(pr=args.pr)
    try:
        diff_scope = load_json(args.bundle_dir / "diff-scope.json", "diff-scope.json")
        metadata = load_json(args.bundle_dir / "metadata.json", "metadata.json")
        summary.pr_url = metadata.get("url")

        findings = load_json(args.findings, "findings.json")
        progress("Validating Copilot review findings against diff hunks")
        comments = validate_comments(findings, diff_scope, summary)
        body = findings.get("body") if isinstance(findings.get("body"), str) else "Automated review."
        suffix = compose_body_suffix(args.triggered_by, args.model, args.model_warning)
        if suffix:
            body = body.rstrip() + "\n\n" + suffix

        payload: dict[str, Any] = {
            "commit_id": metadata["headRefOid"],
            "body": body,
            "comments": comments,
        }
        if args.event == "COMMENT":
            payload["event"] = "COMMENT"
        payload_path = args.bundle_dir / "review-payload.json"
        write_json(payload_path, payload)

        for note in summary.notes:
            print(f"note: {note}")

        if args.no_post:
            print(f"prepared review payload with {len(comments)} comments; not posted (--no-post)")
            return 0

        base_repo = detect_repo(summary)
        progress(f"Posting GitHub review (event={args.event})")
        review_id = post_review(base_repo, args.pr, payload_path, summary)
        print(f"posted review {review_id} (event={args.event}) with {len(comments)} comments")
        if summary.review_url:
            print(f"review url: {summary.review_url}")
        return 0
    except Exception as e:  # pragma: no cover - surfaced to the workflow log
        print(f"ERROR: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
