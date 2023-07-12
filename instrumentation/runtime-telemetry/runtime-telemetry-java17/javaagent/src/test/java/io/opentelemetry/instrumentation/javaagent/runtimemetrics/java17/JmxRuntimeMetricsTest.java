/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java17;

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
        "io.opentelemetry.runtime-telemetry-java8",
        metric -> metric.hasName("process.runtime.jvm.classes.loaded"),
        metric -> metric.hasName("process.runtime.jvm.classes.unloaded"),
        metric -> metric.hasName("process.runtime.jvm.classes.current_loaded"),
        metric -> metric.hasName("process.runtime.jvm.system.cpu.load_1m"),
        metric -> metric.hasName("process.runtime.jvm.system.cpu.utilization"),
        metric -> metric.hasName("process.runtime.jvm.cpu.utilization"),
        metric -> metric.hasName("process.runtime.jvm.gc.duration"),
        metric -> metric.hasName("process.runtime.jvm.memory.init"),
        metric -> metric.hasName("process.runtime.jvm.memory.usage"),
        metric -> metric.hasName("process.runtime.jvm.memory.committed"),
        metric -> metric.hasName("process.runtime.jvm.memory.limit"),
        metric -> metric.hasName("process.runtime.jvm.memory.usage_after_last_gc"),
        metric -> metric.hasName("process.runtime.jvm.threads.count"),
        metric -> metric.hasName("process.runtime.jvm.buffer.limit"),
        metric -> metric.hasName("process.runtime.jvm.buffer.count"),
        metric -> metric.hasName("process.runtime.jvm.buffer.usage"));
  }
}
