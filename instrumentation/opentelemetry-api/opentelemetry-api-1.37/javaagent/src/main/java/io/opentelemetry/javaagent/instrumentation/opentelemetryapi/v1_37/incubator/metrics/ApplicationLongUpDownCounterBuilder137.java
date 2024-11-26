/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongUpDownCounterBuilder;
import java.util.List;

public class ApplicationLongUpDownCounterBuilder137 extends ApplicationLongUpDownCounterBuilder
    implements ExtendedLongUpDownCounterBuilder {

  private final io.opentelemetry.api.metrics.LongUpDownCounterBuilder agentBuilder;

  protected ApplicationLongUpDownCounterBuilder137(
      io.opentelemetry.api.metrics.LongUpDownCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public DoubleUpDownCounterBuilder ofDoubles() {
    return new ApplicationDoubleUpDownCounterBuilder137(agentBuilder.ofDoubles());
  }

  @Override
  public ExtendedLongUpDownCounterBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
    ((io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounterBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
