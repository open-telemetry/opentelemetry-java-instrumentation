/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.testing.internal.armeria.client.WebClient;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class PrometheusSmokeTest {

  private static final int PROMETHEUS_PORT = 9090;

  @RegisterExtension
  static final SmokeTestInstrumentationExtension testing =
      SmokeTestInstrumentationExtension.springBoot("20211213.1570880324")
          .env("OTEL_METRICS_EXPORTER", "prometheus")
          .env("OTEL_EXPORTER_PROMETHEUS_PORT", String.valueOf(PROMETHEUS_PORT))
          .extraPorts(PROMETHEUS_PORT)
          .build();

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17})
  void shouldExportMetrics(int jdk) {
    testing.start(jdk);
    testing.client().get("/greeting").aggregate().join();

    WebClient prometheusClient =
        WebClient.of("h1c://localhost:" + testing.getTargetMappedPort(PROMETHEUS_PORT));
    String prometheusData = prometheusClient.get("/metrics").aggregate().join().contentUtf8();

    assertThat(prometheusData).contains("jvm_memory_used");
  }
}
