/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.oshi.v5_0.AbstractSystemMetricsTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
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

  @Override
  @SuppressWarnings("deprecation") // overriding a deprecated abstract method
  protected String scopeName() {
    return v3Preview() ? "io.opentelemetry.oshi-5.0" : "io.opentelemetry.oshi";
  }

  @Test
  @EnabledIf("processMetricsDisabled")
  @SuppressWarnings("deprecation") // using the legacy scopeName() bridge
  void noProcessMetricsWhenDisabled() {
    testing.waitAndAssertMetrics(
        scopeName(), "system.memory.usage", metrics -> metrics.isNotEmpty());

    assertThat(testing.metrics())
        .noneMatch(metric -> metric.getName().equals("runtime.java.memory"))
        .noneMatch(metric -> metric.getName().equals("runtime.java.cpu_time"));
  }

  static boolean processMetricsDisabled() {
    return v3Preview() || !Boolean.getBoolean("testExperimental");
  }
}
