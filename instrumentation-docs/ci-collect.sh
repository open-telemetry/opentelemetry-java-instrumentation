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
./gradlew "${ALL_TASKS[@]}" \
  -PcollectMetadata=true \
  "${GRADLE_FLAGS[@]}" \
  --rerun-tasks --continue

# Collect and run tasks that need testLatestDeps
LATEST_DEPS_TASKS=()
for task in "${TEST_LATEST_DEPS_INSTRUMENTATIONS[@]}"; do
  LATEST_DEPS_TASKS+=(":instrumentation:${task}")
done

if [[ ${#LATEST_DEPS_TASKS[@]} -gt 0 ]]; then
  echo "Processing instrumentations with -PtestLatestDeps=true..."
  ./gradlew "${LATEST_DEPS_TASKS[@]}" \
    -PcollectMetadata=true \
    -PtestLatestDeps=true \
    "${GRADLE_FLAGS[@]}" \
    --rerun-tasks --continue
fi

echo "Telemetry file regeneration complete."
