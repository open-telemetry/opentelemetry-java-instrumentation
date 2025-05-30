#!/bin/bash
set -euo pipefail

# This script selectively runs tests for specific instrumentations in order to generate telemetry data.

instrumentations=(
  "apache-httpclient:apache-httpclient-5.0:javaagent:test"
  "alibaba-druid-1.0:javaagent:test"
  "c3p0-0.9:javaagent:test"
  "tomcat:tomcat-jdbc:javaagent:test"
  "hikaricp-3.0:javaagent:test"
  "apache-dbcp-2.0:javaagent:test"
)

gradle_tasks=()


for descriptor in "${instrumentations[@]}"; do
  # Split into array by colon
  IFS=':' read -r -a parts <<< "$descriptor"

  # Find "javaagent" / "library" token
  type_idx=-1
  for i in "${!parts[@]}"; do
    if [[ ${parts[$i]} == javaagent || ${parts[$i]} == library ]]; then
      type_idx=$i; break
    fi
  done
  (( type_idx >= 0 )) || { echo "bad descriptor: $descriptor" >&2; continue; }

  type=${parts[$type_idx]}

  # set suffix to the next array element if one exists, otherwise leave it blank.
  suffix=""; (( type_idx+1 < ${#parts[@]} )) && suffix=${parts[$((type_idx+1))]}

  # Slice the array to keep only the module path parts
  path_segments=("${parts[@]:0:type_idx}")

  # Join the path segments to form the full path
  path="instrumentation/$(IFS=/; echo "${path_segments[*]}")"

  [[ -d "$path/.telemetry" ]] && rm -rf "$path/.telemetry"

  task=":instrumentation:$(IFS=:; echo "${path_segments[*]}"):$type"
  [[ -n $suffix ]] && task+=":$suffix"
  gradle_tasks+=("$task")
done

# rerun-tasks is used to force re-running tests that might be cached
echo "Running: ./gradlew ${gradle_tasks[*]} -PcollectMetadata=true --rerun-tasks"
./gradlew "${gradle_tasks[@]}" -PcollectMetadata=true --rerun-tasks
#./gradlew :instrumentation-docs:runAnalysis

# Remove all .telemetry directories recursively from the project root
echo "Searching for and removing all .telemetry directories..."
find . -type d -name ".telemetry" -exec rm -rf {} +
