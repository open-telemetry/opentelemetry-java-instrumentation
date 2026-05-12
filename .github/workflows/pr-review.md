---
description: |
  Runs an automated Copilot code review on a pull request and posts the
  findings as a single GitHub review (event: COMMENT — non-approving,
  non-blocking).

  Trigger: `issue_comment` matching `/review` or `/review <model>`.
  Allowed only when the commenter has write access to the repo. Drafts are
  allowed.

  The dispatch job verifies eligibility (not closed/merged; commenter has
  write permission for `/review`), reacts to the triggering comment with
  `eyes`, and emits trigger-agnostic outputs. The agent job builds a
  deterministic review bundle (PR diff + post-change file contents +
  knowledge articles, all fetched via `gh api`, never by checking out the
  PR head) and hands it to Copilot. The finalize job validates the agent's
  findings JSON against the diff hunks and posts the review.

  Safety: this workflow never checks out PR head code. PR-modified file
  contents are read via the GitHub contents API at the recorded head OID and
  bundled as plain data.

on:
  issue_comment:
    types: [created]

permissions: read-all

concurrency:
  group: pr-review-${{ github.event.pull_request.number || github.event.issue.number }}
  cancel-in-progress: true

timeout-minutes: 30

environment: protected

strict: false

engine:
  id: copilot
  model: ${{ needs.dispatch.outputs.model }}

sandbox:
  agent: false

network:
  allowed:
    - defaults

tools:
  bash:
    - "cat:*"
    - "ls:*"
    - "test:*"
    - "wc:*"
    - "head:*"
    - "tail:*"
    - "find:*"
    - "rg:*"
    - "grep:*"

imports:
  - .github/agents/pr-review.agent.md

jobs:
  dispatch:
    if: >-
      github.event_name != 'issue_comment' ||
      (github.event.issue.pull_request != null &&
       startsWith(github.event.comment.body, '/review'))
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write  # for reactions on the triggering PR comment
      issues: read
    outputs:
      should_run: ${{ steps.gate.outputs.should_run }}
      pr_number: ${{ steps.gate.outputs.pr_number }}
      model: ${{ steps.gate.outputs.model }}
      model_warning: ${{ steps.gate.outputs.model_warning }}
      triggered_by: ${{ steps.gate.outputs.triggered_by }}
    steps:
      - uses: actions/checkout@v5
        with:
          fetch-depth: 1
          persist-credentials: false

      - name: React eyes on triggering comment
        if: github.event_name == 'issue_comment'
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          COMMENT_ID: ${{ github.event.comment.id }}
        run: |
          gh api -X POST \
            "repos/${GITHUB_REPOSITORY}/issues/comments/${COMMENT_ID}/reactions" \
            -f content=eyes >/dev/null || true

      - name: Resolve trigger and gate
        id: gate
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          DEFAULT_MODEL: ${{ vars.PR_REVIEW_MODEL || 'gpt-5' }}
          ALLOWED_MODELS: ${{ vars.PR_REVIEW_ALLOWED_MODELS || 'gpt-5,gpt-5.5,claude-sonnet-4.5' }}
          EVENT_NAME: ${{ github.event_name }}
          PR_FROM_COMMENT: ${{ github.event.issue.number }}
          COMMENT_BODY: ${{ github.event.comment.body }}
          COMMENT_AUTHOR: ${{ github.event.comment.user.login }}
        run: python .github/scripts/pr-review/gate.py

  finalize:
    needs:
      - dispatch
      - agent
    if: always() && needs.dispatch.outputs.should_run == 'true'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    steps:
      - uses: actions/checkout@v5
        with:
          fetch-depth: 1
          persist-credentials: false

      - name: Download agent artifact
        uses: actions/download-artifact@v5
        with:
          name: agent
          path: ./agent-artifact
        continue-on-error: true

      - name: Download review bundle artifact
        uses: actions/download-artifact@v5
        with:
          name: review-bundle
          path: ./review-bundle

      - name: Post review
        if: needs.agent.result == 'success'
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          set -euo pipefail
          python .github/scripts/pr-review/post.py \
            "${{ needs.dispatch.outputs.pr_number }}" \
            --bundle-dir ./review-bundle \
            --findings ./agent-artifact/findings.json \
            --event COMMENT \
            --triggered-by "${{ needs.dispatch.outputs.triggered_by }}" \
            --model "${{ needs.dispatch.outputs.model }}" \
            --model-warning "${{ needs.dispatch.outputs.model_warning }}"

      - name: Comment on PR when agent failed
        if: needs.agent.result != 'success'
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          gh pr comment "${{ needs.dispatch.outputs.pr_number }}" \
            --repo "$GITHUB_REPOSITORY" \
            --body "Automated review did not complete (agent_result=${{ needs.agent.result }}). See workflow run for details."

  # ---- agent job ----
  # The implicit gh-aw agent job is configured by the top-level frontmatter
  # above. It runs the steps below before invoking Copilot with the markdown
  # body of this file as the prompt. Per-step env vars set in the agent's
  # GITHUB_ENV are visible to the LLM's bash tool.

if: ${{ needs.dispatch.outputs.should_run == 'true' }}

steps:

  - name: Build review bundle
    env:
      GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    run: |
      mkdir -p /tmp/gh-aw/agent
      python .github/scripts/pr-review/prepare.py \
        "${{ needs.dispatch.outputs.pr_number }}" \
        --output-dir /tmp/gh-aw/bundle \
        --findings-path /tmp/gh-aw/agent/findings.json

  - name: Upload review bundle for finalize job
    uses: actions/upload-artifact@v5
    with:
      name: review-bundle
      path: /tmp/gh-aw/bundle
      retention-days: 3

  - name: Export bundle paths to env
    run: |
      echo "REVIEW_BUNDLE_DIR=/tmp/gh-aw/bundle" >> "$GITHUB_ENV"
      echo "REVIEW_FINDINGS_PATH=/tmp/gh-aw/agent/findings.json" >> "$GITHUB_ENV"
---

# PR Review

Read the persona section above for the role, the bundle layout, the JSON
output contract, and the hard rules. Those apply unchanged to this run.

## Per-run paths

The bundle for this run is at **`/tmp/gh-aw/bundle`** (also in
`$REVIEW_BUNDLE_DIR`). It contains everything described in the persona:
`pr.diff`, `metadata.json`, `diff-scope.json`, `files/`, and `knowledge/`.

A pre-rendered prompt with the exact bundled-file list and deleted-file list
for this PR is at **`/tmp/gh-aw/bundle/prompt.md`**. Read it once before you
start; it lists every PR-modified file you can read from the bundle and every
file the PR deletes (which you must not read).

## Final mandatory action

Write your findings JSON to **`/tmp/gh-aw/agent/findings.json`** (also in
`$REVIEW_FINDINGS_PATH`). The finalize job downloads the `agent` artifact
that gh-aw auto-uploads from `/tmp/gh-aw/agent/` and posts your findings as
a GitHub review.

If you produce no comments (clean review), still write the JSON file with an
empty `comments` array and a one-line `body` summary. The finalize job
treats a missing file as an agent failure.
