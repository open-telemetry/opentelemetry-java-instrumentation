---
name: fix-ci
description: "Use when: fixing failing GitHub Actions CI, failed checks, test failures, Spotless, FOSSA, Gradle, or build failures on an opentelemetry-java-instrumentation PR branch. Downloads CI logs deterministically before debugging."
argument-hint: "PR number, for example: 12345"
---

# Fix CI

Use this skill to fix failing CI on an `opentelemetry-java-instrumentation` pull request branch.

The first source of truth is always the deterministic CI bundle produced by [.github/scripts/pr-triage/fix_ci.py](../../scripts/pr-triage/fix_ci.py). Do not start by manually browsing GitHub Actions logs or guessing from check names. If the bundle is incomplete, improve or rerun the script before relying on ad hoc log collection.

## Required Input

- Pull request number.

## Workflow

1. Confirm the worktree is clean. If it is dirty, stop and ask the user how to handle the existing changes.
2. Download the failing CI logs with the deterministic script:

   ```bash
   python .github/scripts/pr-triage/fix_ci.py <pr-number> --download-only
   ```

   The script checks out the PR, discovers failed non-aggregate checks, downloads one representative log for each failed job family into `build/pr-triage/otel-ci-fix-<pr-number>/logs/`, writes `summary.json`, and restores the original branch.
3. Read `build/pr-triage/otel-ci-fix-<pr-number>/summary.json` and the referenced log files. Treat them as the source of truth for what failed.
4. Check out the PR branch again if the script restored you to another branch:

   ```bash
   gh pr checkout <pr-number>
   ```

5. Diagnose the root cause from the downloaded logs and make the smallest appropriate fix on the PR branch.
6. For deterministic generated-file or formatting failures, run the repository task instead of editing generated output by hand. Common examples:

   ```bash
   ./gradlew spotlessApply
   ./gradlew generateFossaConfiguration
   ```

7. Validate with the narrowest command that covers the failure. For Gradle, follow repository rules: do not use `--rerun-tasks`, and do not pipe Gradle output through `tail`, `head`, or `grep`.
8. Show the changed files, validation run, and the remaining status to the user. Commit or push only when the user has asked for that.

## Boundaries

- Do not switch away from the PR branch after edits unless cleanup requires restoring the user's original branch.
- Do not invent a code change for flaky or infrastructure-only failures. Leave the tree clean and explain the evidence from the logs.
- Do not broaden the fix to unrelated checks or unrelated files.
- If CI failed because logs are unavailable, authentication is missing, or GitHub returned incomplete check metadata, report that blocker and the exact command that failed.

## Optional Full Automation

The same script also supports an all-in-one automation mode, which invokes Copilot CLI, commits, and pushes unless `--no-push` is supplied:

```bash
python .github/scripts/pr-triage/fix_ci.py <pr-number>
```

Prefer the `--download-only` workflow for this skill so the current VS Code agent reads the deterministic, retained bundle directly and keeps commit or push decisions explicit.