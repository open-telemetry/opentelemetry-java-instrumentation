#!/bin/bash -e

# this has mostly been replaced by .github/scripts/draft-release-notes/draft-release-notes.py
# keeping this around as a backup since that script relies on Copilot CLI

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
echo "### ⚠️ Breaking changes to non-stable APIs"
echo
echo "### 🚫 Deprecations"
echo
echo "### 🌟 New javaagent instrumentation"
echo
echo "### 🌟 New library instrumentation"
echo
echo "### 📈 Enhancements"
echo
echo "### 🛠️ Bug fixes"
echo

git log --reverse \
        --perl-regexp \
        --author='^(?!renovate\[bot\] )' \
        --pretty=format:"- %s" \
        "$range" \
  | sed -E 's,\(#([0-9]+)\)$,\n  ([#\1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/\1)),'
