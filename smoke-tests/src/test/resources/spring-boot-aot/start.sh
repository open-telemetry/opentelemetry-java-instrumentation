#!/bin/bash

set -euo pipefail

agent_jar=/opentelemetry-javaagent.jar
aot_directory=/tmp/opentelemetry-aot
aot_configuration="$aot_directory/application.aotconf"
aot_cache="$aot_directory/application.aot"
training_log="$aot_directory/training.log"
create_log="$aot_directory/create.log"
production_java_tool_options="${JAVA_TOOL_OPTIONS:-}"
training_pid=

mkdir -p "$aot_directory"

if [[ -f /app/app.jar ]]; then
  application_args=(-jar /app/app.jar)
else
  application_jar="$aot_directory/application.jar"
  env -u JAVA_TOOL_OPTIONS jar --create \
    --file "$application_jar" \
    -C /app/classes . \
    -C /app/resources .
  application_args=(
    -cp "$application_jar:/app/libs/*"
    io.opentelemetry.smoketest.springboot.SpringbootApplication
  )
fi

common_options=(
  -Xmx512m
  --add-modules=java.instrument
  "-Xbootclasspath/a:$agent_jar"
)

stop_training() {
  if [[ -n "$training_pid" ]] && kill -0 "$training_pid" 2>/dev/null; then
    kill -TERM "$training_pid"
    wait "$training_pid" || true
  fi
}
trap stop_training EXIT

env -u JAVA_TOOL_OPTIONS java \
  -XX:AOTMode=record \
  "-XX:AOTConfiguration=$aot_configuration" \
  "${common_options[@]}" \
  "${application_args[@]}" \
  >"$training_log" 2>&1 &
training_pid=$!

for _ in $(seq 1 480); do
  if grep -q "Started SpringbootApplication in" "$training_log"; then
    break
  fi
  if ! kill -0 "$training_pid" 2>/dev/null; then
    cat "$training_log"
    wait "$training_pid"
  fi
  sleep 0.25
done

if ! grep -q "Started SpringbootApplication in" "$training_log"; then
  cat "$training_log"
  echo "Timed out waiting for AOT training application"
  exit 1
fi

kill -TERM "$training_pid"
training_status=0
wait "$training_pid" || training_status=$?
training_pid=
if [[ "$training_status" -ne 0 && "$training_status" -ne 143 ]]; then
  cat "$training_log"
  exit "$training_status"
fi

if [[ ! -s "$aot_configuration" ]]; then
  cat "$training_log"
  echo "AOT training did not create a configuration"
  exit 1
fi

if ! env -u JAVA_TOOL_OPTIONS java \
  -XX:AOTMode=create \
  "-XX:AOTConfiguration=$aot_configuration" \
  "-XX:AOTCache=$aot_cache" \
  -XX:+DisableAttachMechanism \
  "${common_options[@]}" \
  "${application_args[@]}" \
  >"$create_log" 2>&1; then
  cat "$create_log"
  exit 1
fi

if [[ ! -s "$aot_cache" ]]; then
  cat "$create_log"
  echo "AOT cache creation did not produce a cache"
  exit 1
fi

export JAVA_TOOL_OPTIONS="$production_java_tool_options \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:+VerifySharedSpaces \
  -XX:AOTMode=on \
  -XX:AOTCache=$aot_cache \
  --add-modules=java.instrument \
  -Xbootclasspath/a:$agent_jar \
  -Dotel.javaagent.debug=false \
  -Djdk.instrument.traceUsage=true \
  -Xlog:aot=debug,class+load=info"

exec java "${application_args[@]}"
