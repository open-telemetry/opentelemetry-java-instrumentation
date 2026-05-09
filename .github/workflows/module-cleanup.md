---
description: |
  Walks instrumentation modules one-at-a-time, processing exactly one
  module per run. Each successful run's commit is appended to the fixed
  `otelbot/module-cleanup-wip` branch. When that branch reaches FILE_THRESHOLD
  modified files (or when the unprocessed-module queue empties), the
  finalize job atomically renames wip to `module-cleanup-batch-<run_id>`
  and opens a PR against main. The next run, finding no wip on remote,
  starts a fresh wip from main.

  After each successful run, the workflow self-dispatches so chains keep
  moving without waiting for cron. The chain stops on its own once
  MAX_OPEN_PRS is reached (matrix returns has_work=false; finalize
  doesn't run; no self-dispatch). Cron (every 1h) restarts work after
  a PR merges and the open-PR count drops below MAX_OPEN_PRS.

  Because state lives on a fixed branch (not in any one run's identity),
  GitHub Actions concurrency cancellations of queued runs are harmless:
  the wip branch survives, and the next run picks up where it was.

  State:
    - `memory/module-cleanup` branch holds `processed.txt` (modules already
      attempted; never re-picked automatically) and `failed.txt` (a
      diagnostic log of timeouts and patch-conflict failures).
    - `otelbot/module-cleanup-wip` branch holds not-yet-PR'd commits. Exists
      only while there is uncommitted work; deleted when promoted to a batch.
    - Open PRs labeled `module cleanup` count toward MAX_OPEN_PRS; while at
      cap, dispatch exits and waits for cron to retry.

on:
  workflow_dispatch:
  schedule:
    - cron: "every 1h"

permissions:
  contents: read

concurrency:
  group: module-cleanup
  cancel-in-progress: false

timeout-minutes: 30

environment: protected

# Disable strict mode so we can opt out of the AWF agent sandbox below.
strict: false

engine:
  id: copilot
  model: ${{ vars.MODULE_CLEANUP_MODEL || 'gpt-5' }}

# Disable the AWF sandbox so copilot-cli connects directly to
# api.githubcopilot.com. In sandboxed Copilot workflows, gh-aw enables
# Copilot BYOK/offline mode but does not set responses wire-API routing
# for GPT-5-family models yet. See https://github.com/github/gh-aw/issues/31241.
sandbox:
  agent: false

network:
  allowed:
    - defaults
    - java

tools:
  edit:
  bash: [":*"]

# The finalize job owns PR creation directly via `gh`, and memory-branch
# state is managed by plain git pushes from the finalize script. This
# keeps all post-LLM logic in shell where it can run reliably regardless
# of how the agent session ends.
#
# The `safe-outputs.jobs.suppress_default_create_issue` placeholder below
# exists solely to opt out of gh-aw's default behavior, which auto-injects
# a `create-issue` safe output whenever no non-builtin safe output is
# configured (see https://github.github.io/gh-aw/reference/safe-outputs/
# under "System Types"). Without this opt-out, every successful run posts
# the agent's narration as a separate `[module-cleanup]` issue, which is
# noise on top of the batch PR the finalize job already opens.
#
# The placeholder safe-job is intentionally never invoked by the agent
# (see "What you must NOT do" in the persona). gh-aw only emits the job
# when the corresponding MCP tool is called, so leaving it uninvoked
# costs nothing at runtime.

safe-outputs:
  # Threat detection requires the AWF agent sandbox, which we disable
  # because of https://github.com/github/gh-aw/issues/31241. The placeholder
  # safe-job below carries no untrusted output, so threat detection is unnecessary.
  threat-detection: false
  jobs:
    suppress_default_create_issue:
      runs-on: ubuntu-latest
      steps:
        - run: 'true'

imports:
  - .github/agents/module-cleanup.agent.md

jobs:
  dispatch:
    if: github.repository == 'open-telemetry/opentelemetry-java-instrumentation'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: read
    outputs:
      has_work: ${{ steps.pick.outputs.has_work }}
      short_name: ${{ steps.pick.outputs.short_name }}
      module_dir: ${{ steps.pick.outputs.module_dir }}
      queue_remaining: ${{ steps.pick.outputs.queue_remaining }}
    steps:
      - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2
        with:
          fetch-depth: 1
          persist-credentials: false
      - name: Pick next module
        id: pick
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          MEMORY_BRANCH: memory/module-cleanup
        run: |
          set -euo pipefail
          # processed.txt lives at the root of the memory branch.
          processed=""
          if git fetch origin "$MEMORY_BRANCH" --depth=1 2>/dev/null; then
            processed=$(git show "origin/$MEMORY_BRANCH:processed.txt" 2>/dev/null || true)
          fi
          export REVIEW_PROGRESS="$(printf '%s\n' "$processed" | grep -v '^$' | sort -u)"
          python .github/scripts/module-cleanup/build-cleanup-matrix.py

  finalize:
    needs:
      - dispatch
      - agent
    if: always() && needs.dispatch.outputs.has_work == 'true'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      actions: write # to trigger next iteration
    steps:
      - uses: actions/create-github-app-token@1b10c78c7865c340bc4f6099eb2f838309f1e8c3 # v3.1.1
        id: otelbot-token
        with:
          app-id: ${{ vars.OTELBOT_JAVA_INSTRUMENTATION_APP_ID }}
          private-key: ${{ secrets.OTELBOT_JAVA_INSTRUMENTATION_PRIVATE_KEY }}
      - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2
        with:
          # Full history is required: finalize computes
          # `origin/main..origin/otelbot/module-cleanup-wip` to build the PR
          # body and decide whether to flush. With a shallow `origin/main`,
          # main's own ancestors leak into that range and corrupt both outputs.
          fetch-depth: 0
          persist-credentials: true
          token: ${{ steps.otelbot-token.outputs.token }}
      - name: Configure git author
        run: .github/scripts/use-cla-approved-bot.sh
      - name: Download agent artifact
        uses: actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c # v8.0.1
        with:
          name: agent
          path: ./agent-artifact
        continue-on-error: true
      - name: Finalize
        env:
          # Use the app token for pushes and PR creation so the PR runs workflows.
          GH_TOKEN: ${{ steps.otelbot-token.outputs.token }}
          SHORT_NAME: ${{ needs.dispatch.outputs.short_name }}
          AGENT_RESULT: ${{ needs.agent.result }}
          QUEUE_REMAINING: ${{ needs.dispatch.outputs.queue_remaining }}
          ARTIFACT_DIR: ./agent-artifact
        run: bash .github/scripts/module-cleanup/finalize.sh
      - name: Trigger next workflow
        if: needs.dispatch.outputs.queue_remaining != '0'
        env:
          # Use the normal GitHub Actions token for workflow_dispatch.
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          WORKFLOW_FILE: module-cleanup.lock.yml
        run: gh workflow run "$WORKFLOW_FILE" --repo "$GITHUB_REPOSITORY"

if: ${{ needs.dispatch.outputs.has_work == 'true' }}

steps:
  - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2
    with:
      persist-credentials: false
  - name: Export module identifiers to env
    # Top-level frontmatter `env:` would land at workflow scope where
    # `needs.*` is not available. Export via GITHUB_ENV instead so the
    # LLM step (and its bash tool) sees MODULE_SHORT_NAME / MODULE_DIR.
    run: |
      echo "MODULE_SHORT_NAME=${{ needs.dispatch.outputs.short_name }}" >> "$GITHUB_ENV"
      echo "MODULE_DIR=${{ needs.dispatch.outputs.module_dir }}" >> "$GITHUB_ENV"
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

You clean up exactly **one** instrumentation module this run, then export
your commit so the finalize job can roll it into a batched PR.

## Inputs

This run targets a single module. Read its identifiers from the workflow
environment, **not** from a JSON list:

- `<short_name>` is in the `MODULE_SHORT_NAME` environment variable.
- `<module_dir>` is in the `MODULE_DIR` environment variable.

Use these directly via `$MODULE_SHORT_NAME` / `$MODULE_DIR` in any shell
command. Do **not** invent module names or guess directories.

## Per-run workflow

Run **inline in this session**. Do **not** spawn background agents,
sub-sessions, or use `Module-cleanup` as a callable tool. The persona
instructions imported into this prompt are yours; execute them yourself.

1. Confirm the module directory exists:
   `test -d "$MODULE_DIR" || { echo "Module directory missing: $MODULE_DIR"; exit 1; }`
2. Apply the imported `module-cleanup` persona's full checklist to
   `$MODULE_DIR`. Reach the persona's commit step. The commit subject must
   match the persona's format: `Cleanup for $MODULE_SHORT_NAME`. If the
   persona reports it had to revert all of its changes (no substantive
   diff remained), proceed to step 3 anyway — "no commit" is a valid
   outcome and finalize handles it.
3. **Final mandatory action** (do not skip even on no-op):

   ```
   bash .github/scripts/module-cleanup/export-cleanup-patch.sh "$MODULE_SHORT_NAME"
   ```

   This writes `/tmp/gh-aw/agent/cleanup.patch` (a `git format-patch` of
   your commit range) so gh-aw's auto-uploader includes it in the
   workflow's `agent` artifact. The finalize job downloads that artifact
   and applies the patch to the `otelbot/module-cleanup-wip` branch. The script
   is idempotent and exits cleanly with no patch if you produced no
   commit. **Run it exactly once as your last action.** If you do not run
   it, your work is lost.

## What you must NOT do

- Do not run `git push`. The finalize job handles all remote writes.
- Do not call `gh pr create`. The finalize job opens the PR.
- Do not modify `processed.txt` or `failed.txt`. The finalize job owns
  the memory branch.
- Do not spawn background agents, child sessions, or sub-tasks. The
  persona is loaded into this session; execute it inline.
- Do not modify files outside `$MODULE_DIR` unless the persona's
  out-of-module-edit allowance applies to your specific change.
- Do not invoke the `suppress_default_create_issue` MCP tool. It is a
  placeholder that exists only to disable gh-aw's default
  create-issue auto-injection; calling it would launch a needless
  no-op job. Use `noop` (already enabled) if you need to record a
  completion message.
