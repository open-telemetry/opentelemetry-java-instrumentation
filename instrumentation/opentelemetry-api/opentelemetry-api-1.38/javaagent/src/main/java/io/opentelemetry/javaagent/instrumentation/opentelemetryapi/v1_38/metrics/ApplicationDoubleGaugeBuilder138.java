/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics;

import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.metrics.DoubleGauge;
import application.io.opentelemetry.api.metrics.LongGaugeBuilder;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleGaugeBuilder;

public class ApplicationDoubleGaugeBuilder138 extends ApplicationDoubleGaugeBuilder {

  private final io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder;

  protected ApplicationDoubleGaugeBuilder138(
      io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder138(agentBuilder.ofLongs());
  }

  @Override
  public DoubleGauge build() {
    io.opentelemetry.api.metrics.DoubleGauge agentDoubleGauge = agentBuilder.build();
    return new DoubleGauge() {

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
    };
  }
}
