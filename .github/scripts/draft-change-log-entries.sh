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
    range=
  else
    range="v$prior_major.$prior_minor.0..HEAD"
  fi
else
  range="v$major.$((minor - 1)).0..HEAD"
fi

echo "## Version $version (Unreleased)"
echo

git log --reverse --pretty=format:"- %s" $range \
  | sed -E 's,\(#([0-9]+)\),\n  ([#\1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/\1)),'
