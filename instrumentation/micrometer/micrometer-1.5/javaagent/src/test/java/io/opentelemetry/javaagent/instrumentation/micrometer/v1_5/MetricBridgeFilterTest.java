/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.MetricBridgeFilter;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetricBridgeFilterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void setUpConfig() {

    for (MeterRegistry registry : Metrics.globalRegistry.getRegistries()) {
      Metrics.removeRegistry(registry);
    }

    MetricBridgeFilter testFilter = MetricBridgeFilter.create("jvm.*,process.cpu.usage");

    MeterRegistry testRegistry =
        OpenTelemetryMeterRegistry.builder(GlobalOpenTelemetry.get())
            .setMetricBridgeFilter(testFilter)
            .build();

    Metrics.addRegistry(testRegistry);
  }

  @Test
  void shouldDropConflictingSemanticConventionMetrics() throws InterruptedException {
    Counter jvmMemoryUsed = Metrics.counter("jvm.memory.used");
    Counter jvmGcPause = Metrics.counter("jvm.gc.pause");
    Counter processCpuUsage = Metrics.counter("process.cpu.usage");
    Counter customBusinessMetric = Metrics.counter("application.orders.processed");

    jvmMemoryUsed.increment();
    jvmGcPause.increment();
    processCpuUsage.increment();
    customBusinessMetric.increment();

    Thread.sleep(500);

    boolean hasCustomBusinessMetric = false;
    boolean hasJvmMemoryUsed = false;
    boolean hasJvmGcPause = false;
    boolean hasProcessCpuUsage = false;

    for (MetricData metric : testing.metrics()) {
      switch (metric.getName()) {
        case "application.orders.processed":
          hasCustomBusinessMetric = true;
          break;
        case "jvm.memory.used":
          hasJvmMemoryUsed = true;
          break;
        case "jvm.gc.pause":
          hasJvmGcPause = true;
          break;
        case "process.cpu.usage":
          hasProcessCpuUsage = true;
          break;
        default:
          break;
      }
    }

    Assertions.assertTrue(hasCustomBusinessMetric, "Standard business metrics must be preserved.");
    Assertions.assertFalse(
        hasJvmMemoryUsed,
        "Micrometer jvm.memory.used should be suppressed to prevent semconv conflict.");
    Assertions.assertFalse(
        hasJvmGcPause, "Metrics matching the jvm.* wildcard prefix must be dropped.");
    Assertions.assertFalse(
        hasProcessCpuUsage, "The explicit process.cpu.usage metric must be dropped.");
  }
}
