/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SpringBootWithSamplingSmokeTest extends AbstractSmokeTest<Integer> {

  private static final double SAMPLER_PROBABILITY = 0.2;
  private static final int NUM_TRIES = 1000;
  private static final int ALLOWED_DEVIATION = (int) (0.1 * NUM_TRIES);

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .springBoot()
        .env("OTEL_TRACES_SAMPLER", "parentbased_traceidratio")
        .env("OTEL_TRACES_SAMPLER_ARG", String.valueOf(SAMPLER_PROBABILITY));
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 25})
  void springBootWithProbabilitySamplingEnabled(int jdk) {
    start(jdk);
    for (int i = 1; i <= NUM_TRIES; i++) {
      client().get("/greeting").aggregate().join();
    }

    long actualCount =
        testing.spans().stream().filter(span -> span.getName().endsWith("GET /greeting")).count();
    int expectedCount = (int) (SAMPLER_PROBABILITY * NUM_TRIES);
    assertThat(Math.abs(actualCount - expectedCount)).isLessThanOrEqualTo(ALLOWED_DEVIATION);
  }
}
