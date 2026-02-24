/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics;

import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.metrics.LongGauge;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

public class ApplicationLongGauge138 implements LongGauge {

  private final io.opentelemetry.api.metrics.LongGauge agentLongGauge;

  protected ApplicationLongGauge138(io.opentelemetry.api.metrics.LongGauge agentLongGauge) {
    this.agentLongGauge = agentLongGauge;
  }

  @Override
  public void set(long value) {
    agentLongGauge.set(value);
  }

  @Override
  public void set(long value, Attributes attributes) {
    agentLongGauge.set(value, Bridging.toAgent(attributes));
  }

  @Override
  public void set(long value, Attributes attributes, Context applicationContext) {
    agentLongGauge.set(
        value,
        Bridging.toAgent(attributes),
        AgentContextStorage.getAgentContext(applicationContext));
  }
}
