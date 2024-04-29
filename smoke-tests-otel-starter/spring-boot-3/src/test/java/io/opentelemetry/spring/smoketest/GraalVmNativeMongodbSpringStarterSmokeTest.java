package io.opentelemetry.smoketest;

import org.junit.jupiter.api.condition.EnabledInNativeImage;

/**
 * GraalVM native image doesn't support Testcontainers, so the docker container is started manually
 * before running the tests.
 * In CI, this is done in reusable-native-tests.yml.
 * If you want to run the tests locally, you need to start the container manually:
 * docker run -d -p 27017:27017 --name mongo --rm mongo:latest
 */
@EnabledInNativeImage
public class GraalVmNativeMongodbSpringStarterSmokeTest extends AbstractMongodbSpringStarterSmokeTest {
}
