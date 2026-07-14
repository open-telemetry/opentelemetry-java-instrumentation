#!/bin/bash
# Final action invoked by the LLM agent: write the cleanup result into
# /tmp/gh-aw/agent so gh-aw's auto-uploader includes it in the `agent`
# workflow artifact. The finalize job then downloads that artifact and either
# applies cleanup.patch onto otelbot/module-cleanup-wip or records cleanup.noop
# as an explicit no-op.
#
# Idempotent and write-only to /tmp. Does NOT push anything.
#
# Args:
#   $1 - module short_name (used for logging only)

set -euo pipefail

SHORT="${1:?short_name argument required}"
OUT_DIR="${OUT_DIR:-/tmp/gh-aw/agent}"
mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR/cleanup.patch" "$OUT_DIR/cleanup.noop"

if ! git rev-parse --verify origin/main >/dev/null 2>&1; then
    git fetch origin main --depth=1
fi

# The agent should leave changes uncommitted. If it accidentally created local
# commits anyway, convert them back into working-tree changes before exporting.
if [ -n "$(git rev-list --max-count=1 origin/main..HEAD)" ]; then
    echo "Converting local cleanup commit(s) for $SHORT into a working-tree diff."
    git reset --mixed origin/main >/dev/null
else
    git reset --mixed >/dev/null
fi

# Capture untracked files in the diff without staging their contents.
git add --intent-to-add .

if git diff --quiet; then
    echo "$SHORT" > "$OUT_DIR/cleanup.noop"
    echo "No changes produced by agent for $SHORT; wrote $OUT_DIR/cleanup.noop."
    exit 0
fi

git diff --binary > "$OUT_DIR/cleanup.patch"
echo "Wrote cleanup patch for $SHORT to $OUT_DIR/cleanup.patch"
