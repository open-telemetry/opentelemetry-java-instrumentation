/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleUpDownCounterBuilder;
import java.util.List;

public class ApplicationDoubleUpDownCounterBuilder137 extends ApplicationDoubleUpDownCounterBuilder
    implements application.io.opentelemetry.api.incubator.metrics
        .ExtendedDoubleUpDownCounterBuilder {

  private final DoubleUpDownCounterBuilder agentBuilder;

  protected ApplicationDoubleUpDownCounterBuilder137(DoubleUpDownCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounterBuilder
      setAttributesAdvice(
          List<application.io.opentelemetry.api.common.AttributeKey<?>> attributes) {
    ((ExtendedDoubleUpDownCounterBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
