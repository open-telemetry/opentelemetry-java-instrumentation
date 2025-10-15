#!/bin/bash -e

# This script extracts PRs with "breaking change" and "deprecation" labels for the given version range
# Usage: extract-labeled-prs.sh [git-range]
# If no range is provided, it uses HEAD

range="${1:-HEAD}"

# Get the date range for filtering
if [[ "$range" == "HEAD" ]]; then
  # Get all commits from HEAD
  oldest_commit=$(git log --reverse --pretty=format:"%H" HEAD | head -1)
  since_date=$(git show -s --format=%ci "$oldest_commit" | cut -d' ' -f1)
else
  # Get commits in the specified range
  if [[ $range =~ ^(.+)\.\.(.+)$ ]]; then
    from_ref="${BASH_REMATCH[1]}"
    oldest_commit=$(git rev-parse "$from_ref")
    since_date=$(git show -s --format=%ci "$oldest_commit" | cut -d' ' -f1)
  else
    echo "[ERROR] Invalid range format: $range" >&2
    exit 1
  fi
fi

# Initialize tracking variables
breaking_changes=""
deprecations=""
breaking_changes_found=false
deprecations_found=false

# Get PRs with "breaking change" label using GitHub search
breaking_prs=$(gh pr list \
  --repo open-telemetry/opentelemetry-java-instrumentation \
  --label "breaking change" \
  --state merged \
  --search "merged:>=$since_date" \
  --json number,title \
  --jq '.[] | "\(.number)|\(.title)"' 2>/dev/null || echo "")

if [[ -n "$breaking_prs" ]]; then
  breaking_changes_found=true
  while IFS='|' read -r pr_number pr_title; do
    breaking_changes+="- $pr_title"$'\n'"  ([#$pr_number](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/$pr_number))"$'\n'
  done <<< "$breaking_prs"
fi

# Get PRs with "deprecation" label using GitHub search
deprecation_prs=$(gh pr list \
  --repo open-telemetry/opentelemetry-java-instrumentation \
  --label "deprecation" \
  --state merged \
  --search "merged:>=$since_date" \
  --json number,title \
  --jq '.[] | "\(.number)|\(.title)"' 2>/dev/null || echo "")

if [[ -n "$deprecation_prs" ]]; then
  deprecations_found=true
  while IFS='|' read -r pr_number pr_title; do
    deprecations+="- $pr_title"$'\n'"  ([#$pr_number](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/$pr_number))"$'\n'
  done <<< "$deprecation_prs"
fi

# Output breaking changes section
if [[ "$breaking_changes_found" == "true" ]]; then
  echo "### ⚠️ Breaking Changes"
  echo
  echo -n "$breaking_changes"
  echo
fi

# Output deprecations section
if [[ "$deprecations_found" == "true" ]]; then
  echo "### 🚫 Deprecations"
  echo
  echo -n "$deprecations"
  echo
fi
