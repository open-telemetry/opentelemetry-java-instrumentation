#!/usr/bin/env python3
"""Render a PR body from a structured review report."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


def strip_code_fences(raw: str) -> str:
    lines = raw.strip().splitlines()
    if len(lines) >= 3 and lines[0].startswith("```") and lines[-1].startswith("```"):
        return "\n".join(lines[1:-1]).strip()
    return raw.strip()


def load_report(path: Path) -> dict:
    return json.loads(strip_code_fences(path.read_text(encoding="utf-8")))


# Characters that can inject Markdown/HTML structure when LLM-supplied text
# ends up as PR body prose. Escape them so headings/links/emphasis/HTML can't
# be forged.
_MARKDOWN_METACHARS = r"\`*_{}[]()#+-.!|<>"
_MARKDOWN_TRANSLATION = str.maketrans({c: "\\" + c for c in _MARKDOWN_METACHARS})


def escape_markdown(value: str) -> str:
    return " ".join(value.translate(_MARKDOWN_TRANSLATION).split()).strip()


def render_path(path: str, line_hint: object) -> str:
    file_name = Path(path).name
    if isinstance(line_hint, int):
        return f"`{file_name}:{line_hint}`"
    return f"`{file_name}`"


def group_changes_by_category(changes: list[dict]) -> list[tuple[str, list[dict]]]:
    grouped: dict[str, list[dict]] = {}
    ordered_categories: list[str] = []
    for change in changes:
        category = escape_markdown(change["category"])
        if category not in grouped:
            grouped[category] = []
            ordered_categories.append(category)
        grouped[category].append(change)
    return [(category, grouped[category]) for category in ordered_categories]


def render_change(change: dict) -> list[str]:
    return [
        f"**File:** {render_path(change['path'], change.get('line_hint'))}  ",
        f"**Change:** {escape_markdown(change['change'])}  ",
        f"**Reason:** {escape_markdown(change['reason'])}",
        "",
    ]


def render_unresolved_item(item: dict) -> list[str]:
    return [
        f"**File:** {render_path(item['path'], None)}  ",
        f"**Reason:** {escape_markdown(item['reason'])}",
        "",
    ]


def render_body(report: dict) -> str:
    lines = [
        "### Summary",
        "",
        report["summary"],
        "",
        "### Applied Changes",
        "",
    ]

    changes = report.get("changes") or []
    if changes:
        for category, category_changes in group_changes_by_category(changes):
            lines.extend([f"#### {category}", ""])
            for change in category_changes:
                lines.extend(render_change(change))
    else:
        lines.extend(["No safe automated changes were applied.", ""])

    unresolved = report.get("unresolved") or []
    if unresolved:
        lines.extend(["### Unresolved Items", ""])
        for item in unresolved:
            lines.extend(render_unresolved_item(item))

    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    report = load_report(Path(args.input))
    Path(args.output).write_text(render_body(report), encoding="utf-8")


if __name__ == "__main__":
    main()
