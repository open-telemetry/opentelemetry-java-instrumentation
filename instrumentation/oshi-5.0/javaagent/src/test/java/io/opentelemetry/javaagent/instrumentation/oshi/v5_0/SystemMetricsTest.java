/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi.v5_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.oshi.v5_0.AbstractSystemMetricsTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SystemMetricsTest extends AbstractSystemMetricsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected void registerMetrics() {}

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void noProcessMetrics() {
    testing.waitAndAssertMetrics(
        scopeName(), "system.memory.usage", metrics -> metrics.isNotEmpty());

    assertThat(testing.metrics())
        .noneMatch(metric -> metric.getName().equals("runtime.java.memory"))
        .noneMatch(metric -> metric.getName().equals("runtime.java.cpu_time"));
  }
}
