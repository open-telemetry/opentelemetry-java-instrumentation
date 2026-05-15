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

if [ -z "$(git log origin/main..HEAD --oneline)" ]; then
    echo "$SHORT" > "$OUT_DIR/cleanup.noop"
    echo "No commit produced by agent for $SHORT; wrote $OUT_DIR/cleanup.noop."
    exit 0
fi

# Capture every commit the persona made on top of main. The persona is
# expected to produce exactly one commit per its Phase 5 contract, but
# format-patch range-form is robust if it makes more than one.
git format-patch origin/main..HEAD --stdout > "$OUT_DIR/cleanup.patch"
echo "Wrote cleanup patch for $SHORT to $OUT_DIR/cleanup.patch"
