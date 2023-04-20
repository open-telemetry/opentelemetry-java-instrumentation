/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimetelemetryjfr;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.testing.AgentTestRunner;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JfrRuntimeMetricsTest {
  @SafeVarargs
  private static void waitAndAssertMetrics(Consumer<MetricAssert>... assertions) {
    await()
        .untilAsserted(
            () -> {
              Collection<MetricData> metrics = AgentTestRunner.instance().getExportedMetrics();
              assertThat(metrics).isNotEmpty();
              for (Consumer<MetricAssert> assertion : assertions) {
                assertThat(metrics).anySatisfy(metric -> assertion.accept(assertThat(metric)));
              }
            });
  }

  @BeforeAll
  static void setUp() {
    try {
      Class.forName("jdk.jfr.consumer.RecordingStream");
    } catch (ClassNotFoundException exception) {
      Assumptions.abort("JFR not present");
    }
  }

  @Test
  void shouldHaveDefaultMetrics() {
    // This should generate some events
    System.gc();

    waitAndAssertMetrics(
        metric -> metric.hasName("process.runtime.jvm.cpu.longlock"),
        metric -> metric.hasName("process.runtime.jvm.cpu.limit"),
        metric -> metric.hasName("process.runtime.jvm.cpu.context_switch"));
  }
}
