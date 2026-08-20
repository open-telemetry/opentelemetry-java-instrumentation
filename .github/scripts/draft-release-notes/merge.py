#!/usr/bin/env python3
"""Merge per-PR decision.json files into a CHANGELOG Unreleased section.

Reads build/changelog-bundle/prs/<N>/decision.json for every PR that has
one, groups kept entries by section, sorts new entries by ascending PR
number, and prints the Unreleased markdown block to stdout.

The output contains only the `## Unreleased` heading and section bullets;
the SDK-version preamble is inserted at release time by
.github/scripts/update-changelog-for-release.sh.

With --splice, existing Unreleased content is preserved. Generated entries
whose PR is already linked from that block are skipped, and the remaining
entries are appended to their sections. This keeps hand-written breaking and
deprecation notes authoritative and makes reruns idempotent.

By default writes to stdout. Use --splice to rewrite CHANGELOG.md in
place. Use --replace-existing with --splice to discard the existing block.

Any entry in state other than `include`/`omit`, or `include` without a
section and bullet, is reported on stderr and excluded.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import textwrap
from pathlib import Path

BUNDLE_ROOT = Path("build/changelog-bundle/prs")
CHANGELOG = Path("CHANGELOG.md")

SECTION_ORDER = [
    ("breaking", "### ⚠️ Breaking changes to non-stable APIs"),
    ("deprecations", "### 🚫 Deprecations"),
    ("new-javaagent", "### 🌟 New javaagent instrumentation"),
    ("new-library", "### 🌟 New library instrumentation"),
    ("enhancements", "### 📈 Enhancements"),
    ("bug-fixes", "### 🛠️ Bug fixes"),
]

PR_URL = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/{pr}"
PR_LINK_RE = re.compile(
    r"^  \(?\[#(\d+)\]\("
    r"https://github\.com/open-telemetry/opentelemetry-java-instrumentation/pull/\1\)",
    re.MULTILINE,
)


def load_decisions() -> list[dict]:
    out = []
    if not BUNDLE_ROOT.is_dir():
        sys.exit(f"{BUNDLE_ROOT} not found")
    for d in sorted(BUNDLE_ROOT.iterdir(), key=lambda p: int(p.name) if p.name.isdigit() else 0):
        if not d.is_dir() or not d.name.isdigit():
            continue
        p = d / "decision.json"
        if not p.exists():
            continue
        try:
            obj = json.loads(p.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as e:
            print(f"#{d.name}: decision.json unreadable: {e}", file=sys.stderr)
            continue
        obj.setdefault("pr", int(d.name))
        out.append(obj)
    return out


def format_bullet(bullet: str, pr: int) -> str:
    bullet = bullet.rstrip()
    # Wrap the bullet text to match repo style (see .editorconfig
    # max_line_length = 100). First line starts with "- " (2-char prefix);
    # continuation lines indent 2 spaces so they align with the bullet text.
    # textwrap preserves inline code spans and punctuation verbatim.
    #
    # Replace spaces inside `...` code spans with U+00A0 (non-breaking space)
    # so textwrap does not split the span across lines. Python textwrap treats
    # only ASCII whitespace as break opportunities, so NBSP survives the fill
    # and is swapped back to a regular space in the output.
    NBSP = "\u00a0"
    protected = re.sub(
        r"`[^`\n]+`",
        lambda m: m.group(0).replace(" ", NBSP),
        bullet,
    )
    wrapped = textwrap.fill(
        protected,
        width=100,
        initial_indent="- ",
        subsequent_indent="  ",
        break_long_words=False,
        break_on_hyphens=False,
    )
    wrapped = wrapped.replace(NBSP, " ")
    return f"{wrapped}\n  ([#{pr}]({PR_URL.format(pr=pr)}))"


def render_generated_block(grouped: dict[str, list[dict]]) -> str:
    out_lines = [
        "## Unreleased",
        "",
    ]
    for key, header in SECTION_ORDER:
        items = sorted(grouped[key], key=lambda d: d["pr"])
        if not items:
            continue
        out_lines.append(header)
        out_lines.append("")
        for d in items:
            out_lines.append(format_bullet(d["bullet"], d["pr"]))
        out_lines.append("")
    return "\n".join(out_lines).rstrip() + "\n"


def merge_with_existing(
    existing_block: str, grouped: dict[str, list[dict]]
) -> tuple[str, int, int]:
    covered_prs = {int(match) for match in PR_LINK_RE.findall(existing_block)}
    pending = {
        key: [d for d in values if d["pr"] not in covered_prs]
        for key, values in grouped.items()
    }
    skipped = sum(len(values) - len(pending[key]) for key, values in grouped.items())

    block = existing_block.rstrip() + "\n"
    for index, (key, header) in enumerate(SECTION_ORDER):
        items = sorted(pending[key], key=lambda d: d["pr"])
        if not items:
            continue
        bullets = "\n".join(format_bullet(d["bullet"], d["pr"]) for d in items)
        header_match = re.search(rf"^{re.escape(header)}\n", block, re.MULTILINE)
        if header_match:
            body_start = header_match.end()
            next_heading = re.search(r"^(?:### |## )", block[body_start:], re.MULTILINE)
            body_end = (
                body_start + next_heading.start()
                if next_heading is not None
                else len(block)
            )
            body = block[body_start:body_end].strip("\n")
            replacement = f"\n{body}\n{bullets}\n\n" if body else f"\n{bullets}\n\n"
            block = block[:body_start] + replacement + block[body_end:]
            continue

        insert_at = len(block)
        for _, later_header in SECTION_ORDER[index + 1:]:
            later_match = re.search(rf"^{re.escape(later_header)}$", block, re.MULTILINE)
            if later_match:
                insert_at = later_match.start()
                break
        section = f"{header}\n\n{bullets}\n\n"
        if insert_at == len(block):
            block = block.rstrip() + "\n\n" + section
        else:
            block = block[:insert_at] + section + block[insert_at:]

    added = sum(len(values) for values in pending.values())
    return block.rstrip() + "\n", added, skipped


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--missing-ok", action="store_true",
                    help="do not warn about PRs lacking decision.json")
    ap.add_argument("--report", action="store_true",
                    help="also print a section-count summary on stderr")
    ap.add_argument("--splice", action="store_true",
                    help="merge into CHANGELOG.md in place (otherwise write to stdout)")
    ap.add_argument(
        "--replace-existing",
        action="store_true",
        help="with --splice, replace rather than preserve the existing Unreleased block",
    )
    args = ap.parse_args()
    if args.replace_existing and not args.splice:
        ap.error("--replace-existing requires --splice")

    decisions = load_decisions()

    if not args.missing_ok:
        # Warn about PR bundles with no decision artifact.
        bundles = {int(d.name) for d in BUNDLE_ROOT.iterdir() if d.is_dir() and d.name.isdigit()}
        decided = {d["pr"] for d in decisions}
        missing = sorted(bundles - decided)
        if missing:
            print(
                f"WARNING: {len(missing)} PR bundles have no decision.json: "
                + ", ".join(f"#{n}" for n in missing[:20])
                + (" ..." if len(missing) > 20 else ""),
                file=sys.stderr,
            )

    grouped: dict[str, list[dict]] = {key: [] for key, _ in SECTION_ORDER}
    errors = 0
    for d in decisions:
        pr = d.get("pr")
        decision = d.get("decision")
        section = d.get("section")
        if decision == "omit":
            continue
        reason: str | None = None
        if decision != "include":
            reason = f"unknown decision {decision!r}"
        elif section not in grouped:
            reason = f"unknown section {section!r}"
        elif not d.get("bullet"):
            reason = "empty bullet"
        if reason is not None:
            print(f"#{pr}: skipping, {reason}", file=sys.stderr)
            errors += 1
            continue
        grouped[section].append(d)

    block = render_generated_block(grouped)
    added = sum(len(values) for values in grouped.values())
    skipped = 0

    if args.splice:
        if not CHANGELOG.exists():
            sys.exit(f"{CHANGELOG} not found")
        text = CHANGELOG.read_text(encoding="utf-8")
        # Match `## Unreleased` through the next `## ` heading, or end of file
        # if Unreleased is the final heading.
        m = re.search(r"^## Unreleased\n.*?(?=^## |\Z)", text, re.S | re.M)
        if not m:
            sys.exit("## Unreleased section not found in CHANGELOG.md")
        if not args.replace_existing:
            block, added, skipped = merge_with_existing(m.group(0), grouped)
        new_text = text[: m.start()] + block + "\n" + text[m.end():]
        CHANGELOG.write_text(new_text, encoding="utf-8")
        print(
            f"Updated {CHANGELOG} ({added} PR-linked bullets added"
            + (f", {skipped} existing PR links preserved" if skipped else "")
            + ")",
            file=sys.stderr,
        )
    else:
        sys.stdout.write(block)

    if args.report:
        print("Section counts:", file=sys.stderr)
        for key, header in SECTION_ORDER:
            print(f"  {key}: {len(grouped[key])}", file=sys.stderr)

    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
