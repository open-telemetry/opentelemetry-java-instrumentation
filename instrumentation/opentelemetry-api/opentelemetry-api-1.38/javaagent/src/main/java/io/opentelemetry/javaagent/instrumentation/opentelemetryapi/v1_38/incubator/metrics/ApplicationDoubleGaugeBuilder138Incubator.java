/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder;
import application.io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationDoubleGaugeBuilder138;
import java.util.List;

public class ApplicationDoubleGaugeBuilder138Incubator extends ApplicationDoubleGaugeBuilder138
    implements ExtendedDoubleGaugeBuilder {

  private final io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder;

  protected ApplicationDoubleGaugeBuilder138Incubator(
      io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder138Incubator(agentBuilder.ofLongs());
  }

  @Override
  public ExtendedDoubleGaugeBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
    ((io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
