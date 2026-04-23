/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import static org.assertj.core.api.Assertions.assertThat;

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
    } catch (ClassNotFoundException ignored) {
      Assumptions.abort("JFR not present");
    }
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");
  }

  @Test
  void shouldHaveDefaultMetrics() {
    boolean legacy =
        Boolean.parseBoolean(
            System.getProperty("otel.instrumentation.runtime-telemetry-java17.enabled"));

    // This should generate some events
    System.gc();

    if (legacy) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.runtime-telemetry-java17",
          metric -> metric.hasName("jvm.cpu.limit"),
          metric -> metric.hasName("jvm.cpu.context_switch"));
    } else {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.runtime-telemetry", metric -> metric.hasName("jvm.cpu.context_switch"));
      assertThat(testing.metrics()).noneMatch(m -> m.getName().equals("jvm.cpu.limit"));
    }
  }
}
