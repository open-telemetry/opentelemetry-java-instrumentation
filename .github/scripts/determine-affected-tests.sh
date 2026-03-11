#!/bin/bash
# Determines which test tasks need to run based on changed files.
#
# Usage: .github/scripts/determine-affected-tests.sh <base-ref> <test-tasks-file>
#
# Reads test-tasks-file (produced by listTestsInPartition) and writes
# affected-test-tasks.txt containing only the tasks that need to run.
#
# Outputs to stdout one of:
#   FULL_BUILD  - run all tests (core module or unclassified change)
#   SKIP_TESTS  - no tests needed (docs-only changes)
#   TARGETED    - only affected-test-tasks.txt tasks need to run

set -euo pipefail

BASE_REF="${1:?Usage: $0 <base-ref> <test-tasks-file>}"
TEST_TASKS_FILE="${2:?Usage: $0 <base-ref> <test-tasks-file>}"
REPO_ROOT="$(git rev-parse --show-toplevel)"

# --- Step 1: Get changed files ---

changed_files=$(git diff --name-only "$BASE_REF"...HEAD 2>/dev/null \
  || git diff --name-only "$BASE_REF" HEAD)

if [ -z "$changed_files" ]; then
  echo "SKIP_TESTS"
  exit 0
fi

# --- Step 2: Classify changes ---

# Top-level dirs/files whose changes affect all instrumentation tests.
# These are modules depended on (directly or via convention plugins) by all instrumentations.
full_build_patterns=(
  "^instrumentation-api/"
  "^instrumentation-api-incubator/"
  "^instrumentation-annotations/"
  "^instrumentation-annotations-support/"
  "^instrumentation-annotations-support-testing/"
  "^javaagent/"
  "^javaagent-bootstrap/"
  "^javaagent-extension-api/"
  "^javaagent-internal-logging-application/"
  "^javaagent-internal-logging-simple/"
  "^javaagent-tooling/"
  "^muzzle/"
  "^testing-common/"
  "^testing/"
  "^conventions/"
  "^custom-checks/"
  "^dependencyManagement/"
  "^buildscripts/"
  "^opentelemetry-.*-shaded-for-instrumenting/"
  "^sdk-autoconfigure-support/"
  "^declarative-config-bridge/"
  "^settings\.gradle\.kts$"
  "^build\.gradle\.kts$"
  "^gradle/"
  "^gradle\.properties$"
  "^gradlew"
  "^\.java-version$"
)

combined_pattern=$(IFS='|'; echo "${full_build_patterns[*]}")
if echo "$changed_files" | grep -qE "$combined_pattern"; then
  echo "FULL_BUILD"
  exit 0
fi

# Files/dirs that don't affect instrumentation tests.
# These either have their own CI jobs or are purely non-code.
non_instrumentation_test_patterns=(
  '\.(md|txt)$'             # documentation
  '/metadata\.yaml$'        # instrumentation metadata (not build config)
  '^docs/'                  # documentation
  '^LICENSE'                # license
  '^\.gitignore$'           # git config
  '^\.github/'              # CI workflows, scripts, configs
  '^\.gitattributes$'       # git config
  '^\.editorconfig$'        # editor config
  '^licenses/'              # license files
  '^\.fossa\.yml$'          # FOSSA config
  '^instrumentation-docs/'  # instrumentation documentation
  '^examples/'              # examples (have their own CI job)
  '^smoke-tests/'           # smoke tests (have their own CI job)
  '^smoke-tests-otel-starter/' # smoke tests (have their own CI job)
  '^benchmark-'             # benchmarks (no CI tests)
  '^bom/'                   # BOM (no instrumentation tests)
  '^bom-alpha/'             # BOM (no instrumentation tests)
  '^gradle-plugins/'        # Gradle plugins (have their own CI job)
  '^test-report/'           # test reporting infra
)

non_test_pattern=$(IFS='|'; echo "${non_instrumentation_test_patterns[*]}")
non_doc_files=$(echo "$changed_files" | grep -vE "$non_test_pattern" || true)
if [ -z "$non_doc_files" ]; then
  echo "SKIP_TESTS"
  exit 0
fi

# Everything remaining must be under instrumentation/ — otherwise full build
unclassified=$(echo "$non_doc_files" | grep -v '^instrumentation/' || true)
if [ -n "$unclassified" ]; then
  echo "Unclassified files trigger full build:" >&2
  echo "$unclassified" >&2
  echo "FULL_BUILD"
  exit 0
fi

# --- Step 3: Map changed files to Gradle module paths ---

changed_modules=""
while IFS= read -r file; do
  dir=$(dirname "$file")
  # Walk up to the nearest build.gradle.kts
  while [ "$dir" != "." ] && [ ! -f "$REPO_ROOT/$dir/build.gradle.kts" ]; do
    dir=$(dirname "$dir")
  done
  if [ "$dir" != "." ]; then
    module=":${dir//\//:}"
    changed_modules="$changed_modules"$'\n'"$module"
  fi
done <<< "$non_doc_files"

changed_modules=$(echo "$changed_modules" | sort -u | grep -v '^$')

if [ -z "$changed_modules" ]; then
  echo "SKIP_TESTS"
  exit 0
fi

# If any file resolved to the umbrella :instrumentation module (e.g. a file in
# instrumentation/ with no submodule build.gradle.kts), fall back to full build.
if echo "$changed_modules" | grep -qx ':instrumentation'; then
  echo "File resolved to umbrella :instrumentation module, falling back to full build" >&2
  echo "FULL_BUILD"
  exit 0
fi

# --- Step 4: Build reverse dependency graph ---
# Parse all project() references in instrumentation build files.
# For each edge "A depends on B", store B -> A in the reverse graph.

declare -A reverse_graph

while IFS= read -r build_file; do
  dir=$(dirname "$build_file")
  rel_dir="${dir#$REPO_ROOT/}"
  source_module=":${rel_dir//\//:}"

  # Extract project(":path") and project(path = ":path") references
  dep_modules=$(
    grep -oP 'project\(\s*"(:[^"]+)"' "$build_file" 2>/dev/null | grep -oP '":[^"]+' | tr -d '"' || true
    grep -oP 'project\(path\s*=\s*"(:[^"]+)"' "$build_file" 2>/dev/null | grep -oP '":[^"]+' | tr -d '"' || true
  )

  while IFS= read -r dep_module; do
    [ -z "$dep_module" ] && continue
    existing="${reverse_graph[$dep_module]:-}"
    if [ -z "$existing" ]; then
      reverse_graph[$dep_module]="$source_module"
    else
      reverse_graph[$dep_module]="$existing"$'\n'"$source_module"
    fi
  done <<< "$dep_modules"
done < <(find "$REPO_ROOT/instrumentation" -name 'build.gradle.kts' -type f)

# --- Step 5: Compute reverse dependency closure (BFS) ---

declare -A affected
queue=()

while IFS= read -r mod; do
  [ -z "$mod" ] && continue
  affected[$mod]=1
  queue+=("$mod")
done <<< "$changed_modules"

while [ ${#queue[@]} -gt 0 ]; do
  current="${queue[0]}"
  queue=("${queue[@]:1}")

  dependents="${reverse_graph[$current]:-}"
  [ -z "$dependents" ] && continue

  while IFS= read -r dep; do
    [ -z "$dep" ] && continue
    if [ -z "${affected[$dep]:-}" ]; then
      affected[$dep]=1
      queue+=("$dep")
    fi
  done <<< "$dependents"
done

# --- Step 6: Filter test tasks ---

if [ ! -e "$TEST_TASKS_FILE" ] || [ "$TEST_TASKS_FILE" = "/dev/null" ]; then
  # When called without a real test tasks file (e.g. from determine-affected job),
  # just output the build scope and affected modules without filtering.
  affected_modules_file="affected-modules.txt"
  > "$affected_modules_file"
  for mod in "${!affected[@]}"; do
    echo "$mod" >> "$affected_modules_file"
  done
  # Write directly changed modules (without reverse deps) for smoke test mapping
  echo "$changed_modules" > "changed-modules.txt"
  echo "TARGETED"
  exit 0
fi

# A test task like :instrumentation:foo:bar:javaagent:test belongs to
# module :instrumentation:foo:bar:javaagent.
# Match if the task's module is in the affected set.
output_file="affected-test-tasks.txt"
> "$output_file"

while IFS= read -r task; do
  [ -z "$task" ] && continue
  # Strip the task name (last segment after final :)
  module="${task%:*}"

  if [ -n "${affected[$module]:-}" ]; then
    echo "$task" >> "$output_file"
  fi
done < "$TEST_TASKS_FILE"

affected_count=$(wc -l < "$output_file")
total_count=$(wc -l < "$TEST_TASKS_FILE")
echo "Affected: $affected_count / $total_count test tasks" >&2
echo "Changed modules:" >&2
echo "$changed_modules" >&2
echo "Affected modules (with reverse deps):" >&2
affected_modules_file="affected-modules.txt"
> "$affected_modules_file"
for mod in "${!affected[@]}"; do
  echo "  $mod" >&2
  echo "$mod" >> "$affected_modules_file"
done
# Write directly changed modules (without reverse deps) for smoke test mapping
echo "$changed_modules" > "changed-modules.txt"

echo "TARGETED"
