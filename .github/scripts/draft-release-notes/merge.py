#!/usr/bin/env python3
"""Merge per-PR decision.json files into a CHANGELOG Unreleased section.

Reads build/changelog-bundle/prs/<N>/decision.json for every PR that has
one, groups kept entries by section, sorts each section by ascending PR
number, and prints the Unreleased markdown block to stdout.

The output contains only the `## Unreleased` heading and section bullets;
the SDK-version preamble is inserted at release time by
.github/scripts/update-changelog-for-release.sh.

Any entry in state other than `include`/`omit`, or `include` without a
section and bullet, is reported on stderr and excluded.

By default writes to stdout. Use --splice to rewrite CHANGELOG.md in
place, replacing just the `## Unreleased` block and preserving any
unlinked summary bullets under `### 🚫 Deprecations` by pulling them
forward into the new block.
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


def extract_unlinked_deprecations(changelog_text: str) -> list[str]:
    """Return bullet blocks under ## Unreleased / ### 🚫 Deprecations that
    have no PR link, preserving multi-line wrapping."""
    m = re.search(r"^## Unreleased\n(.*?)(?=^## |\Z)", changelog_text, re.S | re.M)
    if not m:
        return []
    unreleased = m.group(1)
    m = re.search(
        r"^### 🚫 Deprecations\n(.*?)(?=^### |\Z)",
        unreleased,
        re.S | re.M,
    )
    if not m:
        return []
    section = m.group(1)
    # Split into bullet blocks. A bullet starts with "- " at column 0 and runs
    # until the next "- " at column 0; indented lines and blank lines inside
    # a bullet are absorbed, anything else outside a bullet is dropped.
    blocks: list[list[str]] = []
    for line in section.splitlines():
        if line.startswith("- "):
            blocks.append([line])
        elif blocks and (line.startswith(" ") or not line.strip()):
            blocks[-1].append(line)
    # Keep only blocks without a PR link.
    return [
        b for b in ("\n".join(lines).rstrip() for lines in blocks)
        if b and not re.search(r"\[#\d+\]\(", b)
    ]


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


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--missing-ok", action="store_true",
                    help="do not warn about PRs lacking decision.json")
    ap.add_argument("--report", action="store_true",
                    help="also print a section-count summary on stderr")
    ap.add_argument("--splice", action="store_true",
                    help="rewrite CHANGELOG.md in place (otherwise write to stdout)")
    args = ap.parse_args()

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

    unlinked_deprecations: list[str] = []
    if args.splice and CHANGELOG.exists():
        unlinked_deprecations = extract_unlinked_deprecations(
            CHANGELOG.read_text(encoding="utf-8")
        )

    out_lines = [
        "## Unreleased",
        "",
    ]

    for key, header in SECTION_ORDER:
        items = sorted(grouped[key], key=lambda d: d["pr"])
        preserved = unlinked_deprecations if key == "deprecations" else []
        if not items and not preserved:
            continue
        out_lines.append(header)
        out_lines.append("")
        for block in preserved:
            out_lines.append(block)
        for d in items:
            out_lines.append(format_bullet(d["bullet"], d["pr"]))
        out_lines.append("")

    block = "\n".join(out_lines)
    if not block.endswith("\n"):
        block += "\n"

    if args.splice:
        if not CHANGELOG.exists():
            sys.exit(f"{CHANGELOG} not found")
        text = CHANGELOG.read_text(encoding="utf-8")
        # Match `## Unreleased` through the next `## ` heading, or end of file
        # if Unreleased is the final heading.
        m = re.search(r"^## Unreleased\n.*?(?=^## |\Z)", text, re.S | re.M)
        if not m:
            sys.exit("## Unreleased section not found in CHANGELOG.md")
        new_text = text[: m.start()] + block + "\n" + text[m.end():]
        CHANGELOG.write_text(new_text, encoding="utf-8")
        bullet_count = sum(len(v) for v in grouped.values())
        suffix = f", {len(unlinked_deprecations)} unlinked deprecation(s) preserved" if unlinked_deprecations else ""
        print(f"Rewrote {CHANGELOG} ({bullet_count} PR-linked bullets{suffix})", file=sys.stderr)
    else:
        sys.stdout.write(block)

    if args.report:
        print("Section counts:", file=sys.stderr)
        for key, header in SECTION_ORDER:
            print(f"  {key}: {len(grouped[key])}", file=sys.stderr)
        if unlinked_deprecations:
            print(f"  unlinked-deprecations preserved: {len(unlinked_deprecations)}", file=sys.stderr)

    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
