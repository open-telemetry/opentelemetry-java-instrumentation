/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.testing.internal.armeria.client.WebClient;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class PrometheusSmokeTest extends AbstractSmokeTest<Integer> {

  private static final int PROMETHEUS_PORT = 9090;

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .springBoot()
        .env("OTEL_METRICS_EXPORTER", "prometheus")
        .env("OTEL_EXPORTER_PROMETHEUS_PORT", String.valueOf(PROMETHEUS_PORT))
        .extraPorts(PROMETHEUS_PORT);
  }

  @ParameterizedTest
  // restore after image update
  // @ValueSource(ints = {8, 11, 17, 21, 25})
  @ValueSource(ints = {8, 21, 25})
  void shouldExportMetrics(int jdk) {
    start(jdk);
    client().get("/greeting").aggregate().join();

    WebClient prometheusClient = WebClient.of("h1c://localhost:" + getMappedPort(PROMETHEUS_PORT));
    String prometheusData = prometheusClient.get("/metrics").aggregate().join().contentUtf8();

    assertThat(prometheusData).contains("jvm_memory_used");
  }
}
