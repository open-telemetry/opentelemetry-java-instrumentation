#!/usr/bin/env python3
"""Draft the Unreleased section of CHANGELOG.md end-to-end.

Runs three steps in order, aborting on the first failure:

  1. fetch.py
     - generates build/changelog-bundle/prs/<N>/{patch.diff,meta.json,...}
     - incremental: reuses per-PR dirs whose meta.json commit hash matches;
       (re)fetches only new or changed PRs. Stale PR/ref dirs are pruned.
     - --refetch re-downloads every PR
  2. classify.py
     - deterministic preclassify (renovate, docs/test/build-only, version
       bumps) followed by per-PR LLM classification for everything else
     - requires `copilot` on PATH; model overridable via $CLASSIFY_MODEL
  3. merge.py --splice --report
     - rewrites ## Unreleased in CHANGELOG.md

After it finishes, edit CHANGELOG.md directly to adjust wording, grouping,
or section. Classification rules live in rules.md alongside this script.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent

DRAFT = HERE / "fetch.py"
CLASSIFY = HERE / "classify.py"
MERGE = HERE / "merge.py"


def run(cmd: list[str], *, dry_run: bool) -> int:
    printable = " ".join(repr(c) if " " in c else c for c in cmd)
    print(f"$ {printable}", flush=True)
    if dry_run:
        return 0
    return subprocess.run(cmd, cwd=REPO_ROOT).returncode


def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument(
        "--refetch",
        action="store_true",
        help="re-download every PR even if its bundle already exists",
    )
    ap.add_argument(
        "--force-classify",
        action="store_true",
        help="reclassify PRs that already have a decision.json",
    )
    ap.add_argument(
        "--dry-run",
        action="store_true",
        help="print commands without running them",
    )
    args = ap.parse_args()

    python = sys.executable or "python3"

    fetch_cmd = [python, str(DRAFT)]
    if args.refetch:
        fetch_cmd.append("--refetch")

    classify_cmd = [python, str(CLASSIFY)]
    if args.force_classify:
        classify_cmd.append("--force")

    # fetch.py: incremental by default; --refetch re-downloads all.
    # classify.py: deterministic preclassify + per-PR LLM in one pass.
    # merge.py --splice: rewrite ## Unreleased in CHANGELOG.md.
    steps = [
        ("fetch.py", fetch_cmd),
        ("classify.py", classify_cmd),
        ("merge.py", [python, str(MERGE), "--splice", "--report"]),
    ]
    for name, cmd in steps:
        rc = run(cmd, dry_run=args.dry_run)
        if rc != 0:
            print(f"{name} failed; aborting", file=sys.stderr)
            return rc

    if not args.dry_run:
        print(
            "\nDone. Review:"
            "\n  git diff CHANGELOG.md"
            "\n"
            "\nEdit CHANGELOG.md directly to adjust wording, grouping, or section.",
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
