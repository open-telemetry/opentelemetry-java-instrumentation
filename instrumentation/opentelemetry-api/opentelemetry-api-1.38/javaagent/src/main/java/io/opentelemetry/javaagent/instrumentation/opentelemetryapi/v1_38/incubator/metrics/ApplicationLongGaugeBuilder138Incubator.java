/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.incubator.metrics.ExtendedLongGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationLongGaugeBuilder138;
import java.util.List;

public class ApplicationLongGaugeBuilder138Incubator extends ApplicationLongGaugeBuilder138
    implements ExtendedLongGaugeBuilder {

  private final io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder;

  protected ApplicationLongGaugeBuilder138Incubator(
      io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public ExtendedLongGaugeBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
    ((io.opentelemetry.api.incubator.metrics.ExtendedLongGaugeBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
