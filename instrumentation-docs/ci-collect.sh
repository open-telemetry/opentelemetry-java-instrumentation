#!/usr/bin/env bash

# Runs selected Gradle test tasks to regenerate *.telemetry output for
# individual OpenTelemetry Java agent instrumentations.

set -euo pipefail

source "$(dirname "$0")/instrumentations.sh"

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
    --rerun-tasks --continue
fi

echo "Telemetry file regeneration complete."
