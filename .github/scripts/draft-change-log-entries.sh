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

# Group commits by file type
declare -A src_main_commits
declare -A no_src_main_commits

while IFS= read -r commit_hash; do
  files=$(git diff-tree --no-commit-id --name-only -r "$commit_hash")

  has_src_main=false

  while IFS= read -r file; do
    if [[ $file =~ /src/main/ ]] && [[ ! $file =~ ^smoke-tests/ ]] && [[ ! $file =~ ^smoke-tests-otel-starter/ ]] && [[ ! $file =~ /testing/ ]]; then
      has_src_main=true
      break
    fi
  done <<< "$files"

  commit_msg=$(git log --format=%s -n 1 "$commit_hash" | sed -E 's, *\(#([0-9]+)\)$,\n  ([#\1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/\1)),')

  # Categorize commit
  if [[ $has_src_main == true ]]; then
    src_main_commits["$commit_hash"]="$commit_msg"
  else
    no_src_main_commits["$commit_hash"]="$commit_msg"
  fi
done < <(git log --reverse --perl-regexp --author='^(?!renovate\[bot\] )' --pretty=format:"%H" "$range")

# Output grouped commits
if [[ ${#src_main_commits[@]} -gt 0 ]]; then
  echo "#### Changes with src/main updates"
  echo
  for commit_hash in $(git log --reverse --perl-regexp --author='^(?!renovate\[bot\] )' --pretty=format:"%H" "$range"); do
    if [[ -n ${src_main_commits[$commit_hash]} ]]; then
      echo "- $(git log --format=%s -n 1 "$commit_hash" | sed -E 's, *\(#([0-9]+)\)$, ([#\1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/\1)),')"
    fi
  done
  echo
fi

if [[ ${#no_src_main_commits[@]} -gt 0 ]]; then
  echo "#### Changes without src/main updates"
  echo
  for commit_hash in $(git log --reverse --perl-regexp --author='^(?!renovate\[bot\] )' --pretty=format:"%H" "$range"); do
    if [[ -n ${no_src_main_commits[$commit_hash]} ]]; then
      echo "- $(git log --format=%s -n 1 "$commit_hash" | sed -E 's, *\(#([0-9]+)\)$, ([#\1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/\1)),')"
    fi
  done
  echo
fi
