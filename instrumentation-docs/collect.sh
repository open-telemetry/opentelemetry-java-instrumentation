#!/bin/bash

instrumentations=(
  "apache-httpclient:apache-httpclient-2.0:javaagent:test"
  "alibaba-druid-1.0:javaagent:test"
  "clickhouse-client-0.5:javaagent:testStableSemconv"
)

# Initialize an empty string to hold the Gradle tasks
gradle_tasks=""

# Iterate over each instrumentation
for instrumentation in "${instrumentations[@]}"; do
  # Extract the parts of the instrumentation
  IFS=':' read -r -a parts <<< "$instrumentation"
  module="${parts[0]}"
  version="${parts[1]}"
  type="${parts[2]}"
  suffix="${parts[3]}"

  # Assemble the path to the instrumentation
  path="instrumentation/$module/$version"

  # Remove any occurrence of /javaagent/ or /library/ from the path
  path=$(echo "$path" | sed -e 's/\/javaagent//g' -e 's/\/library//g')

  # Check if the .telemetry directory exists and remove it if it does
  if [ -d "$path/.telemetry" ]; then
    echo "Removing directory: $path/.telemetry"
    rm -rf "$path/.telemetry"
  else
    echo "Directory does not exist: $path/.telemetry"
  fi

  # Append the Gradle task to the gradle_tasks string with a colon between type and suffix if suffix is non-empty
  if [ -n "$suffix" ]; then
    gradle_tasks+=":instrumentation:$module:$version:$type:$suffix "
  else
    gradle_tasks+=":instrumentation:$module:$version:$type "
  fi
done

# rerun-tasks is used to force re-running tests that might be cached
echo Running: ./gradlew "$gradle_tasks" -PcollectMetadata=true --rerun-tasks
./gradlew "$gradle_tasks" -PcollectMetadata=true --rerun-tasks
./gradlew :instrumentation-docs:generateDocs
