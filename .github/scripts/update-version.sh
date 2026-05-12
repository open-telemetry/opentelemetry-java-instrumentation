#!/bin/bash -e

version=$1

if [[ $version == *-SNAPSHOT ]]; then
  alpha_version=${version//-SNAPSHOT/-alpha-SNAPSHOT}
else
  alpha_version=${version}-alpha
fi

sed -Ei "s/val stableVersion = \"[^\"]*\"/val stableVersion = \"$version\"/" version.gradle.kts
sed -Ei "s/val alphaVersion = \"[^\"]*\"/val alphaVersion = \"$alpha_version\"/" version.gradle.kts

sed -Ei "s/(const val opentelemetryJavaagentVersion = )\"[^\"]*\"/\1\"$version\"/" examples/distro/buildSrc/src/main/kotlin/Versions.kt
sed -Ei "s/(const val opentelemetryJavaagentAlphaVersion = )\"[^\"]*\"/\1\"$alpha_version\"/" examples/distro/buildSrc/src/main/kotlin/Versions.kt

sed -Ei "s/(io.opentelemetry.instrumentation.muzzle-(generation|check)\"\) version )\"[^\"]*\"/\1\"$alpha_version\"/" examples/distro/settings.gradle.kts

sed -Ei "s/(\"opentelemetryJavaagent\" to )\"[^\"]*\"/\1\"$version\"/" examples/extension/build.gradle.kts
sed -Ei "s/(\"opentelemetryJavaagentAlpha\" to )\"[^\"]*\"/\1\"$alpha_version\"/" examples/extension/build.gradle.kts

sed -Ei "s/(io.opentelemetry.instrumentation.muzzle-(generation|check)\"\) version )\"[^\"]*\"/\1\"$alpha_version\"/" examples/extension/build.gradle.kts

sed -Ei "1 s/(Comparing source compatibility of [a-z-]+)-[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?.jar/\1-$version.jar/" docs/apidiffs/current_vs_latest/*.txt
