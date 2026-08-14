/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

/**
 * Verifies that the agent starts up and instruments an application when configured with the
 * generated {@code docs/declarative-configuration-example.yaml}, which lists every declarative
 * configuration option known to the agent. The file is copied onto the test classpath by the {@code
 * processTestResources} task.
 */
@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class DeclarativeConfigurationExampleSmokeTest extends AbstractSmokeTest<Integer> {

  private static final String CONFIG_FILE = "declarative-configuration-example.yaml";
  private static final String AGENT_START_FAILURE = "OpenTelemetry Javaagent failed to start";

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .springBoot()
        .env("OTEL_CONFIG_FILE", CONFIG_FILE)
        .extraResources(ResourceMapping.of(CONFIG_FILE, "/" + CONFIG_FILE));
  }

  @Test
  void springBootStartsWithConfigurationExample() {
    SmokeTestOutput output = start(21);

    AggregatedHttpResponse response = client().get("/greeting").aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);

    // the agent logs this and then lets the application run without any instrumentation, so the
    // application responding above is not enough to tell that the configuration file was accepted
    assertThat(
            output.logLines().filter(line -> line.contains(AGENT_START_FAILURE)).collect(toList()))
        .isEmpty();
  }
}
