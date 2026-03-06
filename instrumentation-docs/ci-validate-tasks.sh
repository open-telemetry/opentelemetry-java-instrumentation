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

set +e  # Temporarily disable exit on error
./gradlew "${ALL_TASKS[@]}" --dry-run --quiet > /tmp/gradle-validate.log 2>&1
BULK_EXIT_CODE=$?
set -e  # Re-enable exit on error

if [[ $BULK_EXIT_CODE -ne 0 ]]; then
  echo ""
  echo "⚠️  Bulk validation failed. Analyzing error output..."
  echo ""

  # Gradle outputs errors like: "Cannot locate tasks that match ':instrumentation:...' as project '...' not found"
  # or "Task ':instrumentation:...' not found in root project"
  while IFS= read -r line; do
    if [[ "$line" =~ Cannot\ locate\ tasks\ that\ match\ \'([^\']+)\' ]]; then
      task_name="${BASH_REMATCH[1]}"
      INVALID_TASKS+=("$task_name")
      echo "✗ ${task_name} - TASK NOT FOUND"
    elif [[ "$line" =~ Task\ \'([^\']+)\'\ not\ found ]]; then
      task_name="${BASH_REMATCH[1]}"
      INVALID_TASKS+=("$task_name")
      echo "✗ ${task_name} - TASK NOT FOUND"
    fi
  done < /tmp/gradle-validate.log

  VALID_COUNT=$((TOTAL_TASKS - ${#INVALID_TASKS[@]}))

  # If we couldn't parse any errors from the log, fall back to showing the log
  if [[ ${#INVALID_TASKS[@]} -eq 0 ]]; then
    echo "Could not parse task errors from validation output."
    echo "Gradle error log:"
    cat /tmp/gradle-validate.log
    echo ""
    echo "This might indicate a different type of failure (not a missing task)."
    INVALID_TASKS+=("unknown - check log above")
  fi
else
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
