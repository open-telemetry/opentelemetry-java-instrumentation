/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleCounterBuilder;
import java.util.List;

final class ApplicationDoubleCounterBuilder131 extends ApplicationDoubleCounterBuilder
    implements application.io.opentelemetry.extension.incubator.metrics
        .ExtendedDoubleCounterBuilder {

  private final DoubleCounterBuilder agentBuilder;

  ApplicationDoubleCounterBuilder131(DoubleCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.extension.incubator.metrics.ExtendedDoubleCounterBuilder
      setAttributesAdvice(
          List<application.io.opentelemetry.api.common.AttributeKey<?>> attributes) {
    ((ExtendedDoubleCounterBuilder) agentBuilder).setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
