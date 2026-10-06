/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeTelemetryObservationTest {
  @Test
  void configurationRegistersCollectableClassObservers() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    try (OpenTelemetrySdk sdk =
            OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build())
                .build();
        RuntimeTelemetry telemetry = Internal.configure(sdk, true)) {
      Set<String> names = Internal.getRegisteredJmxObservers(telemetry);
      assertThat(names)
          .contains(
              "jvm.class.count",
              "jvm.class.loaded",
              "jvm.class.unloaded",
              "jvm.memory.used",
              "jvm.memory.committed",
              "jvm.memory.limit",
              "jvm.thread.count",
              "jvm.cpu.count")
          .doesNotContain(
              "jvm.buffer.count", "jvm.file_descriptor.count", "jvm.system.cpu.utilization");
      assertThat(reader.collectAllMetrics())
          .extracting(metric -> metric.getName())
          .contains("jvm.class.count", "jvm.memory.used", "jvm.thread.count", "jvm.cpu.count");
      assertThatThrownBy(() -> names.clear()).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  void absentRuntimeReportsNothing() {
    assertThat(Internal.getRegisteredJmxObservers(null)).isEmpty();
  }

  @Test
  void disabledJmxAndClosedRuntimeReportNothing() {
    try (RuntimeTelemetry telemetry =
        RuntimeTelemetry.builder(OpenTelemetry.noop()).disableAllJmx().build()) {
      assertThat(Internal.getRegisteredJmxObservers(telemetry)).isEmpty();
    }
    RuntimeTelemetry telemetry = RuntimeTelemetry.create(OpenTelemetry.noop());
    try {
      assertThat(Internal.getRegisteredJmxObservers(telemetry)).contains("jvm.class.count");
    } finally {
      telemetry.close();
    }
    assertThat(Internal.getRegisteredJmxObservers(telemetry)).isEmpty();
  }
}
