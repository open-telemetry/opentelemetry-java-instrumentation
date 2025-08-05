#!/usr/bin/env bash

# Runs selected Gradle test tasks to regenerate *.telemetry output for
# individual OpenTelemetry Java agent instrumentations. Some instrumentation test suites don't run
# on ARM, so we use colima to run them in an x86_64 container.

set -euo pipefail

# shellcheck source=instrumentation-docs/instrumentations.sh
source "$(dirname "$0")/instrumentations.sh"

readonly TELEMETRY_DIR_NAME=".telemetry"

# Sets up colima for x86_64 architecture if on ARM
setup_colima() {
  if [[ "$(uname -m)" == "arm64" || "$(uname -m)" == "aarch64" ]]; then
    echo "Setting up colima for x86_64 architecture..."
    colima start --arch x86_64 --memory 4
    export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
    export DOCKER_HOST="unix://${HOME}/.colima/docker.sock"
  fi
}

# Splits a single descriptor into its three logical parts.
#   argument $1: descriptor string (ex: "foo:bar:baz:test")
# Outputs three variables via echo:
#   1. module_path  - instrumentation/foo/bar
#   2. task_type    - javaagent | library
#   3. task_suffix  - test
parse_descriptor() {
  local descriptor="$1"
  local -a parts

  # Convert colon-delimited string into array
  IFS=':' read -r -a parts <<< "$descriptor"

  # Locate "javaagent" or "library" token (there should be exactly one)
  local type_idx=-1
  for i in "${!parts[@]}"; do
    if [[ ${parts[$i]} == "javaagent" || ${parts[$i]} == "library" ]]; then
      type_idx=$i
      break
    fi
  done

  if [[ $type_idx -lt 0 ]]; then
    echo "ERROR: malformed descriptor: $descriptor" >&2
    return 1
  fi

  local task_type="${parts[$type_idx]}"

  # Optional suffix lives after the type token
  local task_suffix=""
  if (( type_idx + 1 < ${#parts[@]} )); then
    task_suffix="${parts[$((type_idx + 1))]}"
  fi

  # Join everything before the type token with slashes to make instrumentation/<path>/...
  local path_segments=("${parts[@]:0:type_idx}")
  local module_path
  IFS=/ module_path="instrumentation/${path_segments[*]}"

  echo "$module_path" "$task_type" "$task_suffix"
}

# Removes a .telemetry directory if it exists under the given module path.
delete_existing_telemetry() {
  local module_path="$1"
  local telemetry_path="$module_path/$TELEMETRY_DIR_NAME"

  if [[ -d "$telemetry_path" ]]; then
    rm -rf "$telemetry_path"
  fi
}

# Converts the three parsed parts into a Gradle task name.
# ex: instrumentation:foo:bar:javaagent:test
build_gradle_task() {
  local module_path="$1"   # instrumentation/foo/bar
  local task_type="$2"     # javaagent | library
  local task_suffix="$3"   # test | <blank>

  # replace slashes with colons, then append task parts
  local module_colon_path="${module_path//\//:}"
  local task=":$module_colon_path:$task_type"

  [[ -n $task_suffix ]] && task+=":$task_suffix"
  echo "$task"
}

# Processes a list of descriptors and returns an array of Gradle tasks
# Args: array of descriptors
# Echoes the Gradle tasks, one per line
process_descriptors() {
  local descriptors=("$@")
  local -a tasks=()

  for descriptor in "${descriptors[@]}"; do
    # Parse the descriptor into its components
    if ! read -r module_path task_type task_suffix \
          < <(parse_descriptor "$descriptor"); then
      continue   # skip any badly formed descriptors
    fi

    # Make sure we're starting fresh for this instrumentation
    delete_existing_telemetry "$module_path"

    # Build the Gradle task string and add to array
    tasks+=( "$(build_gradle_task "$module_path" "$task_type" "$task_suffix")" )
  done

  # Echo tasks, one per line
  for t in "${tasks[@]}"; do
    echo "$t"
  done
}

run_gradle_tasks() {
  local -a tasks=("$@")

  if [[ ${#tasks[@]} -eq 0 ]]; then
    echo "No tasks to run"
    return 0
  fi

  echo
  echo "Running Gradle tasks:"
  printf '    %s\n' "${tasks[@]}"
  echo

  ./gradlew "${tasks[@]}" \
    -PcollectMetadata=true \
    --rerun-tasks --continue --no-parallel
}

# Cleans any stray .telemetry directories left in the repo.
find_and_remove_all_telemetry() {
  echo "Removing stray .telemetry directories..."
  find . -type d -name "$TELEMETRY_DIR_NAME" -exec rm -rf {} +
}


# Main execution
main() {
  colima stop

  # Process regular instrumentations
  echo "Processing standard instrumentations..."
  gradle_tasks=()
  while IFS= read -r line; do
    gradle_tasks+=("$line")
  done < <(process_descriptors "${INSTRUMENTATIONS[@]}")
  run_gradle_tasks "${gradle_tasks[@]}"

  # Setup colima if needed
  setup_colima

  # Process colima-specific instrumentations
  echo "Processing colima instrumentations..."
  gradle_tasks=()
  while IFS= read -r line; do
    gradle_tasks+=("$line")
  done < <(process_descriptors "${COLIMA_INSTRUMENTATIONS[@]}")
  run_gradle_tasks "${gradle_tasks[@]}"

  colima stop

  # uncomment the next line to remove all .telemetry directories
  #find_and_remove_all_telemetry

  echo "Telemetry file regeneration complete."
}

# Run main function
main "$@"
