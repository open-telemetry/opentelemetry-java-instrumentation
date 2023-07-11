/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java17;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JmxRuntimeMetricsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void runtimeMetricsAreEnabled() {
    // Force a gc to "ensure" gc metrics
    System.gc();

    await()
        .untilAsserted(
            () -> {
              List<MetricData> metrics =
                  testing.instrumentationMetrics("io.opentelemetry.runtime-telemetry-java8");
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.classes.loaded"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.classes.unloaded"));
              assertThat(metrics)
                  .anyMatch(hasMetricName("process.runtime.jvm.classes.current_loaded"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.system.cpu.load_1m"));
              assertThat(metrics)
                  .anyMatch(hasMetricName("process.runtime.jvm.system.cpu.utilization"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.cpu.utilization"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.gc.duration"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.memory.init"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.memory.usage"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.memory.committed"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.memory.limit"));
              assertThat(metrics)
                  .anyMatch(hasMetricName("process.runtime.jvm.memory.usage_after_last_gc"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.threads.count"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.buffer.limit"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.buffer.count"));
              assertThat(metrics).anyMatch(hasMetricName("process.runtime.jvm.buffer.usage"));
            });
  }

  private Predicate<? super MetricData> hasMetricName(String name) {
    return metric -> metric.getName().equals(name);
  }
}
