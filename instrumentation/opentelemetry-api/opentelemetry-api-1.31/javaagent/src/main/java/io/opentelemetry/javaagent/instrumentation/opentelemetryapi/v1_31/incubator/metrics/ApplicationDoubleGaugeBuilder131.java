/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.metrics.LongGaugeBuilder;
import application.io.opentelemetry.extension.incubator.metrics.DoubleGauge;
import application.io.opentelemetry.extension.incubator.metrics.ExtendedDoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleGaugeBuilder;
import java.util.List;

final class ApplicationDoubleGaugeBuilder131 extends ApplicationDoubleGaugeBuilder
    implements ExtendedDoubleGaugeBuilder {

  private final io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder;

  ApplicationDoubleGaugeBuilder131(io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder131(agentBuilder.ofLongs());
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
