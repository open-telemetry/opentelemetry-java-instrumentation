#!/usr/bin/env python3
"""Build the ordered list of instrumentation modules for this review run.

Reads module list from settings.gradle.kts, filters out already-reviewed
modules (read from the otelbot/code-review-progress branch by the workflow
and passed via REVIEW_PROGRESS), respects the open-PR cap, and writes a
`modules` JSON array + `has_work` flag to $GITHUB_OUTPUT.

The review job processes modules sequentially on a single branch, stopping
after it accumulates at least `FILE_THRESHOLD` modified files, so the list
emitted here is an upper-bound slice the job is allowed to walk through.

Environment variables:
  GITHUB_OUTPUT      - path to the GitHub Actions output file
  GH_TOKEN           - token for `gh` CLI (set automatically by the workflow)
  REVIEW_PROGRESS    - newline-separated list of reviewed module names
                       (contents of reviewed.txt on the progress branch)
"""

import json
import os
import re
import subprocess
from pathlib import Path

SETTINGS_FILE = "settings.gradle.kts"
# Skip the run entirely if at least this many automated review PRs are already open.
MAX_OPEN_PRS = 10
# Upper bound on modules the review job will walk through in a single run,
# even if the file-count threshold is never reached. Keeps one run bounded.
MODULE_LIMIT_PER_RUN = 50


def parse_modules() -> list[tuple[str, str]]:
    """Return list of (gradle_name, module_dir) from settings.gradle.kts."""
    text = Path(SETTINGS_FILE).read_text(encoding="utf-8")
    # Match include(":instrumentation:activej-http:6.0:javaagent")
    raw = re.findall(r'include\(":instrumentation:([^"]+)"\)', text)
    pairs = []
    for entry in sorted(raw):
        parts = entry.split(":")
        # Skip shared/helper modules (e.g. "cdi-testing") that don't follow the
        # <library>:<variant> layout used for real instrumentation modules.
        if len(parts) < 2:
            continue
        module_dir = "instrumentation/" + "/".join(parts)
        # Gradle module name: second-to-last:last
        gradle_name = f"{parts[-2]}:{parts[-1]}"
        pairs.append((gradle_name, module_dir))
    return pairs


def load_reviewed() -> set[str]:
    """Load already-reviewed module names from the REVIEW_PROGRESS env var."""
    progress = os.environ.get("REVIEW_PROGRESS", "")
    return {line.strip() for line in progress.splitlines() if line.strip()}


def count_open_prs() -> int:
    """Count open PRs with the automated code review label."""
    result = subprocess.run(
        ["gh", "pr", "list", "--label", "automated code review",
         "--state", "open", "--json", "number", "--jq", "length"],
        capture_output=True, text=True, check=True,
    )
    return int(result.stdout)


def write_output(key: str, value: str) -> None:
    """Append a key=value to $GITHUB_OUTPUT. Values must not contain newlines."""
    assert "\n" not in value, f"multi-line $GITHUB_OUTPUT value not supported: {value!r}"
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as f:
        f.write(f"{key}={value}\n")


def main() -> None:
    all_modules = parse_modules()
    print(f"Total instrumentation modules: {len(all_modules)}")

    reviewed = load_reviewed()
    print(f"Already reviewed: {len(reviewed)}")

    remaining = [(name, d) for name, d in all_modules if name not in reviewed]
    print(f"Remaining modules: {len(remaining)}")

    if not remaining:
        print("All modules have been reviewed!")
        write_output("has_work", "false")
        write_output("modules", "[]")
        return

    open_prs = count_open_prs()
    print(f"Open review PRs: {open_prs}")

    if open_prs >= MAX_OPEN_PRS:
        print(f"PR cap reached ({open_prs} open >= {MAX_OPEN_PRS}). Skipping this cycle.")
        write_output("has_work", "false")
        write_output("modules", "[]")
        return

    batch = remaining[:MODULE_LIMIT_PER_RUN]
    print(f"Dispatching {len(batch)} modules (upper bound for this run)")

    modules = [{"short_name": name, "module_dir": d} for name, d in batch]
    modules_json = json.dumps(modules)
    print(json.dumps(modules, indent=2))

    write_output("has_work", "true")
    write_output("modules", modules_json)


if __name__ == "__main__":
    main()
