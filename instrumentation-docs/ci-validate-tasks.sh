#!/usr/bin/env bash

# Validates that all Gradle tasks defined in instrumentations.sh are valid
# This is a dry-run validation to catch broken task definitions before merge

set -euo pipefail

source "$(dirname "$0")/instrumentations.sh"

echo "Validating instrumentation task definitions..."
echo ""

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

TOTAL_TASKS=${#ALL_TASKS[@]}
echo "Checking ${TOTAL_TASKS} tasks..."

INVALID_TASKS=()
VALID_COUNT=0

# Try to validate all tasks in a single Gradle invocation (fast path)
echo "Running bulk validation..."
set +e  # Temporarily disable exit on error
./gradlew "${ALL_TASKS[@]}" --dry-run --quiet > /tmp/gradle-validate.log 2>&1
BULK_EXIT_CODE=$?
set -e  # Re-enable exit on error

if [[ $BULK_EXIT_CODE -ne 0 ]]; then
  echo ""
  echo "⚠️  Bulk validation failed. Running individual validation to identify broken tasks..."
  echo ""

  validate_task() {
    local task_path="$1"

    local output
    output=$(./gradlew "$task_path" --dry-run --quiet 2>&1)
    local exit_code=$?

    if [[ $exit_code -eq 0 ]] && [[ ! "$output" =~ Task.*not\ found ]]; then
      VALID_COUNT=$((VALID_COUNT + 1))
      echo "✓ ${task_path}"
      return 0
    else
      INVALID_TASKS+=("$task_path")
      echo "✗ ${task_path} - TASK NOT FOUND"
      return 1
    fi
  }

  for task in "${ALL_TASKS[@]}"; do
    validate_task "$task"
  done
else
  # All tasks are valid (fast path succeeded)
  VALID_COUNT=$TOTAL_TASKS
  INVALID_TASKS=()
  echo "✓ All ${TOTAL_TASKS} tasks validated successfully"
fi

echo ""
echo "======================================"
echo "Validation Summary"
echo "======================================"
echo "Valid tasks: ${VALID_COUNT}"
echo "Invalid tasks: ${#INVALID_TASKS[@]}"

if [ ${#INVALID_TASKS[@]} -gt 0 ]; then
  echo ""
  echo "The following tasks are invalid:"
  for task in "${INVALID_TASKS[@]}"; do
    echo "  - $task"
  done
  echo ""
  echo "Please fix or remove these tasks from instrumentation-docs/instrumentations.sh"
  exit 1
fi

echo ""
echo "All tasks are valid!"
