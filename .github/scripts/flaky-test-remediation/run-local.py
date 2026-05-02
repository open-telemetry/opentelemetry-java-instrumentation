#!/usr/bin/env python3
"""Local driver for the flaky-test-remediation pipeline.

Mirrors ``.github/workflows/flaky-test-remediation.yml`` but runs on the developer's
machine. Pushes the fix branch to your fork (``origin``), opens the PR
against ``upstream/main``, and reads/writes the progress branch on
``upstream`` so all attempts are tracked in one canonical place.
"""

import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from _paths import OUT_DIR, SELECTED, SKIP

SCRIPT_DIR = Path(__file__).resolve().parent
PROGRESS_BRANCH = "otelbot/flaky-test-remediation-progress"
FORK_REMOTE = "origin"
UPSTREAM_REMOTE = "upstream"
BASE_BRANCH = "main"
MODEL = "gpt-5.5"

# Toolkit files needed after `git checkout -B branch upstream/main` (which
# replaces the working tree with main's content; the toolkit may predate
# that base, so we stash a copy from start_branch).
TOOLKIT_FILES = ("2-fix-flaky-test.py", "3-open-pr.py",
                 "_paths.py", "_render.py")


def git(*args, cwd=None):
    return subprocess.run(
        ["git", *args], cwd=cwd, check=True,
        stdout=subprocess.PIPE,
    ).stdout.decode("utf-8", errors="replace").strip()


def stash_toolkit(start_branch, dest):
    for fname in TOOLKIT_FILES:
        blob = subprocess.check_output(
            ["git", "show",
             f"{start_branch}:.github/scripts/flaky-test-remediation/{fname}"])
        (Path(dest) / fname).write_bytes(blob)


def remove_progress_checkout(progress_dir):
    if not progress_dir.exists():
        return
    subprocess.run(
        ["git", "worktree", "remove", "--force", str(progress_dir)],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if progress_dir.exists():
        shutil.rmtree(progress_dir)


def main():
    repo_root = Path(git("rev-parse", "--show-toplevel"))
    os.chdir(repo_root)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    progress_dir = OUT_DIR / "progress-checkout"

    os.environ.setdefault("DEVELOCITY_URL", "https://develocity.opentelemetry.io")

    if git("status", "--porcelain"):
        print("error: working tree is dirty; commit or stash first.",
              file=sys.stderr)
        return 1

    start_branch = git("rev-parse", "--abbrev-ref", "HEAD")
    print(f"Starting from {start_branch} @ {git('rev-parse', 'HEAD')[:10]}")

    # ---- step 0: hydrate skip list from upstream progress branch -----------
    print(f"==> Fetching progress branch {UPSTREAM_REMOTE}/{PROGRESS_BRANCH}")
    git("fetch", UPSTREAM_REMOTE, PROGRESS_BRANCH, "--quiet")
    skip_text = git("show", f"{UPSTREAM_REMOTE}/{PROGRESS_BRANCH}:attempted.txt")
    SKIP.write_text(skip_text + "\n", encoding="utf-8")
    n_skip = sum(1 for line in skip_text.splitlines() if line.strip())
    print(f"Loaded {n_skip} previously-attempted entries.")

    # ---- step 1: analyze ---------------------------------------------------
    print("==> Querying Develocity for top flaky test")
    subprocess.run(
        ["python", "-u", str(SCRIPT_DIR / "1-select-flaky-test.py")],
        check=True)

    if not SELECTED.exists():
        print("No candidate selected; nothing to fix.")
        return 0

    selection = json.loads(SELECTED.read_text(encoding="utf-8"))
    fq = selection["fully_qualified"]
    test_class = selection["class"]
    print(f"Selected: {fq}")
    print(f"Source:   {selection['source_file']}")

    with tempfile.TemporaryDirectory(prefix="flaky-test-remediation-toolkit-") as toolkit:
        stash_toolkit(start_branch, toolkit)

        # ---- step 2: branch + render prompt + Copilot fix ------------------
        slug = re.sub(r"[^A-Za-z0-9]+", "-", fq).strip("-")[:60]
        ts = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d%H%M%S")
        branch = f"otelbot/flaky-test-remediation-{slug}-{ts}"

        print(f"==> Checking out {branch} from {UPSTREAM_REMOTE}/{BASE_BRANCH}")
        git("fetch", UPSTREAM_REMOTE, BASE_BRANCH, "--quiet")
        git("checkout", "-B", branch, f"{UPSTREAM_REMOTE}/{BASE_BRANCH}")

        print(f"==> Rendering prompt + running Copilot CLI (model={MODEL})")
        os.environ["MODEL"] = MODEL
        subprocess.run(
            ["python", str(Path(toolkit) / "2-fix-flaky-test.py")],
            check=False)

        # ---- step 3: commit + push + PR ------------------------------------
        print("==> Committing changes")
        git("add", "-A")
        git("commit",
            "-m", f"Reduce flakiness in {fq}",
            "-m", "Automated fix attempt based on Develocity flaky-test analysis.")

        print(f"==> Pushing {branch} to {FORK_REMOTE}")
        git("push", "-u", FORK_REMOTE, branch)

        print(f"==> Rendering PR body + opening PR against "
              f"{UPSTREAM_REMOTE}/{BASE_BRANCH} (draft)")
        fork_url = git("remote", "get-url", FORK_REMOTE)
        upstream_url = git("remote", "get-url", UPSTREAM_REMOTE)
        head_owner = re.match(r"^.*github\.com[:/]([^/]+)/", fork_url).group(1)
        upstream_repo = re.match(
            r"^.*github\.com[:/]([^/]+/[^/.]+)(\.git)?$", upstream_url).group(1)
        os.environ["PR_HEAD"] = f"{head_owner}:{branch}"
        os.environ["PR_BASE"] = BASE_BRANCH
        os.environ["PR_REPO"] = upstream_repo
        os.environ["PR_DRAFT"] = "1"  # local runs always open as draft
        subprocess.run(
            ["python", str(Path(toolkit) / "3-open-pr.py")], check=True)

    # ---- step 4: record attempt on upstream progress branch ----------------
    print(f"==> Recording {test_class} on {UPSTREAM_REMOTE}/{PROGRESS_BRANCH}")
    git("fetch", UPSTREAM_REMOTE, PROGRESS_BRANCH, "--quiet")
    remove_progress_checkout(progress_dir)
    # `-B` force-resets the local branch to match upstream, avoiding
    # non-fast-forward errors from a stale local copy.
    git("worktree", "add", "--quiet", "-B", PROGRESS_BRANCH,
        str(progress_dir), f"{UPSTREAM_REMOTE}/{PROGRESS_BRANCH}")
    try:
        attempted = {
            line.strip()
            for line in (progress_dir / "attempted.txt").read_text(
                encoding="utf-8").splitlines()
            if line.strip()
        }
        if test_class in attempted:
            print(f"{test_class} already recorded; skipping progress commit.")
        else:
            with (progress_dir / "attempted.txt").open(
                    "a", encoding="utf-8", newline="\n") as f:
                f.write(f"{test_class}\n")
            git("add", "attempted.txt", cwd=progress_dir)
            git("commit", "-q", "-m",
                f"Mark {test_class} as attempted", cwd=progress_dir)
            git("push", "-q", UPSTREAM_REMOTE,
                f"HEAD:{PROGRESS_BRANCH}", cwd=progress_dir)
    finally:
        git("worktree", "remove", "--force", str(progress_dir))

    print(f"==> Done. Returning to {start_branch}")
    git("checkout", "--quiet", start_branch)
    return 0


if __name__ == "__main__":
    sys.exit(main())
