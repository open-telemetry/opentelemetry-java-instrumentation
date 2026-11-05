#!/usr/bin/env bash

# Runs selected Gradle test tasks to regenerate *.telemetry output for
# individual OpenTelemetry Java agent instrumentations.

set -euo pipefail

cd "$(dirname "$0")/.."
source .github/scripts/instrumentations.sh

USE_COLIMA=false
case "$(uname -m)" in
  arm64|aarch64)
    if [[ ${#COLIMA_INSTRUMENTATIONS[@]} -gt 0 ]]; then
      USE_COLIMA=true
      # Use a pre-started profile rather than changing a developer's existing VM.
      if ! command -v colima > /dev/null ||
          [[ "$(colima --profile otel-metadata ssh -- uname -m)" != x86_64 ]]; then
        echo "ARM collection requires an x86_64 Colima profile. Run: colima start --profile otel-metadata --arch x86_64 --memory 4 --activate=false" >&2
        exit 1
      fi
    fi
    ;;
esac

# Telemetry lives in the parent of the test project, including *-testing projects.
# Do this once, before any pass writes profiles shared with subsequent passes.
MODULES=()
for task in "${INSTRUMENTATIONS[@]}" "${COLIMA_INSTRUMENTATIONS[@]}" "${TEST_LATEST_DEPS_INSTRUMENTATIONS[@]}"; do
  project="${task%:*}"
  module="${project%:*}"
  module="instrumentation/${module//:/\/}"
  if [[ ! -f "$module/metadata.yaml" ]]; then
    echo "Cannot locate instrumentation metadata for $task" >&2
    exit 1
  fi
  MODULES+=("$module")
done
if [[ ${#MODULES[@]} -gt 0 ]]; then
  while IFS= read -r module; do
    rm -rf "$module/.telemetry"
  done < <(printf '%s\n' "${MODULES[@]}" | sort -u)
fi

# Configure Gradle flags based on DEBUG_LOGGING environment variable
GRADLE_FLAGS=(-PcollectMetadata=true)
if [[ "${DEBUG_LOGGING:-false}" != "true" ]]; then
  GRADLE_FLAGS+=(--no-daemon --quiet)
  echo "Running with reduced logging (set DEBUG_LOGGING=true to enable full output)"
else
  echo "Running with full debug logging enabled"
fi

# Collect standard and colima tasks (without testLatestDeps)
ALL_TASKS=()
for task in "${INSTRUMENTATIONS[@]}"; do
  ALL_TASKS+=(":instrumentation:${task}" --rerun)
done
for task in "${COLIMA_INSTRUMENTATIONS[@]}"; do
  if [[ "$USE_COLIMA" == false ]]; then
    ALL_TASKS+=(":instrumentation:${task}" --rerun)
  fi
done

echo "Processing standard instrumentations..."
if [[ ${#ALL_TASKS[@]} -gt 0 ]]; then
  ./gradlew "${ALL_TASKS[@]}" \
    "${GRADLE_FLAGS[@]}"
fi

# Collect and run tasks that need testLatestDeps
LATEST_DEPS_TASKS=()
for task in "${TEST_LATEST_DEPS_INSTRUMENTATIONS[@]}"; do
  LATEST_DEPS_TASKS+=(":instrumentation:${task}" --rerun)
done

if [[ ${#LATEST_DEPS_TASKS[@]} -gt 0 ]]; then
  echo "Processing instrumentations with -PtestLatestDeps=true..."
  ./gradlew "${LATEST_DEPS_TASKS[@]}" \
    -PtestLatestDeps=true \
    "${GRADLE_FLAGS[@]}"
fi

if [[ "$USE_COLIMA" == true ]]; then
  COLIMA_TASKS=()
  for task in "${COLIMA_INSTRUMENTATIONS[@]}"; do
    COLIMA_TASKS+=(":instrumentation:${task}" --rerun)
  done
  echo "Processing instrumentations requiring x86_64 containers..."
  (
    unset DOCKER_CONTEXT
    export DOCKER_HOST="unix://${HOME}/.colima/otel-metadata/docker.sock"
    export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
    ./gradlew "${COLIMA_TASKS[@]}" \
      "${GRADLE_FLAGS[@]}"
  )
fi

echo "Telemetry file regeneration complete."
