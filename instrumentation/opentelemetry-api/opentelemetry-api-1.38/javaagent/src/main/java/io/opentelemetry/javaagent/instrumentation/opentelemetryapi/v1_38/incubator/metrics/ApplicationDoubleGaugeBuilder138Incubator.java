/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationDoubleGaugeBuilder138;
import java.util.List;

public class ApplicationDoubleGaugeBuilder138Incubator extends ApplicationDoubleGaugeBuilder138
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder {

  private final DoubleGaugeBuilder agentBuilder;

  protected ApplicationDoubleGaugeBuilder138Incubator(DoubleGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder138Incubator(agentBuilder.ofLongs());
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder
      setAttributesAdvice(
          List<application.io.opentelemetry.api.common.AttributeKey<?>> attributes) {
    ((ExtendedDoubleGaugeBuilder) agentBuilder).setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
