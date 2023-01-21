#!/bin/bash -e

version=$1

sed -Ei "s/val otelSdkVersion = \"[^\"]*\"/val otelSdkVersion = \"$version\"/" dependencyManagement/build.gradle.kts

sed -Ei "s/(opentelemetrySdk *: )\"[^\"]*\"/\1\"$version\"/" examples/distro/build.gradle

sed -Ei "s/(opentelemetrySdk *: )\"[^\"]*\"/\1\"$version\"/" examples/extension/build.gradle
