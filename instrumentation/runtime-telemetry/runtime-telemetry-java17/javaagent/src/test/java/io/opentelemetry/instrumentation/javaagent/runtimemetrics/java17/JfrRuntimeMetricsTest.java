/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java17;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import jdk.jfr.FlightRecorder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrRuntimeMetricsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void setUp() {
    try {
      Class.forName("jdk.jfr.FlightRecorder");
    } catch (ClassNotFoundException exception) {
      Assumptions.abort("JFR not present");
    }
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");
  }

  @Test
  void shouldHaveDefaultMetrics() {
    // This should generate some events
    System.gc();

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java17",
        metric -> metric.hasName("jvm.cpu.longlock"),
        metric -> metric.hasName("jvm.cpu.limit"),
        metric -> metric.hasName("jvm.cpu.context_switch"));
  }
}
