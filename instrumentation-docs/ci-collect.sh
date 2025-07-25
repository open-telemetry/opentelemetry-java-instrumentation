#!/usr/bin/env bash

# Runs selected Gradle test tasks to regenerate *.telemetry output for
# individual OpenTelemetry Java agent instrumentations.

set -euo pipefail

# shellcheck source=instrumentation-docs/instrumentations.sh
source "$(dirname "$0")/instrumentations.sh"

ALL_TASKS=()
for task in "${INSTRUMENTATIONS[@]}"; do
  ALL_TASKS+=(":instrumentation:${task}")
done
for task in "${COLIMA_INSTRUMENTATIONS[@]}"; do
  ALL_TASKS+=(":instrumentation:${task}")
done

echo "Processing instrumentations..."
./gradlew "${ALL_TASKS[@]}" \
  -PcollectMetadata=true \
  --rerun-tasks --continue
echo "Telemetry file regeneration complete."
