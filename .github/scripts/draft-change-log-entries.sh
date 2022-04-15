#!/bin/bash -e

version=$(grep -Eo "[0-9]+.[0-9]+.0" version.gradle.kts | head -1)

if [[ $version =~ ([0-9]+).([0-9]+).0 ]]; then
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
else
  echo "unexpected version: $version"
  exit 1
fi

if [[ $minor == 0 ]]; then
  prior_major=$((major - 1))
  prior_minor=$(grep -Po "^## Version $prior_major.\K([0-9]+)" CHANGELOG.md  | head -1)
  prior_version="$prior_major.$prior_minor"
else
  prior_version="$major.$((minor - 1)).0"
fi

git log --reverse --pretty=format:"- %s" "v$prior_version"..HEAD \
  | sed -r 's,\(#([0-9]+)\),\n  ([#\1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/\1)),'
