/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.incubator.metrics.DoubleGauge;
import application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder;
import application.io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleGaugeBuilder;
import java.util.List;

final class ApplicationDoubleGaugeBuilder137 extends ApplicationDoubleGaugeBuilder
    implements ExtendedDoubleGaugeBuilder {

  private final io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder;

  ApplicationDoubleGaugeBuilder137(io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder137(agentBuilder.ofLongs());
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
    };
  }

  @Override
  public ExtendedDoubleGaugeBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
    ((io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
