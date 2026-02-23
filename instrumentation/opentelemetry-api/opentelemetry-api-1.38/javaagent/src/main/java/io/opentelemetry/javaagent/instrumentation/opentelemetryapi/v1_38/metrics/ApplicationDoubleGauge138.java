/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics;

import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

public class ApplicationDoubleGauge138
    implements application.io.opentelemetry.api.metrics.DoubleGauge {

  private final DoubleGauge agentDoubleGauge;

  protected ApplicationDoubleGauge138(DoubleGauge agentDoubleGauge) {
    this.agentDoubleGauge = agentDoubleGauge;
  }

  @Override
  public void set(double value) {
    agentDoubleGauge.set(value);
  }

  @Override
  public void set(double value, application.io.opentelemetry.api.common.Attributes attributes) {
    agentDoubleGauge.set(value, Bridging.toAgent(attributes));
  }

  @Override
  public void set(
      double value,
      application.io.opentelemetry.api.common.Attributes attributes,
      application.io.opentelemetry.context.Context applicationContext) {
    agentDoubleGauge.set(
        value,
        Bridging.toAgent(attributes),
        AgentContextStorage.getAgentContext(applicationContext));
  }
}
