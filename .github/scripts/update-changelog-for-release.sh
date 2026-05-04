#!/bin/bash -e

# Updates CHANGELOG.md in place for a release by replacing the `## Unreleased`
# heading with a new `## Version X.Y.Z (YYYY-MM-DD)` section followed by the
# standard preamble (SDK version and `-alpha` notice).
#
# Usage:
#   update-changelog-for-release.sh <version> <date> [--keep-unreleased-section]
#
# With --keep-unreleased-section, the `## Unreleased` heading is preserved above
# the new version section (used when updating CHANGELOG.md on `main` after
# cutting a release branch).

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <version> <date> [--keep-unreleased-section]" >&2
  exit 1
fi

version=$1
date=$2
keep_unreleased_section=false
if [[ $# -eq 3 ]]; then
  if [[ $3 != "--keep-unreleased-section" ]]; then
    echo "unexpected argument: $3" >&2
    exit 1
  fi
  keep_unreleased_section=true
fi

sdk_version=$(sed -En 's/^val otelSdkVersion = "([0-9]+\.[0-9]+\.[0-9]+)".*/\1/p' dependencyManagement/build.gradle.kts)
if [[ -z $sdk_version ]]; then
  echo "could not determine otelSdkVersion from dependencyManagement/build.gradle.kts" >&2
  exit 1
fi

preamble=$(cat << EOF
This release targets the OpenTelemetry SDK $sdk_version.

Note that many artifacts have the \`-alpha\` suffix attached to their version
number, reflecting that they will continue to have breaking changes. Please see
[VERSIONING.md](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/VERSIONING.md#opentelemetry-java-instrumentation-versioning)
for more details.
EOF
)

# Escape newlines as the literal two-character sequence \n so the text can be
# interpolated into the replacement side of a `sed -E` command.
preamble_escaped=${preamble//$'\n'/\\n}

if [[ $keep_unreleased_section == true ]]; then
  replacement="## Unreleased\n\n## Version $version ($date)\n\n$preamble_escaped"
else
  replacement="## Version $version ($date)\n\n$preamble_escaped"
fi

sed -Ei "s|^## Unreleased$|$replacement|" CHANGELOG.md
