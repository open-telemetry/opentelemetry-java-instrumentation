/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleCounterBuilder;
import java.util.List;

public class ApplicationDoubleCounterBuilder137 extends ApplicationDoubleCounterBuilder
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounterBuilder {

  private final DoubleCounterBuilder agentBuilder;

  protected ApplicationDoubleCounterBuilder137(DoubleCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounterBuilder
      setAttributesAdvice(
          List<application.io.opentelemetry.api.common.AttributeKey<?>> attributes) {
    ((ExtendedDoubleCounterBuilder) agentBuilder).setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
