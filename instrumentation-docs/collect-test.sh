#!/usr/bin/env bash

set -euo pipefail

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT

mkdir -p "$fixture/instrumentation-docs" "$fixture/.github/scripts" "$fixture/bin"
cp "$(dirname "$0")/collect.sh" "$fixture/instrumentation-docs/collect.sh"

cat > "$fixture/.github/scripts/instrumentations.sh" <<'EOF'
INSTRUMENTATIONS=(
  shared:javaagent:test
  shared:javaagent:testExperimental
  standard:library:test
  vertx:vertx-kafka-client-3.6:vertx-kafka-client-3.6-testing:test
)
TEST_LATEST_DEPS_INSTRUMENTATIONS=(
  shared:javaagent:testExperimental
  latest:javaagent:test
)
COLIMA_INSTRUMENTATIONS=(
  shared:javaagent:testColima
  colima:javaagent:test
)
EOF

cat > "$fixture/bin/uname" <<'EOF'
#!/usr/bin/env bash
echo "$TEST_ARCH"
EOF

cat > "$fixture/bin/colima" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$FIXTURE/colima-calls"
test "$TEST_ARCH" != x86_64
test "$*" = '--profile otel-metadata ssh -- uname -m'
if [[ "$SCENARIO" == unavailable ]]; then
  exit 1
fi
if [[ "$SCENARIO" == wrong-arch ]]; then
  echo aarch64
else
  echo x86_64
fi
EOF

cat > "$fixture/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

for module in shared standard latest colima vertx/vertx-kafka-client-3.6; do
  test ! -f "instrumentation/$module/.telemetry/stale"
  mkdir -p "instrumentation/$module/.telemetry"
done
test -f instrumentation/unrelated/.telemetry/stale
[[ " $* " == *" -PcollectMetadata=true "* ]]
[[ " $* " == *" --rerun "* ]]
[[ " $* " != *" --rerun-tasks "* ]]
if [[ "$DEBUG_LOGGING" == true ]]; then
  [[ " $* " != *" --quiet "* ]]
else
  [[ " $* " == *" --no-daemon --quiet "* ]]
fi

# --rerun is a task option, so every selected task must have its own.
arguments=()
while [[ $# -gt 0 ]]; do
  arguments+=("$1")
  if [[ "$1" == :instrumentation:* ]]; then
    shift
    test "$1" = --rerun
  fi
  shift
done
set -- "${arguments[@]}"

pass=$(cat pass)
if [[ "$TEST_ARCH" != x86_64 && "$pass" == 2 ]]; then
  test "$DOCKER_HOST" = "unix://$HOME/.colima/otel-metadata/docker.sock"
  test "$TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE" = /var/run/docker.sock
  test "${DOCKER_CONTEXT+x}" != x
else
  test "$DOCKER_HOST" = original-host
  test "$TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE" = original-socket
  test "$DOCKER_CONTEXT" = original-context
fi

case "$pass" in
  0)
    test "$1" = :instrumentation:shared:javaagent:test
    test "$2" = :instrumentation:shared:javaagent:testExperimental
    test "$3" = :instrumentation:standard:library:test
    test "$4" = :instrumentation:vertx:vertx-kafka-client-3.6:vertx-kafka-client-3.6-testing:test
    shift 4
    if [[ "$TEST_ARCH" == x86_64 ]]; then
      test "$1" = :instrumentation:shared:javaagent:testColima
      test "$2" = :instrumentation:colima:javaagent:test
      shift 2
      touch instrumentation/shared/.telemetry/colima instrumentation/colima/.telemetry/default
    fi
    [[ "$1" != :instrumentation:* ]]
    touch instrumentation/shared/.telemetry/default instrumentation/shared/.telemetry/experimental
    touch instrumentation/standard/.telemetry/default
    touch instrumentation/vertx/vertx-kafka-client-3.6/.telemetry/default
    ;;
  1)
    test "$1" = :instrumentation:shared:javaagent:testExperimental
    test "$2" = :instrumentation:latest:javaagent:test
    [[ "$3" != :instrumentation:* ]]
    [[ " $* " == *" -PtestLatestDeps=true "* ]]
    test -f instrumentation/shared/.telemetry/default
    test -f instrumentation/shared/.telemetry/experimental
    touch instrumentation/shared/.telemetry/latest instrumentation/latest/.telemetry/default
    ;;
  2)
    test "$TEST_ARCH" != x86_64
    test "$1" = :instrumentation:shared:javaagent:testColima
    test "$2" = :instrumentation:colima:javaagent:test
    [[ "$3" != :instrumentation:* ]]
    [[ " $* " != *" -PtestLatestDeps=true "* ]]
    test -f instrumentation/shared/.telemetry/default
    test -f instrumentation/shared/.telemetry/experimental
    test -f instrumentation/shared/.telemetry/latest
    if [[ "$SCENARIO" == gradle-failure ]]; then
      exit 42
    fi
    touch instrumentation/shared/.telemetry/colima instrumentation/colima/.telemetry/default
    ;;
  *) exit 1 ;;
esac
echo "$((pass + 1))" > pass
EOF

chmod +x "$fixture/gradlew" "$fixture/bin/"*
export FIXTURE="$fixture"
export PATH="$fixture/bin:$PATH"
export DOCKER_HOST=original-host
export DOCKER_CONTEXT=original-context
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=original-socket

for TEST_ARCH in x86_64 arm64 aarch64; do
  for SCENARIO in success unavailable wrong-arch gradle-failure; do
    if [[ "$TEST_ARCH" == x86_64 && "$SCENARIO" != success ]]; then
      continue
    fi
    export TEST_ARCH SCENARIO
    export DEBUG_LOGGING=false
    if [[ "$TEST_ARCH" == arm64 ]]; then
      export DEBUG_LOGGING=true
    fi
    rm -rf "$fixture/instrumentation"
    rm -f "$fixture/colima-calls"
    for module in shared standard latest colima vertx/vertx-kafka-client-3.6 unrelated; do
      mkdir -p "$fixture/instrumentation/$module/.telemetry"
      touch "$fixture/instrumentation/$module/metadata.yaml"
      touch "$fixture/instrumentation/$module/.telemetry/stale"
    done
    mkdir -p "$fixture/instrumentation/shared/javaagent" \
      "$fixture/instrumentation/standard/library" \
      "$fixture/instrumentation/latest/javaagent" \
      "$fixture/instrumentation/colima/javaagent" \
      "$fixture/instrumentation/vertx/vertx-kafka-client-3.6/vertx-kafka-client-3.6-testing"
    echo 0 > "$fixture/pass"
    echo "Testing $TEST_ARCH / $SCENARIO"
    status=0
    # Invoke the same entry point as CI, from outside the repository.
    "$fixture/instrumentation-docs/collect.sh" || status=$?
    if [[ "$SCENARIO" == success ]]; then
      test "$status" = 0
      expected_passes=3
      if [[ "$TEST_ARCH" == x86_64 ]]; then
        expected_passes=2
      fi
      test "$(cat "$fixture/pass")" = "$expected_passes"
      for profile in default experimental latest colima; do
        test -f "$fixture/instrumentation/shared/.telemetry/$profile"
      done
      for module in standard latest colima vertx/vertx-kafka-client-3.6; do
        test -f "$fixture/instrumentation/$module/.telemetry/default"
      done
    else
      test "$status" != 0
      if [[ "$SCENARIO" == gradle-failure ]]; then
        test "$status" = 42
        test -f "$fixture/instrumentation/shared/.telemetry/latest"
      else
        test "$(cat "$fixture/pass")" = 0
        test -f "$fixture/instrumentation/shared/.telemetry/stale"
      fi
    fi
    if [[ "$TEST_ARCH" == x86_64 ]]; then
      test ! -f "$fixture/colima-calls"
    else
      test "$(wc -l < "$fixture/colima-calls" | tr -d ' ')" = 1
    fi
    test -f "$fixture/instrumentation/unrelated/.telemetry/stale"
    test "$DOCKER_HOST" = original-host
    test "$DOCKER_CONTEXT" = original-context
    test "$TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE" = original-socket
  done
done
echo "Collector regressions passed."
