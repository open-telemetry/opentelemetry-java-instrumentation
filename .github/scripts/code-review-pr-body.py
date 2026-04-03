#!/usr/bin/env python3
"""Validate a structured review report and render a PR body."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import TypedDict


class ValidatedChange(TypedDict):
    path: str
    category: str
    change: str
    reason: str
    line_hint: int | None


class ValidatedUnresolved(TypedDict):
    path: str
    reason: str


class ValidatedReport(TypedDict):
    summary: str
    changes: list[ValidatedChange]
    unresolved: list[ValidatedUnresolved]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--module-dir", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--artifact-url", required=True)
    return parser.parse_args()


def strip_code_fences(raw: str) -> str:
    lines = raw.strip().splitlines()
    if len(lines) >= 2 and lines[0].startswith("```") and lines[-1].startswith("```"):
        return "\n".join(lines[1:-1]).strip()
    return raw.strip()


def load_report(path: Path) -> dict[str, object]:
    raw = path.read_text(encoding="utf-8")
    if not raw.strip():
        raise ValueError(f"Review report is empty: {path}")

    candidates = [strip_code_fences(raw)]
    start = raw.find("{")
    end = raw.rfind("}")
    if start != -1 and end != -1 and end >= start:
        snippet = raw[start : end + 1].strip()
        if snippet not in candidates:
            candidates.append(snippet)

    for candidate in candidates:
        try:
            report = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if not isinstance(report, dict):
            raise ValueError("Review report must be a JSON object")
        return report

    raise ValueError("Review report is not valid JSON")


def require_string(value: object, field_name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field_name} must be a non-empty string")
    return value.strip()


def validate_change(change: object, field_name: str) -> ValidatedChange:
    if not isinstance(change, dict):
        raise ValueError(f"{field_name} entries must be JSON objects")

    line_hint = change.get("line_hint")
    if line_hint is not None and not isinstance(line_hint, int):
        raise ValueError(f"{field_name}.line_hint must be an integer or null")

    return {
        "path": require_string(change.get("path"), f"{field_name}.path"),
        "category": require_string(change.get("category"), f"{field_name}.category"),
        "change": require_string(change.get("change"), f"{field_name}.change"),
        "reason": require_string(change.get("reason"), f"{field_name}.reason"),
        "line_hint": line_hint,
    }


def validate_unresolved(item: object, field_name: str) -> ValidatedUnresolved:
    if not isinstance(item, dict):
        raise ValueError(f"{field_name} entries must be JSON objects")
    return {
        "path": require_string(item.get("path"), f"{field_name}.path"),
        "reason": require_string(item.get("reason"), f"{field_name}.reason"),
    }


def validate_report(report: dict[str, object]) -> ValidatedReport:
    summary = require_string(report.get("summary"), "summary")

    changes = report.get("changes")
    if not isinstance(changes, list):
        raise ValueError("changes must be a JSON array")

    unresolved = report.get("unresolved")
    if not isinstance(unresolved, list):
        raise ValueError("unresolved must be a JSON array")

    return {
        "summary": summary,
        "changes": [
            validate_change(change, f"changes[{index}]")
            for index, change in enumerate(changes)
        ],
        "unresolved": [
            validate_unresolved(item, f"unresolved[{index}]")
            for index, item in enumerate(unresolved)
        ],
    }


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ").strip()


def render_path(path: str, line_hint: object) -> str:
    file_name = Path(path).name
    if isinstance(line_hint, int):
        return f"`{file_name}:{line_hint}`"
    return f"`{file_name}`"


def group_changes_by_category(
    changes: list[ValidatedChange],
) -> list[tuple[str, list[ValidatedChange]]]:
    grouped: dict[str, list[ValidatedChange]] = {}
    ordered_categories: list[str] = []

    for change in changes:
        category = escape_cell(change["category"])
        if category not in grouped:
            grouped[category] = []
            ordered_categories.append(category)
        grouped[category].append(change)

    return [(category, grouped[category]) for category in ordered_categories]


def render_change(change: ValidatedChange) -> list[str]:
    location = render_path(change["path"], change["line_hint"])
    change_text = escape_cell(change["change"])
    reason = escape_cell(change["reason"])
    return [
        f"**File:** {location}  ",
        f"**Change:** {change_text}  ",
        f"**Reason:** {reason}",
        "",
    ]


def render_unresolved_item(item: ValidatedUnresolved) -> list[str]:
    location = render_path(item["path"], None)
    reason = escape_cell(item["reason"])
    return [
        f"**File:** {location}  ",
        f"**Reason:** {reason}",
        "",
    ]


def render_body(
    report: ValidatedReport,
    module_dir: str,
    model: str,
    artifact_url: str,
) -> str:
    details_url = artifact_url.strip()
    details_label = "Download code review diagnostics"

    lines = [
        "### Summary",
        "",
        str(report["summary"]),
        "",
        "### Applied Changes",
        "",
    ]

    changes = report["changes"]
    if changes:
        for category, category_changes in group_changes_by_category(changes):
            lines.extend([f"#### {category}", ""])
            for change in category_changes:
                lines.extend(render_change(change))
    else:
        lines.extend(["No safe automated changes were applied.", ""])

    unresolved = report["unresolved"]
    if unresolved:
        lines.extend(["### Unresolved Items", ""])
        for item in unresolved:
            lines.extend(render_unresolved_item(item))

    lines.extend(["---", "", f"[{details_label}]({details_url})", ""])
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    report = validate_report(load_report(Path(args.input)))
    body = render_body(
        report,
        args.module_dir,
        args.model,
        args.artifact_url,
    )
    Path(args.output).write_text(body, encoding="utf-8")


if __name__ == "__main__":
    main()