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
import org.assertj.core.api.AbstractIterableAssert;
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

    MeterRegistry testRegistry =
        OpenTelemetryMeterRegistry.builder(GlobalOpenTelemetry.get())
            .setMetricBridgeFilter("jvm.*,process.cpu.usage")
            .build();

    Metrics.addRegistry(testRegistry);
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
