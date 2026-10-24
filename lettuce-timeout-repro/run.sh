#!/usr/bin/env bash
#
# Reproduction attempt for
# https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/19814
#
# Runs the same bounded reactive Redis GET workload against the same Redis setup once per agent
# configuration and prints a summary. See README.md for the parameters.

set -euo pipefail
cd "$(dirname "$0")"

MODE=${MODE:-cluster}
AGENTS=${AGENTS:-"none 2.29.0 2.31.1 2.33.0"}
CONCURRENCY=${CONCURRENCY:-256}
RATE=${RATE:-0}
WARMUP_SECONDS=${WARMUP_SECONDS:-30}
DURATION_SECONDS=${DURATION_SECONDS:-120}
REDIS_TIMEOUT=${REDIS_TIMEOUT:-60s}
REQUEST_TIMEOUT_SECONDS=${REQUEST_TIMEOUT_SECONDS:-90}
REDIS_STARTUP_TIMEOUT_SECONDS=${REDIS_STARTUP_TIMEOUT_SECONDS:-30}
CLUSTER_READY_TIMEOUT_SECONDS=${CLUSTER_READY_TIMEOUT_SECONDS:-30}
KEY_COUNT=${KEY_COUNT:-10000}
APP_HEAP=${APP_HEAP:-512m}
REDIS_IMAGE=${REDIS_IMAGE:-redis:7.4.6-alpine}
COLLECTOR_IMAGE=${COLLECTOR_IMAGE:-otel/opentelemetry-collector-contrib:0.138.0}
JAVA=${JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}

APP_PORT=18080
CLUSTER_PORTS="17000 17001 17002"
STANDALONE_PORT=16379
PREFIX=lettuce-timeout-repro
RUN_ID="${BASHPID:-$$}-$RANDOM"
CONTAINER_PREFIX="$PREFIX-$RUN_ID"
APP_JAR=build/libs/lettuce-timeout-repro.jar
RESULTS=build/results/$(date +%Y%m%d-%H%M%S)-$RUN_ID
APP_PID=
CONTAINER_IDS=()
REDIS_CONTAINER_IDS=()

cleanup() {
  if [[ -n "$APP_PID" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
    APP_PID=
  fi
  for container_id in "${CONTAINER_IDS[@]}"; do
    docker rm -f "$container_id" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

cleanup_redis() {
  for container_id in "${REDIS_CONTAINER_IDS[@]}"; do
    docker rm -f "$container_id" >/dev/null 2>&1 || true
  done
  REDIS_CONTAINER_IDS=()
}

check_port_is_free() {
  local port=$1
  if (true >/dev/tcp/127.0.0.1/"$port") 2>/dev/null; then
    echo "port $port is already in use; refusing to reuse an existing service" >&2
    return 1
  fi
}

check_ports() {
  if [[ "$MODE" == cluster ]]; then
    for port in $CLUSTER_PORTS; do
      check_port_is_free "$port"
      check_port_is_free "$((port + 10000))"
    done
  elif [[ "$MODE" == standalone ]]; then
    check_port_is_free "$STANDALONE_PORT"
  else
    echo "unknown MODE=$MODE (expected cluster or standalone)" >&2
    return 1
  fi
  for port in "$APP_PORT" 4317 4318; do
    check_port_is_free "$port"
  done
}

start_container() {
  local name=$1
  shift
  local container_id
  if ! container_id=$(docker run -d --name "$name" --network host "$@"); then
    echo "failed to start container '$name'; check Docker and host port availability" >&2
    return 1
  fi
  CONTAINER_IDS+=("$container_id")
  CREATED_CONTAINER_ID=$container_id
}

start_redis_container() {
  start_container "$@"
  REDIS_CONTAINER_IDS+=("$CREATED_CONTAINER_ID")
}

wait_for_redis() {
  local container_id=$1
  local name=$2
  local port=$3
  local deadline=$((SECONDS + REDIS_STARTUP_TIMEOUT_SECONDS))
  while ((SECONDS <= deadline)); do
    if ! docker inspect --format '{{.State.Running}}' "$container_id" 2>/dev/null | grep -qx true; then
      echo "Redis container '$name' exited before becoming ready" >&2
      docker logs "$container_id" >&2 || true
      return 1
    fi
    if docker exec "$container_id" redis-cli -p "$port" ping >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done
  echo "Redis container '$name' did not become ready within ${REDIS_STARTUP_TIMEOUT_SECONDS}s" >&2
  docker logs "$container_id" >&2 || true
  return 1
}

wait_for_cluster() {
  local container_id=$1
  local name=$2
  local port=$3
  local deadline=$((SECONDS + CLUSTER_READY_TIMEOUT_SECONDS))
  local cluster_info=
  while ((SECONDS <= deadline)); do
    if ! docker inspect --format '{{.State.Running}}' "$container_id" 2>/dev/null | grep -qx true; then
      echo "Redis container '$name' exited while waiting for cluster health" >&2
      docker logs "$container_id" >&2 || true
      return 1
    fi
    cluster_info=$(docker exec "$container_id" redis-cli -p "$port" cluster info 2>/dev/null || true)
    if grep -q '^cluster_state:ok' <<<"$cluster_info"; then
      return 0
    fi
    sleep 0.5
  done
  echo "Redis cluster did not become healthy within ${CLUSTER_READY_TIMEOUT_SECONDS}s" >&2
  echo "$cluster_info" >&2
  docker logs "$container_id" >&2 || true
  return 1
}

start_redis() {
  cleanup_redis
  if [[ "$MODE" == cluster ]]; then
    local nodes=()
    local first_container_id=
    for port in $CLUSTER_PORTS; do
      local name="$CONTAINER_PREFIX-redis-$port"
      start_redis_container "$name" "$REDIS_IMAGE" \
        redis-server --port "$port" --cluster-enabled yes --cluster-announce-ip 127.0.0.1 \
        --save "" --appendonly no
      if [[ -z "$first_container_id" ]]; then
        first_container_id=$CREATED_CONTAINER_ID
      fi
      nodes+=("127.0.0.1:$port")
      wait_for_redis "$CREATED_CONTAINER_ID" "$name" "$port"
    done
    local first_port=${CLUSTER_PORTS%% *}
    local first_container="$CONTAINER_PREFIX-redis-$first_port"
    docker exec "$first_container_id" \
      redis-cli --cluster create "${nodes[@]}" --cluster-replicas 0 --cluster-yes >/dev/null
    wait_for_cluster "$first_container_id" "$first_container" "$first_port"
    REDIS_ARGS=("--spring.data.redis.cluster.nodes=$(IFS=,; echo "${nodes[*]}")")
  elif [[ "$MODE" == standalone ]]; then
    local name="$CONTAINER_PREFIX-redis"
    start_redis_container "$name" "$REDIS_IMAGE" \
      redis-server --port "$STANDALONE_PORT" --save "" --appendonly no
    wait_for_redis "$CREATED_CONTAINER_ID" "$name" "$STANDALONE_PORT"
    REDIS_ARGS=("--spring.data.redis.host=127.0.0.1" "--spring.data.redis.port=$STANDALONE_PORT")
  fi
}

agent_jar() {
  local version=$1
  local jar=build/agents/opentelemetry-javaagent-$version.jar
  if [[ ! -f "$jar" ]]; then
    mkdir -p build/agents
    curl -sSfL -o "$jar.tmp" \
      "https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/$version/opentelemetry-javaagent-$version.jar"
    mv "$jar.tmp" "$jar"
  fi
  echo "$jar"
}

run_load() {
  "$JAVA" -Xmx512m -jar "$APP_JAR" load --url="http://127.0.0.1:$APP_PORT" \
    --concurrency="$CONCURRENCY" --rate="$RATE" --duration-seconds="$1" \
    --request-timeout-seconds="$REQUEST_TIMEOUT_SECONDS" --keys="$KEY_COUNT"
}

run_one() {
  local agent=$1
  local dir=$RESULTS/$agent
  mkdir -p "$dir"
  echo "=== agent=$agent mode=$MODE"

  start_redis

  local agent_args=()
  if [[ "$agent" != none ]]; then
    # default agent configuration; telemetry goes to the local collector on the default endpoint
    agent_args=("-javaagent:$(agent_jar "$agent")" "-Dotel.service.name=$PREFIX")
  fi
  "$JAVA" "-Xms$APP_HEAP" "-Xmx$APP_HEAP" "${agent_args[@]}" -jar "$APP_JAR" \
    "--server.port=$APP_PORT" "--spring.data.redis.timeout=$REDIS_TIMEOUT" \
    "--repro.keys=$KEY_COUNT" "${REDIS_ARGS[@]}" >"$dir/app.log" 2>&1 &
  APP_PID=$!

  local deadline=$((SECONDS + 120))
  until curl -sf "http://127.0.0.1:$APP_PORT/stats" >/dev/null; do
    if ((SECONDS > deadline)) || ! kill -0 "$APP_PID" 2>/dev/null; then
      echo "application did not start, see $dir/app.log" >&2
      exit 1
    fi
    sleep 1
  done
  curl -sSf --max-time 120 "http://127.0.0.1:$APP_PORT/populate"
  echo
  curl -sSf "http://127.0.0.1:$APP_PORT/ready" >/dev/null

  if ((WARMUP_SECONDS > 0)); then
    run_load "$WARMUP_SECONDS" >"$dir/warmup.txt" 2>&1
  fi
  run_load "$DURATION_SECONDS" 2>&1 | tee "$dir/load.txt"
  curl -sSf "http://127.0.0.1:$APP_PORT/stats" >"$dir/stats.json"
  echo
  cat "$dir/stats.json"
  echo

  kill "$APP_PID"
  wait "$APP_PID" 2>/dev/null || true
  APP_PID=
  cleanup_redis
}

summary() {
  local file=$RESULTS/summary.md
  {
    echo "mode=$MODE redis=$REDIS_IMAGE concurrency=$CONCURRENCY rate=$RATE warmup=${WARMUP_SECONDS}s duration=${DURATION_SECONDS}s" \
      "redisTimeout=$REDIS_TIMEOUT keys=$KEY_COUNT heap=$APP_HEAP lettuce=$(unzip -l "$APP_JAR" | grep -o 'lettuce-core-[^/]*\.jar')"
    echo
    echo "| agent | requests | ok | failed | skipped | rps | p50 ms | p99 ms | p99.9 ms | max ms | >=1s | Redis errors (app) | 'timed out' in app log |"
    echo "|---|---|---|---|---|---|---|---|---|---|---|---|---|"
    for agent in $AGENTS; do
      local dir=$RESULTS/$agent
      value() { grep -m1 "^result\.$1=" "$dir/load.txt" | cut -d= -f2; }
      local redis_errors
      redis_errors=$(grep -o '"redis.get.error[^,}]*' "$dir/stats.json" | tr '\n' ' ' || true)
      echo "| $agent | $(value requests) | $(value ok) | $(value failed) | $(value skipped)" \
        "| $(value throughput_rps)" \
        "| $(value latency_p50.0_ms) | $(value latency_p99.0_ms) | $(value latency_p99.9_ms)" \
        "| $(value latency_max_ms) | $(value latency_over_1s) | ${redis_errors:-none}" \
        "| $(grep -c 'timed out' "$dir/app.log" || true) |"
    done
  } >"$file"
  echo
  cat "$file"
}

if ! JAVA_EXECUTABLE=$(command -v "$JAVA"); then
  echo "JAVA executable not found: $JAVA" >&2
  exit 1
fi
JAVA_EXECUTABLE=$(readlink -f "$JAVA_EXECUTABLE")
JAVA_HOME=$(dirname "$(dirname "$JAVA_EXECUTABLE")")
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "cannot determine JDK home from JAVA=$JAVA" >&2
  exit 1
fi
export JAVA_HOME
JAVA=$JAVA_EXECUTABLE
"$JAVA" -version 2>&1 | head -1
if [[ ! -f "$APP_JAR" ]]; then
  ./gradlew --no-daemon -q bootJar
fi
mkdir -p "$RESULTS"
check_ports
start_container "$CONTAINER_PREFIX-collector" \
  -v "$PWD/collector.yaml:/etc/otelcol-contrib/config.yaml:ro" "$COLLECTOR_IMAGE"

for agent in $AGENTS; do
  run_one "$agent"
done
summary
