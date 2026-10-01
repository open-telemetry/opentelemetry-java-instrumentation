/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JmxRuntimeMetricsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void registeredObserversArePublishedOnBootstrap() throws Exception {
    // loading through the bootstrap loader proves the holder is visible to every class loader
    Class<?> holder =
        Class.forName(
            "io.opentelemetry.javaagent.bootstrap.runtimetelemetry.RuntimeTelemetryObservation",
            true,
            null);
    assertThat(holder.getMethod("registeredJmxObservers").invoke(null))
        .isEqualTo(
            new HashSet<>(asList("jvm.class.count", "jvm.class.loaded", "jvm.class.unloaded")));
  }

  @Test
  void runtimeMetricsAreEnabled() {
    // Force a gc to "ensure" gc metrics
    System.gc();

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        metric -> metric.hasName("jvm.class.loaded"),
        metric -> metric.hasName("jvm.class.unloaded"),
        metric -> metric.hasName("jvm.class.count"),
        metric -> metric.hasName("jvm.cpu.time"),
        metric -> metric.hasName("jvm.cpu.count"),
        metric -> metric.hasName("jvm.cpu.recent_utilization"),
        metric -> metric.hasName("jvm.gc.duration"),
        metric -> metric.hasName("jvm.memory.used"),
        metric -> metric.hasName("jvm.memory.committed"),
        metric -> metric.hasName("jvm.memory.limit"),
        metric -> metric.hasName("jvm.memory.used_after_last_gc"),
        metric -> metric.hasName("jvm.thread.count"));
  }
}
