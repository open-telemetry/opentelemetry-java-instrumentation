#!/bin/bash

# Bumps versions in these files
# - version.gradle.kts
# - examples/distro/build.gradle
# - examples/extension/build.gradle

new_version=$1
new_alpha_version=$2

if [[ $new_version == *-SNAPSHOT ]]; then
  sed -ri "s/val snapshot = .*/val snapshot = true/" version.gradle.kts
else
  sed -ri "s/val snapshot = .*/val snapshot = false/" version.gradle.kts
fi

sed -ri "s/[0-9]*\.[0-9]*\.[0-9]*/$new_version/" version.gradle.kts

sed -ri "s/(opentelemetryJavaagent *: )\"[^\"]*\"/\1\"$new_version\"/" examples/distro/build.gradle
sed -ri "s/(opentelemetryJavaagentAlpha *: )\"[^\"]*\"/\1\"$new_alpha_version\"/" examples/distro/build.gradle

sed -ri "s/(opentelemetryJavaagent *: )\"[^\"]*\"/\1\"$new_version\"/" examples/extension/build.gradle
sed -ri "s/(opentelemetryJavaagentAlpha *: )\"[^\"]*\"/\1\"$new_alpha_version\"/" examples/extension/build.gradle

sed -ri "s/(io.opentelemetry.instrumentation.muzzle-generation\" version )\"[^\"]*\"/\1\"$new_alpha_version\"/" examples/extension/build.gradle
sed -ri "s/(io.opentelemetry.instrumentation.muzzle-check\" version )\"[^\"]*\"/\1\"$new_alpha_version\"/" examples/extension/build.gradle
