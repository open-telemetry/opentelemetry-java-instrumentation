#!/bin/bash -e

# Emits the CHANGELOG.md preamble that appears under each release heading.

sdk_version=$(grep -Po "val otelSdkVersion = \"\K[0-9]+\.[0-9]+\.[0-9]+" dependencyManagement/build.gradle.kts)

text=$(cat << EOF
This release targets the OpenTelemetry SDK $sdk_version.

Note that many artifacts have the \`-alpha\` suffix attached to their version
number, reflecting that they will continue to have breaking changes. Please see
[VERSIONING.md](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/VERSIONING.md#opentelemetry-java-instrumentation-versioning)
for more details.
EOF
)

# Replace real newlines with the literal two-character sequence \n so the output
# can be interpolated directly into the replacement side of a `sed -E` command.
printf '%s' "${text//$'\n'/\\n}"
