/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.extension.incubator.metrics.ExtendedDoubleUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleUpDownCounterBuilder;
import java.util.List;

final class ApplicationDoubleUpDownCounterBuilder131 extends ApplicationDoubleUpDownCounterBuilder
    implements ExtendedDoubleUpDownCounterBuilder {

  private final io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder agentBuilder;

  ApplicationDoubleUpDownCounterBuilder131(
      io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public ExtendedDoubleUpDownCounterBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
    ((io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounterBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
