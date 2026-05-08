#!/usr/bin/env python3
"""Pick the next instrumentation module for this cleanup run.

Reads the module list from settings.gradle.kts, filters out already-processed
modules (passed via REVIEW_PROGRESS), and emits a single module to walk this
run plus a count of how many unprocessed modules remain after it.

The workflow chains itself one module at a time. The finalize step uses
`queue_remaining` to decide whether to self-dispatch or flush the pending
queue into a PR.

Environment variables:
  GITHUB_OUTPUT   - path to the GitHub Actions output file
  GH_TOKEN        - token for `gh` CLI (set automatically by the workflow)
  REVIEW_PROGRESS - newline-separated list of processed module names
                    (contents of processed.txt on the memory branch, plus
                    shorts already in inflight module-cleanup PR bodies)

Outputs (to $GITHUB_OUTPUT):
  has_work        - "true" if a module was picked, "false" otherwise
  short_name      - picked module's gradle short name (e.g. "akka-actor:javaagent")
  module_dir      - picked module's repo-relative directory
  queue_remaining - count of unprocessed modules left AFTER this one
"""

import os
import re
import subprocess
from pathlib import Path

SETTINGS_FILE = "settings.gradle.kts"
# Skip the run entirely if at least this many module-cleanup PRs are already open.
MAX_OPEN_PRS = 5


def parse_modules() -> list[tuple[str, str]]:
    """Return list of (gradle_name, module_dir) from settings.gradle.kts."""
    text = Path(SETTINGS_FILE).read_text(encoding="utf-8")
    raw = re.findall(r'include\(":instrumentation:([^"]+)"\)', text)
    pairs = []
    for entry in sorted(raw):
        parts = entry.split(":")
        if len(parts) < 2:
            continue
        module_dir = "instrumentation/" + "/".join(parts)
        gradle_name = f"{parts[-2]}:{parts[-1]}"
        pairs.append((gradle_name, module_dir))
    return pairs


def load_processed() -> set[str]:
    """Load already-processed module names from the REVIEW_PROGRESS env var."""
    progress = os.environ.get("REVIEW_PROGRESS", "")
    return {line.strip() for line in progress.splitlines() if line.strip()}


def count_open_prs() -> int:
    result = subprocess.run(
        ["gh", "pr", "list", "--label", "module cleanup",
         "--state", "open", "--json", "number", "--jq", "length"],
        capture_output=True, text=True, check=True,
    )
    return int(result.stdout)


def write_output(key: str, value: str) -> None:
    assert "\n" not in value, f"multi-line $GITHUB_OUTPUT value not supported: {value!r}"
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as f:
        f.write(f"{key}={value}\n")


def emit_no_work() -> None:
    write_output("has_work", "false")
    write_output("short_name", "")
    write_output("module_dir", "")
    write_output("queue_remaining", "0")


def main() -> None:
    all_modules = parse_modules()
    print(f"Total instrumentation modules: {len(all_modules)}")

    processed = load_processed()
    print(f"Already processed: {len(processed)}")

    remaining = [(n, d) for n, d in all_modules if n not in processed]
    print(f"Remaining modules: {len(remaining)}")

    if not remaining:
        print("All modules have been processed!")
        emit_no_work()
        return

    open_prs = count_open_prs()
    print(f"Open module-cleanup PRs: {open_prs}")
    if open_prs >= MAX_OPEN_PRS:
        print(f"PR cap reached ({open_prs} open >= {MAX_OPEN_PRS}). Skipping this cycle.")
        emit_no_work()
        return

    short_name, module_dir = remaining[0]
    queue_remaining = len(remaining) - 1
    print(f"Picked: {short_name} ({module_dir})")
    print(f"Queue remaining after this run: {queue_remaining}")

    write_output("has_work", "true")
    write_output("short_name", short_name)
    write_output("module_dir", module_dir)
    write_output("queue_remaining", str(queue_remaining))


if __name__ == "__main__":
    main()
