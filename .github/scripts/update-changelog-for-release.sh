#!/bin/bash -e

# Renders pending Towncrier fragments into CHANGELOG.md and consumes them.
# Adds the standard SDK-version and alpha-artifact preamble.
#
# Usage:
#   update-changelog-for-release.sh <version> <date> [--keep-unreleased-section]
#
# With --keep-unreleased-section, the Unreleased heading and fragment-directory
# link are preserved above the new version section.

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

if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ || ! $date =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "expected a release version X.Y.Z and date YYYY-MM-DD" >&2
  exit 1
fi

if [[ $(grep -Fxc '<!-- towncrier release notes start -->' CHANGELOG.md) != 1 ]]; then
  echo "CHANGELOG.md must contain exactly one Towncrier insertion marker" >&2
  exit 1
fi

if grep -Eq "^## Version ${version//./\\.} " CHANGELOG.md; then
  echo "CHANGELOG.md already contains version $version" >&2
  exit 1
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

python -m towncrier build --version "$version" --date "$date" --yes

sed -Ei "s|^## Version ${version//./\\.} \($date\)$|&\n\n$preamble_escaped|" CHANGELOG.md

if [[ $keep_unreleased_section == false ]]; then
  sed -i '/^## Unreleased$/,/^<!-- towncrier release notes start -->$/ {
    /^<!-- towncrier release notes start -->$/!d
  }' CHANGELOG.md
fi

git add CHANGELOG.md
