/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics;

import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

public class ApplicationLongGauge138 implements application.io.opentelemetry.api.metrics.LongGauge {

  private final LongGauge agentLongGauge;

  protected ApplicationLongGauge138(LongGauge agentLongGauge) {
    this.agentLongGauge = agentLongGauge;
  }

  @Override
  public void set(long value) {
    agentLongGauge.set(value);
  }

  @Override
  public void set(long value, application.io.opentelemetry.api.common.Attributes attributes) {
    agentLongGauge.set(value, Bridging.toAgent(attributes));
  }

  @Override
  public void set(
      long value,
      application.io.opentelemetry.api.common.Attributes attributes,
      application.io.opentelemetry.context.Context applicationContext) {
    agentLongGauge.set(
        value,
        Bridging.toAgent(attributes),
        AgentContextStorage.getAgentContext(applicationContext));
  }
}
