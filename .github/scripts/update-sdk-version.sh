#!/bin/bash -e

version=$1

alpha_version=${version}-alpha

sed -Ei "s/val otelVersion = \"[^\"]*\"/val otelVersion = \"$version\"/" dependencyManagement/build.gradle.kts

sed -Ei "s/(opentelemetry *: )\"[^\"]*\"/\1\"$version\"/" examples/distro/build.gradle
sed -Ei "s/(opentelemetryAlpha *: )\"[^\"]*\"/\1\"$alpha_version\"/" examples/distro/build.gradle

sed -Ei "s/(opentelemetry *: )\"[^\"]*\"/\1\"$version\"/" examples/extension/build.gradle
sed -Ei "s/(opentelemetryAlpha *: )\"[^\"]*\"/\1\"$alpha_version\"/" examples/extension/build.gradle
