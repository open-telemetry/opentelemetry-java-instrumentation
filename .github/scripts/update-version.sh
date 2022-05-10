#!/bin/bash -e

sed -Ei "s/[0-9]+\.[0-9]+\.[0-9]+/$1/" version.gradle.kts

# also update versions in these files
# - examples/distro/build.gradle
# - examples/extension/build.gradle

if [[ $version == *-SNAPSHOT ]]; then
  alpha_version=${version//-SNAPSHOT/-alpha-SNAPSHOT}
else
  alpha_version=$version-alpha
fi

sed -Ei "s/(opentelemetryJavaagent *: )\"[^\"]*\"/\1\"$version\"/" examples/distro/build.gradle
sed -Ei "s/(opentelemetryJavaagentAlpha *: )\"[^\"]*\"/\1\"$alpha_version\"/" examples/distro/build.gradle

sed -Ei "s/(opentelemetryJavaagent *: )\"[^\"]*\"/\1\"$version\"/" examples/extension/build.gradle
sed -Ei "s/(opentelemetryJavaagentAlpha *: )\"[^\"]*\"/\1\"$alpha_version\"/" examples/extension/build.gradle

sed -Ei "s/(io.opentelemetry.instrumentation.muzzle-generation\" version )\"[^\"]*\"/\1\"$alpha_version\"/" examples/extension/build.gradle
sed -Ei "s/(io.opentelemetry.instrumentation.muzzle-check\" version )\"[^\"]*\"/\1\"$alpha_version\"/" examples/extension/build.gradle
