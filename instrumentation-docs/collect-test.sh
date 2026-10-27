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

for module in shared standard latest colima unrelated; do
  mkdir -p "$fixture/instrumentation/$module/.telemetry"
  touch "$fixture/instrumentation/$module/.telemetry/stale"
done

cat > "$fixture/bin/colima" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

cat > "$fixture/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

for module in shared standard latest colima; do
  test ! -f "instrumentation/$module/.telemetry/stale"
  mkdir -p "instrumentation/$module/.telemetry"
done
test -f instrumentation/unrelated/.telemetry/stale

pass=$(cat pass)
case "$pass" in
  0)
    test "$1" = :instrumentation:shared:javaagent:test
    test "$2" = :instrumentation:shared:javaagent:testExperimental
    test "$3" = :instrumentation:standard:library:test
    touch instrumentation/shared/.telemetry/default
    touch instrumentation/shared/.telemetry/experimental
    touch instrumentation/standard/.telemetry/default
    ;;
  1)
    test "$1" = :instrumentation:shared:javaagent:testExperimental
    test "$2" = :instrumentation:latest:javaagent:test
    [[ " $* " == *" -PtestLatestDeps=true "* ]]
    test -f instrumentation/shared/.telemetry/default
    test -f instrumentation/shared/.telemetry/experimental
    touch instrumentation/shared/.telemetry/latest
    touch instrumentation/latest/.telemetry/default
    ;;
  2)
    test "$1" = :instrumentation:shared:javaagent:testColima
    test "$2" = :instrumentation:colima:javaagent:test
    test -f instrumentation/shared/.telemetry/default
    test -f instrumentation/shared/.telemetry/experimental
    test -f instrumentation/shared/.telemetry/latest
    touch instrumentation/shared/.telemetry/colima
    touch instrumentation/colima/.telemetry/default
    ;;
  *) exit 1 ;;
esac
echo "$((pass + 1))" > pass
EOF

chmod +x "$fixture/gradlew" "$fixture/bin/colima"
cd "$fixture"
echo 0 > pass
PATH="$fixture/bin:$PATH" bash instrumentation-docs/collect.sh

test "$(cat pass)" = 3
for profile in default experimental latest colima; do
  test -f "instrumentation/shared/.telemetry/$profile"
done
for module in standard latest colima; do
  test -f "instrumentation/$module/.telemetry/default"
done
test -f instrumentation/unrelated/.telemetry/stale
echo "Collector regression passed."
