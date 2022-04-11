#!/bin/bash

# Bumps versions in these files
# - version.gradle.kts
# - examples/distro/build.gradle
# - examples/extension/build.gradle

current_version=$1
current_alpha_version=$2
new_version=$3
new_alpha_version=$4

echo "updating from $current_version to $new_version and from $current_alpha_version to $new_alpha_version"

sed -ri "s/$current_version/$new_version/" version.gradle.kts

sed -ri "s/(opentelemetryJavaagent *: \")$current_version/\1$new_version/" examples/distro/build.gradle
sed -ri "s/(opentelemetryJavaagentAlpha *: \")$current_alpha_version/\1$new_alpha_version/" examples/distro/build.gradle

sed -ri "s/(opentelemetryJavaagent *: \")$current_version/\1$new_version/" examples/extension/build.gradle
sed -ri "s/(opentelemetryJavaagentAlpha *: \")$current_alpha_version/\1$new_alpha_version/" examples/extension/build.gradle

sed -ri "s/(io.opentelemetry.instrumentation.muzzle-generation\" version \")$current_alpha_version/\1$new_alpha_version/" examples/extension/build.gradle
sed -ri "s/(io.opentelemetry.instrumentation.muzzle-check\" version \")$current_alpha_version/\1$new_alpha_version/" examples/extension/build.gradle
