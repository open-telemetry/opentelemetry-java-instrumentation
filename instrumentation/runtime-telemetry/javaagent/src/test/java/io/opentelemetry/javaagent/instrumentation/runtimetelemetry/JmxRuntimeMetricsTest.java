/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.JvmAttributes.JVM_GC_ACTION;
import static io.opentelemetry.semconv.JvmAttributes.JVM_GC_NAME;
import static io.opentelemetry.semconv.incubating.JvmIncubatingAttributes.JVM_GC_CAUSE;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JmxRuntimeMetricsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void runtimeMetricsAreEnabled() {
    // Force a gc to "ensure" gc metrics
    System.gc();

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry",
        metric -> metric.hasName("jvm.class.loaded"),
        metric -> metric.hasName("jvm.class.unloaded"),
        metric -> metric.hasName("jvm.class.count"),
        metric -> metric.hasName("jvm.cpu.time"),
        metric -> metric.hasName("jvm.cpu.count"),
        metric -> metric.hasName("jvm.cpu.recent_utilization"),
        metric ->
            metric
                .hasName("jvm.gc.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point.hasAttributesSatisfyingExactly(
                                    satisfies(JVM_GC_NAME, val -> val.isNotBlank()),
                                    satisfies(JVM_GC_ACTION, val -> val.isNotBlank()),
                                    satisfies(JVM_GC_CAUSE, val -> val.isNotBlank())))),
        metric -> metric.hasName("jvm.memory.used"),
        metric -> metric.hasName("jvm.memory.committed"),
        metric -> metric.hasName("jvm.memory.limit"),
        metric -> metric.hasName("jvm.memory.used_after_last_gc"),
        metric -> metric.hasName("jvm.thread.count"));
  }
}
