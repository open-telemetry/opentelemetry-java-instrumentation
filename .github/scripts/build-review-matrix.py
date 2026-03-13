#!/usr/bin/env python3
"""Build a GitHub Actions matrix of instrumentation modules to review.

Reads module list from settings.gradle.kts, filters out already-reviewed
modules (stored in the REVIEW_PROGRESS repository variable), respects the
open-PR cap, and writes matrix JSON + has_work flag to $GITHUB_OUTPUT.

Environment variables:
  GITHUB_OUTPUT      - path to the GitHub Actions output file
  GH_TOKEN           - token for `gh` CLI (set automatically by the workflow)
  REVIEW_PROGRESS    - comma-separated list of reviewed module names
                       (passed from the repo variable by the workflow)
"""

import json
import os
import re
import subprocess
from pathlib import Path

SETTINGS_FILE = "settings.gradle.kts"
MAX_PRS = 10
BATCH_SIZE = 10


def parse_modules() -> list[tuple[str, str]]:
    """Return list of (gradle_name, module_dir) from settings.gradle.kts."""
    text = Path(SETTINGS_FILE).read_text()
    # Match include(":instrumentation:activej-http:6.0:javaagent")
    raw = re.findall(r'include\(":instrumentation:([^"]+)"\)', text)
    pairs = []
    for entry in sorted(raw):
        parts = entry.split(":")
        module_dir = "instrumentation/" + "/".join(parts)
        # Gradle module name: second-to-last:last
        gradle_name = f"{parts[-2]}:{parts[-1]}" if len(parts) >= 2 else parts[0]
        pairs.append((gradle_name, module_dir))
    return pairs


def load_reviewed() -> set[str]:
    """Load already-reviewed module names from the REVIEW_PROGRESS env var."""
    progress = os.environ.get("REVIEW_PROGRESS", "")
    if not progress:
        return set()
    return set(progress.split(","))


def count_open_prs() -> int:
    """Count open PRs with the automated-code-review label."""
    try:
        result = subprocess.run(
            ["gh", "pr", "list", "--label", "automated-code-review",
             "--state", "open", "--json", "number", "--jq", "length"],
            capture_output=True, text=True, check=True,
        )
        return int(result.stdout.strip() or "0")
    except (subprocess.CalledProcessError, ValueError):
        return 0


def write_output(key: str, value: str) -> None:
    """Append a key=value to $GITHUB_OUTPUT."""
    output_file = os.environ["GITHUB_OUTPUT"]
    with open(output_file, "a") as f:
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
        write_output("matrix", '{"include":[]}')
        return

    open_prs = count_open_prs()
    print(f"Open review PRs: {open_prs}")

    available = MAX_PRS - open_prs
    if available <= 0:
        print(f"PR cap reached ({open_prs} open). Skipping this cycle.")
        write_output("has_work", "false")
        write_output("matrix", '{"include":[]}')
        return

    batch = remaining[:min(BATCH_SIZE, available)]
    print(f"Dispatching {len(batch)} modules")

    matrix = {"include": [
        {"short_name": name, "module_dir": d} for name, d in batch
    ]}
    matrix_json = json.dumps(matrix)
    print(json.dumps(matrix, indent=2))

    write_output("has_work", "true")
    write_output("matrix", matrix_json)


if __name__ == "__main__":
    main()
