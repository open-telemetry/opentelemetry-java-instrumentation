#!/bin/bash -e

version=$1

sed -Ei "s/val otelSdkVersion = \"[^\"]*\"/val otelSdkVersion = \"$version\"/" dependencyManagement/build.gradle.kts

sed -Ei "s/(const val opentelemetrySdkVersion = )\"[^\"]*\"/\1\"$version\"/" examples/distro/buildSrc/src/main/kotlin/Versions.kt

sed -Ei "s/(\"opentelemetrySdk\" to )\"[^\"]*\"/\1\"$version\"/" examples/extension/build.gradle.kts
