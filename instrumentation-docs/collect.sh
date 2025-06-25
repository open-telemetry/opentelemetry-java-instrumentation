#!/usr/bin/env bash

# Runs selected Gradle test tasks to regenerate *.telemetry output for
# individual OpenTelemetry Java agent instrumentations.

set -euo pipefail

# Oracle UCP won't run on Apple Silicon, so we need to use colima to run as x86_64
if [[ "$(uname -m)" == "arm64" || "$(uname -m)" == "aarch64" ]]; then
  colima start --arch x86_64 --memory 4
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  export DOCKER_HOST="unix://${HOME}/.colima/docker.sock"
fi

readonly INSTRUMENTATIONS=(
  # <module path (colon-separated)> : <javaagent|library> : [ gradle-task-suffix ]
  "activej-http-6.0:javaagent:test"
  "akka:akka-http-10.0:javaagent:test"
  "apache-httpasyncclient-4.1:javaagent:test"
  "alibaba-druid-1.0:javaagent:test"
  "alibaba-druid-1.0:javaagent:testStableSemconv"
  "apache-dbcp-2.0:javaagent:test"
  "apache-dbcp-2.0:javaagent:testStableSemconv"
  "apache-httpclient:apache-httpclient-2.0:javaagent:test"
  "apache-httpclient:apache-httpclient-4.0:javaagent:test"
  "apache-httpclient:apache-httpclient-4.3:library:test"
  "apache-httpclient:apache-httpclient-5.0:javaagent:test"
  "apache-dubbo-2.7:javaagent:testDubbo"
  "c3p0-0.9:javaagent:test"
  "c3p0-0.9:javaagent:testStableSemconv"
  "clickhouse-client-0.5:javaagent:test"
  "clickhouse-client-0.5:javaagent:testStableSemconv"
  "aws-sdk:aws-sdk-1.11:javaagent:test"
  "google-http-client-1.19:javaagent:test"
  "http-url-connection:javaagent:test"
  "java-http-client:javaagent:test"
  "jetty-httpclient:jetty-httpclient-9.2:javaagent:test"
  "jetty-httpclient:jetty-httpclient-12.0:javaagent:test"
  "jodd-http-4.2:javaagent:test"
  "netty:netty-3.8:javaagent:test"
  "netty:netty-4.0:javaagent:test"
  "netty:netty-4.1:javaagent:test"
  "okhttp:okhttp-2.2:javaagent:test"
  "okhttp:okhttp-3.0:javaagent:test"
  "pekko:pekko-http-1.0:javaagent:test"
  "play:play-ws:play-ws-1.0:javaagent:test"
  "play:play-ws:play-ws-2.0:javaagent:test"
  "play:play-ws:play-ws-2.1:javaagent:test"
  "reactor:reactor-netty:reactor-netty-0.9:javaagent:test"
  "reactor:reactor-netty:reactor-netty-1.0:javaagent:test"
  "spring:spring-webflux:spring-webflux-5.0:javaagent:test"
  "vertx:vertx-http-client:vertx-http-client-3.0:javaagent:test"
  "vertx:vertx-http-client:vertx-http-client-4.0:javaagent:test"
  "vertx:vertx-http-client:vertx-http-client-5.0:javaagent:test"
  "vertx:vertx-redis-client-4.0:javaagent:test"
  "vertx:vertx-redis-client-4.0:javaagent:testStableSemconv"
  "vertx:vertx-sql-client:vertx-sql-client-4.0:javaagent:test"
  "vertx:vertx-sql-client:vertx-sql-client-4.0:javaagent:testStableSemconv"
  "vertx:vertx-sql-client:vertx-sql-client-5.0:javaagent:test"
  "vertx:vertx-sql-client:vertx-sql-client-5.0:javaagent:testStableSemconv"
  "aws-sdk:aws-sdk-1.11:javaagent:testStableSemconv"
  "aws-sdk:aws-sdk-2.2:javaagent:test"
  "aws-sdk:aws-sdk-2.2:javaagent:testStableSemconv"
  "aws-sdk:aws-sdk-2.2:javaagent:testBedrockRuntime"
  "hikaricp-3.0:javaagent:test"
  "hikaricp-3.0:javaagent:testStableSemconv"
  "tomcat:tomcat-jdbc:javaagent:test"
  "tomcat:tomcat-jdbc:javaagent:testStableSemconv"
  "oracle-ucp-11.2:javaagent:test"
  "oracle-ucp-11.2:javaagent:testStableSemconv"
  "oshi:javaagent:test"
  "oshi:javaagent:testExperimental"
  "vibur-dbcp-11.0:javaagent:test"
  "vibur-dbcp-11.0:javaagent:testStableSemconv"
)

readonly TELEMETRY_DIR_NAME=".telemetry"

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

# Cleans any stray .telemetry directories left in the repo.
find_and_remove_all_telemetry() {
  echo "Removing stray .telemetry directories..."
  find . -type d -name "$TELEMETRY_DIR_NAME" -exec rm -rf {} +
}

# Main
declare -a gradle_tasks=()

for descriptor in "${INSTRUMENTATIONS[@]}"; do
  # Parse the descriptor into its components
  if ! read -r module_path task_type task_suffix \
        < <(parse_descriptor "$descriptor"); then
    continue   # skip any badly formed descriptors
  fi

  # Make sure we’re starting fresh for this instrumentation
  delete_existing_telemetry "$module_path"

  # Build the Gradle task string and queue it
  gradle_tasks+=( "$(build_gradle_task "$module_path" "$task_type" "$task_suffix")" )
done

echo
echo "Running Gradle tasks:"
printf '    %s\n' "${gradle_tasks[@]}"
echo

./gradlew "${gradle_tasks[@]}" \
  -PcollectMetadata=true \
  --rerun-tasks --continue

# uncomment the next line to remove all .telemetry directories
#find_and_remove_all_telemetry

echo "Telemetry file regeneration complete."
