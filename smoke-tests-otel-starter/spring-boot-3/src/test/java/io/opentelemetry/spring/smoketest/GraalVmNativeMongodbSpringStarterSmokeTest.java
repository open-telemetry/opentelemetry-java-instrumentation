/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.junit.jupiter.api.condition.EnabledInNativeImage;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * GraalVM native image doesn't support Testcontainers in our case, so the docker container is
 * started manually before running the tests.
 *
 * <p>In other cases, it does work, e.g. <a
 * href="https://info.michael-simons.eu/2023/10/25/run-your-integration-tests-against-testcontainers-with-graalvm-native-image/">here</a>,
 * it's not yet clear why it doesn't work in our case.
 *
 * <p>In CI, this is done in reusable-native-tests.yml. If you want to run the tests locally, you
 * need to start the container manually: see .github/workflows/reusable-native-tests.yml for the
 * command.
 */
@SpringBootTest(
    classes = {OtelSpringStarterSmokeTestApplication.class, SpringSmokeOtelConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledInNativeImage // see JvmMongodbSpringStarterSmokeTest for the JVM test
@RequiresDockerCompose
public class GraalVmNativeMongodbSpringStarterSmokeTest
    extends AbstractMongodbSpringStarterSmokeTest {}
