#!/bin/bash
# Determines which smoke test suites need to run based on directly changed instrumentation modules.
#
# Usage: .github/scripts/determine-affected-smoke-tests.sh <changed-modules-file>
#
# Reads a file with one changed module path per line (e.g. :instrumentation:servlet:servlet-3.0:javaagent)
# and outputs a JSON array of smoke test suites to run (e.g. ["jetty","tomcat"]).
# Only directly changed modules are considered (not reverse dependencies) because
# reverse deps are already covered by instrumentation test filtering.

set -euo pipefail

AFFECTED_FILE="${1:?Usage: $0 <affected-modules-file>}"
REPO_ROOT="$(git rev-parse --show-toplevel)"
MAPPING_FILE="$REPO_ROOT/.github/config/smoke-test-suite-mapping.txt"

ALL_SUITES='["jetty","liberty","payara","tomcat","tomee","websphere","wildfly","other"]'

if [ ! -f "$MAPPING_FILE" ]; then
  echo "$ALL_SUITES"
  exit 0
fi

if [ ! -f "$AFFECTED_FILE" ]; then
  echo "$ALL_SUITES"
  exit 0
fi

# Read mapping into arrays
declare -a map_prefixes=()
declare -a map_suites=()
while IFS= read -r line; do
  [[ "$line" =~ ^#.*$ || -z "$line" ]] && continue
  prefix=$(echo "$line" | awk '{print $1}')
  suites=$(echo "$line" | awk '{print $2}')
  map_prefixes+=("$prefix")
  map_suites+=("$suites")
done < "$MAPPING_FILE"

# For each affected module, find matching suites
declare -A needed_suites=()


while IFS= read -r module; do
  [ -z "$module" ] && continue

  # Extract the instrumentation path: :instrumentation:foo:bar:javaagent → foo/bar
  instr_path=$(echo "$module" | sed 's/^:instrumentation://; s/:/\//g')

  matched=false
  for i in "${!map_prefixes[@]}"; do
    prefix="${map_prefixes[$i]}"
    if [[ "$instr_path" == ${prefix}* ]]; then
      # Split comma-separated suites
      IFS=',' read -ra suites <<< "${map_suites[$i]}"
      for suite in "${suites[@]}"; do
        needed_suites[$suite]=1
      done
      matched=true
      break
    fi
  done

  # Unmatched modules are instrumentations not exercised by any smoke test — skip them
done < "$AFFECTED_FILE"

if [ "${#needed_suites[@]}" -eq 0 ]; then
  echo "No affected smoke test suites" >&2
  echo "[]"
  exit 0
fi

# Build JSON array
json="["
first=true
for suite in "${!needed_suites[@]}"; do
  if [ "$first" = true ]; then
    first=false
  else
    json+=","
  fi
  json+="\"$suite\""
done
json+="]"

echo "Affected smoke test suites: $json" >&2
echo "$json"
