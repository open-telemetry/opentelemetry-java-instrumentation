/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrRuntimeMetricsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void setUp() {
    // Use reflection to avoid a compile-time dependency on jdk.jfr (Java 11+), since this test
    // module targets the agent's minimum supported Java version.
    Class<?> flightRecorderClass;
    try {
      flightRecorderClass = Class.forName("jdk.jfr.FlightRecorder");
    } catch (ClassNotFoundException e) {
      Assumptions.abort("JFR not present");
      return;
    }
    boolean available;
    try {
      available = (boolean) flightRecorderClass.getMethod("isAvailable").invoke(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    Assumptions.assumeTrue(available, "JFR not available");
  }

  @Test
  void shouldHaveJfrMetrics() {
    // This should generate some events
    System.gc();

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry", metric -> metric.hasName("jvm.cpu.context_switch"));
  }
}
