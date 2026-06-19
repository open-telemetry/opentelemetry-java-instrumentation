/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MicrometerMetricBridgeFilterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final List<MeterRegistry> originalRegistries = new ArrayList<>();
  private static MeterRegistry testRegistry;

  @BeforeAll
  static void setUpConfig() {

    for (MeterRegistry registry : Metrics.globalRegistry.getRegistries()) {
      originalRegistries.add(registry);
      Metrics.removeRegistry(registry);
    }

    testRegistry =
        OpenTelemetryMeterRegistry.builder(GlobalOpenTelemetry.get())
            .setMetricBridgeFilter("jvm.*,process.cpu.usage")
            .build();

    Metrics.addRegistry(testRegistry);
  }

  @AfterAll
  static void tearDownConfig() {
    if (testRegistry != null) {
      Metrics.removeRegistry(testRegistry);
    }

    for (MeterRegistry registry : originalRegistries) {
      Metrics.addRegistry(registry);
    }
    originalRegistries.clear();
  }

  @Test
  void shouldDropConflictingSemanticConventionMetrics() {
    Counter jvmMemoryUsed = Metrics.counter("jvm.memory.used");
    Counter jvmGcPause = Metrics.counter("jvm.gc.pause");
    Counter processCpuUsage = Metrics.counter("process.cpu.usage");
    Counter customBusinessMetric = Metrics.counter("application.orders.processed");

    jvmMemoryUsed.increment();
    jvmGcPause.increment();
    processCpuUsage.increment();
    customBusinessMetric.increment();

    testing.waitAndAssertMetrics(
        "io.opentelemetry.micrometer-1.5",
        "application.orders.processed",
        AbstractIterableAssert::isNotEmpty);

    assertThat(testing.metrics())
        .extracting(MetricData::getName)
        .doesNotContain("jvm.memory.used", "jvm.gc.pause", "process.cpu.usage");
  }
}
