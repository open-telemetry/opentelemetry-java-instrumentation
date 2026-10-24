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
KEY_COUNT=${KEY_COUNT:-10000}
APP_HEAP=${APP_HEAP:-512m}
REDIS_IMAGE=${REDIS_IMAGE:-redis:7.4.6-alpine}
COLLECTOR_IMAGE=${COLLECTOR_IMAGE:-otel/opentelemetry-collector-contrib:0.138.0}
JAVA=${JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}

APP_PORT=18080
CLUSTER_PORTS="17000 17001 17002"
STANDALONE_PORT=16379
PREFIX=lettuce-timeout-repro
APP_JAR=build/libs/lettuce-timeout-repro.jar
RESULTS=build/results/$(date +%Y%m%d-%H%M%S)
APP_PID=

cleanup() {
  if [[ -n "$APP_PID" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
    APP_PID=
  fi
  docker ps -aq --filter "name=^${PREFIX}-" | xargs -r docker rm -f >/dev/null
}
trap cleanup EXIT

start_redis() {
  docker ps -aq --filter "name=^${PREFIX}-redis" | xargs -r docker rm -f >/dev/null
  if [[ "$MODE" == cluster ]]; then
    local nodes=()
    for port in $CLUSTER_PORTS; do
      docker run -d --rm --name "$PREFIX-redis-$port" --network host "$REDIS_IMAGE" \
        redis-server --port "$port" --cluster-enabled yes --cluster-announce-ip 127.0.0.1 \
        --save "" --appendonly no >/dev/null
      nodes+=("127.0.0.1:$port")
    done
    for port in $CLUSTER_PORTS; do
      until docker exec "$PREFIX-redis-$port" redis-cli -p "$port" ping >/dev/null 2>&1; do
        sleep 0.5
      done
    done
    docker exec "$PREFIX-redis-${CLUSTER_PORTS%% *}" \
      redis-cli --cluster create "${nodes[@]}" --cluster-replicas 0 --cluster-yes >/dev/null
    until docker exec "$PREFIX-redis-${CLUSTER_PORTS%% *}" redis-cli -p "${CLUSTER_PORTS%% *}" \
      cluster info | grep -q cluster_state:ok; do
      sleep 0.5
    done
    REDIS_ARGS=("--spring.data.redis.cluster.nodes=$(IFS=,; echo "${nodes[*]}")")
  elif [[ "$MODE" == standalone ]]; then
    docker run -d --rm --name "$PREFIX-redis" --network host "$REDIS_IMAGE" \
      redis-server --port "$STANDALONE_PORT" --save "" --appendonly no >/dev/null
    until docker exec "$PREFIX-redis" redis-cli -p "$STANDALONE_PORT" ping >/dev/null 2>&1; do
      sleep 0.5
    done
    REDIS_ARGS=("--spring.data.redis.host=127.0.0.1" "--spring.data.redis.port=$STANDALONE_PORT")
  else
    echo "unknown MODE=$MODE (expected cluster or standalone)" >&2
    exit 1
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

"$JAVA" -version 2>&1 | head -1
if [[ ! -f "$APP_JAR" ]]; then
  ./gradlew --no-daemon -q bootJar
fi
mkdir -p "$RESULTS"
docker run -d --rm --name "$PREFIX-collector" --network host \
  -v "$PWD/collector.yaml:/etc/otelcol-contrib/config.yaml:ro" "$COLLECTOR_IMAGE" >/dev/null

for agent in $AGENTS; do
  run_one "$agent"
done
summary
