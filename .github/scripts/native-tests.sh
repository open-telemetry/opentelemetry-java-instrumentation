#!/bin/bash -e

echo "GRAALVM_HOME: $GRAALVM_HOME"
echo "JAVA_HOME: $JAVA_HOME"
java --version
native-image --version
# Testcontainers does not work in some cases with GraalVM native images,
# therefore we're starting a Kafka container manually for the tests
docker compose -f .github/graal-native-docker-compose.yaml up -d
# don't wait for startup - gradle compile takes long enough

EXTRA_ARGS=""
if [[ $TEST_LATEST_DEPS == "true" ]]
then
  EXTRA_ARGS="-PtestLatestDeps=true"
fi

./gradlew $EXTRA_ARGS nativeTest
docker compose -f .github/graal-native-docker-compose.yaml down
