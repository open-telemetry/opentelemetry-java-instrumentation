#!/bin/bash -e

gradle_version=$1

for dir in . \
           benchmark-overhead \
           gradle-plugins \
           examples/distro \
           examples/extension
do
  (cd $dir && ./gradlew wrapper --gradle-version "$gradle_version")
done
