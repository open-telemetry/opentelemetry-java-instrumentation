#!/bin/bash

set -euo pipefail

work=/benchmark
application=io.opentelemetry.smoketest.springboot.SpringbootApplication
pid=

stop_training() {
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    kill -TERM "$pid"
    wait "$pid" || true
  fi
}
trap stop_training EXIT

if [[ ! -d /app/classes || ! -d /app/resources || ! -d /app/libs ]]; then
  echo "Expected the pinned Spring smoke image with /app/classes, /app/resources, and /app/libs."
  echo "A nested Spring Boot launcher does not provide the flat class path this benchmark requires."
  exit 1
fi

jar --create --file "$work/application.jar" -C /app/classes . -C /app/resources .
cp -p /tmp/agent.jar "$work/agent.jar"
app=(-cp "$work/application.jar:/app/libs/*" "$application")
java -version >"$work/java-version.log" 2>&1
printf '%s\n' /app/libs/spring-boot-*.jar >"$work/spring-version.log"

for variant in no-agent agent; do
  common=(-Xmx512m)
  if [[ "$variant" == agent ]]; then
    common+=(--add-modules=java.instrument "-Xbootclasspath/a:$work/agent.jar")
  fi
  started=$(date +%s%N)
  java "${common[@]}" -XX:AOTMode=record "-XX:AOTConfiguration=$work/$variant.aotconf" \
    "${app[@]}" >"$work/$variant-record.log" 2>&1 &
  pid=$!
  for _ in $(seq 1 480); do
    if grep -q "Started SpringbootApplication in" "$work/$variant-record.log"; then
      break
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      cat "$work/$variant-record.log"
      wait "$pid"
      exit 1
    fi
    sleep 0.25
  done
  if ! grep -q "Started SpringbootApplication in" "$work/$variant-record.log"; then
    cat "$work/$variant-record.log"
    echo "Timed out during $variant training"
    exit 1
  fi
  kill -TERM "$pid"
  status=0
  wait "$pid" || status=$?
  pid=
  if [[ "$status" != 0 && "$status" != 143 ]]; then
    cat "$work/$variant-record.log"
    exit "$status"
  fi
  [[ -s "$work/$variant.aotconf" ]]
  echo "cache.$variant.record.millis=$(( ($(date +%s%N) - started) / 1000000 ))" >>"$work/cache.properties"
  started=$(date +%s%N)
  if ! java "${common[@]}" -XX:+DisableAttachMechanism -XX:AOTMode=create \
    "-XX:AOTConfiguration=$work/$variant.aotconf" "-XX:AOTCache=$work/$variant.aot" \
    -cp "$work/application.jar:/app/libs/*" >"$work/$variant-create.log" 2>&1; then
    cat "$work/$variant-create.log"
    exit 1
  fi
  [[ -s "$work/$variant.aot" ]]
  echo "cache.$variant.create.millis=$(( ($(date +%s%N) - started) / 1000000 ))" >>"$work/cache.properties"
  echo "cache.$variant.bytes=$(stat -c %s "$work/$variant.aot")" >>"$work/cache.properties"
done

echo BENCHMARK_CACHES_READY
# Keep the container available for copying diagnostics and metadata out.
exec sleep infinity
