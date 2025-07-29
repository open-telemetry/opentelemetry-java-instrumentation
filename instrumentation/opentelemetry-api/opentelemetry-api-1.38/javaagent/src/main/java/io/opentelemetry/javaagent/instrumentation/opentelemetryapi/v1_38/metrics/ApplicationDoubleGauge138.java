/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics;

import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.metrics.DoubleGauge;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

public class ApplicationDoubleGauge138 implements DoubleGauge {

  private final io.opentelemetry.api.metrics.DoubleGauge agentDoubleGauge;

  protected ApplicationDoubleGauge138(io.opentelemetry.api.metrics.DoubleGauge agentDoubleGauge) {
    this.agentDoubleGauge = agentDoubleGauge;
  }

  @Override
  public void set(double value) {
    agentDoubleGauge.set(value);
  }

  @Override
  public void set(double value, Attributes attributes) {
    agentDoubleGauge.set(value, Bridging.toAgent(attributes));
  }

  @Override
  public void set(double value, Attributes attributes, Context applicationContext) {
    agentDoubleGauge.set(
        value,
        Bridging.toAgent(attributes),
        AgentContextStorage.getAgentContext(applicationContext));
  }
}
