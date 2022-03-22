#!/bin/bash -e

gradle_version=$1

for dir in . \
           benchmark-overhead \
           examples/distro \
           examples/extension \
           smoke-tests/images/fake-backend \
           smoke-tests/images/grpc \
           smoke-tests/images/quarkus \
           smoke-tests/images/servlet \
           smoke-tests/images/play \
           smoke-tests/images/spring-boot
do
  (cd $dir && ./gradlew wrapper --gradle-version $gradle_version)
done
