#!/bin/bash -e

# This script extracts PRs with "breaking change" and "deprecation" labels for the given version range
# Usage: extract-labeled-prs.sh [git-range]
# If no range is provided, it uses HEAD

range="${1:-HEAD}"

if [[ "$range" == "HEAD" ]]; then
  # Get all commits from HEAD
  commits=$(git log --reverse --pretty=format:"%H %s" HEAD)
else
  # Get commits in the specified range
  commits=$(git log --reverse --pretty=format:"%H %s" "$range")
fi

# Initialize tracking variables
breaking_changes=""
deprecations=""
breaking_changes_found=false
deprecations_found=false

# Process each commit to find PRs with specified labels
while IFS= read -r line; do
  if [[ -z "$line" ]]; then
    continue
  fi

  # Extract PR number from commit message
  if [[ $line =~ \(#([0-9]+)\)$ ]]; then
    pr_number="${BASH_REMATCH[1]}"
    commit_subject=$(echo "$line" | cut -d' ' -f2- | sed 's/ (#[0-9]*)$//')

    # Get PR labels using GitHub CLI
    if pr_labels=$(gh pr view "$pr_number" --json labels --jq '.labels[].name' 2>/dev/null); then
      # Check for breaking change label
      if echo "$pr_labels" | grep -q "^breaking change$"; then
        breaking_changes_found=true
        breaking_changes+="- $commit_subject"$'\n'"  ([#$pr_number](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/$pr_number))"$'\n'$'\n'
      fi

      # Check for deprecation label
      if echo "$pr_labels" | grep -q "^deprecation$"; then
        deprecations_found=true
        deprecations+="- $commit_subject"$'\n'"  ([#$pr_number](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/$pr_number))"$'\n'$'\n'
      fi
    fi
  fi
done <<< "$commits"

# Output breaking changes section
if [[ "$breaking_changes_found" == "true" ]]; then
  echo "## ⚠️ Breaking Changes"
  echo
  echo -n "$breaking_changes"
fi

# Output deprecations section
if [[ "$deprecations_found" == "true" ]]; then
  echo "## 🚫 Deprecations"
  echo
  echo -n "$deprecations"
fi

# Output "no changes" messages if needed
if [[ "$breaking_changes_found" == "false" ]]; then
  echo "## ⚠️ Breaking Changes"
  echo
  echo "*No breaking changes in this release.*"
  echo
fi

if [[ "$deprecations_found" == "false" ]]; then
  echo "## 🚫 Deprecations"
  echo
  echo "*No deprecations in this release.*"
  echo
fi
