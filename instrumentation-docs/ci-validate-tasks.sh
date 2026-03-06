#!/usr/bin/env bash

# Validates that all Gradle tasks defined in instrumentations.sh are valid

set -euo pipefail

source "$(dirname "$0")/instrumentations.sh"

ALL_TASKS=()
for task in "${INSTRUMENTATIONS[@]}"; do
  ALL_TASKS+=(":instrumentation:${task}")
done
for task in "${COLIMA_INSTRUMENTATIONS[@]}"; do
  ALL_TASKS+=(":instrumentation:${task}")
done
for task in "${TEST_LATEST_DEPS_INSTRUMENTATIONS[@]}"; do
  ALL_TASKS+=(":instrumentation:${task}")
done

echo "Validating ${#ALL_TASKS[@]} tasks..."
echo ""

if ./gradlew "${ALL_TASKS[@]}" --dry-run --quiet 2>&1; then
  echo "✓ All tasks are valid!"
  exit 0
else
  echo ""
  echo "✗ Validation failed!"
  echo ""
  echo "Please check the error output above and fix or remove invalid tasks from instrumentation-docs/instrumentations.sh"
  exit 1
fi
