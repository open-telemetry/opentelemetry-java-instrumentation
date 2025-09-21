/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SpringBootWithSamplingSmokeTest {

  private static final double SAMPLER_PROBABILITY = 0.2;
  private static final int NUM_TRIES = 1000;
  private static final int ALLOWED_DEVIATION = (int) (0.1 * NUM_TRIES);

  @RegisterExtension
  static final SmokeTestInstrumentationExtension<Integer> testing =
      SmokeTestInstrumentationExtension.springBoot("20211213.1570880324")
          .env("OTEL_TRACES_SAMPLER", "parentbased_traceidratio")
          .env("OTEL_TRACES_SAMPLER_ARG", String.valueOf(SAMPLER_PROBABILITY))
          .build();

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17})
  void springBootWithProbabilitySamplingEnabled(int jdk) {
    testing.start(jdk);
    for (int i = 1; i <= NUM_TRIES; i++) {
      testing.client().get("/greeting").aggregate().join();
    }

    long actualCount =
        testing.spans().stream().filter(span -> span.getName().endsWith("GET /greeting")).count();
    int expectedCount = (int) (SAMPLER_PROBABILITY * NUM_TRIES);
    Assertions.assertThat(Math.abs(actualCount - expectedCount))
        .isLessThanOrEqualTo(ALLOWED_DEVIATION);
  }
}
