#!/bin/bash -e

version=$("$(dirname "$0")/get-version.sh")

if [[ $version =~ ([0-9]+)\.([0-9]+)\.0 ]]; then
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
else
  echo "unexpected version: $version"
  exit 1
fi

if [[ $minor == 0 ]]; then
  prior_major=$((major - 1))
  prior_minor=$(sed -n "s/^## Version $prior_major\.\([0-9]\+\)\..*/\1/p" CHANGELOG.md | head -1)
  if [[ -z $prior_minor ]]; then
    # assuming this is the first release
    range=HEAD
  else
    range="v$prior_major.$prior_minor.0..HEAD"
  fi
else
  range="v$major.$((minor - 1)).0..HEAD"
fi

echo "# Changelog"
echo
echo "## Unreleased"
echo

"$(dirname "$0")/extract-labeled-prs.sh" "$range"

echo "### 🌟 New javaagent instrumentation"
echo
echo "### 🌟 New library instrumentation"
echo
echo "### 📈 Enhancements"
echo
echo "### 🛠️ Bug fixes"
echo
echo "### 🧰 Tooling"
echo

# Group commits by file type and @Deprecated detection
declare -A src_main_commits
declare -A no_src_main_commits
declare -A deprecated_added_commits
declare -A deprecated_removed_commits

format_commit_msg() {
  git log --format=%s -n 1 "$1" | sed -E 's, *\(#([0-9]+)\)$, ([#\1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/\1)),'
}

while IFS= read -r commit_hash; do
  files=$(git diff-tree --no-commit-id --name-only -r "$commit_hash")

  has_src_main=false

  while IFS= read -r file; do
    if [[ $file =~ /src/main/ ]] && [[ ! $file =~ ^smoke-tests/ ]] && [[ ! $file =~ ^smoke-tests-otel-starter/ ]] && [[ ! $file =~ /testing/ ]] && [[ ! $file =~ -testing/ ]] && [[ ! $file =~ ^instrumentation-docs/ ]]; then
      has_src_main=true
      break
    fi
  done <<< "$files"

  commit_msg=$(git log --format=%s -n 1 "$commit_hash" | sed -E 's, *\(#([0-9]+)\)$,\n  ([#\1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/\1)),')

  # Check diff for @Deprecated additions/removals in src/main Java files
  # Count added vs removed to distinguish real changes from moves/refactors
  diff_output=$(git diff-tree -p "$commit_hash" -- '*/src/main/**/*.java' 2>/dev/null || true)

  added_count=$(echo "$diff_output" | grep -cP '^\+(?!\+).*@Deprecated' || true)
  removed_count=$(echo "$diff_output" | grep -cP '^-(?!-).*@Deprecated' || true)

  # Net new @Deprecated annotations = new deprecation
  if [[ $added_count -gt $removed_count ]]; then
    deprecated_added_commits["$commit_hash"]=1
  fi
  # Net removed @Deprecated annotations = removed deprecated API (breaking)
  if [[ $removed_count -gt $added_count ]]; then
    deprecated_removed_commits["$commit_hash"]=1
  fi

  # Categorize commit
  if [[ $has_src_main == true ]]; then
    src_main_commits["$commit_hash"]="$commit_msg"
  else
    no_src_main_commits["$commit_hash"]="$commit_msg"
  fi
done < <(git log --reverse --perl-regexp --author='^(?!renovate\[bot\] )' --pretty=format:"%H" "$range")

# Output @Deprecated-based breaking changes (removed @Deprecated = removed deprecated API)
if [[ ${#deprecated_removed_commits[@]} -gt 0 ]]; then
  echo "#### Possible breaking changes (diff removes @Deprecated)"
  echo
  for commit_hash in $(git log --reverse --perl-regexp --author='^(?!renovate\[bot\] )' --pretty=format:"%H" "$range"); do
    if [[ -n ${deprecated_removed_commits[$commit_hash]} ]]; then
      echo "- $(format_commit_msg "$commit_hash")"
    fi
  done
  echo
fi

# Output @Deprecated-based deprecations (added @Deprecated = new deprecation)
if [[ ${#deprecated_added_commits[@]} -gt 0 ]]; then
  echo "#### Possible deprecations (diff adds @Deprecated)"
  echo
  for commit_hash in $(git log --reverse --perl-regexp --author='^(?!renovate\[bot\] )' --pretty=format:"%H" "$range"); do
    if [[ -n ${deprecated_added_commits[$commit_hash]} ]]; then
      echo "- $(format_commit_msg "$commit_hash")"
    fi
  done
  echo
fi

# Output grouped commits
if [[ ${#src_main_commits[@]} -gt 0 ]]; then
  echo "#### Changes with src/main updates"
  echo
  for commit_hash in $(git log --reverse --perl-regexp --author='^(?!renovate\[bot\] )' --pretty=format:"%H" "$range"); do
    if [[ -n ${src_main_commits[$commit_hash]} ]] && [[ -z ${deprecated_added_commits[$commit_hash]} ]] && [[ -z ${deprecated_removed_commits[$commit_hash]} ]]; then
      echo "- $(format_commit_msg "$commit_hash")"
    fi
  done
  echo
fi

if [[ ${#no_src_main_commits[@]} -gt 0 ]]; then
  echo "#### Changes without src/main updates"
  echo
  for commit_hash in $(git log --reverse --perl-regexp --author='^(?!renovate\[bot\] )' --pretty=format:"%H" "$range"); do
    if [[ -n ${no_src_main_commits[$commit_hash]} ]] && [[ -z ${deprecated_added_commits[$commit_hash]} ]] && [[ -z ${deprecated_removed_commits[$commit_hash]} ]]; then
      echo "- $(format_commit_msg "$commit_hash")"
    fi
  done
  echo
fi
