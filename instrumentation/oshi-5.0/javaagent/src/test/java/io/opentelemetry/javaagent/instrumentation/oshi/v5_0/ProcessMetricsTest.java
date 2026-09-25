/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi.v5_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.oshi.v5_0.AbstractProcessMetricsTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

class ProcessMetricsTest extends AbstractProcessMetricsTest {

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
    return "io.opentelemetry.oshi";
  }

  @Test
  @EnabledIfSystemProperty(named = "testExperimental", matches = "true")
  @SuppressWarnings("deprecation") // using the legacy scopeName() bridge
  void processMetricsHaveNoSchema() {
    testing.waitAndAssertMetrics(
        scopeName(),
        "runtime.java.memory",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl()).isNull()));
  }
}
