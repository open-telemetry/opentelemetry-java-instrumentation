#!/bin/bash -e

# This script extracts PRs with "breaking change" label for the given version range
# Usage: extract-breaking-changes.sh [git-range]
# If no range is provided, it uses HEAD

range="${1:-HEAD}"

if [[ "$range" == "HEAD" ]]; then
  # Get all commits from HEAD
  commits=$(git log --reverse --pretty=format:"%H %s" HEAD)
else
  # Get commits in the specified range
  commits=$(git log --reverse --pretty=format:"%H %s" "$range")
fi

echo "### ⚠️ Breaking Changes"
echo

breaking_changes_found=false

# Process each commit to find PRs with breaking change labels
while IFS= read -r line; do
  if [[ -z "$line" ]]; then
    continue
  fi

  # Extract PR number from commit message
  if [[ $line =~ \(#([0-9]+)\)$ ]]; then
    pr_number="${BASH_REMATCH[1]}"
    commit_subject=$(echo "$line" | cut -d' ' -f2- | sed 's/ (#[0-9]*)$//')

    # Check if this PR has the breaking change label using GitHub CLI
    if gh pr view "$pr_number" --json labels --jq '.labels[].name' 2>/dev/null | grep -q "breaking change"; then
      breaking_changes_found=true
      echo "- $commit_subject"
      echo "  ([#$pr_number](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/$pr_number))"
      echo
    fi
  fi
done <<< "$commits"

if [[ "$breaking_changes_found" == "false" ]]; then
  echo "*No breaking changes in this release.*"
  echo
fi
