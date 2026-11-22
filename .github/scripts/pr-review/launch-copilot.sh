#!/usr/bin/env bash
set -euo pipefail

fail() { echo "Sandbox check failed: $1" >&2; exit 1; }

checkout=$1
inputs=$2
host_config=$3
expected_head=$4
shift 4
test ! -S /var/run/docker.sock || fail 'Docker socket is visible'
test -z "${GH_TOKEN:-}${GITHUB_TOKEN:-}${ACTIONS_RUNTIME_TOKEN:-}${ACTIONS_ID_TOKEN_REQUEST_TOKEN:-}" ||
  fail 'Actions credential is visible'
test "${COPILOT_GITHUB_TOKEN:-}" = ghu_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ||
  fail 'Copilot proxy token not isolated'
test ! -e "$host_config" || fail 'Host configuration is visible'
test -w "$checkout" && test "$(git -C "$checkout" rev-parse HEAD)" = "$expected_head" ||
  fail 'Expected PR checkout is not available'
test ! -w "$inputs" && test -r "$inputs/prompt.txt" || fail 'Review inputs are not read-only'
"$JAVA_HOME/bin/java" -version >/dev/null 2>&1 || fail 'JDK is not usable'
export PATH="$JAVA_HOME/bin:$PATH"
echo '[review] Verified sandbox checkout, JDK, and credential isolation' >&2

# Start Copilot outside the PR checkout so it does not load PR-supplied configuration.
exec "$@" -C "$inputs" -p "$(cat "$inputs/prompt.txt")

PR checkout: $checkout"
