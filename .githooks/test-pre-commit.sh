#!/bin/bash
# Exercise the pre-commit formatter status contract without running Gradle or
# Flint. Run this from the repository root with:
#
#   .githooks/test-pre-commit.sh
#
# The table below is the contract implemented by .githooks/pre-commit:
#
#   Flint       Spotless             expected status
#   clean       clean                0
#   clean       changed              1
#   clean       failed (status 7)    7
#   fixed (1)   clean                1
#   fixed (1)   changed              1
#   fixed (1)   failed (status 7)    1
#   failed (7)  clean                7
#   failed (7)  changed              7
#   failed (7)  failed (status 3)    7
#   no relevant files (Flint 1)      1 (Spotless skipped)
#   changed-files discovery failure  1 (both formatters skipped)
#
# Spotless must still run when Flint returns nonzero, but Flint's status has
# precedence. Flint's strict fix mode is invoked exactly once.
set -euo pipefail
IFS=$'\n\t'

hook=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/pre-commit

run_case() {
  local name=$1
  local changed_files_status=$2
  local flint_status=$3
  local spotless_status=$4
  local spotless_changed=$5
  local spotless_relevant=$6
  local expected=$7
  local expected_flint_calls=$8
  local expected_spotless_calls=$9

  local repo
  repo=$(mktemp -d "${TMPDIR:-/tmp}/otel-pre-commit-test.XXXXXX")
  trap 'rm -rf "$repo"' RETURN

  mkdir -p "$repo/bin"
  cat >"$repo/bin/mise" <<'EOF'
#!/bin/bash
set -euo pipefail
if [[ "$*" == "exec -- flint changed-files --null" ]]; then
  printf 'changed-files\n' >>"$RUN_LOG"
  if [[ "${CHANGED_FILES_STATUS:?}" -ne 0 ]]; then
    exit "$CHANGED_FILES_STATUS"
  fi
  if [[ "${SPOTLESS_RELEVANT:-1}" == 1 ]]; then
    printf 'file.scala\0'
  fi
  exit 0
fi
if [[ "$*" == "run lint:fix" ]]; then
  printf 'flint\n' >>"$RUN_LOG"
  if [[ "${FLINT_CHANGED:-0}" == 1 ]]; then
    printf 'flint changed\n' >file.java
  fi
  exit "${FLINT_STATUS:?}"
fi
printf 'unexpected mise invocation: %s\n' "$*" >&2
exit 99
EOF
  cat >"$repo/gradlew" <<'EOF'
#!/bin/bash
set -euo pipefail
if [[ "$1" != spotlessApply ]]; then
  printf 'unexpected Gradle invocation: %s\n' "$*" >&2
  exit 99
fi
printf 'spotless\n' >>"$RUN_LOG"
if [[ "${SPOTLESS_CHANGED:-0}" == 1 ]]; then
  printf 'spotless changed\n' >file.scala
fi
exit "${SPOTLESS_STATUS:?}"
EOF
  chmod +x "$repo/bin/mise" "$repo/gradlew"

  git -C "$repo" init -q
  git -C "$repo" config user.email test@example.com
  git -C "$repo" config user.name test
  printf 'original scala\n' >"$repo/file.scala"
  printf 'original java\n' >"$repo/file.java"
  git -C "$repo" add file.scala file.java
  git -C "$repo" commit -qm initial

  local actual
  set +e
  (
    cd "$repo"
    PATH="$repo/bin:$PATH" \
      FLINT_STATUS="$flint_status" \
      FLINT_CHANGED="$([[ "$flint_status" == 1 ]] && echo 1 || echo 0)" \
      SPOTLESS_STATUS="$spotless_status" \
      SPOTLESS_CHANGED="$spotless_changed" \
      SPOTLESS_RELEVANT="$spotless_relevant" \
      CHANGED_FILES_STATUS="$changed_files_status" \
      RUN_LOG="$repo/run.log" \
      "$hook"
  )
  actual=$?
  set -e

  if [[ "$actual" -ne "$expected" ]]; then
    printf '%s: expected status %s, got %s\n' "$name" "$expected" "$actual" >&2
    return 1
  fi

  local changed_files_calls flint_calls spotless_calls
  changed_files_calls=$(grep -c '^changed-files$' "$repo/run.log" 2>/dev/null || true)
  flint_calls=$(grep -c '^flint$' "$repo/run.log" || true)
  spotless_calls=$(grep -c '^spotless$' "$repo/run.log" || true)
  if [[ "$changed_files_calls" -ne 1 ]]; then
    printf '%s: expected one changed-files invocation, got %s\n' "$name" "$changed_files_calls" >&2
    return 1
  fi
  if [[ "$flint_calls" -ne "$expected_flint_calls" ]]; then
    printf '%s: expected %s Flint fix invocations, got %s\n' "$name" "$expected_flint_calls" "$flint_calls" >&2
    return 1
  fi
  if [[ "$spotless_calls" -ne "$expected_spotless_calls" ]]; then
    printf '%s: expected %s Spotless invocations, got %s\n' "$name" "$expected_spotless_calls" "$spotless_calls" >&2
    return 1
  fi
  printf 'ok: %s\n' "$name"
}

run_case 'clean/clean' 0 0 0 0 1 0 1 1
run_case 'clean/spotless-changed' 0 0 0 1 1 1 1 1
run_case 'clean/spotless-failed' 0 0 7 0 1 7 1 1
run_case 'clean/spotless-failed-after-change' 0 0 7 1 1 7 1 1
run_case 'flint-fixed/clean' 0 1 0 0 1 1 1 1
run_case 'flint-fixed/spotless-changed' 0 1 0 1 1 1 1 1
run_case 'flint-fixed/spotless-failed' 0 1 7 0 1 1 1 1
run_case 'flint-failed/clean' 0 7 0 0 1 7 1 1
run_case 'flint-failed/spotless-changed' 0 7 0 1 1 7 1 1
run_case 'flint-failed/spotless-failed' 0 7 3 0 1 7 1 1
run_case 'no-relevant-files' 0 1 0 0 0 1 1 0
run_case 'changed-files-failed' 7 0 0 0 1 1 0 0
