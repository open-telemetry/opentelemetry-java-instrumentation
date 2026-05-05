#!/usr/bin/env python3
"""Walk instrumentation modules sequentially, applying Copilot cleanup fixes.

Invoked by the `module-cleanup` workflow's `Run Copilot cleanup loop` step.
Reads the dispatch list from MODULES_JSON, invokes the `copilot` CLI per
module, squashes each module's edits into a single commit on the current
branch, and stops once the total number of files modified versus `origin/main`
reaches FILE_THRESHOLD.

Per-module diagnostics (raw JSONL, extracted report, final assistant message,
JSONL diagnostics, and a PR body fragment) are written under COPILOT_ROOT,
and the ordered list of modules actually processed (including no-op ones) is
written to PROCESSED_MODULES so the workflow can append them to `reviewed.txt`.

Environment variables (all required):
  MODULES_JSON                    - JSON array of {short_name, module_dir}
  FILE_THRESHOLD                  - integer; stop after this many modified files
  MODEL                           - Copilot model name to pass via `--model`
  COPILOT_ROOT                    - directory for per-module diagnostics
  FRAGMENTS_DIR                   - directory for per-module PR body fragments
  PROCESSED_MODULES               - path to file receiving processed short_names
  COPILOT_REVIEW_PROMPT_TEMPLATE  - prompt with `__MODULE_DIR__` placeholder
  COPILOT_GITHUB_TOKEN            - Copilot CLI auth token
  GITHUB_OUTPUT                   - GitHub Actions output file
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
EXTRACT_REPORT_SCRIPT = SCRIPTS_DIR / "extract-report.py"
JSONL_DIAGNOSTICS_SCRIPT = SCRIPTS_DIR / "jsonl-diagnostics.py"
PR_BODY_SCRIPT = SCRIPTS_DIR / "pr-body.py"


def require_env(name: str) -> str:
    value = os.environ.get(name)
    if value is None or value == "":
        raise SystemExit(f"Missing required environment variable: {name}")
    return value


def write_github_output(key: str, value: str) -> None:
    with open(require_env("GITHUB_OUTPUT"), "a", encoding="utf-8") as f:
        f.write(f"{key}={value}\n")


def run_git(*args: str, capture: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", *args],
        check=True,
        text=True,
        capture_output=capture,
    )


def current_head_sha() -> str:
    return run_git("rev-parse", "HEAD", capture=True).stdout.strip()


def count_modified_files_vs_main() -> int:
    result = run_git("diff", "--name-only", "origin/main", capture=True)
    return sum(1 for line in result.stdout.splitlines() if line.strip())


def count_commits_since_main() -> int:
    result = run_git("rev-list", "--count", "origin/main..HEAD", capture=True)
    return int(result.stdout)


def staged_changes_present() -> bool:
    # `git diff --cached --quiet` exits 0 if no staged changes, 1 if there are.
    return subprocess.run(["git", "diff", "--cached", "--quiet"], check=False).returncode != 0


def run_copilot(prompt: str, model: str, output_path: Path) -> int:
    """Invoke the Copilot CLI, streaming JSONL to `output_path`. Returns exit code."""
    with output_path.open("wb") as out:
        # `--yolo` disables Copilot CLI confirmation prompts for tool calls.
        # This is intentional for autonomous CI and must not be copied into
        # interactive/local scripts without review.
        proc = subprocess.run(
            [
                "copilot",
                "-p", prompt,
                "--agent", "module-cleanup",
                "--model", model,
                "--output-format", "json",
                "--silent",
                "--stream", "off",
                "--yolo",
            ],
            stdout=out,
            check=False,
            # stderr inherits the workflow's stderr so Actions logs show live output.
        )
    return proc.returncode


def run_extract_report(
    *,
    copilot_output: Path,
    final_message: Path,
    review_report: Path,
) -> int:
    return subprocess.run(
        [
            sys.executable, str(EXTRACT_REPORT_SCRIPT),
            "--input", str(copilot_output),
            "--final-message-output", str(final_message),
            "--output", str(review_report),
        ],
        check=False,
    ).returncode


def write_diagnostics(copilot_output: Path, diagnostics: Path) -> None:
    with diagnostics.open("wb") as out:
        subprocess.run(
            [sys.executable, str(JSONL_DIAGNOSTICS_SCRIPT), "--input", str(copilot_output)],
            stdout=out,
            check=False,
        )


def render_pr_body_fragment(
    *,
    fragment_path: Path,
    short_name: str,
    module_dir: str,
    review_report: Path,
) -> None:
    body_file = fragment_path.with_suffix(fragment_path.suffix + ".body")
    subprocess.run(
        [
            sys.executable, str(PR_BODY_SCRIPT),
            "--input", str(review_report),
            "--output", str(body_file),
        ],
        check=True,
    )
    header = (
        f"## Module: `{short_name}`\n"
        f"\n"
        f"_Module path: `{module_dir}`_\n"
        f"\n"
    )
    fragment_path.write_text(header + body_file.read_text(encoding="utf-8"), encoding="utf-8")
    body_file.unlink()


def process_module(
    *,
    short_name: str,
    module_dir: str,
    fragment_index: int,
    copilot_root: Path,
    fragments_dir: Path,
    processed_modules_file: Path,
    model: str,
    prompt_template: str,
) -> bool:
    """Process one module.

    Returns True if the module ran to completion (even with zero edits),
    False if Copilot or report extraction failed and the module should be
    retried on a future run.
    """
    slug = short_name.replace(":", "-")
    work_dir = copilot_root / slug
    work_dir.mkdir(parents=True, exist_ok=True)

    copilot_output = work_dir / "copilot-output.jsonl"
    final_message = work_dir / "final-assistant-message.txt"
    review_report = work_dir / "review-report.json"
    diagnostics = work_dir / "diagnostics.txt"

    print(f"::group::Copilot review ({model}) for {module_dir}", flush=True)
    try:
        prompt = prompt_template.replace("__MODULE_DIR__", module_dir)
        pre_run_sha = current_head_sha()

        copilot_rc = run_copilot(prompt, model, copilot_output)

        # Diagnostics are best-effort and should be produced even if Copilot failed.
        write_diagnostics(copilot_output, diagnostics)

        if copilot_rc != 0:
            print(
                f"Copilot invocation for {short_name} failed with exit {copilot_rc};"
                " skipping (module will be retried next run)."
            )
            run_git("reset", "--hard", pre_run_sha)
            return False

        extract_rc = run_extract_report(
            copilot_output=copilot_output,
            final_message=final_message,
            review_report=review_report,
        )
        if extract_rc != 0:
            print(
                f"Report extraction failed for {short_name} (exit {extract_rc});"
                " discarding edits and skipping."
            )
            run_git("reset", "--hard", pre_run_sha)
            return False

        # Squash whatever Copilot committed for this module into one commit on our branch.
        run_git("reset", "--soft", pre_run_sha)
        run_git("add", "-A")

        # Record this module as processed regardless of whether it produced edits,
        # so no-op modules aren't re-walked on the next run.
        with processed_modules_file.open("a", encoding="utf-8") as f:
            f.write(short_name + "\n")

        fragment_path = fragments_dir / f"{fragment_index:03d}-{slug}.md"
        render_pr_body_fragment(
            fragment_path=fragment_path,
            short_name=short_name,
            module_dir=module_dir,
            review_report=review_report,
        )

        if staged_changes_present():
            run_git(
                "commit",
                "-m", f"Cleanup for {short_name}",
                "-m", f"Automated module cleanup of {module_dir}.",
            )
        else:
            print(f"No edits from {short_name}; not committing.")

        total_changed = count_modified_files_vs_main()
        print(f"Total modified files so far: {total_changed}")
        return True
    finally:
        print("::endgroup::", flush=True)


def main() -> None:
    modules_json = require_env("MODULES_JSON")
    file_threshold = int(require_env("FILE_THRESHOLD"))
    model = require_env("MODEL")
    copilot_root = Path(require_env("COPILOT_ROOT"))
    fragments_dir = Path(require_env("FRAGMENTS_DIR"))
    processed_modules_file = Path(require_env("PROCESSED_MODULES"))
    prompt_template = require_env("COPILOT_REVIEW_PROMPT_TEMPLATE")

    modules: list[dict[str, str]] = json.loads(modules_json)

    copilot_root.mkdir(parents=True, exist_ok=True)
    fragments_dir.mkdir(parents=True, exist_ok=True)
    processed_modules_file.write_text("", encoding="utf-8")

    print(f"Modules to walk (upper bound for this run): {len(modules)}")

    fragment_index = 0
    for module in modules:
        processed = process_module(
            short_name=module["short_name"],
            module_dir=module["module_dir"],
            fragment_index=fragment_index,
            copilot_root=copilot_root,
            fragments_dir=fragments_dir,
            processed_modules_file=processed_modules_file,
            model=model,
            prompt_template=prompt_template,
        )
        if processed:
            fragment_index += 1

        total_changed = count_modified_files_vs_main()
        if total_changed >= file_threshold:
            print(
                f"Reached file threshold ({total_changed} >= {file_threshold});"
                " stopping loop."
            )
            break

    processed_lines = processed_modules_file.read_text(encoding="utf-8").splitlines()
    processed_count = sum(1 for line in processed_lines if line.strip())
    commits_on_branch = count_commits_since_main()
    print(f"Processed modules: {processed_count}")
    print(f"Commits on cleanup branch: {commits_on_branch}")
    write_github_output("processed_count", str(processed_count))
    write_github_output("commits_on_branch", str(commits_on_branch))


if __name__ == "__main__":
    main()
