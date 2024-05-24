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
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongGaugeBuilder;

public class ApplicationLongGaugeBuilder138 extends ApplicationLongGaugeBuilder {

  private final io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder;

  protected ApplicationLongGaugeBuilder138(
      io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongGauge build() {
    io.opentelemetry.api.metrics.LongGauge agentLongGauge = agentBuilder.build();
    return new LongGauge() {
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
    };
  }
}
