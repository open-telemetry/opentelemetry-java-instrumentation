#!/bin/bash

# Updates versions in these files
# - examples/distro/build.gradle
# - examples/extension/build.gradle

version=$1

if [[ $version == *-SNAPSHOT ]]; then
  alpha_version=${version//-SNAPSHOT/-alpha-SNAPSHOT}
else
  alpha_version=$version-alpha
fi


sed -ri "s/(opentelemetryJavaagent *: )\"[^\"]*\"/\1\"$version\"/" examples/distro/build.gradle
sed -ri "s/(opentelemetryJavaagentAlpha *: )\"[^\"]*\"/\1\"$alpha_version\"/" examples/distro/build.gradle

sed -ri "s/(opentelemetryJavaagent *: )\"[^\"]*\"/\1\"$version\"/" examples/extension/build.gradle
sed -ri "s/(opentelemetryJavaagentAlpha *: )\"[^\"]*\"/\1\"$alpha_version\"/" examples/extension/build.gradle

sed -ri "s/(io.opentelemetry.instrumentation.muzzle-generation\" version )\"[^\"]*\"/\1\"$alpha_version\"/" examples/extension/build.gradle
sed -ri "s/(io.opentelemetry.instrumentation.muzzle-check\" version )\"[^\"]*\"/\1\"$alpha_version\"/" examples/extension/build.gradle
