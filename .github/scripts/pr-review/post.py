"""Validate findings and publish a GitHub review."""

from __future__ import annotations

import difflib
import json
import re
from pathlib import Path
from typing import Any

from common import Summary, gh_json


def small_suggestion(source: Path, start: int, end: int, replacement: str) -> bool:
    if "```" in replacement or not source.is_file():
        return False
    lines = source.read_text(encoding="utf-8").splitlines()
    if lines and lines[-1] == "...[file truncated]...":
        lines = lines[:-2]
    if end > len(lines):
        return False
    original = lines[start - 1 : end]
    proposed = replacement.rstrip("\n").splitlines()
    ops = difflib.SequenceMatcher(None, original, proposed, autojunk=False).get_opcodes()
    return original != proposed and sum(j2 - j1 for op, _, _, j1, j2 in ops if op != "equal") < 15


def prepare_review(
    findings: dict[str, Any], bundle: Path, metadata: dict[str, Any], summary: Summary
) -> dict[str, Any]:
    diff_scope = load_json(bundle / "diff-scope.json", "diff scope")
    raw_comments = findings.get("comments") if isinstance(findings, dict) else None
    if not isinstance(raw_comments, list):
        raise ValueError("Final findings must contain a comments array")
    comments: list[dict[str, Any]] = []
    for raw in raw_comments:
        if (
            not isinstance(raw, dict)
            or raw.get("kind") not in ("review", "simplification")
            or not isinstance(raw.get("path"), str)
            or not isinstance(raw.get("body"), str)
            or not raw["body"].strip()
            or re.search(r"(?im)^\s*(?:`{3,}|~{3,})\s*suggestion\b", raw["body"])
            or type(raw.get("line")) is not int
            or ("start_line" in raw and type(raw["start_line"]) is not int)
            or ("suggestion" in raw and not isinstance(raw["suggestion"], str))
        ):
            raise ValueError("Invalid final review comment")
        path, line, body = raw["path"], raw["line"], raw["body"].strip()
        start = raw.get("start_line", line)
        scope = diff_scope.get(path, {})
        if line not in scope.get("changed_lines", []) or not any(
            first <= start <= line <= last for first, last in scope.get("hunks", [])
        ):
            raise ValueError(f"Invalid diff anchor for {path!r}:{start}-{line}")
        if raw["kind"] == "simplification" and not body.startswith("[Simplification] "):
            body = f"[Simplification] {body}"
        if "suggestion" in raw:
            suggestion = raw["suggestion"]
            if small_suggestion(bundle / "files" / path, start, line, suggestion):
                body += "\n\n```suggestion\n" + suggestion.rstrip("\n") + "\n```"
            else:
                summary.notes.append(f"Kept comment without suggestion for {path!r}:{line}")
        comment = {"path": path, "line": line, "side": "RIGHT", "body": body}
        if start != line:
            comment.update(start_line=start, start_side="RIGHT")
        comments.append(comment)
    body = findings.get("body")
    if comments and (not isinstance(body, str) or not body.strip()):
        raise ValueError("Final findings with comments must include a review summary")
    return {
        "pr": metadata["number"],
        "base_sha": metadata["baseRefOid"],
        "review": {"commit_id": metadata["headRefOid"], "body": body if comments else "No findings.", "comments": comments},
    }


def post_review(repo: str, pr: int, payload_path: Path, summary: Summary) -> None:
    review = gh_json(
        ["api", f"repos/{repo}/pulls/{pr}/reviews", "--method", "POST", "--input", str(payload_path)], summary
    )
    summary.review_url = review["html_url"]


def load_json(path: Path, label: str) -> dict[str, Any]:
    if not path.exists():
        raise RuntimeError(f"missing {label}: {path}")
    return json.loads(path.read_text(encoding="utf-8"))
