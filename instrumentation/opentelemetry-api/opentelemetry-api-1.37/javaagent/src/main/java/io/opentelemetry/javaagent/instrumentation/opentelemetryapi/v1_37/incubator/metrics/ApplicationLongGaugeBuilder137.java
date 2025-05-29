/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.incubator.metrics.ExtendedLongGaugeBuilder;
import application.io.opentelemetry.api.incubator.metrics.LongGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongGaugeBuilder;
import java.util.List;

final class ApplicationLongGaugeBuilder137 extends ApplicationLongGaugeBuilder
    implements ExtendedLongGaugeBuilder {

  private final io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder;

  ApplicationLongGaugeBuilder137(io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder) {
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
    };
  }

  @Override
  public ExtendedLongGaugeBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
    ((io.opentelemetry.api.incubator.metrics.ExtendedLongGaugeBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
