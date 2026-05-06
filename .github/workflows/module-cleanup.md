---
description: |
  Walks instrumentation modules sequentially, applying safe repository-guideline
  fixes, and opens a single PR per run once the accumulated change set reaches
  FILE_THRESHOLD modified files. Reads the list of already-reviewed modules from
  persistent repo memory and appends to it as modules are processed.

on:
  schedule:
    - cron: "*/15 * * * *"
  workflow_dispatch:

permissions: read-all

concurrency:
  group: module-cleanup
  cancel-in-progress: false

timeout-minutes: 60

engine:
  id: copilot
  model: ${{ vars.MODULE_CLEANUP_MODEL || 'gpt-5' }}

network:
  allowed:
    - defaults
    - java

tools:
  edit:
  bash: [":*"]
  repo-memory: true

safe-outputs:
  # Mint an otelbot app token so the resulting PR is authored by the
  # otelbot GitHub App rather than by GITHUB_TOKEN. PRs created by
  # GITHUB_TOKEN do not trigger downstream workflow runs.
  github-app:
    app-id: ${{ vars.OTELBOT_APP_ID }}
    private-key: ${{ secrets.OTELBOT_PRIVATE_KEY }}
  create-pull-request:
    title-prefix: "Module cleanup: "
    labels: ["module cleanup"]
    draft: false
    max: 1
    if-no-changes: "ignore"
    # This workflow is explicitly designed to edit Gradle build files,
    # gradle.properties, and similar configuration files inside
    # instrumentation modules, all of which are in the default protected
    # set. Disable the protected-files defense.
    protected-files: allowed

imports:
  - .github/agents/module-cleanup.agent.md

jobs:
  dispatch:
    if: github.repository == 'open-telemetry/opentelemetry-java-instrumentation'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: read
    env:
      # Orphan branch managed by gh-aw's `repo-memory` tool (see
      # frontmatter `tools:` block). Holds `reviewed.txt`, the list of
      # modules already reviewed in prior runs. The agent job mounts
      # this branch automatically; the dispatch job is a separate plain
      # Actions job and must fetch + read it manually.
      MEMORY_BRANCH: memory/module-cleanup
    outputs:
      modules: ${{ steps.build-matrix.outputs.modules }}
      has_work: ${{ steps.build-matrix.outputs.has_work }}
    steps:
      - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2
        with:
          fetch-depth: 1
      - name: Fetch progress branch
        run: git fetch origin "$MEMORY_BRANCH" || true
      - name: Build cleanup matrix
        id: build-matrix
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          # Files inside the repo-memory branch live at <branch>/<file>.
          progress=$(git show "origin/$MEMORY_BRANCH:$MEMORY_BRANCH/reviewed.txt" 2>/dev/null || true)
          if [[ -n "$progress" ]]; then
            export REVIEW_PROGRESS="$progress"
          fi
          python .github/scripts/module-cleanup/build-cleanup-matrix.py

if: ${{ needs.dispatch.outputs.has_work == 'true' }}

steps:
  - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2
    with:
      persist-credentials: false
  - name: Set up JDK for running Gradle
    uses: actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654 # v5.2.0
    with:
      distribution: temurin
      java-version-file: .java-version
  - name: Setup Gradle
    uses: gradle/actions/setup-gradle@50e97c2cd7a37755bbfafc9c5b7cafaece252f6e # v6.1.0
    with:
      cache-read-only: true
  - name: Use CLA approved bot
    run: .github/scripts/use-cla-approved-bot.sh
---

# Module Cleanup

You walk a list of instrumentation modules sequentially, applying safe
repository-guideline fixes per module, and stop after the accumulated change
set reaches a file-count threshold so the result fits in one reasonably sized
pull request.

## Inputs

The dispatch job has computed which modules to walk through this run.
Read the JSON array of `{short_name, module_dir}` objects below — these are
the modules in walk order, already filtered to exclude modules that have been
reviewed in any prior run:

```
${{ needs.dispatch.outputs.modules }}
```

`FILE_THRESHOLD = 10`. Stop walking modules as soon as
`git diff --name-only origin/main | wc -l` is at least `10` after committing a
module. Do not exceed this by a wide margin — finish the current module, then
stop.

## Persistent state

Persistent run-to-run state lives in `/tmp/gh-aw/repo-memory/default/`.

- `reviewed.txt` (newline-separated `<short_name>` values, one per line):
  modules that have already been reviewed in any prior run. The dispatch job
  has already filtered the input list using this file, so the list above is
  authoritative for this run. **You must append the `<short_name>` of every
  module you process, including modules that produced no edits, before
  finishing the run.** Create the file if it does not yet exist.

The repo-memory tool will commit and push this file automatically after the
run completes; you do not need to run any git commands against the memory
directory.

## Per-module workflow

For each module in the input list, in order:

1. Record the current branch SHA: `pre=$(git rev-parse HEAD)`.
2. Invoke the `module-cleanup` persona (loaded via `imports:`) on
   `<module_dir>`. Run the full persona end-to-end and reach its commit
   step (a single commit with subject `Cleanup for <short_name>`).
3. If the persona reports it had to revert all of its changes (no substantive
   diff remained), reset back to `pre` (`git reset --hard "$pre"`) so the
   branch state is exactly as it was before this module ran, then continue to
   step 4.
4. Append `<short_name>\n` to `/tmp/gh-aw/repo-memory/default/reviewed.txt`.
   Do this whether or not the module produced edits — modules that produced
   no edits must still be marked as reviewed so they are not re-walked on the
   next run.
5. Compute `count=$(git diff --name-only origin/main | wc -l)`. If
   `count >= 10`, stop the loop. Otherwise continue to the next module.

If a tool error or unrecoverable failure prevents you from completing a
module's review, **do not** append that module to `reviewed.txt`. Reset to
`pre`, skip that module, and continue with the next one. The module will be
retried on a future run.

## Output

After the loop ends, your only remaining action is to emit a single
`create_pull_request` safe output with:

- **title**: `run ${{ github.run_id }}` (the `Module cleanup: ` prefix is
  prepended automatically by the safe-output framework).
- **body**: An ordered list of the modules processed in this run (using
  `<short_name>` and `<module_dir>`), followed by a per-module section
  summarizing the changes applied and any unresolved items. Use the same
  Markdown shape that the persona's report produces, with a top-level
  `## Module: <short_name>` heading per module and a single combined
  `_Module path: <module_dir>_` line below each heading.
- **branch**: do not specify; let the safe-output framework choose.
- **labels**: already configured in frontmatter.

The framework will collect every commit you have made in this run, push them
to a fresh branch, and open the PR. Do not run `git push` yourself and do not
attempt to call `gh pr create`.

If no commits were produced this run (every module reverted or the loop
terminated immediately), do not emit a `create_pull_request` output. The
`if-no-changes: ignore` configuration handles this case automatically.

## Constraints

- Use repository-relative paths in the PR body.
- Do not modify files in `.github/workflows/` or other infrastructure paths
  unless a per-module fix strictly requires it.
- Do not run `git push`, `git fetch`, or `gh pr create`. The framework
  handles all remote operations.
- Do not modify `/tmp/gh-aw/repo-memory/default/reviewed.txt` outside the
  per-module append described above.
- Honor every constraint in the imported `module-cleanup` persona, including
  its skip list, its auto-fix boundaries, its validation procedure, and its
  prohibition on inline review comments in source files.
