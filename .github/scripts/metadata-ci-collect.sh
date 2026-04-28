#!/usr/bin/env bash

# Runs selected Gradle test tasks to regenerate *.telemetry output for
# individual OpenTelemetry Java agent instrumentations.

set -euo pipefail

source "$(dirname "$0")/instrumentations.sh"

# Configure Gradle flags based on DEBUG_LOGGING environment variable
GRADLE_FLAGS=()
if [[ "${DEBUG_LOGGING:-false}" != "true" ]]; then
  GRADLE_FLAGS+=(--no-daemon --quiet)
  echo "Running with reduced logging (set DEBUG_LOGGING=true to enable full output)"
else
  echo "Running with full debug logging enabled"
fi

# Collect standard and colima tasks (without testLatestDeps)
ALL_TASKS=()
for task in "${INSTRUMENTATIONS[@]}"; do
  ALL_TASKS+=(":instrumentation:${task}")
done
for task in "${COLIMA_INSTRUMENTATIONS[@]}"; do
  ALL_TASKS+=(":instrumentation:${task}")
done

echo "Processing standard instrumentations..."
STANDARD_EXIT_CODE=0
./gradlew "${ALL_TASKS[@]}" \
  -PcollectMetadata=true \
  "${GRADLE_FLAGS[@]}" \
  --rerun-tasks --continue || STANDARD_EXIT_CODE=$?

# Collect and run tasks that need testLatestDeps
LATEST_DEPS_TASKS=()
for task in "${TEST_LATEST_DEPS_INSTRUMENTATIONS[@]}"; do
  LATEST_DEPS_TASKS+=(":instrumentation:${task}")
done

LATEST_DEPS_EXIT_CODE=0
if [[ ${#LATEST_DEPS_TASKS[@]} -gt 0 ]]; then
  echo "Processing instrumentations with -PtestLatestDeps=true..."
  ./gradlew "${LATEST_DEPS_TASKS[@]}" \
    -PcollectMetadata=true \
    -PtestLatestDeps=true \
    "${GRADLE_FLAGS[@]}" \
    --rerun-tasks --continue || LATEST_DEPS_EXIT_CODE=$?
fi

echo ""
echo "=== Diagnostic Summary ==="
FAILED_MODULES=()
while IFS= read -r -d '' xml_file; do
  if grep -qlE '<failure|<error' "$xml_file" 2>/dev/null; then
    module=$(echo "$xml_file" | sed 's|^\./||;s|/build/test-results/.*||')
    FAILED_MODULES+=("$module")
  fi
done < <(find . -name "TEST-*.xml" -print0 2>/dev/null)

if [[ ${#FAILED_MODULES[@]} -gt 0 ]]; then
  echo "Modules with test failures:"
  printf '%s\n' "${FAILED_MODULES[@]}" | sort -u | while IFS= read -r mod; do
    echo "  - $mod"
  done
  UNIQUE_COUNT=$(printf '%s\n' "${FAILED_MODULES[@]}" | sort -u | wc -l | tr -d ' ')
  echo "Total modules with failures: ${UNIQUE_COUNT}"
else
  echo "No test failures detected."
fi

echo ""
echo "Telemetry file regeneration complete."

if [[ $STANDARD_EXIT_CODE -ne 0 ]] || [[ $LATEST_DEPS_EXIT_CODE -ne 0 ]]; then
  exit 1
fi
