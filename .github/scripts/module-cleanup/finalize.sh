#!/bin/bash
# Finalize: single writer for both otelbot/module-cleanup-wip and the
# memory/module-cleanup branch. Runs after the agent job (regardless of
# whether the agent succeeded, no-oped, or failed).
#
# Steps:
#   1. Append <short> to memory/module-cleanup:processed.txt; if the
#      agent failed, also append to failed.txt. This guarantees a failing
#      module is recorded as "processed" (so it isn't retried in a loop)
#      AND logged as a failure for diagnostics.
#   2. If the agent produced a cleanup patch, apply it onto the fixed
#      otelbot/module-cleanup-wip branch and push.
#   3. If wip diff vs origin/main has reached FLUSH_THRESHOLD files OR
#      the queue is empty, atomically rename wip to a
#      otelbot/module-cleanup-batch-<run_id> branch and open the PR. The wip
#      branch ceases to exist on remote until the next run recreates
#      it from main.
#   4. Leave self-dispatch to the workflow step that follows this script,
#      so PR creation can use the app token while workflow dispatch uses
#      the normal GitHub Actions token.
#
# No rebase-retry loops on push: the workflow uses
# concurrency.group=module-cleanup with cancel-in-progress=false, so this
# job is the only writer of either branch and runs serialized across
# workflow runs.
#
# Required env:
#   GH_TOKEN          - token with contents:write and pull-requests:write
#   GITHUB_REPOSITORY - owner/repo
#   SHORT_NAME        - the module short_name processed this run
#   AGENT_RESULT      - github.needs.agent.result ('success'|'failure'|...)
#   ARTIFACT_DIR      - directory of the downloaded `agent` artifact
#                       (may or may not contain cleanup.patch)
#   QUEUE_REMAINING   - count of unprocessed modules left after this one
#
# Optional env:
#   FLUSH_THRESHOLD   - file count that triggers a PR (default 10)
#   MEMORY_BRANCH     - default: memory/module-cleanup
#   WIP_BRANCH        - default: otelbot/module-cleanup-wip

set -euo pipefail

MEMORY_BRANCH="${MEMORY_BRANCH:-memory/module-cleanup}"
WIP_BRANCH="${WIP_BRANCH:-otelbot/module-cleanup-wip}"
THRESHOLD="${FLUSH_THRESHOLD:-10}"
QUEUE_REMAINING="${QUEUE_REMAINING:-0}"
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY required}"
SHORT="${SHORT_NAME:?SHORT_NAME required}"
AGENT_RESULT="${AGENT_RESULT:-failure}"
ARTIFACT_DIR="${ARTIFACT_DIR:-./agent-artifact}"

# Full history is required for `origin/main..origin/$WIP_BRANCH` log/diff
# below. The finalize job's checkout uses `fetch-depth: 0`, so don't
# re-shallow any of these refs with `--depth`.
git fetch origin main
git fetch origin "$MEMORY_BRANCH" 2>/dev/null || true
git fetch origin "$WIP_BRANCH"    2>/dev/null || true

# ---- 1. Update processed.txt (and failed.txt on failure) ----

MEM_WT=/tmp/memory-wt
rm -rf "$MEM_WT"
if git rev-parse --verify "origin/$MEMORY_BRANCH" >/dev/null 2>&1; then
    git worktree add -B "$MEMORY_BRANCH" "$MEM_WT" "origin/$MEMORY_BRANCH"
else
    git worktree add --orphan -B "$MEMORY_BRANCH" "$MEM_WT"
    rm -rf "$MEM_WT"/*
fi

PROCESSED="$MEM_WT/processed.txt"
FAILED="$MEM_WT/failed.txt"

touch "$PROCESSED"
if ! grep -Fxq "$SHORT" "$PROCESSED"; then
    echo "$SHORT" >> "$PROCESSED"
fi

if [ "$AGENT_RESULT" != "success" ]; then
    ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    echo -e "$SHORT\t$ts\tagent_result=$AGENT_RESULT" >> "$FAILED"
fi

(
    cd "$MEM_WT"
    git add -A
    if ! git diff --cached --quiet; then
        git commit -m "Mark $SHORT processed (agent_result=$AGENT_RESULT)"
        git push origin "$MEMORY_BRANCH"
    fi
)

# ---- 2. Apply cleanup patch (if any) onto wip ----

PATCH_SRC=""
for candidate in \
    "$ARTIFACT_DIR/agent/cleanup.patch" \
    "$ARTIFACT_DIR/tmp/gh-aw/agent/cleanup.patch" \
    "$ARTIFACT_DIR/cleanup.patch"; do
    if [ -f "$candidate" ]; then
        # Absolute path so the value survives the cd into $WIP_WT below.
        PATCH_SRC="$(realpath "$candidate")"
        echo "Found cleanup patch at $PATCH_SRC"
        break
    fi
done
if [ -z "$PATCH_SRC" ]; then
    echo "No cleanup.patch (no-op or agent failed before commit)."
fi

WIP_WT=/tmp/wip-wt
rm -rf "$WIP_WT"
if git rev-parse --verify "origin/$WIP_BRANCH" >/dev/null 2>&1; then
    git worktree add -B "$WIP_BRANCH" "$WIP_WT" "origin/$WIP_BRANCH"
else
    git worktree add -B "$WIP_BRANCH" "$WIP_WT" origin/main
fi

if [ -n "$PATCH_SRC" ]; then
    (
        cd "$WIP_WT"
        if git am --3way "$PATCH_SRC"; then
            echo "Applied cleanup for $SHORT to $WIP_BRANCH"
            git push origin "$WIP_BRANCH"
        else
            git am --abort 2>/dev/null || true
            echo "FAILED to apply cleanup for $SHORT (rebase conflict)."
            ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
            (
                cd "$MEM_WT"
                echo -e "$SHORT\t$ts\tgit am failed (rebase conflict)" >> "$FAILED"
                git add -A
                git commit -m "Record $SHORT as patch-conflict failure"
                git push origin "$MEMORY_BRANCH" || true
            )
        fi
    )
fi

git fetch origin "$WIP_BRANCH" 2>/dev/null || true

# ---- 3. Decide flush ----

# Count files touched by wip's own commits only. Diffing against
# `origin/main` directly would also count files that have moved forward
# on main since the wip branch was created.
if git rev-parse --verify "origin/$WIP_BRANCH" >/dev/null 2>&1; then
    WIP_BASE=$(git merge-base origin/main "origin/$WIP_BRANCH")
    FILE_COUNT=$(git diff --name-only "$WIP_BASE" "origin/$WIP_BRANCH" | wc -l)
    AHEAD=$(git rev-list --count "origin/main..origin/$WIP_BRANCH")
else
    FILE_COUNT=0
    AHEAD=0
fi

echo "wip ahead of main: $AHEAD commit(s), $FILE_COUNT file(s)"
echo "queue remaining:   $QUEUE_REMAINING"
echo "threshold:         $THRESHOLD"

SHOULD_FLUSH=false
if [ "$AHEAD" -gt 0 ]; then
    if [ "$FILE_COUNT" -ge "$THRESHOLD" ]; then
        SHOULD_FLUSH=true
        echo "Flushing: file count >= threshold."
    elif [ "$QUEUE_REMAINING" -eq 0 ]; then
        SHOULD_FLUSH=true
        echo "Flushing: queue exhausted."
    fi
fi

OPENED_PR=false
if [ "$SHOULD_FLUSH" = "true" ]; then
    RUN_ID="${GITHUB_RUN_ID:-$(date -u +%Y%m%d%H%M%S)}"
    BATCH_BRANCH="otelbot/module-cleanup-batch-$RUN_ID"

    MODULE_COUNT=$(git -C "$WIP_WT" rev-list --count "origin/main..origin/$WIP_BRANCH")

    # processed.txt on the memory branch is the sole source of truth for
    # which modules dispatch skips, so no hidden marker block is needed here.
    BODY_FILE=$(mktemp)
    {
        echo "Automated module-cleanup batch."
        echo
        echo "## Modules in this batch"
        echo
        git -C "$WIP_WT" log "origin/main..origin/$WIP_BRANCH" \
            --reverse --format='- `%s`' \
            | sed 's|^- `Cleanup for |- `|'
        echo
        echo "---"
        echo
        git -C "$WIP_WT" log "origin/main..origin/$WIP_BRANCH" \
            --reverse --format='## %s%n%n%b%n'
    } > "$BODY_FILE"

    if [ "$MODULE_COUNT" -eq 1 ]; then
        TITLE_SUFFIX="1 module"
    else
        TITLE_SUFFIX="$MODULE_COUNT modules"
    fi

    # Atomic rename: in one push, create the batch branch at wip's tip
    # and delete the wip branch. Either both succeed or both fail, so we
    # never leave wip and batch pointing at the same commits.
    git push --atomic origin \
        "refs/remotes/origin/$WIP_BRANCH:refs/heads/$BATCH_BRANCH" \
        ":refs/heads/$WIP_BRANCH"

    gh pr create \
        --repo "$REPO" \
        --base main \
        --head "$BATCH_BRANCH" \
        --title "Module cleanup: batch of $TITLE_SUFFIX (run $RUN_ID)" \
        --body-file "$BODY_FILE" \
        --label "module cleanup"

    OPENED_PR=true
fi

git worktree remove --force "$MEM_WT" 2>/dev/null || true
git worktree remove --force "$WIP_WT" 2>/dev/null || true
